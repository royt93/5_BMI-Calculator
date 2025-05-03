package com.samsunggalaxy.ui

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.lifecycle.lifecycleScope
import com.samsunggalaxy.BaseActivity
import com.samsunggalaxy.BuildConfig
import com.samsunggalaxy.sdkadbmob.AdMobManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.jvm.java

@SuppressLint("CustomSplashScreen")
class SplashAct : BaseActivity() {

    public override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        lifecycleScope.launch {
            var hasCalledGoToMain = false
            val job = launch {
                delay(3_000)
                if (!hasCalledGoToMain) {
                    hasCalledGoToMain = true
                    Log.d("roy93~", "goToMain #1")
                    goToMain()
                }
            }
            AdMobManager.loadAppOpenAd(
                context = this@SplashAct,
                adUnitId = BuildConfig.ADMOB_APP_OPEN_ID,
                onAdLoaded = {
                    if (!hasCalledGoToMain) {
                        hasCalledGoToMain = true
                        job.cancel()
                        Log.d("roy93~", "goToMain #2")
                        goToMain()
                        AdMobManager.showAppOpenAd(this@SplashAct)
                    }
                },
            )
        }
    }

    private fun goToMain() {
        val intent = Intent(this, MainAct::class.java)
        startActivity(intent)
        overridePendingTransition(0, 0)
        finish()
    }
}
