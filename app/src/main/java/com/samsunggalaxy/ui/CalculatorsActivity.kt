package com.samsunggalaxy.ui

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.cardview.widget.CardView
import com.samsunggalaxy.BaseActivity
import com.samsunggalaxy.R
import com.samsunggalaxy.sdkadbmob.UIUtils

class CalculatorsActivity : BaseActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        UIUtils.setupEdgeToEdge1(window)
        setContentView(R.layout.a_calculators)
        UIUtils.setupEdgeToEdge2(
            rootView = findViewById(R.id.layoutRoot),
            paddingTop = true,
            paddingBottom = true
        )

        findViewById<View>(R.id.ivBack)?.setOnClickListener {
            finish()
        }

        findViewById<CardView>(R.id.cardBmr).setOnClickListener {
            startActivity(Intent(this, BmrCalculatorActivity::class.java))
        }

        findViewById<CardView>(R.id.cardTdee).setOnClickListener {
            startActivity(Intent(this, TdeeCalculatorActivity::class.java))
        }

        findViewById<CardView>(R.id.cardIdealWeight).setOnClickListener {
            startActivity(Intent(this, IdealWeightCalculatorActivity::class.java))
        }

        findViewById<CardView>(R.id.cardBodyFat).setOnClickListener {
            startActivity(Intent(this, BodyFatCalculatorActivity::class.java))
        }
    }
}
