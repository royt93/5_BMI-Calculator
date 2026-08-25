package com.samsunggalaxy.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.samsunggalaxy.R
import com.samsunggalaxy.data.AppDatabase
import com.samsunggalaxy.data.BmiRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class AchievementsBottomSheet : BottomSheetDialogFragment() {

    override fun getTheme(): Int = com.google.android.material.R.style.ThemeOverlay_Material3_BottomSheetDialog

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_achievements, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val ctx = context ?: return

        // Close button
        view.findViewById<View>(R.id.ivCloseAchievements)?.setOnClickListener { dismiss() }

        // Badges are scoped per profile (EPIC-05) — fetch the current one before loading.
        lifecycleScope.launch(Dispatchers.IO) {
            val database = AppDatabase.getDatabase(ctx)
            val repository = BmiRepository(database.bmiDao(), database.profileDao(), database.bodyMeasurementDao())
            val profileId = repository.getCurrentProfile()?.id ?: 1L

            val earnedCount = BadgeManager.getEarnedCount(ctx, profileId)
            val totalCount = BadgeManager.Badge.values().size
            val badges = BadgeManager.Badge.values().map { badge ->
                BadgeAdapter.BadgeItem(
                    badge = badge,
                    earned = BadgeManager.isEarned(ctx, profileId, badge),
                    earnedDate = BadgeManager.getEarnedDate(ctx, profileId, badge)
                )
            }

            kotlinx.coroutines.withContext(Dispatchers.Main) {
                if (!isAdded) return@withContext
                view.findViewById<TextView>(R.id.tvAchievementsTitle)?.text =
                    getString(R.string.achievements_title, earnedCount, totalCount)

                val rv = view.findViewById<RecyclerView>(R.id.rvBadges) ?: return@withContext
                rv.layoutManager = GridLayoutManager(ctx, 2)
                rv.adapter = BadgeAdapter(badges)
            }
        }
    }

    companion object {
        const val TAG = "AchievementsBottomSheet"
    }
}
