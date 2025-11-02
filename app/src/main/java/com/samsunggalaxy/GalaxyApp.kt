package com.samsunggalaxy

import android.app.Application
import android.content.Context
import android.util.Log
import com.google.android.gms.ads.MobileAds
import com.google.android.material.color.DynamicColors
import com.samsunggalaxy.sdkadbmob.AdMobManager
import com.samsunggalaxy.utils.LocaleHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

//TODO firebase
//TODO keep value cuoi cung de hien thi len wheel view

//admob
//done mckimquyen
//applovin ad
//splash screen
//review in app bingo
//keystore
//leak canary
//policy
//share app
//rate app
//more app
//ic launcher
//change color
//ad id manifest
//scale 1.0
//120hz

class GalaxyApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Log.d("roy93~", "GalaxyApp.onCreate: Application started")
        DynamicColors.applyToActivitiesIfAvailable(this)
//        this.setupApplovinAd()
        setupAdmob()
        initializeDefaultProfile()
    }

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(LocaleHelper.onAttach(base))
    }

    private fun initializeDefaultProfile() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val database = com.samsunggalaxy.data.AppDatabase.getDatabase(applicationContext)
                val repository = com.samsunggalaxy.data.BmiRepository(database.bmiDao(), database.profileDao())

                // Check if default profile exists
                val currentProfile = repository.getCurrentProfile()
                if (currentProfile == null) {
                    // Create default profile with ID that will be auto-generated
                    repository.createDefaultProfile()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun setupAdmob() {
        CoroutineScope(Dispatchers.IO).launch {
            MobileAds.initialize(this@GalaxyApp) {}
            AdMobManager.init(this@GalaxyApp) { success, gaidCurrent ->
                Log.d("roy93~", "AdMobManager init success $success, gaidCurrent $gaidCurrent")
            }
        }
//        registerActivityLifecycleCallbacks(
//            AppLifecycleListener(
//                { isForeground, activity ->
//                    if (isForeground) {
//                        Log.d("roy93~", "App moved to Foreground")
//                        Log.d("roy93~", "activity.localClassName ${activity.localClassName}")
//                        Log.d(
//                            "roy93~",
//                            "SplashActivity::class.java.simpleName ${SplashAct::class.java.simpleName}"
//                        )
//                        if (activity.localClassName == SplashAct::class.java.simpleName) {
//                            //do nothing
//                        } else {
////                            AdMobManager.showAppOpenAd(activity)
//                        }
//                    } else {
//                        Log.d("roy93~", "App moved to Background")
//                    }
//                }, { activity ->
//                    Log.d("roy93~", "callbackActivityCreated ${activity.localClassName}")
//                    if (activity.localClassName == SplashAct::class.java.simpleName) {
//                        //do nothing
//                    } else {
////                        AdMobManager.loadAppOpenAd(
////                            context = this,
////                            adUnitId = BuildConfig.ADMOB_APP_OPEN_ID,
////                            onAdLoaded = {},
////                        )
//                    }
//                }
//            )
//        )
    }
}
