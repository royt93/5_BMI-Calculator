package com.samsunggalaxy

import android.app.Application
import android.content.Context
import android.util.Log
import com.google.android.material.color.DynamicColors
import com.roy.sdkadbmob.AdManager
import com.roy.sdkadbmob.AdSafetyLimits
import com.roy.sdkadbmob.AdSdkConfig
import com.roy.sdkadbmob.ErrorReporter
import com.roy.sdkadbmob.PaidEventListener
import com.samsunggalaxy.common.const.AdKeys
import com.samsunggalaxy.utils.AppLog
import com.samsunggalaxy.utils.LocaleHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

class GalaxyApp : Application() {

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        AppLog.d("GalaxyApp.onCreate")

        DynamicColors.applyToActivitiesIfAvailable(this)
        applyPersistedTheme()

        // AdSafety: release dùng UTILITY preset (90s throttle, 3/session, 5/day),
        // debug dùng TEST preset (gần như không throttle để QC test nhanh).
        val safetyLimits =
            if (BuildConfig.DEBUG) AdSafetyLimits.TEST else AdSafetyLimits.UTILITY

        val adConfig = AdSdkConfig(
            isEnableAdmob = BuildConfig.IS_ENABLE_ADMOB,
            isDebug = BuildConfig.DEBUG,
            admobBannerId = BuildConfig.ADMOB_BANNER_ID,
            admobInterstitialId = BuildConfig.ADMOB_INTERSTITIAL_ID,
            admobAppOpenId = BuildConfig.ADMOB_APP_OPEN_ID,
            admobRewardedId = BuildConfig.ADMOB_REWARDED_ID,
            applovinBannerId = BuildConfig.APPLOVIN_BANNER_ID,
            applovinInterstitialId = BuildConfig.APPLOVIN_INTERSTITIAL_ID,
            applovinAppOpenId = BuildConfig.APPLOVIN_APP_OPEN_ID,
            applovinRewardedId = BuildConfig.APPLOVIN_REWARDED_ID,
            applovinSdkKey = BuildConfig.APPLOVIN_SDK_KEY,
            vipKeySecret = AdKeys.VIP_SECRET,
            vipTokenPublicKey = BuildConfig.VIP_TOKEN_PUBLIC_KEY,
            // Step 7 rule 2 (AD_PROMPT_AOS.MD): App Open không được đè splash. Field cũ
            // `appOpenExcludedActivityNames` fallback theo tên string "SplashActivity" — class
            // thật ở đây là `SplashAct`, không khớp default ngầm đó, nên phải khai tường minh.
            appOpenExcludedActivities = listOf(com.samsunggalaxy.ui.SplashAct::class.java),
            safety = safetyLimits,
        )

        // Observability hook — forward SDK exception vào logcat (no Crashlytics yet).
        AdManager.errorReporter = ErrorReporter { throwable, context ->
            if (BuildConfig.DEBUG) {
                Log.w("roy93~Err", "[$context] ${throwable.message}", throwable)
            }
        }

        // Revenue tracking — PHẢI set trong Application.onCreate (không phải Activity): SDK gắn
        // "chủ sở hữu" listener là Activity foreground lúc set, tự xoá khi Activity đó destroy.
        AdManager.paidEventListener = PaidEventListener { adType, valueMicros, currency, precision, adSource ->
            AppLog.d("AdRevenue: $adType ${valueMicros / 1_000_000.0} $currency $precision $adSource")
        }

        AdManager.setConfig(adConfig)
        // Bước 3b (AD_PROMPT_AOS.MD) — BẮT BUỘC trước khi QA với ad-unit ID thật: khai hash test
        // device để tránh invalid traffic (KHÔNG PHẢI GAID). Danh sách đầy đủ + cách thu thập:
        // @mckimquyen/myKeyStore/com.samsunggalaxy.bmicalculator/test_device_ids.md
        AdManager.setTestDeviceIds(*AdKeys.QA_TEST_DEVICE_HASHES)
        // SDK có built-in 1-day grace auto-trial trong init() (installBeginMs+24h). KHÔNG được
        // gọi `activateVipByKey`/`grantVipDays` ở app-side để grant 1 ngày này — sẽ stomp giá
        // trị SDK tự ghi.
        AdManager.initialize(this) { success, gaid ->
            AppLog.d("AdManager init: success=$success, gaid=$gaid")
        }

        initializeDefaultProfile()
    }

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(LocaleHelper.onAttach(base))
    }

    /**
     * EPIC-04 T04.2 — apply the persisted theme BEFORE any Activity is created. Deliberately
     * blocking (not `applicationScope.launch`): this runs in Application.onCreate() before
     * SplashAct's window exists, so there's no frame to jank — but AppCompatDelegate.
     * setDefaultNightMode() recreates any already-resumed Activity, so applying it
     * asynchronously (as originally written) let SplashAct start with the wrong mode and
     * then get abruptly recreated once the DataStore read resolved (audit-found regression).
     */
    private fun applyPersistedTheme() {
        try {
            val mode = runBlocking { com.samsunggalaxy.utils.PreferencesManager(applicationContext).themeMode.first() }
            com.samsunggalaxy.utils.ThemeHelper.apply(mode)
        } catch (e: Exception) {
            Log.e("roy93~", "applyPersistedTheme error", e)
        }
    }

    private fun initializeDefaultProfile() {
        applicationScope.launch {
            try {
                val database = com.samsunggalaxy.data.AppDatabase.getDatabase(applicationContext)
                val repository = com.samsunggalaxy.data.BmiRepository(
                    database.bmiDao(),
                    database.profileDao(),
                    database.bodyMeasurementDao()
                )
                if (repository.getCurrentProfile() == null) {
                    repository.createDefaultProfile()
                }
            } catch (e: Exception) {
                Log.e("roy93~", "initializeDefaultProfile error", e)
            }
        }
    }
}
