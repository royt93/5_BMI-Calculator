package com.samsunggalaxy.ui

import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.LimitLine
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
        val chartTextColor = ContextCompat.getColor(this, R.color.textColor)
        lineChart.apply {
            description.isEnabled = false
            setDrawGridBackground(false)
            setTouchEnabled(true)
            isDragEnabled = true
            setScaleEnabled(true)
            setPinchZoom(true)
            legend.textColor = chartTextColor
            xAxis.textColor = chartTextColor
            xAxis.position = XAxis.XAxisPosition.BOTTOM
            axisLeft.textColor = chartTextColor
            axisRight.isEnabled = false
        }
    }

    private fun loadData() {
        lifecycleScope.launch {
            val currentProfile = repository.getCurrentProfile()
            val profileId = currentProfile?.id ?: 1L
            val goalWeight = currentProfile?.goalWeight
            Log.d("roy93~", "HistoryActivity loadData: profileId=$profileId, goalWeight=$goalWeight")

            repository.getAllRecordsAscending(profileId).observe(this@HistoryActivity) { records ->
                Log.d("roy93~", "HistoryActivity records received: count=${records.size}")
                records.forEachIndexed { i, r ->
                    Log.d("roy93~", "  record[$i]: id=${r.id}, bmi=${r.bmi}, weight=${r.weight}, profileId=${r.profileId}, ts=${r.timestamp}")
                }
                adapter.submitList(records.reversed())
                updateChart(records, goalWeight)
            }
        }
    }

    private fun updateChart(records: List<BmiRecord>, goalWeight: Double? = null) {
        if (records.isEmpty()) {
            lineChart.clear()
            return
        }

        val entries = records.mapIndexed { index, record ->
            Entry(index.toFloat(), record.bmi.toFloat())
        }

        val primaryColor = ContextCompat.getColor(this, R.color.bmi_healthy)
        val dataSet = LineDataSet(entries, getString(R.string.progress)).apply {
            color = primaryColor
            valueTextColor = ContextCompat.getColor(this@HistoryActivity, R.color.textColor)
            lineWidth = 2f
            setCircleColor(primaryColor)
            circleRadius = 4f
            setDrawValues(false)
            mode = LineDataSet.Mode.CUBIC_BEZIER
        }

        lineChart.data = LineData(dataSet)

        // Draw goal BMI line if goalWeight is set
        lineChart.axisLeft.removeAllLimitLines()
        if (goalWeight != null && goalWeight > 0 && records.isNotEmpty()) {
            // Estimate goal BMI using first record's height as reference
            val heightM = records.first().height / 100.0
            val goalBmi = goalWeight / (heightM * heightM)
            val limitLineColor = ContextCompat.getColor(this, R.color.bmi_overweight)
            val limitLine = LimitLine(goalBmi.toFloat(), getString(R.string.goal_bmi_target_label)).apply {
                lineWidth = 1.5f
                enableDashedLine(10f, 5f, 0f)
                lineColor = limitLineColor
                textColor = limitLineColor
                textSize = 10f
            }
            lineChart.axisLeft.addLimitLine(limitLine)
        }

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
            .setTitle(getString(R.string.delete_record))
            .setMessage(getString(R.string.are_you_sure_delete))
            .setPositiveButton(getString(R.string.delete)) { _, _ ->
                deleteRecord(record)
            }
            .setNegativeButton(getString(R.string.cancel)) { dialog, _ ->
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
            // BUG-06: Guard against NO_POSITION to prevent IndexOutOfBoundsException on fast swipe
            val position = viewHolder.adapterPosition
            if (position == RecyclerView.NO_POSITION) {
                adapter.notifyDataSetChanged()
                return
            }
            val record = adapter.getRecordAt(position)
            showDeleteConfirmDialog(record)
        }
    }
}

// BUG-05: Replaced HistoryAdapter with ListAdapter + DiffUtil for efficient updates
class HistoryAdapter(
    private val onDelete: (BmiRecord) -> Unit
) : androidx.recyclerview.widget.ListAdapter<BmiRecord, HistoryAdapter.ViewHolder>(
    object : androidx.recyclerview.widget.DiffUtil.ItemCallback<BmiRecord>() {
        override fun areItemsTheSame(oldItem: BmiRecord, newItem: BmiRecord): Boolean =
            oldItem.id == newItem.id

        override fun areContentsTheSame(oldItem: BmiRecord, newItem: BmiRecord): Boolean =
            oldItem == newItem
    }
) {

    fun getRecordAt(position: Int): BmiRecord = getItem(position)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_history_record, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position), position)
    }

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvDate: TextView = itemView.findViewById(R.id.tvDate)
        private val tvBmi: TextView = itemView.findViewById(R.id.tvBmi)
        private val tvCategory: TextView = itemView.findViewById(R.id.tvCategory)
        private val tvDetails: TextView = itemView.findViewById(R.id.tvDetails)
        private val tvTrend: TextView = itemView.findViewById(R.id.tvTrend)
        private val btnDelete: View = itemView.findViewById(R.id.btnDelete)
        private val viewDot: View = itemView.findViewById(R.id.viewCategoryDot)

        fun bind(record: BmiRecord, position: Int) {
            val ctx = itemView.context
            val dateFormat = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
            tvDate.text = dateFormat.format(Date(record.timestamp))
            tvBmi.text = "BMI: ${String.format("%.1f", record.bmi)}"
            tvDetails.text = "${String.format("%.0f", record.weight)}kg • ${String.format("%.0f", record.height)}cm"

            // BMI category color (dark/light mode via @color resources)
            val (categoryRes, categoryStrRes) = when {
                record.bmi < 18.5 -> Pair(R.color.bmi_underweight, R.string.bmi_category_underweight)
                record.bmi < 25.0 -> Pair(R.color.bmi_healthy, R.string.bmi_category_healthy)
                record.bmi < 30.0 -> Pair(R.color.bmi_overweight, R.string.bmi_category_overweight)
                else               -> Pair(R.color.bmi_obese, R.string.bmi_category_obese)
            }
            val catColor = ContextCompat.getColor(ctx, categoryRes)
            viewDot.setBackgroundColor(catColor)
            tvCategory.text = ctx.getString(categoryStrRes)
            tvCategory.setTextColor(catColor)

            // Trend arrow: compare with next item (list is reversed so next = older)
            val currentList = currentList
            val nextPosition = position + 1
            val prevBmi = if (nextPosition < currentList.size) currentList[nextPosition].bmi else null
            val (trendArrow, trendColor) = when {
                prevBmi == null     -> Pair("—", R.color.textColorAdditional)
                record.bmi < prevBmi - 0.1 -> Pair("↓", R.color.bmi_healthy)   // improved
                record.bmi > prevBmi + 0.1 -> Pair("↑", R.color.bmi_obese)     // worsened
                else                -> Pair("→", R.color.textColorAdditional)   // stable
            }
            tvTrend.text = trendArrow
            tvTrend.setTextColor(ContextCompat.getColor(ctx, trendColor))

            btnDelete.setOnClickListener {
                onDelete(record)
            }
        }
    }
}
