package com.samsunggalaxy.ui

import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.lifecycle.lifecycleScope
import com.google.android.material.textfield.MaterialAutoCompleteTextView
import com.samsunggalaxy.BaseActivity
import com.samsunggalaxy.R
import com.samsunggalaxy.data.AppDatabase
import com.samsunggalaxy.data.BmiRepository
import com.samsunggalaxy.sdkadbmob.UIUtils
import com.samsunggalaxy.utils.CalculatorUtils
import com.samsunggalaxy.utils.PreferencesManager
import com.samsunggalaxy.utils.UnitFormatter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class TdeeCalculatorActivity : BaseActivity() {
    private var unitSystem: String = UnitFormatter.METRIC

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        UIUtils.setupEdgeToEdge1(window)
        setContentView(R.layout.a_tdee_calculator)
        UIUtils.setupEdgeToEdge2(
            rootView = findViewById(R.id.layoutRoot),
            paddingTop = true,
            paddingBottom = true
        )

        val etWeight = findViewById<EditText>(R.id.etWeight)
        val etHeight = findViewById<EditText>(R.id.etHeight)
        val etAge = findViewById<EditText>(R.id.etAge)
        val rgGender = findViewById<RadioGroup>(R.id.rgGender)
        val spinnerActivity = findViewById<AutoCompleteTextView>(R.id.spinnerActivity)
        val btnCalculate = findViewById<Button>(R.id.btnCalculate)
        val tvResult = findViewById<TextView>(R.id.tvResult)

        findViewById<View>(R.id.ivBack)?.setOnClickListener {
            finish()
        }

        val activities = arrayOf(
            getString(R.string.sedentary),
            getString(R.string.lightly_active),
            getString(R.string.moderately_active),
            getString(R.string.very_active),
            getString(R.string.super_active)
        )
        val adapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, activities)
        spinnerActivity.setAdapter(adapter)
        spinnerActivity.setText(activities[0], false)

        // EPIC-04 T04.1 (unit hints) + T04.3 (preselect the last-used activity level instead
        // of always resetting to Sedentary — the write path already existed from EPIC-00 T00.2).
        lifecycleScope.launch {
            val prefs = PreferencesManager(this@TdeeCalculatorActivity)
            unitSystem = prefs.unitSystem.first()
            etWeight.hint = "${getString(R.string.weight)} (${UnitFormatter.weightUnitLabel(unitSystem)})"
            etHeight.hint = "${getString(R.string.height)} (${UnitFormatter.heightUnitLabel(unitSystem)})"

            val savedActivityLevel = prefs.activityLevel.first()
            if (savedActivityLevel in activities.indices) {
                spinnerActivity.setText(activities[savedActivityLevel], false)
            }

            // EPIC-06 T06.1: prefill weight/height/age/gender from the latest weigh-in.
            val database = AppDatabase.getDatabase(this@TdeeCalculatorActivity)
            val repository = BmiRepository(database.bmiDao(), database.profileDao(), database.bodyMeasurementDao())
            val record = repository.getCurrentProfileMostRecentRecord()
            if (record != null) {
                // Locale.US — see BmrCalculatorActivity for why (toDoubleOrNull needs '.').
                etWeight.setText(String.format(java.util.Locale.US, "%.1f", UnitFormatter.weightToDisplay(record.weight, unitSystem)))
                etHeight.setText(String.format(java.util.Locale.US, "%.1f", UnitFormatter.heightToDisplay(record.height, unitSystem)))
                etAge.setText(record.age.toString())
                rgGender.check(if (record.gender == 1) R.id.rbFemale else R.id.rbMale)
            }
        }

        btnCalculate.setOnClickListener {
            val weightInput = etWeight.text.toString().toDoubleOrNull() ?: 0.0
            val heightInput = etHeight.text.toString().toDoubleOrNull() ?: 0.0
            val age = etAge.text.toString().toIntOrNull() ?: 0
            val isMale = rgGender.checkedRadioButtonId == R.id.rbMale
            val activityLevel = activities.indexOf(spinnerActivity.text.toString())

            if (weightInput > 0 && heightInput > 0 && age > 0) {
                val weight = UnitFormatter.weightToMetric(weightInput, unitSystem)
                val height = UnitFormatter.heightToMetric(heightInput, unitSystem)
                val bmr = CalculatorUtils.calculateBMR(weight, height, age, isMale)
                val tdee = CalculatorUtils.calculateTDEE(bmr, activityLevel)
                tvResult.text = getString(R.string.tdee_description) + ": ${String.format("%.0f", tdee)} ${getString(R.string.cal_per_day)}"
                // Persist so ResultAct's saved/displayed TDEE stops defaulting to Sedentary — EPIC-00 T00.2.
                if (activityLevel in 0..4) {
                    lifecycleScope.launch { PreferencesManager(this@TdeeCalculatorActivity).setActivityLevel(activityLevel) }
                }
            } else {
                tvResult.text = getString(R.string.please_enter_valid_values)
            }
        }
    }
}
