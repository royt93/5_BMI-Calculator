package com.samsunggalaxy.ui

import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.LimitLine
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.formatter.ValueFormatter
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.tabs.TabLayout
import com.google.android.material.textfield.TextInputEditText
import com.samsunggalaxy.BaseActivity
import com.samsunggalaxy.BuildConfig
import com.samsunggalaxy.R
import com.samsunggalaxy.data.AppDatabase
import com.samsunggalaxy.data.BmiRecord
import com.samsunggalaxy.data.BmiRepository
import com.samsunggalaxy.sdkadbmob.UIUtils
import com.samsunggalaxy.utils.CalculatorUtils
import com.samsunggalaxy.utils.PreferencesManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

/**
 * Unified Weight Dashboard (EPIC-07): merges the old separate BMI-only chart
 * (this Activity) and weight/height-only chart (TrackerBottomSheet) into one
 * screen with a series switcher, a single goal-weight source of truth (moved
 * from ResultAct's goal card), a trend ETA estimate, and a quick-log FAB that
 * doesn't require the full MainAct wizard. See doc/task/todo/EPIC-07-weight-dashboard.md.
 */
class HistoryActivity : BaseActivity() {
    private enum class Series { BMI, WEIGHT, HEIGHT }

    private lateinit var repository: BmiRepository
    private lateinit var recyclerView: RecyclerView
    private lateinit var lineChart: LineChart
    private lateinit var tabLayoutSeries: TabLayout
    private lateinit var emptyStateContainer: View
    private lateinit var cardGoalRow: View
    private lateinit var tvGoalSummary: TextView
    private lateinit var tvGoalEta: TextView
    private lateinit var fabQuickLog: FloatingActionButton
    private lateinit var adapter: HistoryAdapter

    private var currentSeries = Series.BMI
    private var records: List<BmiRecord> = emptyList()
    private var currentProfileId: Long = 1L
    private var currentGoalWeight: Double? = null

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
        tabLayoutSeries = findViewById(R.id.tabLayoutSeries)
        emptyStateContainer = findViewById(R.id.emptyStateContainer)
        cardGoalRow = findViewById(R.id.cardGoalRow)
        tvGoalSummary = findViewById(R.id.tvGoalSummary)
        tvGoalEta = findViewById(R.id.tvGoalEta)
        fabQuickLog = findViewById(R.id.fabQuickLog)

        recyclerView.layoutManager = LinearLayoutManager(this)
        adapter = HistoryAdapter { record ->
            showDeleteConfirmDialog(record)
        }
        recyclerView.adapter = adapter

        val itemTouchHelper = ItemTouchHelper(SwipeToDeleteCallback())
        itemTouchHelper.attachToRecyclerView(recyclerView)

        findViewById<View>(R.id.ivBack)?.setOnClickListener {
            finish()
        }

        setupChart()
        setupSeriesTabs()

        findViewById<View>(R.id.ivEditGoalDashboard).setOnClickListener { showGoalDialog() }
        cardGoalRow.setOnClickListener { if (currentGoalWeight == null) showGoalDialog() }
        fabQuickLog.setOnClickListener { showQuickLogDialog() }
    }

    private fun setupSeriesTabs() {
        tabLayoutSeries.addTab(tabLayoutSeries.newTab().setText(getString(R.string.dashboard_series_bmi)))
        tabLayoutSeries.addTab(tabLayoutSeries.newTab().setText(getString(R.string.dashboard_series_weight)))
        tabLayoutSeries.addTab(tabLayoutSeries.newTab().setText(getString(R.string.dashboard_series_height)))
        tabLayoutSeries.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                currentSeries = when (tab?.position) {
                    1 -> Series.WEIGHT
                    2 -> Series.HEIGHT
                    else -> Series.BMI
                }
                updateChart()
            }
            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })
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
        lifecycleScope.launch(Dispatchers.IO) {
            val currentProfile = repository.getCurrentProfile()
            currentProfileId = currentProfile?.id ?: 1L
            currentGoalWeight = currentProfile?.goalWeight
            if (BuildConfig.DEBUG) Log.d("roy93~", "HistoryActivity loadData: profileId=$currentProfileId, goalWeight=$currentGoalWeight")

            withContext(Dispatchers.Main) {
                repository.getAllRecordsAscending(currentProfileId).observe(this@HistoryActivity) { recs ->
                    if (BuildConfig.DEBUG) Log.d("roy93~", "HistoryActivity records received: count=${recs.size}")
                    records = recs
                    adapter.submitList(recs.reversed())
                    updateChart()
                    updateGoalRow()
                    updateEmptyState()
                }
            }
        }
    }

    private fun updateEmptyState() {
        val isEmpty = records.isEmpty()
        emptyStateContainer.isVisible = isEmpty
        lineChart.isVisible = !isEmpty
        cardGoalRow.isVisible = !isEmpty
    }

    private fun updateChart() {
        if (records.isEmpty()) {
            lineChart.clear()
            return
        }

        val entries = records.mapIndexed { index, record ->
            val value = when (currentSeries) {
                Series.BMI -> record.bmi
                Series.WEIGHT -> record.weight
                Series.HEIGHT -> record.height
            }
            Entry(index.toFloat(), value.toFloat())
        }

        val label = when (currentSeries) {
            Series.BMI -> getString(R.string.dashboard_series_bmi)
            Series.WEIGHT -> getString(R.string.dashboard_series_weight)
            Series.HEIGHT -> getString(R.string.dashboard_series_height)
        }
        val primaryColor = ContextCompat.getColor(this, R.color.bmi_healthy)
        val dataSet = LineDataSet(entries, label).apply {
            color = primaryColor
            valueTextColor = ContextCompat.getColor(this@HistoryActivity, R.color.textColor)
            lineWidth = 2f
            setCircleColor(primaryColor)
            circleRadius = 4f
            setDrawValues(false)
            mode = LineDataSet.Mode.CUBIC_BEZIER
        }

        lineChart.data = LineData(dataSet)

        // Goal line — EPIC-00/07 fix: the old version always converted goalWeight to a BMI
        // using the FIRST record's height, which drifts if height changes across records
        // (e.g. a growing child) or a one-off bad entry. Weight series needs no conversion
        // at all (goal is stored in kg); BMI series uses the LATEST height as the closest
        // approximation of "today", not the oldest.
        lineChart.axisLeft.removeAllLimitLines()
        val goalWeight = currentGoalWeight
        if (goalWeight != null && goalWeight > 0) {
            val limitLineValue: Float? = when (currentSeries) {
                Series.WEIGHT -> goalWeight.toFloat()
                Series.BMI -> {
                    val latestHeightM = records.last().height / 100.0
                    if (latestHeightM > 0) (goalWeight / (latestHeightM * latestHeightM)).toFloat() else null
                }
                Series.HEIGHT -> null // goal is a weight target, not meaningful on the height series
            }
            if (limitLineValue != null) {
                val limitLineColor = ContextCompat.getColor(this, R.color.bmi_overweight)
                val limitLabel = if (currentSeries == Series.WEIGHT)
                    getString(R.string.goal_weight_label) else getString(R.string.goal_bmi_target_label)
                val limitLine = LimitLine(limitLineValue, limitLabel).apply {
                    lineWidth = 1.5f
                    enableDashedLine(10f, 5f, 0f)
                    lineColor = limitLineColor
                    textColor = limitLineColor
                    textSize = 10f
                }
                lineChart.axisLeft.addLimitLine(limitLine)
            }
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

    private fun updateGoalRow() {
        val goalWeight = currentGoalWeight
        if (goalWeight == null || goalWeight <= 0 || records.isEmpty()) {
            tvGoalSummary.text = getString(R.string.goal_weight_no_goal)
            tvGoalEta.isVisible = false
            return
        }

        val startWeight = records.first().weight
        val currentWeight = records.last().weight
        val progress = CalculatorUtils.calculateGoalProgress(startWeight, currentWeight, goalWeight)

        tvGoalSummary.text = if (progress.achieved) {
            getString(R.string.goal_weight_achieved)
        } else {
            "${getString(R.string.goal_weight_target, goalWeight)} — ${getString(R.string.goal_weight_remaining, progress.remainingKg)}"
        }

        if (progress.achieved) {
            tvGoalEta.isVisible = false
        } else {
            val eta = CalculatorUtils.estimateGoalEtaDays(records.map { it.timestamp to it.weight }, goalWeight)
            tvGoalEta.isVisible = true
            tvGoalEta.text = when {
                eta.etaDays != null -> getString(R.string.dashboard_eta_days, eta.etaDays)
                // hasEnoughData=true + etaDays=null means the trend is flat or moving AWAY
                // from the goal — a different (and more useful) message than "not enough data".
                eta.hasEnoughData -> getString(R.string.dashboard_eta_wrong_direction)
                else -> getString(R.string.dashboard_eta_not_enough_data)
            }
        }
    }

    private fun showGoalDialog() {
        if (records.isEmpty()) return
        val latest = records.last()
        val currentBmi = latest.bmi

        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_goal_weight, null)
        dialogView.findViewById<TextView>(R.id.tvDialogCurrentBmi).text = String.format("%.1f", currentBmi)
        dialogView.findViewById<TextView>(R.id.tvDialogCurrentWeight).text =
            getString(R.string.goal_weight_current, latest.weight)

        val tvCategory = dialogView.findViewById<TextView>(R.id.tvDialogBmiCategory)
        val categoryInfo = CalculatorUtils.getBMICategoryInfo(currentBmi)
        tvCategory.text = getString(categoryInfo.labelRes)
        tvCategory.setTextColor(ContextCompat.getColor(this, categoryInfo.colorRes))

        val etGoal = dialogView.findViewById<TextInputEditText>(R.id.etGoalWeight)
        val tvPreview = dialogView.findViewById<TextView>(R.id.tvDialogGoalBmiPreview)
        val heightM = latest.height / 100.0
        currentGoalWeight?.let { etGoal.setText(String.format("%.1f", it)) }

        etGoal.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) {
                val goalW = s.toString().toDoubleOrNull()
                if (goalW != null && goalW > 0 && heightM > 0) {
                    val goalBmi = goalW / (heightM * heightM)
                    tvPreview.visibility = View.VISIBLE
                    tvPreview.text = "${getString(R.string.goal_bmi_target_label)}: ${String.format("%.1f", goalBmi)}"
                    val previewInfo = CalculatorUtils.getBMICategoryInfo(goalBmi)
                    tvPreview.setTextColor(ContextCompat.getColor(this@HistoryActivity, previewInfo.colorRes))
                } else {
                    tvPreview.visibility = View.GONE
                }
            }
        })

        MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.goal_weight_label))
            .setView(dialogView)
            .setPositiveButton(getString(R.string.goal_weight_save)) { _, _ ->
                val input = etGoal.text.toString().toDoubleOrNull()
                lifecycleScope.launch(Dispatchers.IO) {
                    repository.updateGoalWeight(currentProfileId, input)
                    withContext(Dispatchers.Main) {
                        currentGoalWeight = input
                        updateChart()
                        updateGoalRow()
                    }
                }
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    /** T07.5: log a weight point directly, without the full MainAct wizard. */
    private fun showQuickLogDialog() {
        if (records.isEmpty()) {
            Toast.makeText(this, getString(R.string.quick_log_no_baseline), Toast.LENGTH_LONG).show()
            return
        }
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_quick_log, null)
        val etWeight = dialogView.findViewById<TextInputEditText>(R.id.etQuickLogWeight)
        etWeight.setText(String.format("%.1f", records.last().weight))

        MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.quick_log_title))
            .setView(dialogView)
            .setPositiveButton(getString(R.string.quick_log_save)) { _, _ ->
                val weight = etWeight.text.toString().toDoubleOrNull()
                if (weight == null || weight <= 0) {
                    Toast.makeText(this, getString(R.string.quick_log_invalid_weight), Toast.LENGTH_SHORT).show()
                } else {
                    quickLogWeight(weight)
                }
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    private fun quickLogWeight(weight: Double) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val last = records.last() // reuse height/age/gender — quick-log is weight-only
                val activityLevel = PreferencesManager(this@HistoryActivity).activityLevel.first()
                val bmi = CalculatorUtils.calculateBMI(weight, last.height)
                val bmr = CalculatorUtils.calculateBMR(weight, last.height, last.age, last.gender)
                val tdee = CalculatorUtils.calculateTDEE(bmr, activityLevel)
                val idealWeight = CalculatorUtils.calculateIdealWeightRange(last.height, last.gender)

                val record = BmiRecord(
                    timestamp = System.currentTimeMillis(),
                    height = last.height,
                    weight = weight,
                    gender = last.gender,
                    age = last.age,
                    bmi = bmi,
                    bmr = bmr,
                    tdee = tdee,
                    idealWeightMin = idealWeight.first,
                    idealWeightMax = idealWeight.second,
                    bodyFatPercentage = null,
                    profileId = currentProfileId
                )
                val newlyEarned = RecordSaveHelper.saveAndCheckBadges(
                    context = this@HistoryActivity,
                    repository = repository,
                    record = record,
                    goalWeight = currentGoalWeight
                )

                withContext(Dispatchers.Main) {
                    if (!isFinishing && !isDestroyed) {
                        val message = newlyEarned.firstOrNull()?.let { badge ->
                            "🎉 ${getString(badge.titleRes)}!"
                        } ?: getString(R.string.quick_log_saved)
                        com.google.android.material.snackbar.Snackbar
                            .make(recyclerView, message, com.google.android.material.snackbar.Snackbar.LENGTH_LONG)
                            .setBackgroundTint(ContextCompat.getColor(this@HistoryActivity, R.color.bmi_healthy))
                            .show()
                    }
                }
            } catch (e: Exception) {
                Log.e("roy93~", "quickLogWeight error", e)
            }
        }
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

            // BMI category — unified via CalculatorUtils.getBMICategoryInfo (EPIC-00 T00.3)
            val categoryInfo = CalculatorUtils.getBMICategoryInfo(record.bmi)
            val catColor = ContextCompat.getColor(ctx, categoryInfo.colorRes)
            viewDot.setBackgroundColor(catColor)
            tvCategory.text = ctx.getString(categoryInfo.labelRes)
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
