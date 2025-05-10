package com.samsunggalaxy.ui

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.lifecycle.lifecycleScope
import com.samsunggalaxy.BaseActivity
import com.samsunggalaxy.BuildConfig
import com.samsunggalaxy.R
import com.samsunggalaxy.sdkadbmob.AdMobManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.jvm.java

@SuppressLint("CustomSplashScreen")
class SplashAct : BaseActivity() {

    public override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)

        AdMobManager.initSplashScreen(this, {
            goToMain()
        })
    }

    private fun goToMain() {
        val intent = Intent(this, MainAct::class.java)
        startActivity(intent)
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        // Trì hoãn finish để đợi animation hoàn tất
        window.decorView.postDelayed({
            finish() // Finish sau animation
        }, 300) // delay khoảng 300ms (hoặc đúng thời gian của animation)
    }
}
