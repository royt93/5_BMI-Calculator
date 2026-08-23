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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class IdealWeightCalculatorActivity : BaseActivity() {
    private var unitSystem: String = UnitFormatter.METRIC

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        UIUtils.setupEdgeToEdge1(window)
        setContentView(R.layout.a_ideal_weight_calculator)
        UIUtils.setupEdgeToEdge2(
            rootView = findViewById(R.id.layoutRoot),
            paddingTop = true,
            paddingBottom = true
        )

        val etHeight = findViewById<EditText>(R.id.etHeight)
        val rgGender = findViewById<RadioGroup>(R.id.rgGender)
        val btnCalculate = findViewById<Button>(R.id.btnCalculate)
        val tvResult = findViewById<TextView>(R.id.tvResult)

        findViewById<View>(R.id.ivBack)?.setOnClickListener {
            finish()
        }

        // EPIC-04 T04.1: input (height) is converted to metric before CalculatorUtils; the
        // OUTPUT here is a weight range, so — unlike BMR/TDEE calorie results — it must be
        // converted back to the display unit too, or an imperial user gets a "kg" range.
        lifecycleScope.launch {
            unitSystem = PreferencesManager(this@IdealWeightCalculatorActivity).unitSystem.first()
            etHeight.hint = "${getString(R.string.height)} (${UnitFormatter.heightUnitLabel(unitSystem)})"

            // EPIC-06 T06.1: prefill height/gender from the latest weigh-in.
            val database = AppDatabase.getDatabase(this@IdealWeightCalculatorActivity)
            val repository = BmiRepository(database.bmiDao(), database.profileDao())
            val record = repository.getCurrentProfileMostRecentRecord()
            if (record != null) {
                // Locale.US — see BmrCalculatorActivity for why (toDoubleOrNull needs '.').
                etHeight.setText(String.format(java.util.Locale.US, "%.1f", UnitFormatter.heightToDisplay(record.height, unitSystem)))
                rgGender.check(if (record.gender == 1) R.id.rbFemale else R.id.rbMale)
            }
        }

        btnCalculate.setOnClickListener {
            val heightInput = etHeight.text.toString().toDoubleOrNull() ?: 0.0
            val isMale = rgGender.checkedRadioButtonId == R.id.rbMale

            if (heightInput > 0) {
                val height = UnitFormatter.heightToMetric(heightInput, unitSystem)
                val range = CalculatorUtils.calculateIdealWeightRange(height, isMale)
                val minDisplay = UnitFormatter.weightToDisplay(range.first, unitSystem)
                val maxDisplay = UnitFormatter.weightToDisplay(range.second, unitSystem)
                val unitLabel = UnitFormatter.weightUnitLabel(unitSystem)
                tvResult.text = "${getString(R.string.ideal_weight)}:\n${String.format("%.0f", minDisplay)} - ${String.format("%.0f", maxDisplay)} $unitLabel\n\n${getString(R.string.ideal_weight_description)}"
            } else {
                tvResult.text = getString(R.string.please_enter_valid_values)
            }
        }
    }
}
