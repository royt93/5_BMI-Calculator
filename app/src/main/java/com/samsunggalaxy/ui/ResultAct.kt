package com.samsunggalaxy.ui

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
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

class ResultAct : BaseActivity(), AdMobManager.InterstitialAdListener {
    private lateinit var binding: AResultBinding
    private val _binding get() = binding
    private var weight: Double = 1.0
    private var height: Double = 1.0
    private var result: Double = 0.0
    private var gender: Int = 0

    //    private var adView: MaxAdView? = null
    private var adView: AdView? = null

    // handle permission dialog
    private val requestLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (isGranted) shareImage() else showErrorDialog()
        }

    private fun showErrorDialog() {
        displayToast("Please allow External Storage Read and Write Permissions.")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = DataBindingUtil.setContentView(this, R.layout.a_result)

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

        bmiCal()
        animationView()

        _binding.cvReload.setOnClickListener {
            backPreviousPage(true)
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
            AdMobManager.loadBanner(
                context = this,
                adUnitId = BuildConfig.ADMOB_BANNER_ID,
                container = it,
                adSize = AdSize.BANNER,
            )
        }
    }

    private fun shareImage() {
        if (!isStoragePermissionGranted()) {
            requestLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            return
        }

        // unHide the app logo and name
        val imageURI = _binding.llDetailView.drawToBitmap().let { bitmap ->
            saveBitmap(this, bitmap)
        } ?: run {
            displayToast("Error occurred!")
            return
        }

        val intent = ShareCompat.IntentBuilder(this)
            .setType("image/jpeg")
            .setStream(imageURI)
            .intent

        startActivity(Intent.createChooser(intent, null))
    }

    private fun isStoragePermissionGranted(): Boolean = ContextCompat.checkSelfPermission(
        this,
        Manifest.permission.WRITE_EXTERNAL_STORAGE
    ) == PackageManager.PERMISSION_GRANTED

    private fun backPreviousPage(isShowAd: Boolean) {
        animationViewUp()
        Handler(Looper.getMainLooper()).postDelayed({
            val resultIntent = Intent()
            resultIntent.putExtra(REQUEST_RESULT, true)
            setResult(RESULT_OK, resultIntent)
            finish()
            overridePendingTransition(0, 0)
            if (isShowAd) {
                AdMobManager.showInterstitial(this)
            }
//            showAd {
//                //            startActivity(Intent(this, MainAct::class.java))
////            overridePendingTransition(0, 0)
//                val resultIntent = Intent()
//                resultIntent.putExtra(REQUEST_RESULT, true)
//                setResult(RESULT_OK, resultIntent)
//                finish()
//                overridePendingTransition(0, 0)
//            }
        }, 600)
    }

    private fun animationView() {

        _binding.apply {

            ivDesk.translationY = 100f
            tvResult.translationY = 40f
            tvBmi.translationY = 50f
            tvBmiTextNormal.translationY = 50f
            ivDeleteBtn.translationY = 70f
            cvReload.translationY = 70f
            ivShare.translationY = 70f

            ivDesk.alpha = 0f
            tvResult.alpha = 0f
            tvBmi.alpha = 0f
            tvBmiTextNormal.alpha = 0f
            ivDeleteBtn.alpha = 0f
            cvReload.alpha = 0f
            ivShare.alpha = 0f

            ivDesk.setPadding(100)
            ivDesk.animate().translationY(0f).alpha(1f).setDuration(500).setStartDelay(300)
                .start()
            ivDesk.setPadding(0)
            tvResult.animate().translationY(0f).alpha(1f).setDuration(500).setStartDelay(500)
                .start()
            tvBmi.animate().translationY(0f).alpha(1f).setDuration(500).setStartDelay(450).start()
            tvBmiTextNormal.animate().translationY(0f).alpha(.3f).setDuration(500)
                .setStartDelay(500)
                .start()
            ivDeleteBtn.animate().translationY(0f).alpha(.3f).setDuration(500).setStartDelay(600)
                .start()
            cvReload.animate().translationY(0f).alpha(1f).setDuration(500).setStartDelay(750)
                .start()
            ivShare.animate().translationY(0f).alpha(.3f).setDuration(500).setStartDelay(900)
                .start()
        }
    }

    private fun animationViewUp() {

        _binding.apply {

            textView.animate().translationY(0f).alpha(0f).setDuration(500).setStartDelay(0)
                .start()
            ivDesk.animate().translationY(-250f).alpha(0f).setDuration(500).setStartDelay(0)
                .start()

            tvResult.animate().translationY(-250f).alpha(0f).setDuration(500).setStartDelay(50)
                .start()
            tvBmi.animate().translationY(-250f).alpha(0f).setDuration(500).setStartDelay(100)
                .start()
            tvBmiTextNormal.animate().translationY(-250f).alpha(0f).setDuration(500)
                .setStartDelay(150)
                .start()
            ivDeleteBtn.animate().translationY(-250f).alpha(0f).setDuration(300).setStartDelay(200)
                .start()
            cvReload.animate().translationY(-250f).alpha(0f).setDuration(300)
                .setStartDelay(250).start()
            ivShare.animate().translationY(-250f).alpha(0f).setDuration(300).setStartDelay(300)
                .start()
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

    @SuppressLint("MissingSuperCall")
    override fun onBackPressed() {
//        super.onBackPressed()
        backPreviousPage(false)
    }

    override fun onDestroy() {
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
