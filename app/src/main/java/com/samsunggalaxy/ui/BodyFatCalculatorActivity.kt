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

class BodyFatCalculatorActivity : BaseActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        UIUtils.setupEdgeToEdge1(window)
        setContentView(R.layout.a_body_fat_calculator)
        UIUtils.setupEdgeToEdge2(
            rootView = findViewById(R.id.layoutRoot),
            paddingTop = true,
            paddingBottom = true
        )

        val etHeight = findViewById<EditText>(R.id.etHeight)
        val etWaist = findViewById<EditText>(R.id.etWaist)
        val etNeck = findViewById<EditText>(R.id.etNeck)
        val etHip = findViewById<EditText>(R.id.etHip)
        val rgGender = findViewById<RadioGroup>(R.id.rgGender)
        val btnCalculate = findViewById<Button>(R.id.btnCalculate)
        val tvResult = findViewById<TextView>(R.id.tvResult)

        findViewById<View>(R.id.ivBack)?.setOnClickListener {
            finish()
        }

        rgGender.setOnCheckedChangeListener { _, checkedId ->
            etHip.isEnabled = checkedId == R.id.rbFemale
        }

        btnCalculate.setOnClickListener {
            val height = etHeight.text.toString().toDoubleOrNull() ?: 0.0
            val waist = etWaist.text.toString().toDoubleOrNull() ?: 0.0
            val neck = etNeck.text.toString().toDoubleOrNull() ?: 0.0
            val hip = etHip.text.toString().toDoubleOrNull()
            val isMale = rgGender.checkedRadioButtonId == R.id.rbMale

            if (height > 0 && waist > 0 && neck > 0) {
                if (!isMale && hip == null) {
                    tvResult.text = "${getString(R.string.hip)} - ${getString(R.string.female_only)}"
                    return@setOnClickListener
                }
                val bodyFat = CalculatorUtils.calculateBodyFat(height, waist, neck, hip, isMale)
                tvResult.text = "${getString(R.string.body_fat_calculator)}: ${String.format("%.1f", bodyFat)}%\n\n${getString(R.string.body_fat_description)}"
            } else {
                tvResult.text = getString(R.string.please_enter_valid_measurements)
            }
        }
    }
}
