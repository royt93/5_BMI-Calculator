package com.samsunggalaxy.ui

import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
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
import com.samsunggalaxy.R
import com.samsunggalaxy.data.AppDatabase
import com.samsunggalaxy.data.BmiRecord
import com.samsunggalaxy.data.BmiRepository
import com.samsunggalaxy.data.BodyMeasurement
import com.samsunggalaxy.sdkadbmob.UIUtils
import com.samsunggalaxy.utils.AppLog
import com.samsunggalaxy.utils.CalculatorUtils
import com.samsunggalaxy.utils.CoachEngine
import com.samsunggalaxy.utils.InsightsEngine
import com.samsunggalaxy.widget.WidgetUpdateHelper
import com.samsunggalaxy.utils.PreferencesManager
import com.samsunggalaxy.utils.UnitFormatter
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
    private enum class Series { BMI, WEIGHT, HEIGHT, MEASUREMENTS }

    companion object {
        // Idea I3 — Share Progress Card window; matches the example in the idea spec
        // ("Trong 30 ngày, giảm 2.3kg...") and the Health Connect sync window (EPIC-09).
        private const val SHARE_PROGRESS_PERIOD_DAYS = 30
    }

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
    // No lateinit (CLAUDE.md convention) — populated in setupViews(), read only from
    // updateInsights() which onCreate always calls after setupViews() via loadData().
    private var llInsightsList: LinearLayout? = null
    private var tvInsightsEmpty: TextView? = null
    // Idea I9 — same nullable/populated-in-setupViews pattern as the Smart Insights fields above.
    private var llCoachList: LinearLayout? = null
    private var tvCoachEmpty: TextView? = null
    private var tvCoachDisclaimer: TextView? = null

    private var currentSeries = Series.BMI
    private var records: List<BmiRecord> = emptyList()
    private var measurements: List<BodyMeasurement> = emptyList()
    private var currentProfileId: Long = 1L
    private var currentGoalWeight: Double? = null
    // EPIC-04 T04.1: chart series/goal-line + the history list rows are unit-aware. The goal
    // row's own text (`goal_weight_target`/`goal_weight_current` locale templates) and the
    // set-goal dialog stay metric-only for this pass — see UnitFormatter's doc comment.
    private var unitSystem: String = UnitFormatter.METRIC

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
        repository = BmiRepository(database.bmiDao(), database.profileDao(), database.bodyMeasurementDao())

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
        llInsightsList = findViewById(R.id.llInsightsList)
        tvInsightsEmpty = findViewById(R.id.tvInsightsEmpty)
        llCoachList = findViewById(R.id.llCoachList)
        tvCoachEmpty = findViewById(R.id.tvCoachEmpty)
        tvCoachDisclaimer = findViewById(R.id.tvCoachDisclaimer)


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
        findViewById<View>(R.id.ivExportCsv).setOnClickListener { exportCsv() }
        findViewById<View>(R.id.ivShareProgress).setOnClickListener { shareProgressCard() }
        findViewById<View>(R.id.ivExportPdf).setOnClickListener { exportPdf() }
    }

    private fun setupSeriesTabs() {
        tabLayoutSeries.addTab(tabLayoutSeries.newTab().setText(getString(R.string.dashboard_series_bmi)))
        tabLayoutSeries.addTab(tabLayoutSeries.newTab().setText(getString(R.string.dashboard_series_weight)))
        tabLayoutSeries.addTab(tabLayoutSeries.newTab().setText(getString(R.string.dashboard_series_height)))
        tabLayoutSeries.addTab(tabLayoutSeries.newTab().setText(getString(R.string.dashboard_series_measurements)))
        tabLayoutSeries.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                currentSeries = when (tab?.position) {
                    1 -> Series.WEIGHT
                    2 -> Series.HEIGHT
                    3 -> Series.MEASUREMENTS
                    else -> Series.BMI
                }
                updateChart()
                updateEmptyState()
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
            unitSystem = PreferencesManager(this@HistoryActivity).unitSystem.first()
            AppLog.d("HistoryActivity loadData: profileId=$currentProfileId, goalWeight=$currentGoalWeight, unitSystem=$unitSystem")

            withContext(Dispatchers.Main) {
                adapter.unitSystem = unitSystem
                repository.getAllRecordsAscending(currentProfileId).observe(this@HistoryActivity) { recs ->
                    AppLog.d("HistoryActivity records received: count=${recs.size}")
                    records = recs
                    adapter.submitList(recs.reversed())
                    updateChart()
                    updateGoalRow()
                    updateEmptyState()
                    val weeklyChanges = updateInsights()
                    updateCoach(weeklyChanges)
                }
                // EPIC-08 T08.3 — independent series. Its own empty-state check (below) is
                // per-tab, since a user can delete all BmiRecords via History while measurement
                // rows survive (deleteRecord() doesn't cascade to body_measurements).
                repository.getMeasurementsAscending(currentProfileId).observe(this@HistoryActivity) { m ->
                    measurements = m
                    if (currentSeries == Series.MEASUREMENTS) {
                        updateChart()
                        updateEmptyState()
                    }
                }
            }
        }
    }

    private fun updateEmptyState() {
        // The Measurements tab has its own data source (body_measurements, not bmi_records) —
        // an empty BMI history must not hide a non-empty measurements chart, and vice versa.
        val chartEmpty = if (currentSeries == Series.MEASUREMENTS) measurements.isEmpty() else records.isEmpty()
        emptyStateContainer.isVisible = chartEmpty
        lineChart.isVisible = !chartEmpty
        // Goal row/FAB depend on having at least one weigh-in at all, independent of which
        // series tab is showing.
        cardGoalRow.isVisible = records.isNotEmpty()
    }

    /**
     * Idea I2 — Smart Insights: on-device stats over the profile's full weigh-in history (not
     * just the visible chart window), no API/LLM calls. Silently shows the cold-start message
     * when the data doesn't yet support any of the three insight types (new users, sparse
     * logging) rather than a partial/misleading card.
     */
    private fun updateInsights(): List<InsightsEngine.WeeklyChange> {
        val recordPairs = records.map { it.timestamp to it.weight }
        val weeklyChanges = InsightsEngine.computeWeeklyChanges(recordPairs)

        val insightsList = llInsightsList ?: return weeklyChanges
        val emptyLabel = tvInsightsEmpty ?: return weeklyChanges
        insightsList.removeAllViews()
        val lines = mutableListOf<String>()

        val bestWeek = InsightsEngine.findBestWeek(weeklyChanges)
        if (bestWeek != null && bestWeek.deltaKg < 0) {
            val weekOf = java.text.SimpleDateFormat("MMM dd, yyyy", java.util.Locale.getDefault())
                .format(java.util.Date(java.time.LocalDate.ofEpochDay(bestWeek.weekStartEpochDay).atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()))
            lines += getString(
                R.string.insight_best_week,
                UnitFormatter.formatWeight(-bestWeek.deltaKg, unitSystem),
                weekOf
            )
        }

        InsightsEngine.findMostStableDayOfWeek(recordPairs)?.let { day ->
            val dayName = day.getDisplayName(java.time.format.TextStyle.FULL, java.util.Locale.getDefault())
            lines += getString(R.string.insight_stable_day, dayName)
        }

        InsightsEngine.compareStreakWeeks(weeklyChanges)?.let { correlation ->
            lines += getString(
                R.string.insight_streak_correlation,
                UnitFormatter.formatSignedWeightDelta(correlation.fullStreakAvgDeltaKg, unitSystem),
                UnitFormatter.formatSignedWeightDelta(correlation.otherWeeksAvgDeltaKg, unitSystem)
            )
        }

        emptyLabel.isVisible = lines.isEmpty()
        lines.forEach { line ->
            val row = TextView(this).apply {
                text = line
                textSize = 14f
                setTextColor(ContextCompat.getColor(this@HistoryActivity, R.color.textColor))
                setPadding(0, 8, 0, 8)
            }
            insightsList.addView(row)
        }
        return weeklyChanges
    }

    /**
     * Idea I9 — local rule-based Coach: prescriptive nudges (weekly calorie target + check-in
     * cadence) built on top of I2's InsightsEngine weekly buckets. Same cold-start handling as
     * updateInsights() — hides the card content and shows the empty message when there isn't
     * enough data yet, rather than a partial/misleading suggestion.
     *
     * [precomputedWeeklyChanges] lets the records-changed observer reuse updateInsights()'s
     * result instead of re-bucketing the same records into weeks twice per emission; other
     * callers (e.g. after saving a goal weight, which doesn't change weekly cadence) pass null
     * and this recomputes it — a rare, cheap call, not worth threading state through for.
     */
    private fun updateCoach(precomputedWeeklyChanges: List<InsightsEngine.WeeklyChange>? = null) {
        val coachList = llCoachList ?: return
        val emptyLabel = tvCoachEmpty ?: return
        val disclaimer = tvCoachDisclaimer ?: return
        coachList.removeAllViews()
        val lines = mutableListOf<String>()

        records.lastOrNull()?.let { latestRecord ->
            CoachEngine.suggestWeeklyCalorieTarget(latestRecord.tdee, latestRecord.weight, currentGoalWeight)?.let { suggestion ->
                val rateText = UnitFormatter.formatWeight(kotlin.math.abs(suggestion.weeklyRateKg), unitSystem)
                lines += when {
                    suggestion.weeklyRateKg < 0 -> getString(R.string.coach_calorie_target_loss, suggestion.targetCalories, rateText)
                    suggestion.weeklyRateKg > 0 -> getString(R.string.coach_calorie_target_gain, suggestion.targetCalories, rateText)
                    else -> getString(R.string.coach_calorie_target_maintain, suggestion.targetCalories)
                }
            }
        }

        val weeklyChanges = precomputedWeeklyChanges ?: InsightsEngine.computeWeeklyChanges(records.map { it.timestamp to it.weight })
        CoachEngine.suggestCheckInFrequency(weeklyChanges)?.let { advice ->
            lines += when (advice) {
                CoachEngine.CheckInFrequencyAdvice.LOG_MORE_OFTEN -> getString(R.string.coach_checkin_log_more)
                CoachEngine.CheckInFrequencyAdvice.ON_TRACK -> getString(R.string.coach_checkin_on_track)
            }
        }

        emptyLabel.isVisible = lines.isEmpty()
        disclaimer.isVisible = lines.isNotEmpty()
        lines.forEach { line ->
            val row = TextView(this).apply {
                text = line
                textSize = 14f
                setTextColor(ContextCompat.getColor(this@HistoryActivity, R.color.textColor))
                setPadding(0, 8, 0, 8)
            }
            coachList.addView(row)
        }
    }

    private fun updateChart() {
        if (currentSeries == Series.MEASUREMENTS) {
            updateMeasurementsChart()
            return
        }

        if (records.isEmpty()) {
            lineChart.clear()
            return
        }

        val entries = records.mapIndexed { index, record ->
            val value = when (currentSeries) {
                Series.BMI -> record.bmi
                Series.WEIGHT -> UnitFormatter.weightToDisplay(record.weight, unitSystem)
                Series.HEIGHT -> UnitFormatter.heightToDisplay(record.height, unitSystem)
                Series.MEASUREMENTS -> 0.0 // unreachable — handled by updateMeasurementsChart() above
            }
            Entry(index.toFloat(), value.toFloat())
        }

        val label = when (currentSeries) {
            Series.BMI -> getString(R.string.dashboard_series_bmi)
            Series.WEIGHT -> "${getString(R.string.dashboard_series_weight)} (${UnitFormatter.weightUnitLabel(unitSystem)})"
            Series.HEIGHT -> "${getString(R.string.dashboard_series_height)} (${UnitFormatter.heightUnitLabel(unitSystem)})"
            Series.MEASUREMENTS -> ""
        }
        val dataSet = styledLineDataSet(entries, label, R.color.bmi_healthy)

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
                Series.WEIGHT -> UnitFormatter.weightToDisplay(goalWeight, unitSystem).toFloat()
                Series.BMI -> {
                    val latestHeightM = records.last().height / 100.0
                    if (latestHeightM > 0) (goalWeight / (latestHeightM * latestHeightM)).toFloat() else null
                }
                Series.HEIGHT -> null // goal is a weight target, not meaningful on the height series
                Series.MEASUREMENTS -> null // unreachable — handled by updateMeasurementsChart() above
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

    /** Shared styling for every LineDataSet drawn in this Activity (BMI/Weight/Height/Measurements). */
    private fun styledLineDataSet(entries: List<Entry>, label: String, colorRes: Int): LineDataSet {
        val color = ContextCompat.getColor(this, colorRes)
        return LineDataSet(entries, label).apply {
            this.color = color
            valueTextColor = ContextCompat.getColor(this@HistoryActivity, R.color.textColor)
            lineWidth = 2f
            setCircleColor(color)
            circleRadius = 4f
            setDrawValues(false)
            mode = LineDataSet.Mode.CUBIC_BEZIER
        }
    }

    /** EPIC-08 T08.3 — multi-line chart (waist/neck/hip) from the independent measurements table. */
    private fun updateMeasurementsChart() {
        if (measurements.isEmpty()) {
            lineChart.clear()
            return
        }
        lineChart.axisLeft.removeAllLimitLines() // goal line is a weight target, not meaningful here

        fun buildSet(label: String, colorRes: Int, selector: (BodyMeasurement) -> Double?): LineDataSet? {
            val entries = measurements.mapIndexedNotNull { index, m ->
                selector(m)?.let { Entry(index.toFloat(), UnitFormatter.heightToDisplay(it, unitSystem).toFloat()) }
            }
            if (entries.isEmpty()) return null
            return styledLineDataSet(entries, label, colorRes)
        }

        val unitLabel = UnitFormatter.heightUnitLabel(unitSystem)
        val sets = listOfNotNull(
            buildSet("${getString(R.string.waist)} ($unitLabel)", R.color.bmi_healthy) { it.waist },
            buildSet("${getString(R.string.neck)} ($unitLabel)", R.color.bmi_overweight) { it.neck },
            buildSet("${getString(R.string.hip)} ($unitLabel)", R.color.bmi_obese) { it.hip }
        )
        if (sets.isEmpty()) {
            lineChart.clear()
            return
        }

        lineChart.data = LineData(sets)
        lineChart.xAxis.valueFormatter = object : ValueFormatter() {
            private val dateFormat = SimpleDateFormat("MM/dd", Locale.getDefault())
            override fun getFormattedValue(value: Float): String {
                val index = value.toInt()
                return if (index >= 0 && index < measurements.size) {
                    dateFormat.format(Date(measurements[index].timestamp))
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
                        updateCoach()
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
        val tilWeight = dialogView.findViewById<com.google.android.material.textfield.TextInputLayout>(R.id.tilQuickLogWeight)
        tilWeight.suffixText = UnitFormatter.weightUnitLabel(unitSystem)
        etWeight.setText(String.format("%.1f", UnitFormatter.weightToDisplay(records.last().weight, unitSystem)))

        MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.quick_log_title))
            .setView(dialogView)
            .setPositiveButton(getString(R.string.quick_log_save)) { _, _ ->
                val displayWeight = etWeight.text.toString().toDoubleOrNull()
                if (displayWeight == null || displayWeight <= 0) {
                    Toast.makeText(this, getString(R.string.quick_log_invalid_weight), Toast.LENGTH_SHORT).show()
                } else {
                    quickLogWeight(UnitFormatter.weightToMetric(displayWeight, unitSystem))
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

    // Guards exportCsv() against a rapid double-tap launching two concurrent exports/badge
    // unlocks/share sheets from a single user action.
    private var isExportingCsv = false

    /** Shares a previously-exported file and, if it just unlocked a new badge, shows a snackbar. */
    private fun shareExportedFile(uri: Uri, mimeType: String, chooserTitleRes: Int, newBadge: BadgeManager.Badge?) {
        if (isFinishing || isDestroyed) return
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(intent, getString(chooserTitleRes)))
        newBadge?.let { badge ->
            com.google.android.material.snackbar.Snackbar
                .make(recyclerView, "🎉 ${getString(badge.titleRes)}!", com.google.android.material.snackbar.Snackbar.LENGTH_LONG)
                .show()
        }
    }

    /** EPIC-08 T08.2 — CSV export of BMI history via the share sheet. */
    private fun exportCsv() {
        if (records.isEmpty()) {
            Toast.makeText(this, getString(R.string.export_csv_empty), Toast.LENGTH_SHORT).show()
            return
        }
        if (isExportingCsv) return
        isExportingCsv = true
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val uri = com.samsunggalaxy.utils.CsvExporter.exportBmiRecords(this@HistoryActivity, records, unitSystem)
                val newBadge = uri?.let { BadgeManager.tryUnlockDataExporter(this@HistoryActivity, currentProfileId) }
                withContext(Dispatchers.Main) {
                    if (uri != null) shareExportedFile(uri, "text/csv", R.string.export_csv, newBadge)
                }
            } catch (e: Exception) {
                Log.e("roy93~", "exportCsv error", e)
                withContext(Dispatchers.Main) {
                    if (!isFinishing && !isDestroyed) {
                        Toast.makeText(this@HistoryActivity, getString(R.string.export_csv_error), Toast.LENGTH_SHORT).show()
                    }
                }
            } finally {
                isExportingCsv = false
            }
        }
    }

    // Guards exportPdf() the same way isExportingCsv guards exportCsv().
    private var isExportingPdf = false

    /** Idea I8 — PDF health report export, meant to be handed to a doctor/coach. */
    private fun exportPdf() {
        if (records.isEmpty()) {
            Toast.makeText(this, getString(R.string.export_pdf_empty), Toast.LENGTH_SHORT).show()
            return
        }
        if (isExportingPdf) return
        isExportingPdf = true
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val profileName = repository.getCurrentProfile()?.name.orEmpty()
                val uri = com.samsunggalaxy.report.PdfReportExporter.export(
                    this@HistoryActivity, profileName, records, unitSystem
                )
                val newBadge = uri?.let { BadgeManager.tryUnlockDataExporter(this@HistoryActivity, currentProfileId) }
                withContext(Dispatchers.Main) {
                    if (uri != null) shareExportedFile(uri, "application/pdf", R.string.export_pdf, newBadge)
                }
            } catch (e: Exception) {
                Log.e("roy93~", "exportPdf error", e)
                withContext(Dispatchers.Main) {
                    if (!isFinishing && !isDestroyed) {
                        Toast.makeText(this@HistoryActivity, getString(R.string.export_pdf_error), Toast.LENGTH_SHORT).show()
                    }
                }
            } finally {
                isExportingPdf = false
            }
        }
    }

    // Guards shareProgressCard() the same way isExportingCsv guards exportCsv().
    private var isSharingProgress = false

    /** Idea I3 — Share Progress Card: renders + shares a summary image of the last 30 days. */
    private fun shareProgressCard() {
        if (isSharingProgress) return
        isSharingProgress = true
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val sinceMs = System.currentTimeMillis() - java.util.concurrent.TimeUnit.DAYS.toMillis(SHARE_PROGRESS_PERIOD_DAYS.toLong())
                val recentRecords = repository.getRecordsSince(currentProfileId, sinceMs)
                if (recentRecords.size < 2) {
                    withContext(Dispatchers.Main) {
                        if (!isFinishing && !isDestroyed) {
                            Toast.makeText(this@HistoryActivity, getString(R.string.share_progress_not_enough_data_toast), Toast.LENGTH_SHORT).show()
                        }
                    }
                    return@launch
                }

                val weightChange = CalculatorUtils.calculateWeightChange(recentRecords.map { it.timestamp to it.weight })
                val streakDays = StreakManager.getDisplayStreak(this@HistoryActivity, currentProfileId).current
                val bitmap = com.samsunggalaxy.share.ShareProgressCardRenderer.render(
                    context = this@HistoryActivity,
                    periodDays = SHARE_PROGRESS_PERIOD_DAYS,
                    deltaKg = weightChange,
                    unitSystem = unitSystem,
                    streakDays = streakDays,
                    sparklineValues = recentRecords.map { it.weight }
                )
                val uri = com.samsunggalaxy.share.ShareProgressCardExporter.save(this@HistoryActivity, bitmap)

                withContext(Dispatchers.Main) {
                    shareExportedFile(uri, "image/png", R.string.share_progress, newBadge = null)
                }
            } catch (e: Exception) {
                Log.e("roy93~", "shareProgressCard error", e)
                withContext(Dispatchers.Main) {
                    if (!isFinishing && !isDestroyed) {
                        Toast.makeText(this@HistoryActivity, getString(R.string.share_progress_error), Toast.LENGTH_SHORT).show()
                    }
                }
            } finally {
                isSharingProgress = false
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
            WidgetUpdateHelper.updateAllWidgets(applicationContext)
            // EPIC-09 T09.2 — without this, the next Health Connect sync would find the still-
            // present linked record and re-import it as "new", resurrecting the deletion.
            record.healthConnectRecordId?.let {
                com.samsunggalaxy.health.HealthConnectManager.deleteRecords(applicationContext, listOf(it))
            }
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
    /** Set by HistoryActivity from the persisted preference — see EPIC-04 T04.1. */
    var unitSystem: String = UnitFormatter.METRIC
        set(value) {
            if (field == value) return
            field = value
            notifyDataSetChanged()
        }

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
            tvDetails.text = "${UnitFormatter.formatWeight(record.weight, unitSystem)} • ${UnitFormatter.formatHeight(record.height, unitSystem)}"

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
