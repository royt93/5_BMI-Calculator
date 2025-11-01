package com.samsunggalaxy.ui

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ShareCompat
import androidx.core.content.ContextCompat
import androidx.core.content.ContextCompat.startActivity
import androidx.core.view.drawToBitmap
import androidx.core.view.setPadding
import androidx.databinding.DataBindingUtil
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.LoadAdError
import com.samsunggalaxy.BaseActivity
import com.samsunggalaxy.BuildConfig
import com.samsunggalaxy.R
import com.samsunggalaxy.databinding.AResultBinding
import com.samsunggalaxy.ext.displayToast
import com.samsunggalaxy.ext.saveBitmap
import com.samsunggalaxy.rateAppInApp
import com.samsunggalaxy.sdkadbmob.AdMobManager
import com.samsunggalaxy.sdkadbmob.UIUtils
import com.samsunggalaxy.utils.CalculatorUtils
import com.samsunggalaxy.data.AppDatabase
import com.samsunggalaxy.data.BmiRecord
import com.samsunggalaxy.data.BmiRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlin.jvm.java

class ResultAct : BaseActivity(), AdMobManager.InterstitialAdListener {
    private lateinit var binding: AResultBinding
    private val _binding get() = binding
    private var weight: Double = 1.0
    private var height: Double = 1.0
    private var result: Double = 0.0
    private var gender: Int = 0
    private var age: Int = 25
    private lateinit var repository: BmiRepository

    //    private var adView: MaxAdView? = null
    private var adView: AdView? = null
    private val handler = Handler(Looper.getMainLooper())
    private val backRunnable = Runnable {
        val resultIntent = Intent()
        resultIntent.putExtra(REQUEST_RESULT, true)
        setResult(RESULT_OK, resultIntent)
        finish()
        overridePendingTransition(0, 0)
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        UIUtils.setupEdgeToEdge1(window)
        binding = DataBindingUtil.setContentView(this, R.layout.a_result)
        UIUtils.setupEdgeToEdge2(
            rootView = findViewById(R.id.layoutRoot),
            paddingTop = true,
            paddingBottom = true
        )

        // Setup OnBackPressedDispatcher
        onBackPressedDispatcher.addCallback(this, object : androidx.activity.OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                backPreviousPage(false)
            }
        })

        // Initialize repository
        val database = AppDatabase.getDatabase(this)
        repository = BmiRepository(database.bmiDao(), database.profileDao())

        AdMobManager.setCurrentActivity(this)
        AdMobManager.interstitialListener = this
        AdMobManager.loadInterstitial(this, BuildConfig.ADMOB_INTERSTITIAL_ID)

//        createAdInter()
        setupViews()
    }

    override fun onResume() {
        super.onResume()
        rateAppInApp(BuildConfig.DEBUG)
        adView?.resume()
    }

    override fun onPause() {
        adView?.pause()
        super.onPause()
    }

    private fun setupViews() {
        weight = intent.getDoubleExtra("Weight", 50.0)
        height = intent.getDoubleExtra("Height", 1.0)
        gender = intent.getIntExtra("Gender", 0)
        age = intent.getIntExtra("Age", 25)

        bmiCal()
        calculateAndDisplayInsights()
        saveToHistory()
        animationView()

        _binding.cvReload.setOnClickListener {
            backPreviousPage(false)
        }
        _binding.ivDeleteBtn.setOnClickListener {
            backPreviousPage(true)
        }
        _binding.ivShare.setOnClickListener {
            shareImage()
        }

//        adView = this.createAdBanner(
//            logTag = ResultAct::class.simpleName,
//            viewGroup = binding.flAd,
//            isAdaptiveBanner = true,
//        )
        adView = binding.flAd?.let {
            val bannerContainer = it.findViewById<ViewGroup>(R.id.bannerContainer)
            val tvLabelAd = it.findViewById<TextView>(R.id.tvLabelAd)
            AdMobManager.loadBanner(
                context = this,
                adUnitId = BuildConfig.ADMOB_BANNER_ID,
                container = bannerContainer,
                tvLabelAd = tvLabelAd,
                adSize = AdSize.BANNER,
            )
        }
    }

    private fun shareImage() {
        try {
            // Hide ad banner before capturing
            val adContainer = _binding.flAd
            val originalVisibility = adContainer?.visibility
            adContainer?.visibility = View.GONE

            // Capture the root layout with gradient background
            val imageURI = _binding.layoutRoot.drawToBitmap().let { bitmap ->
                saveBitmap(this, bitmap)
            } ?: run {
                displayToast("Error occurred!")
                adContainer?.visibility = originalVisibility ?: View.VISIBLE
                return
            }

            // Restore ad visibility
            adContainer?.visibility = originalVisibility ?: View.VISIBLE

            val intent = ShareCompat.IntentBuilder(this)
                .setType("image/jpeg")
                .setStream(imageURI)
                .intent
                .apply {
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }

            startActivity(Intent.createChooser(intent, null))
        } catch (e: Exception) {
            displayToast("Share failed: ${e.message}")
        }
    }

    private fun backPreviousPage(isShowAd: Boolean) {
        animationViewUp()
        if (isShowAd) {
            handler.postDelayed({
                AdMobManager.showInterstitial(this) { success ->
                    if (success) {
                        Log.d("roy93~", "Ad đã hiển thị và đóng thành công")
                    } else {
                        Log.d("roy93~", "Ad không hiển thị được hoặc có lỗi")
                    }
                }
                handler.postDelayed(backRunnable, 100)
            }, 600)
        } else {
            handler.postDelayed(backRunnable, 600)
        }
    }

    private fun animationView() {
        _binding.apply {
            // Glass card scale animation
            val cardResult = root.findViewById<View>(R.id.cardResult)
            cardResult.scaleX = 0.8f
            cardResult.scaleY = 0.8f
            cardResult.alpha = 0f

            cardResult.animate()
                .scaleX(1f)
                .scaleY(1f)
                .alpha(1f)
                .setDuration(700)
                .setStartDelay(200)
                .setInterpolator(android.view.animation.OvershootInterpolator(0.8f))
                .start()

            // Action buttons animation
            ivDeleteBtn.parent.let { deleteCard ->
                (deleteCard as? View)?.apply {
                    scaleX = 0f
                    scaleY = 0f
                    alpha = 0f
                    animate()
                        .scaleX(1f)
                        .scaleY(1f)
                        .alpha(1f)
                        .setDuration(400)
                        .setStartDelay(800)
                        .setInterpolator(android.view.animation.OvershootInterpolator())
                        .start()
                }
            }

            cvReload.scaleX = 0f
            cvReload.scaleY = 0f
            cvReload.alpha = 0f
            cvReload.animate()
                .scaleX(1f)
                .scaleY(1f)
                .alpha(1f)
                .setDuration(400)
                .setStartDelay(900)
                .setInterpolator(android.view.animation.OvershootInterpolator())
                .start()

            ivShare.parent.let { shareCard ->
                (shareCard as? View)?.apply {
                    scaleX = 0f
                    scaleY = 0f
                    alpha = 0f
                    animate()
                        .scaleX(1f)
                        .scaleY(1f)
                        .alpha(1f)
                        .setDuration(400)
                        .setStartDelay(1000)
                        .setInterpolator(android.view.animation.OvershootInterpolator())
                        .start()
                }
            }
        }
    }

    private fun animationViewUp() {
        _binding.apply {
            textView.animate()
                .alpha(0f)
                .setDuration(200)
                .start()

            val cardResult = root.findViewById<View>(R.id.cardResult)
            cardResult.animate()
                .scaleX(0.85f)
                .scaleY(0.85f)
                .translationY(-100f)
                .alpha(0f)
                .setDuration(400)
                .setInterpolator(android.view.animation.AccelerateInterpolator())
                .start()

            // Buttons fade out quickly
            ivDeleteBtn.parent.let { (it as? View)?.animate()?.alpha(0f)?.setDuration(200)?.start() }
            cvReload.animate().alpha(0f).setDuration(200).start()
            ivShare.parent.let { (it as? View)?.animate()?.alpha(0f)?.setDuration(200)?.start() }
        }
    }

    private fun bmiCal() {
        if (height > 0 && weight > 0) {
            if (gender == 0) {
                bmiCalMale()
            } else if (gender == 1) {
                bmiCalFemale()
            }
            showResult()
        }

    }

    @SuppressLint("SetTextI18n", "DefaultLocale")
    private fun showResult() {
        val solution = String.format("%.1f", result)
        _binding.tvResult.text = solution
        _binding.tvBmi.apply {
            if (result < 18.5) {
                this.text = "You are Under Weight"
            } else if (result >= 18.5 && result < 24.9) {
                this.text = "You are Healthy"
            } else if (result >= 24.9 && result < 30) {
                this.text = "You are Overweight"
            } else if (result >= 30) {
                this.text = "You are Suffering from Obesity"
            }
        }

    }

    private fun bmiCalMale() {
        result = ((weight / (height * height)) * 10000)
    }

    private fun bmiCalFemale() {
        result = ((weight / (height * height)) * 10000)
    }

    private fun calculateAndDisplayInsights() {
        val isMale = gender == 0

        // Calculate BMR
        val bmr = CalculatorUtils.calculateBMR(weight, height, age, isMale)

        // Calculate TDEE (assuming sedentary activity level = 0)
        val tdee = CalculatorUtils.calculateTDEE(bmr, 0)

        // Calculate ideal weight range
        val idealWeight = CalculatorUtils.calculateIdealWeightRange(height, isMale)

        // Calculate water intake
        val water = CalculatorUtils.calculateWaterIntake(weight)

        // Update UI
        _binding.root.findViewById<TextView>(R.id.tvBmrValue)?.text =
            "${String.format("%.0f", bmr)} cal/day"
        _binding.root.findViewById<TextView>(R.id.tvTdeeValue)?.text =
            "${String.format("%.0f", tdee)} cal/day"
        _binding.root.findViewById<TextView>(R.id.tvIdealWeightValue)?.text =
            "${String.format("%.0f", idealWeight.first)}-${String.format("%.0f", idealWeight.second)} kg"
        _binding.root.findViewById<TextView>(R.id.tvWaterValue)?.text =
            "${String.format("%.1f", water)} L/day"
    }

    private fun saveToHistory() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val isMale = gender == 0
                val bmr = CalculatorUtils.calculateBMR(weight, height, age, isMale)
                val tdee = CalculatorUtils.calculateTDEE(bmr, 0)
                val idealWeight = CalculatorUtils.calculateIdealWeightRange(height, isMale)

                // Get current profile ID or use 1 as default (created in GalaxyApp)
                val currentProfile = repository.getCurrentProfile()
                val profileId = currentProfile?.id ?: 1L

                val record = BmiRecord(
                    timestamp = System.currentTimeMillis(),
                    height = height,
                    weight = weight,
                    gender = gender,
                    age = age,
                    bmi = result,
                    bmr = bmr,
                    tdee = tdee,
                    idealWeightMin = idealWeight.first,
                    idealWeightMax = idealWeight.second,
                    bodyFatPercentage = null,
                    profileId = profileId
                )

                repository.insertRecord(record)

//                runOnUiThread {
//                    displayToast("Saved to history!")
//                }
            } catch (e: Exception) {
//                runOnUiThread {
//                    displayToast("Failed to save: ${e.message}")
//                }
            }
        }
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        AdMobManager.interstitialListener = null
        AdMobManager.clearCurrentActivity()
//        binding.flAd?.destroyAdBanner(adView)
        adView?.destroy()
        super.onDestroy()
    }

    override fun onAdLoaded() {
    }

    override fun onAdFailedToLoad(error: LoadAdError) {
    }

    override fun onAdShowed() {
    }

    override fun onAdDismissed() {
    }

    override fun onAdClicked() {
    }

    override fun onAdFailedToShow(error: AdError) {
    }

    override fun onAdNotAvailable() {
    }

//    private var interstitialAd: MaxInterstitialAd? = null
//
//    private fun createAdInter() {
//        val enableAdInter = getString(R.string.EnableAdInter) == "true"
//        if (enableAdInter) {
//            interstitialAd = MaxInterstitialAd(getString(R.string.INTER), this)
//            interstitialAd?.let { ad ->
//                ad.setListener(object : MaxAdListener {
//                    override fun onAdLoaded(p0: MaxAd) {
////                        logI("onAdLoaded")
////                        retryAttempt = 0
//                    }
//
//                    override fun onAdDisplayed(p0: MaxAd) {
////                        logI("onAdDisplayed")
//                    }
//
//                    override fun onAdHidden(p0: MaxAd) {
////                        logI("onAdHidden")
//                        // Interstitial Ad is hidden. Pre-load the next ad
//                        interstitialAd?.loadAd()
//                    }
//
//                    override fun onAdClicked(p0: MaxAd) {
////                        logI("onAdClicked")
//                    }
//
//                    override fun onAdLoadFailed(p0: String, p1: MaxError) {
////                        logI("onAdLoadFailed")
////                        retryAttempt++
////                        val delayMillis =
////                            TimeUnit.SECONDS.toMillis(2.0.pow(min(6, retryAttempt)).toLong())
////
////                        Handler(Looper.getMainLooper()).postDelayed(
////                            {
////                                interstitialAd?.loadAd()
////                            }, delayMillis
////                        )
//                    }
//
//                    override fun onAdDisplayFailed(p0: MaxAd, p1: MaxError) {
////                        logI("onAdDisplayFailed")
//                        // Interstitial ad failed to display. We recommend loading the next ad.
//                        interstitialAd?.loadAd()
//                    }
//
//                })
//                ad.setRevenueListener {
////                    logI("onAdDisplayed")
//                }
//
//                // Load the first ad.
//                ad.loadAd()
//            }
//        }
//    }
//
//    private fun showAd(runnable: Runnable? = null) {
//        val enableAdInter = getString(R.string.EnableAdInter) == "true"
//        if (enableAdInter) {
//            if (interstitialAd == null) {
//                runnable?.run()
//            } else {
//                interstitialAd?.let { ad ->
//                    if (ad.isReady) {
////                        showDialogProgress()
////                        setDelay(500.getRandomNumber() + 500) {
////                            hideDialogProgress()
////                            ad.showAd()
////                            runnable?.run()
////                        }
//                        ad.showAd()
//                        runnable?.run()
//                    } else {
//                        runnable?.run()
//                    }
//                }
//            }
//        } else {
//            Toast.makeText(this, "Applovin show ad Inter in debug mode", Toast.LENGTH_SHORT).show()
//            runnable?.run()
//        }
//    }
}
