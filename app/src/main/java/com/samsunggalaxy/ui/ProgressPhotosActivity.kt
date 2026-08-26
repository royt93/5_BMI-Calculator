package com.samsunggalaxy.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.samsunggalaxy.BaseActivity
import com.samsunggalaxy.R
import com.samsunggalaxy.data.AppDatabase
import com.samsunggalaxy.data.BmiRecord
import com.samsunggalaxy.data.BmiRepository
import com.samsunggalaxy.photo.PhotoStorageHelper
import com.samsunggalaxy.sdkadbmob.UIUtils
import com.samsunggalaxy.utils.PreferencesManager
import com.samsunggalaxy.utils.UnitFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Idea I1 — Progress Photo Timeline. Grid of every weigh-in that has a photo attached (see
 * ResultAct's Add Photo card); tap two to select, then Compare shows them side by side with
 * date + weight captions. All photos live in this app's private storage (PhotoStorageHelper) —
 * nothing here ever touches MediaStore or a network call.
 */
class ProgressPhotosActivity : BaseActivity() {

    private var repository: BmiRepository? = null
    private var recyclerView: RecyclerView? = null
    private var emptyStateContainer: View? = null
    private var btnCompare: View? = null
    private var compareContainer: View? = null

    private var unitSystem: String = UnitFormatter.METRIC
    private var records: List<BmiRecord> = emptyList()
    // LinkedHashSet — oldest selection is dropped first when a 3rd photo is tapped.
    private val selectedIds = LinkedHashSet<Long>()
    private var adapter: PhotoGridAdapter? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        UIUtils.setupEdgeToEdge1(window)
        setContentView(R.layout.a_progress_photos)
        UIUtils.setupEdgeToEdge2(
            rootView = findViewById(R.id.layoutRoot),
            paddingTop = true,
            paddingBottom = true
        )

        val database = AppDatabase.getDatabase(this)
        repository = BmiRepository(database.bmiDao(), database.profileDao(), database.bodyMeasurementDao())

        val recycler = findViewById<RecyclerView>(R.id.rvPhotos).also { recyclerView = it }
        recycler.layoutManager = GridLayoutManager(this, 3)
        emptyStateContainer = findViewById(R.id.emptyStateContainer)
        val compareButton = findViewById<View>(R.id.btnCompare).also { btnCompare = it }
        compareContainer = findViewById(R.id.compareContainer)

        findViewById<View>(R.id.ivBack).setOnClickListener { onBackPressedForScreen() }
        findViewById<View>(R.id.cvCloseCompare).setOnClickListener { compareContainer?.isVisible = false }
        compareButton.setOnClickListener { showComparison() }

        onBackPressedDispatcher.addCallback(this, object : androidx.activity.OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = onBackPressedForScreen()
        })

        loadPhotos()
    }

    private fun onBackPressedForScreen() {
        if (compareContainer?.isVisible == true) {
            compareContainer?.isVisible = false
        } else {
            finish()
        }
    }

    private fun loadPhotos() {
        val repo = repository ?: return
        lifecycleScope.launch(Dispatchers.IO) {
            unitSystem = PreferencesManager(this@ProgressPhotosActivity).unitSystem.first()
            val profile = repo.getCurrentProfile()
            val withPhotos = profile?.let { repo.getRecordsWithPhotos(it.id) } ?: emptyList()

            withContext(Dispatchers.Main) {
                if (isFinishing || isDestroyed) return@withContext
                records = withPhotos
                val recycler = recyclerView ?: return@withContext
                val empty = emptyStateContainer ?: return@withContext
                if (withPhotos.isEmpty()) {
                    recycler.isVisible = false
                    empty.isVisible = true
                } else {
                    recycler.isVisible = true
                    empty.isVisible = false
                    adapter = PhotoGridAdapter(withPhotos, selectedIds) { record -> toggleSelection(record) }
                    recycler.adapter = adapter
                }
            }
        }
    }

    private fun toggleSelection(record: BmiRecord) {
        if (selectedIds.contains(record.id)) {
            selectedIds.remove(record.id)
        } else {
            if (selectedIds.size >= 2) {
                val oldest = selectedIds.first()
                selectedIds.remove(oldest)
            }
            selectedIds.add(record.id)
        }
        adapter?.notifyDataSetChanged()
        btnCompare?.isEnabled = selectedIds.size == 2
    }

    private fun showComparison() {
        val selected = records.filter { selectedIds.contains(it.id) }.sortedBy { it.timestamp }
        if (selected.size != 2) return
        val (before, after) = selected[0] to selected[1]

        bindComparePhoto(R.id.ivCompareBefore, R.id.tvCompareBeforeCaption, before)
        bindComparePhoto(R.id.ivCompareAfter, R.id.tvCompareAfterCaption, after)
        compareContainer?.isVisible = true
    }

    private fun bindComparePhoto(imageViewId: Int, captionId: Int, record: BmiRecord) {
        val imageView = findViewById<ImageView>(imageViewId)
        val caption = findViewById<TextView>(captionId)
        record.photoPath?.let { path ->
            imageView.setImageBitmap(PhotoStorageHelper.decodeThumbnail(path, maxDimension = COMPARE_PHOTO_DIMENSION))
        }
        val dateFormat = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
        caption.text = "${dateFormat.format(Date(record.timestamp))} • ${UnitFormatter.formatWeight(record.weight, unitSystem)}"
    }

    private companion object {
        const val COMPARE_PHOTO_DIMENSION = 800
    }
}

private class PhotoGridAdapter(
    private val items: List<BmiRecord>,
    private val selectedIds: Set<Long>,
    private val onToggle: (BmiRecord) -> Unit
) : RecyclerView.Adapter<PhotoGridAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val ivPhoto: ImageView = view.findViewById(R.id.ivPhoto)
        val viewSelectedOverlay: View = view.findViewById(R.id.viewSelectedOverlay)
        val tvSelectedCheck: View = view.findViewById(R.id.tvSelectedCheck)
        val tvPhotoDate: TextView = view.findViewById(R.id.tvPhotoDate)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_progress_photo, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val record = items[position]
        val path = record.photoPath
        if (path != null) {
            holder.ivPhoto.setImageBitmap(PhotoStorageHelper.decodeThumbnail(path))
        }
        val isSelected = selectedIds.contains(record.id)
        holder.viewSelectedOverlay.isVisible = isSelected
        holder.tvSelectedCheck.isVisible = isSelected
        holder.tvPhotoDate.text = SimpleDateFormat("MMM dd", Locale.getDefault()).format(Date(record.timestamp))
        holder.itemView.setOnClickListener { onToggle(record) }
    }

    override fun getItemCount(): Int = items.size
}
