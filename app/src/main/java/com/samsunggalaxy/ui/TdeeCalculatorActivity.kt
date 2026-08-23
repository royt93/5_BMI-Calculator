package com.samsunggalaxy.ui

import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.lifecycle.lifecycleScope
import com.google.android.material.textfield.MaterialAutoCompleteTextView
import com.samsunggalaxy.BaseActivity
import com.samsunggalaxy.R
import com.samsunggalaxy.sdkadbmob.UIUtils
import com.samsunggalaxy.utils.CalculatorUtils
import com.samsunggalaxy.utils.PreferencesManager
import kotlinx.coroutines.launch

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

        btnCalculate.setOnClickListener {
            val weight = etWeight.text.toString().toDoubleOrNull() ?: 0.0
            val height = etHeight.text.toString().toDoubleOrNull() ?: 0.0
            val age = etAge.text.toString().toIntOrNull() ?: 0
            val isMale = rgGender.checkedRadioButtonId == R.id.rbMale
            val activityLevel = activities.indexOf(spinnerActivity.text.toString())

            if (weight > 0 && height > 0 && age > 0) {
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
