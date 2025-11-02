package com.samsunggalaxy.ui

import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.RadioGroup
import android.widget.TextView
import com.samsunggalaxy.BaseActivity
import com.samsunggalaxy.R
import com.samsunggalaxy.sdkadbmob.UIUtils
import com.samsunggalaxy.utils.CalculatorUtils

class BmrCalculatorActivity : BaseActivity() {
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

        btnCalculate.setOnClickListener {
            val weight = etWeight.text.toString().toDoubleOrNull() ?: 0.0
            val height = etHeight.text.toString().toDoubleOrNull() ?: 0.0
            val age = etAge.text.toString().toIntOrNull() ?: 0
            val isMale = rgGender.checkedRadioButtonId == R.id.rbMale

            if (weight > 0 && height > 0 && age > 0) {
                val bmr = CalculatorUtils.calculateBMR(weight, height, age, isMale)
                tvResult.text = "${getString(R.string.bmr_calculator)}: ${String.format("%.0f", bmr)} ${getString(R.string.cal_per_day)}\n\n${getString(R.string.bmr_description)}"
            } else {
                tvResult.text = getString(R.string.please_enter_valid_values)
            }
        }
    }
}
