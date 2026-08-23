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

class BodyFatCalculatorActivity : BaseActivity() {
    private var unitSystem: String = UnitFormatter.METRIC

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

        // EPIC-04 T04.1: all 4 measurements are lengths, converted to metric before
        // CalculatorUtils; body fat % is dimensionless, so the result needs no conversion.
        lifecycleScope.launch {
            unitSystem = PreferencesManager(this@BodyFatCalculatorActivity).unitSystem.first()
            val unitLabel = UnitFormatter.heightUnitLabel(unitSystem)
            etHeight.hint = "${getString(R.string.height)} ($unitLabel)"
            etWaist.hint = "${getString(R.string.waist)} ($unitLabel)"
            etNeck.hint = "${getString(R.string.neck)} ($unitLabel)"
            etHip.hint = "${getString(R.string.hip)} ($unitLabel)"
        }

        btnCalculate.setOnClickListener {
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
                tvResult.text = "${getString(R.string.body_fat_calculator)}: ${String.format("%.1f", bodyFat)}%\n\n${getString(R.string.body_fat_description)}"
            } else {
                tvResult.text = getString(R.string.please_enter_valid_measurements)
            }
        }
    }
}
