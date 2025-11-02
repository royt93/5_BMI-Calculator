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

class IdealWeightCalculatorActivity : BaseActivity() {
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

        btnCalculate.setOnClickListener {
            val height = etHeight.text.toString().toDoubleOrNull() ?: 0.0
            val isMale = rgGender.checkedRadioButtonId == R.id.rbMale

            if (height > 0) {
                val range = CalculatorUtils.calculateIdealWeightRange(height, isMale)
                tvResult.text = "Your Ideal Weight Range:\n${String.format("%.0f", range.first)} - ${String.format("%.0f", range.second)} kg\n\nBased on the Devine formula."
            } else {
                tvResult.text = "Please enter valid height"
            }
        }
    }
}
