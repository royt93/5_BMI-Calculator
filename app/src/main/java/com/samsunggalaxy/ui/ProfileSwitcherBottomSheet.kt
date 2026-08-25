package com.samsunggalaxy.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.setFragmentResult
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.samsunggalaxy.R
import com.samsunggalaxy.data.AppDatabase
import com.samsunggalaxy.data.BmiRepository
import com.samsunggalaxy.data.Profile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Profile switcher + CRUD (EPIC-05 T05.1/T05.2). Backend (`ProfileDao`/`BmiRepository`) has
 * supported multiple profiles since the Room schema was designed — this is the first UI
 * that ever calls it. Switching, creating, renaming, and deleting all emit [REQUEST_KEY] so
 * the host (MainAct) can refresh profile-scoped UI (chip, streak, badges) without polling.
 */
class ProfileSwitcherBottomSheet : BottomSheetDialogFragment() {

    private lateinit var repository: BmiRepository
    private lateinit var adapter: ProfileAdapter

    override fun getTheme(): Int = com.google.android.material.R.style.ThemeOverlay_Material3_BottomSheetDialog

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_profile_switcher, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val ctx = context ?: return
        val database = AppDatabase.getDatabase(ctx)
        repository = BmiRepository(database.bmiDao(), database.profileDao(), database.bodyMeasurementDao())

        view.findViewById<View>(R.id.ivCloseProfileSwitcher)?.setOnClickListener { dismiss() }

        adapter = ProfileAdapter(
            onSwitch = { profile -> switchTo(profile) },
            onRename = { profile -> showNameDialog(existing = profile) },
            onDelete = { profile -> confirmDelete(profile) }
        )
        val rv = view.findViewById<RecyclerView>(R.id.rvProfiles) ?: return
        rv.layoutManager = LinearLayoutManager(ctx)
        rv.adapter = adapter

        repository.getAllProfiles().observe(viewLifecycleOwner) { profiles ->
            adapter.submitList(profiles)
        }

        view.findViewById<View>(R.id.rowAddProfile)?.setOnClickListener {
            showNameDialog(existing = null)
        }
    }

    private fun switchTo(profile: Profile) {
        if (profile.isCurrent) return
        val ctx = context
        lifecycleScope.launch(Dispatchers.IO) {
            repository.setCurrentProfile(profile.id)
            notifyChanged()
            if (ctx != null) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(ctx, getString(R.string.profile_switched_toast, profile.name), Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun confirmDelete(profile: Profile) {
        val ctx = context ?: return
        lifecycleScope.launch(Dispatchers.IO) {
            val count = repository.getProfileCount()
            kotlinx.coroutines.withContext(Dispatchers.Main) {
                if (count <= 1) {
                    Toast.makeText(ctx, getString(R.string.profile_delete_last_error), Toast.LENGTH_SHORT).show()
                    return@withContext
                }
                MaterialAlertDialogBuilder(ctx)
                    .setTitle(getString(R.string.profile_delete_confirm_title))
                    .setMessage(getString(R.string.profile_delete_confirm_message, profile.name))
                    .setPositiveButton(getString(R.string.profile_delete)) { _, _ -> deleteProfile(profile) }
                    .setNegativeButton(getString(R.string.cancel), null)
                    .show()
            }
        }
    }

    private fun deleteProfile(profile: Profile) {
        val ctx = context ?: return
        lifecycleScope.launch(Dispatchers.IO) {
            repository.deleteProfileWithRecords(profile)
            // No FK cascade covers SharedPrefs — clear the streak/badge files too, or they're
            // orphaned on disk forever (autoIncrement ids are never reused, so nothing else
            // will ever read `streak_prefs_<id>`/`badge_prefs_<id>` again after this).
            StreakManager.clearProfileData(ctx, profile.id)
            BadgeManager.clearProfileData(ctx, profile.id)
            if (profile.isCurrent) {
                // Deleted the active profile — fall back to whichever profile remains.
                val remaining = repository.getAllProfilesOnce()
                remaining.firstOrNull()?.let { repository.setCurrentProfile(it.id) }
            }
            notifyChanged()
        }
    }

    private fun showNameDialog(existing: Profile?) {
        val ctx = context ?: return
        val dialogView = LayoutInflater.from(ctx).inflate(R.layout.dialog_profile_name, null)
        val etName = dialogView.findViewById<TextInputEditText>(R.id.etProfileName)
        etName.setText(existing?.name ?: "")

        MaterialAlertDialogBuilder(ctx)
            .setTitle(getString(if (existing == null) R.string.profile_name_dialog_title_create else R.string.profile_name_dialog_title_rename))
            .setView(dialogView)
            .setPositiveButton(getString(R.string.save)) { _, _ ->
                val name = etName.text.toString().trim()
                if (name.isEmpty()) {
                    Toast.makeText(ctx, getString(R.string.profile_name_empty_error), Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                if (existing == null) createProfile(name) else renameProfile(existing, name)
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    private fun createProfile(name: String) {
        lifecycleScope.launch(Dispatchers.IO) {
            val newId = repository.insertProfile(Profile(name = name, isCurrent = false))
            // New profile becomes the active one — matches "add a family member and start
            // tracking them right away" flow, rather than silently sitting unused.
            repository.setCurrentProfile(newId)
            notifyChanged()
        }
    }

    private fun renameProfile(profile: Profile, newName: String) {
        lifecycleScope.launch(Dispatchers.IO) {
            repository.updateProfile(profile.copy(name = newName))
            notifyChanged()
        }
    }

    private suspend fun notifyChanged() {
        kotlinx.coroutines.withContext(Dispatchers.Main) {
            if (isAdded) setFragmentResult(REQUEST_KEY, Bundle())
        }
    }

    companion object {
        const val TAG = "ProfileSwitcherBottomSheet"
        const val REQUEST_KEY = "profile_switcher_result"
    }
}

private class ProfileAdapter(
    private val onSwitch: (Profile) -> Unit,
    private val onRename: (Profile) -> Unit,
    private val onDelete: (Profile) -> Unit
) : androidx.recyclerview.widget.ListAdapter<Profile, ProfileAdapter.ViewHolder>(
    object : androidx.recyclerview.widget.DiffUtil.ItemCallback<Profile>() {
        override fun areItemsTheSame(oldItem: Profile, newItem: Profile) = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: Profile, newItem: Profile) = oldItem == newItem
    }
) {
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_profile, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val profile = getItem(position)
        holder.tvName.text = profile.name
        holder.tvCurrentLabel.visibility = if (profile.isCurrent) View.VISIBLE else View.GONE
        holder.itemView.setOnClickListener { onSwitch(profile) }
        holder.ivRename.setOnClickListener { onRename(profile) }
        holder.ivDelete.setOnClickListener { onDelete(profile) }
    }

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvName: android.widget.TextView = view.findViewById(R.id.tvProfileName)
        val tvCurrentLabel: android.widget.TextView = view.findViewById(R.id.tvProfileCurrentLabel)
        val ivRename: View = view.findViewById(R.id.ivProfileRename)
        val ivDelete: View = view.findViewById(R.id.ivProfileDelete)
    }
}
