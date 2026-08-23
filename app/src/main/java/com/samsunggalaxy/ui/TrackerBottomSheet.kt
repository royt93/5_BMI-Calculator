package com.samsunggalaxy.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.formatter.ValueFormatter
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.tabs.TabLayout
import com.samsunggalaxy.R
import com.samsunggalaxy.data.AppDatabase
import com.samsunggalaxy.data.BmiRecord
import com.samsunggalaxy.data.BmiRepository
import com.samsunggalaxy.utils.PreferencesManager
import com.samsunggalaxy.utils.UnitFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class TrackerBottomSheet : BottomSheetDialogFragment() {

    private lateinit var repository: BmiRepository
    private var records: List<BmiRecord> = emptyList()
    private var currentTab = 0 // 0=weight, 1=height
    private var unitSystem: String = UnitFormatter.METRIC

    override fun getTheme(): Int = com.google.android.material.R.style.ThemeOverlay_Material3_BottomSheetDialog

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_tracker_bottom_sheet, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val ctx = requireContext()
        val db = AppDatabase.getDatabase(ctx)
        repository = BmiRepository(db.bmiDao(), db.profileDao())

        // Close button
        view.findViewById<View>(R.id.ivCloseTracker).setOnClickListener { dismiss() }

        // Tabs
        val tabLayout = view.findViewById<TabLayout>(R.id.tabLayoutTracker)
        tabLayout.addTab(tabLayout.newTab().setText(getString(R.string.tracker_tab_weight)))
        tabLayout.addTab(tabLayout.newTab().setText(getString(R.string.tracker_tab_height)))
        tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                currentTab = tab?.position ?: 0
                updateUI(view)
            }
            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })

        // Load data: fetch profile on IO, observe on Main
        lifecycleScope.launch(Dispatchers.IO) {
            val profile = repository.getCurrentProfile()
            val profileId = profile?.id ?: 1L
            unitSystem = PreferencesManager(ctx).unitSystem.first()
            withContext(Dispatchers.Main) {
                repository.getAllRecordsAscending(profileId).observe(viewLifecycleOwner) { recs ->
                    records = recs
                    updateUI(view)
                }
            }
        }
    }

    private fun updateUI(view: View) {
        if (!isAdded) return
        if (records.isEmpty()) return
        val ctx = context ?: return

        val chart = view.findViewById<LineChart>(R.id.chartTracker) ?: return
        val tvLatest = view.findViewById<TextView>(R.id.tvStatLatest) ?: return
        val tvMin = view.findViewById<TextView>(R.id.tvStatMin) ?: return
        val tvMax = view.findViewById<TextView>(R.id.tvStatMax) ?: return
        val tvTrend = view.findViewById<TextView>(R.id.tvStatTrend) ?: return
        val tvSummary = view.findViewById<TextView>(R.id.tvTrackerSummary) ?: return

        val isWeight = currentTab == 0
        // Converted to the current display unit right here — every downstream stat/chart
        // read in this function operates on `values`, so nothing else needs to know units.
        val values = if (isWeight) records.map { UnitFormatter.weightToDisplay(it.weight, unitSystem) }
            else records.map { UnitFormatter.heightToDisplay(it.height, unitSystem) }
        val unit = if (isWeight) UnitFormatter.weightUnitLabel(unitSystem) else UnitFormatter.heightUnitLabel(unitSystem)
        val label = if (isWeight) getString(R.string.weight) else getString(R.string.height)

        // --- Stats ---
        val latest = values.last()
        val min = values.min()
        val max = values.max()
        tvLatest.text = String.format("%.1f %s", latest, unit)
        tvMin.text = String.format("%.1f %s", min, unit)
        tvMax.text = String.format("%.1f %s", max, unit)

        // Trend: compare latest vs first
        val first = values.first()
        val diff = latest - first
        val trendArrow = when {
            diff > 0.1 -> "↑"
            diff < -0.1 -> "↓"
            else -> "→"
        }
        tvTrend.text = "$trendArrow ${String.format("%+.1f", diff)}"
        val trendColor = when {
            isWeight && diff < -0.1 -> R.color.bmi_healthy
            isWeight && diff > 0.1 -> R.color.bmi_obese
            !isWeight && diff > 0.1 -> R.color.bmi_healthy
            !isWeight && diff < -0.1 -> R.color.bmi_obese
            else -> R.color.textColorAdditional
        }
        tvTrend.setTextColor(ContextCompat.getColor(ctx, trendColor))

        // Summary (last 30 days)
        val thirtyDaysAgo = System.currentTimeMillis() - 30L * 24 * 60 * 60 * 1000
        val recentRecords = records.filter { it.timestamp >= thirtyDaysAgo }
        if (recentRecords.size >= 2) {
            val recentValues = if (isWeight) recentRecords.map { UnitFormatter.weightToDisplay(it.weight, unitSystem) }
                else recentRecords.map { UnitFormatter.heightToDisplay(it.height, unitSystem) }
            val recentDiff = recentValues.last() - recentValues.first()
            val direction = if (recentDiff > 0) getString(R.string.tracker_increased)
                else getString(R.string.tracker_decreased)
            tvSummary.text = getString(R.string.tracker_summary_30d, label, direction,
                String.format("%.1f", kotlin.math.abs(recentDiff)), unit)
            tvSummary.visibility = View.VISIBLE
        } else {
            tvSummary.visibility = View.GONE
        }

        // --- Chart ---
        val entries = records.mapIndexed { i, rec ->
            val v = if (isWeight) UnitFormatter.weightToDisplay(rec.weight, unitSystem).toFloat()
                else UnitFormatter.heightToDisplay(rec.height, unitSystem).toFloat()
            Entry(i.toFloat(), v)
        }

        val primaryColor = ContextCompat.getColor(ctx, R.color.bmi_healthy)
        val dataSet = LineDataSet(entries, label).apply {
            color = primaryColor
            valueTextColor = ContextCompat.getColor(ctx, R.color.textColor)
            lineWidth = 2.5f
            setCircleColor(primaryColor)
            circleRadius = 3.5f
            setDrawValues(false)
            mode = LineDataSet.Mode.CUBIC_BEZIER
            setDrawFilled(true)
            fillColor = primaryColor
            fillAlpha = 30
        }

        val textColor = ContextCompat.getColor(ctx, R.color.textColor)
        chart.apply {
            data = LineData(dataSet)
            description.isEnabled = false
            setDrawGridBackground(false)
            setTouchEnabled(true)
            isDragEnabled = true
            setScaleEnabled(true)
            setPinchZoom(true)
            legend.isEnabled = false
            axisRight.isEnabled = false
            axisLeft.textColor = textColor
            xAxis.textColor = textColor
            xAxis.position = XAxis.XAxisPosition.BOTTOM
            xAxis.setDrawGridLines(false)
            xAxis.valueFormatter = object : ValueFormatter() {
                private val fmt = SimpleDateFormat("MM/dd", Locale.getDefault())
                override fun getFormattedValue(value: Float): String {
                    val idx = value.toInt()
                    return if (idx in records.indices) fmt.format(Date(records[idx].timestamp)) else ""
                }
            }
            animateX(500)
            invalidate()
        }
    }

    companion object {
        const val TAG = "TrackerBottomSheet"
    }
}
