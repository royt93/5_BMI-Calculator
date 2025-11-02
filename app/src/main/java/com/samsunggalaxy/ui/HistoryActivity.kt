package com.samsunggalaxy.ui

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.formatter.ValueFormatter
import com.samsunggalaxy.BaseActivity
import com.samsunggalaxy.R
import com.samsunggalaxy.data.AppDatabase
import com.samsunggalaxy.data.BmiRecord
import com.samsunggalaxy.data.BmiRepository
import com.samsunggalaxy.sdkadbmob.UIUtils
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class HistoryActivity : BaseActivity() {
    private lateinit var repository: BmiRepository
    private lateinit var recyclerView: RecyclerView
    private lateinit var lineChart: LineChart
    private lateinit var adapter: HistoryAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        UIUtils.setupEdgeToEdge1(window)
        setContentView(R.layout.a_history)
        UIUtils.setupEdgeToEdge2(
            rootView = findViewById(R.id.layoutRoot),
            paddingTop = true,
            paddingBottom = true
        )

        val database = AppDatabase.getDatabase(this)
        repository = BmiRepository(database.bmiDao(), database.profileDao())

        setupViews()
        loadData()
    }

    private fun setupViews() {
        recyclerView = findViewById(R.id.recyclerViewHistory)
        lineChart = findViewById(R.id.lineChart)

        recyclerView.layoutManager = LinearLayoutManager(this)
        adapter = HistoryAdapter { record ->
            showDeleteConfirmDialog(record)
        }
        recyclerView.adapter = adapter

        // Setup swipe to delete
        val itemTouchHelper = ItemTouchHelper(SwipeToDeleteCallback())
        itemTouchHelper.attachToRecyclerView(recyclerView)

        findViewById<View>(R.id.ivBack)?.setOnClickListener {
            finish()
        }

        setupChart()
    }

    private fun setupChart() {
        lineChart.apply {
            description.isEnabled = false
            setDrawGridBackground(false)
            setTouchEnabled(true)
            isDragEnabled = true
            setScaleEnabled(true)
            setPinchZoom(true)
            legend.textColor = Color.WHITE
            xAxis.textColor = Color.WHITE
            xAxis.position = XAxis.XAxisPosition.BOTTOM
            axisLeft.textColor = Color.WHITE
            axisRight.isEnabled = false
        }
    }

    private fun loadData() {
        // Get current profile ID and load its records
        lifecycleScope.launch {
            val currentProfile = repository.getCurrentProfile()
            val profileId = currentProfile?.id ?: 1L // Default to 1 if no profile found

            // Observe LiveData on main thread
            repository.getAllRecordsAscending(profileId).observe(this@HistoryActivity) { records ->
                adapter.submitList(records.reversed())
                updateChart(records)
            }
        }
    }

    private fun updateChart(records: List<BmiRecord>) {
        if (records.isEmpty()) {
            lineChart.clear()
            return
        }

        val entries = records.mapIndexed { index, record ->
            Entry(index.toFloat(), record.bmi.toFloat())
        }

        val dataSet = LineDataSet(entries, "BMI History").apply {
            color = Color.parseColor("#4CAF50")
            valueTextColor = Color.WHITE
            lineWidth = 2f
            setCircleColor(Color.parseColor("#4CAF50"))
            circleRadius = 4f
            setDrawValues(false)
            mode = LineDataSet.Mode.CUBIC_BEZIER
        }

        lineChart.data = LineData(dataSet)
        lineChart.xAxis.valueFormatter = object : ValueFormatter() {
            private val dateFormat = SimpleDateFormat("MM/dd", Locale.getDefault())
            override fun getFormattedValue(value: Float): String {
                val index = value.toInt()
                return if (index >= 0 && index < records.size) {
                    dateFormat.format(Date(records[index].timestamp))
                } else ""
            }
        }
        lineChart.invalidate()
    }

    private fun showDeleteConfirmDialog(record: BmiRecord) {
        MaterialAlertDialogBuilder(this)
            .setTitle("Delete Record")
            .setMessage("Are you sure you want to delete this BMI record?")
            .setPositiveButton("Delete") { _, _ ->
                deleteRecord(record)
            }
            .setNegativeButton("Cancel") { dialog, _ ->
                dialog.dismiss()
                // Refresh adapter to restore the swiped item
                adapter.notifyDataSetChanged()
            }
            .setOnCancelListener {
                // Refresh adapter to restore the swiped item when dismissed
                adapter.notifyDataSetChanged()
            }
            .show()
    }

    private fun deleteRecord(record: BmiRecord) {
        lifecycleScope.launch {
            repository.deleteRecord(record)
        }
    }

    inner class SwipeToDeleteCallback : ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT) {
        override fun onMove(
            recyclerView: RecyclerView,
            viewHolder: RecyclerView.ViewHolder,
            target: RecyclerView.ViewHolder
        ): Boolean = false

        override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
            val position = viewHolder.adapterPosition
            val record = adapter.getRecordAt(position)
            showDeleteConfirmDialog(record)
        }
    }
}

class HistoryAdapter(
    private val onDelete: (BmiRecord) -> Unit
) : RecyclerView.Adapter<HistoryAdapter.ViewHolder>() {

    private var records = listOf<BmiRecord>()

    fun submitList(newRecords: List<BmiRecord>) {
        records = newRecords
        notifyDataSetChanged()
    }

    fun getRecordAt(position: Int): BmiRecord = records[position]

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_history_record, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(records[position])
    }

    override fun getItemCount() = records.size

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvDate: TextView = itemView.findViewById(R.id.tvDate)
        private val tvBmi: TextView = itemView.findViewById(R.id.tvBmi)
        private val tvDetails: TextView = itemView.findViewById(R.id.tvDetails)
        private val btnDelete: View = itemView.findViewById(R.id.btnDelete)

        fun bind(record: BmiRecord) {
            val dateFormat = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
            tvDate.text = dateFormat.format(Date(record.timestamp))
            tvBmi.text = "BMI: ${String.format("%.1f", record.bmi)}"
            tvDetails.text = "${String.format("%.0f", record.weight)}kg • ${String.format("%.0f", record.height)}cm"

            btnDelete.setOnClickListener {
                onDelete(record)
            }
        }
    }
}
