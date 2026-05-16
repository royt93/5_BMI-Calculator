package com.samsunggalaxy.feature.vip

import android.os.Bundle
import androidx.appcompat.widget.AppCompatImageView
import com.samsunggalaxy.BaseActivity
import com.samsunggalaxy.R
import com.samsunggalaxy.sdkadbmob.UIUtils

/** Host activity cho [FVipManagement]. Custom action bar + fragment container. */
class VipActivity : BaseActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        UIUtils.setupEdgeToEdge1(window)
        setContentView(R.layout.a_vip)
        UIUtils.setupEdgeToEdge2(
            rootView = findViewById(R.id.layoutRoot),
            paddingTop = true,
            paddingBottom = true,
        )

        findViewById<AppCompatImageView>(R.id.ivBack)?.setOnClickListener {
            finish()
        }
    }
}
