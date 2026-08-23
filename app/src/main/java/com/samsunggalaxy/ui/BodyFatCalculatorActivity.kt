package com.samsunggalaxy.ui

import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.RadioGroup
import android.widget.TextView
import androidx.lifecycle.lifecycleScope
import com.samsunggalaxy.BaseActivity
import com.samsunggalaxy.R
import com.samsunggalaxy.data.AppDatabase
import com.samsunggalaxy.data.BmiRepository
import com.samsunggalaxy.sdkadbmob.UIUtils
import com.samsunggalaxy.utils.CalculatorUtils
import com.samsunggalaxy.utils.PreferencesManager
import com.samsunggalaxy.utils.UnitFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class BodyFatCalculatorActivity : BaseActivity() {
    private var unitSystem: String = UnitFormatter.METRIC
    private var pendingBodyFat: Double? = null
    private var pendingRecordId: Long? = null
    // Guards against a stale Calculate tap's async DB lookup overwriting a newer one's
    // pending save state if two taps' IO coroutines resolve out of order.
    private var calculationToken = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        UIUtils.setupEdgeToEdge1(window)
        setContentView(R.layout.a_body_fat_calculator)
        UIUtils.setupEdgeToEdge2(
            rootView = findViewById(R.id.layoutRoot),
            paddingTop = true,
            paddingBottom = true
        )

        val database = AppDatabase.getDatabase(this)
        val repository = BmiRepository(database.bmiDao(), database.profileDao())

        val etHeight = findViewById<EditText>(R.id.etHeight)
        val etWaist = findViewById<EditText>(R.id.etWaist)
        val etNeck = findViewById<EditText>(R.id.etNeck)
        val etHip = findViewById<EditText>(R.id.etHip)
        val rgGender = findViewById<RadioGroup>(R.id.rgGender)
        val btnCalculate = findViewById<Button>(R.id.btnCalculate)
        val tvResult = findViewById<TextView>(R.id.tvResult)
        val btnSaveToHistory = findViewById<Button>(R.id.btnSaveToHistory)
        val tvSaveStatus = findViewById<TextView>(R.id.tvSaveStatus)

        findViewById<View>(R.id.ivBack)?.setOnClickListener {
            finish()
        }

        rgGender.setOnCheckedChangeListener { _, checkedId ->
            etHip.isEnabled = checkedId == R.id.rbFemale
        }

        // Registered once — reads whatever the most recent Calculate tap resolved, instead of
        // rebinding a fresh listener (with its own captured bodyFat/recordId) on every tap.
        btnSaveToHistory.setOnClickListener {
            val bodyFat = pendingBodyFat
            val recordId = pendingRecordId
            if (bodyFat == null || recordId == null) return@setOnClickListener
            btnSaveToHistory.isEnabled = false
            lifecycleScope.launch(Dispatchers.IO) {
                val rowsUpdated = repository.updateBodyFatPercentage(recordId, bodyFat)
                withContext(Dispatchers.Main) {
                    tvSaveStatus.text = if (rowsUpdated > 0) {
                        getString(R.string.body_fat_saved)
                    } else {
                        // Record was deleted (e.g. via History) between Calculate and Save.
                        getString(R.string.body_fat_no_record_today)
                    }
                    tvSaveStatus.visibility = View.VISIBLE
                }
            }
        }

        // EPIC-04 T04.1: all 4 measurements are lengths, converted to metric before
        // CalculatorUtils; body fat % is dimensionless, so the result needs no conversion.
        lifecycleScope.launch {
            unitSystem = PreferencesManager(this@BodyFatCalculatorActivity).unitSystem.first()
            val unitLabel = UnitFormatter.heightUnitLabel(unitSystem)
            etHeight.hint = "${getString(R.string.height)} ($unitLabel)"
            etWaist.hint = "${getString(R.string.waist)} ($unitLabel)"
            etNeck.hint = "${getString(R.string.neck)} ($unitLabel)"
            etHip.hint = "${getString(R.string.hip)} ($unitLabel)"

            // EPIC-06 T06.1: height/gender come from the latest weigh-in; waist/neck/hip
            // aren't tracked anywhere else in the app, so they stay blank for the user to fill.
            val record = repository.getCurrentProfileMostRecentRecord()
            if (record != null) {
                // Locale.US — see BmrCalculatorActivity for why (toDoubleOrNull needs '.').
                etHeight.setText(String.format(java.util.Locale.US, "%.1f", UnitFormatter.heightToDisplay(record.height, unitSystem)))
                rgGender.check(if (record.gender == 1) R.id.rbFemale else R.id.rbMale)
            }
        }

        btnCalculate.setOnClickListener {
            val token = ++calculationToken
            pendingBodyFat = null
            pendingRecordId = null
            btnSaveToHistory.visibility = View.GONE
            btnSaveToHistory.isEnabled = true
            tvSaveStatus.visibility = View.GONE

            val heightInput = etHeight.text.toString().toDoubleOrNull() ?: 0.0
            val waistInput = etWaist.text.toString().toDoubleOrNull() ?: 0.0
            val neckInput = etNeck.text.toString().toDoubleOrNull() ?: 0.0
            val hipInput = etHip.text.toString().toDoubleOrNull()
            val isMale = rgGender.checkedRadioButtonId == R.id.rbMale

            if (heightInput > 0 && waistInput > 0 && neckInput > 0) {
                if (!isMale && hipInput == null) {
                    tvResult.text = "${getString(R.string.hip)} - ${getString(R.string.female_only)}"
                    return@setOnClickListener
                }
                val height = UnitFormatter.heightToMetric(heightInput, unitSystem)
                val waist = UnitFormatter.heightToMetric(waistInput, unitSystem)
                val neck = UnitFormatter.heightToMetric(neckInput, unitSystem)
                val hip = hipInput?.let { UnitFormatter.heightToMetric(it, unitSystem) }
                val bodyFat = CalculatorUtils.calculateBodyFat(height, waist, neck, hip, isMale)

                // calculateBodyFat() returns 0.0 both for a (near-impossible) genuine zero
                // result AND for invalid measurement combos (e.g. neck >= waist) — treat it
                // as invalid input rather than persisting a bogus 0% to history.
                if (bodyFat <= 0) {
                    tvResult.text = getString(R.string.please_enter_valid_measurements)
                    return@setOnClickListener
                }

                tvResult.text = "${getString(R.string.body_fat_calculator)}: ${String.format("%.1f", bodyFat)}%\n\n${getString(R.string.body_fat_description)}"

                // EPIC-06 T06.2: attach the result onto today's weigh-in record, if one exists,
                // instead of fabricating a whole new BmiRecord with a made-up weight/BMI.
                lifecycleScope.launch(Dispatchers.IO) {
                    val todayRecord = repository.getCurrentProfileMostRecentRecord()
                        ?.takeIf { CalculatorUtils.isSameCalendarDay(it.timestamp, System.currentTimeMillis()) }

                    withContext(Dispatchers.Main) {
                        if (token != calculationToken) return@withContext // superseded by a newer Calculate tap

                        if (todayRecord != null) {
                            pendingBodyFat = bodyFat
                            pendingRecordId = todayRecord.id
                            btnSaveToHistory.visibility = View.VISIBLE
                        } else {
                            tvSaveStatus.text = getString(R.string.body_fat_no_record_today)
                            tvSaveStatus.visibility = View.VISIBLE
                        }
                    }
                }
            } else {
                tvResult.text = getString(R.string.please_enter_valid_measurements)
            }
        }
    }
}
