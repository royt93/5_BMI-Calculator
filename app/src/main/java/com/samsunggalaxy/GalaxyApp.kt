package com.samsunggalaxy

import android.app.Application
import android.content.Context
import android.util.Log
import com.applovin.sdk.AppLovinSdk
import com.google.android.material.color.DynamicColors
import com.roy.sdkadbmob.AdManager
import com.roy.sdkadbmob.AdSdkConfig
import com.samsunggalaxy.utils.LocaleHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class GalaxyApp : Application() {

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.DEBUG) Log.d("roy93~", "GalaxyApp.onCreate")

        // ⚡ FIX: Start AppLovin SDK init AS THE VERY FIRST ACTION
        // AppLovin network handshake (~5s) now starts before DynamicColors, setConfig, earlyInit
        // → gives initSplashScreen max time to load App Open Ad before 8s timeout
        val applovinSdk = AppLovinSdk.getInstance(this).also { sdk ->
            sdk.mediationProvider = "max"
        }

        // These are synchronous & fast (<2ms) — run right after init kickoff
        DynamicColors.applyToActivitiesIfAvailable(this)

        val adConfig = AdSdkConfig(
            isEnableAdmob          = BuildConfig.IS_ENABLE_ADMOB,
            isDebug                = BuildConfig.DEBUG,
            admobBannerId          = BuildConfig.ADMOB_BANNER_ID,
            admobInterstitialId    = BuildConfig.ADMOB_INTERSTITIAL_ID,
            admobAppOpenId         = BuildConfig.ADMOB_APP_OPEN_ID,
            applovinBannerId       = BuildConfig.APPLOVIN_BANNER_ID,
            applovinInterstitialId = BuildConfig.APPLOVIN_INTERSTITIAL_ID,
            applovinAppOpenId      = BuildConfig.APPLOVIN_APP_OPEN_ID,
        )
        AdManager.setConfig(adConfig)
        AdManager.earlyInit(this)

        // Start SDK init callback — by the time this fires (~5s later),
        // setConfig + earlyInit are already done
        applovinSdk.initializeSdk {
            AdManager.init(this, adConfig) { success, gaid ->
                if (BuildConfig.DEBUG) Log.d("roy93~", "AdManager init: success=$success, gaid=$gaid")
            }
        }

        initializeDefaultProfile()
    }

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(LocaleHelper.onAttach(base))
    }

    private fun initializeDefaultProfile() {
        applicationScope.launch {
            try {
                val database = com.samsunggalaxy.data.AppDatabase.getDatabase(applicationContext)
                val repository = com.samsunggalaxy.data.BmiRepository(database.bmiDao(), database.profileDao())
                if (repository.getCurrentProfile() == null) {
                    repository.createDefaultProfile()
                }
            } catch (e: Exception) {
                Log.e("roy93~", "initializeDefaultProfile error", e)
            }
        }
    }
}
