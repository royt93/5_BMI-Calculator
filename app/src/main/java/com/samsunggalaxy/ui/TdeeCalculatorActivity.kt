package com.samsunggalaxy.ui

import android.os.Bundle
import android.view.View
import android.widget.*
import com.samsunggalaxy.BaseActivity
import com.samsunggalaxy.R
import com.samsunggalaxy.sdkadbmob.UIUtils
import com.samsunggalaxy.utils.CalculatorUtils

class TdeeCalculatorActivity : BaseActivity() {
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
        val spinnerActivity = findViewById<Spinner>(R.id.spinnerActivity)
        val btnCalculate = findViewById<Button>(R.id.btnCalculate)
        val tvResult = findViewById<TextView>(R.id.tvResult)

        findViewById<View>(R.id.ivBack)?.setOnClickListener {
            finish()
        }

        val activities = arrayOf("Sedentary", "Lightly Active", "Moderately Active", "Very Active", "Super Active")
        spinnerActivity.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, activities)

        btnCalculate.setOnClickListener {
            val weight = etWeight.text.toString().toDoubleOrNull() ?: 0.0
            val height = etHeight.text.toString().toDoubleOrNull() ?: 0.0
            val age = etAge.text.toString().toIntOrNull() ?: 0
            val isMale = rgGender.checkedRadioButtonId == R.id.rbMale
            val activityLevel = spinnerActivity.selectedItemPosition

            if (weight > 0 && height > 0 && age > 0) {
                val bmr = CalculatorUtils.calculateBMR(weight, height, age, isMale)
                val tdee = CalculatorUtils.calculateTDEE(bmr, activityLevel)
                tvResult.text = "Your TDEE: ${String.format("%.0f", tdee)} cal/day\n\nTotal daily energy expenditure based on your activity level."
            } else {
                tvResult.text = "Please enter valid values"
            }
        }
    }
}
