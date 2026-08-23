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
import com.samsunggalaxy.sdkadbmob.UIUtils
import com.samsunggalaxy.utils.CalculatorUtils
import com.samsunggalaxy.utils.PreferencesManager
import com.samsunggalaxy.utils.UnitFormatter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class BmrCalculatorActivity : BaseActivity() {
    private var unitSystem: String = UnitFormatter.METRIC

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        UIUtils.setupEdgeToEdge1(window)
        setContentView(R.layout.a_bmr_calculator)
        UIUtils.setupEdgeToEdge2(
            rootView = findViewById(R.id.layoutRoot),
            paddingTop = true,
            paddingBottom = true
        )

        val etWeight = findViewById<EditText>(R.id.etWeight)
        val etHeight = findViewById<EditText>(R.id.etHeight)
        val etAge = findViewById<EditText>(R.id.etAge)
        val rgGender = findViewById<RadioGroup>(R.id.rgGender)
        val btnCalculate = findViewById<Button>(R.id.btnCalculate)
        val tvResult = findViewById<TextView>(R.id.tvResult)

        findViewById<View>(R.id.ivBack)?.setOnClickListener {
            finish()
        }

        // EPIC-04 T04.1: hints reflect the persisted unit system; input is converted to
        // metric before CalculatorUtils (which is metric-only) — BMR itself is calorie-based
        // so the result needs no conversion back.
        lifecycleScope.launch {
            unitSystem = PreferencesManager(this@BmrCalculatorActivity).unitSystem.first()
            etWeight.hint = "${getString(R.string.weight)} (${UnitFormatter.weightUnitLabel(unitSystem)})"
            etHeight.hint = "${getString(R.string.height)} (${UnitFormatter.heightUnitLabel(unitSystem)})"
        }

        btnCalculate.setOnClickListener {
            val weightInput = etWeight.text.toString().toDoubleOrNull() ?: 0.0
            val heightInput = etHeight.text.toString().toDoubleOrNull() ?: 0.0
            val age = etAge.text.toString().toIntOrNull() ?: 0
            val isMale = rgGender.checkedRadioButtonId == R.id.rbMale

            if (weightInput > 0 && heightInput > 0 && age > 0) {
                val weight = UnitFormatter.weightToMetric(weightInput, unitSystem)
                val height = UnitFormatter.heightToMetric(heightInput, unitSystem)
                val bmr = CalculatorUtils.calculateBMR(weight, height, age, isMale)
                tvResult.text = "${getString(R.string.bmr_calculator)}: ${String.format("%.0f", bmr)} ${getString(R.string.cal_per_day)}\n\n${getString(R.string.bmr_description)}"
            } else {
                tvResult.text = getString(R.string.please_enter_valid_values)
            }
        }
    }
}
