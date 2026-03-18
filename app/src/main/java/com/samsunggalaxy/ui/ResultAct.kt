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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.lifecycle.lifecycleScope
import android.widget.EditText
import android.widget.ProgressBar
import androidx.core.view.isVisible
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
        loadGoalCard()

        _binding.cvReload.setOnClickListener {
            backPreviousPage(false)
        }
        _binding.ivDeleteBtn.setOnClickListener {
            // BUG-01: use onBackPressedDispatcher instead of deprecated back flow
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

    // ---- Goal Weight Feature ----
    // Uses binding directly to goalCard in XML — no dynamic addView
    private fun loadGoalCard() {
        lifecycleScope.launch(Dispatchers.IO) {
            val profile = repository.getCurrentProfile()
            val goalWeight = profile?.goalWeight
            val profileId = profile?.id ?: 1L
            withContext(Dispatchers.Main) {
                setupGoalCard(goalWeight, profileId)
            }
        }
    }

    private fun setupGoalCard(goalWeight: Double?, profileId: Long) {
        val goalCardView = _binding.root.findViewById<View>(R.id.goalCard) ?: return
        val tvCurrent = goalCardView.findViewById<TextView>(R.id.tvGoalCurrent)
        val tvTarget = goalCardView.findViewById<TextView>(R.id.tvGoalTarget)
        val progressBar = goalCardView.findViewById<ProgressBar>(R.id.progressGoal)
        val tvRemaining = goalCardView.findViewById<TextView>(R.id.tvGoalRemaining)
        val ivEdit = goalCardView.findViewById<View>(R.id.ivEditGoal)

        // Show the card with fade-in
        goalCardView.visibility = View.VISIBLE
        goalCardView.alpha = 0f
        goalCardView.animate().alpha(1f).setDuration(400).start()

        updateGoalUI(goalWeight, tvCurrent, tvTarget, progressBar, tvRemaining)

        val editClickListener = View.OnClickListener {
            showGoalDialog(profileId, tvCurrent, tvTarget, progressBar, tvRemaining)
        }
        ivEdit.setOnClickListener(editClickListener)
        if (goalWeight == null) goalCardView.setOnClickListener(editClickListener)
    }

    private fun updateGoalUI(
        goalWeight: Double?,
        tvCurrent: TextView,
        tvTarget: TextView,
        progressBar: ProgressBar,
        tvRemaining: TextView
    ) {
        tvCurrent.text = getString(R.string.goal_weight_current, weight)
        if (goalWeight == null || goalWeight <= 0) {
            tvTarget.text = getString(R.string.goal_weight_no_goal)
            progressBar.isVisible = false
            tvRemaining.isVisible = false
            return
        }
        tvTarget.text = getString(R.string.goal_weight_target, goalWeight)
        progressBar.isVisible = true
        tvRemaining.isVisible = true
        val diff = weight - goalWeight
        if (diff <= 0) {
            progressBar.progress = 100
            tvRemaining.text = getString(R.string.goal_weight_achieved)
            tvRemaining.setTextColor(ContextCompat.getColor(this, R.color.bmi_healthy))
        } else {
            val progress = ((goalWeight / weight) * 100).toInt().coerceIn(0, 99)
            progressBar.progress = progress
            tvRemaining.text = getString(R.string.goal_weight_remaining, diff)
            tvRemaining.setTextColor(ContextCompat.getColor(this, R.color.textColorAdditional))
        }
    }

    private fun showGoalDialog(
        profileId: Long,
        tvCurrent: TextView,
        tvTarget: TextView,
        progressBar: ProgressBar,
        tvRemaining: TextView
    ) {
        val dialogView = android.view.LayoutInflater.from(this)
            .inflate(R.layout.dialog_goal_weight, null)

        // Current BMI info
        val currentBmi = weight / ((height / 100.0) * (height / 100.0))
        dialogView.findViewById<TextView>(R.id.tvDialogCurrentBmi).text =
            String.format("%.1f", currentBmi)
        dialogView.findViewById<TextView>(R.id.tvDialogCurrentWeight).text =
            getString(R.string.goal_weight_current, weight)

        // Category color
        val tvCategory = dialogView.findViewById<TextView>(R.id.tvDialogBmiCategory)
        val (catColorRes, catStringRes) = when {
            currentBmi < 18.5 -> Pair(R.color.bmi_underweight, R.string.bmi_category_underweight)
            currentBmi < 25.0 -> Pair(R.color.bmi_healthy, R.string.bmi_category_healthy)
            currentBmi < 30.0 -> Pair(R.color.bmi_overweight, R.string.bmi_category_overweight)
            else -> Pair(R.color.bmi_obese, R.string.bmi_category_obese)
        }
        tvCategory.text = getString(catStringRes)
        tvCategory.setTextColor(ContextCompat.getColor(this, catColorRes))

        // Goal BMI preview (live update as user types)
        val etGoal = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.etGoalWeight)
        val tvPreview = dialogView.findViewById<TextView>(R.id.tvDialogGoalBmiPreview)
        val heightM = height / 100.0

        etGoal.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) {
                val goalW = s.toString().toDoubleOrNull()
                if (goalW != null && goalW > 0 && heightM > 0) {
                    val goalBmi = goalW / (heightM * heightM)
                    tvPreview.visibility = View.VISIBLE
                    tvPreview.text = "${getString(R.string.goal_bmi_target_label)}: ${String.format("%.1f", goalBmi)}"
                    val previewColor = when {
                        goalBmi < 18.5 -> R.color.bmi_underweight
                        goalBmi < 25.0 -> R.color.bmi_healthy
                        goalBmi < 30.0 -> R.color.bmi_overweight
                        else -> R.color.bmi_obese
                    }
                    tvPreview.setTextColor(ContextCompat.getColor(this@ResultAct, previewColor))
                } else {
                    tvPreview.visibility = View.GONE
                }
            }
        })

        com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.goal_weight_label))
            .setView(dialogView)
            .setPositiveButton(getString(R.string.goal_weight_save)) { _, _ ->
                val input = etGoal.text.toString().toDoubleOrNull()
                lifecycleScope.launch(Dispatchers.IO) {
                    repository.updateGoalWeight(profileId, input)
                    withContext(Dispatchers.Main) {
                        updateGoalUI(input, tvCurrent, tvTarget, progressBar, tvRemaining)
                    }
                }
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
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
            // BUG-04: Convert height from cm to meters for standard BMI formula
            val heightInMeters = height / 100.0
            result = weight / (heightInMeters * heightInMeters)
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

    // BUG-03: Removed duplicate bmiCalMale/bmiCalFemale — BMI formula is gender-neutral.
    // Gender is used for BMR, TDEE, ideal weight in calculateAndDisplayInsights().

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
            "${String.format("%.0f", bmr)} ${getString(R.string.cal_per_day)}"
        _binding.root.findViewById<TextView>(R.id.tvTdeeValue)?.text =
            "${String.format("%.0f", tdee)} ${getString(R.string.cal_per_day)}"
        _binding.root.findViewById<TextView>(R.id.tvIdealWeightValue)?.text =
            "${String.format("%.0f", idealWeight.first)}-${String.format("%.0f", idealWeight.second)} kg"
        _binding.root.findViewById<TextView>(R.id.tvWaterValue)?.text =
            "${String.format("%.1f", water)} ${getString(R.string.l_per_day)}"
    }

    private fun saveToHistory() {
        // ML-03: Use lifecycleScope instead of raw CoroutineScope to tie to Activity lifecycle
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val isMale = gender == 0
                val bmr = CalculatorUtils.calculateBMR(weight, height, age, isMale)
                val tdee = CalculatorUtils.calculateTDEE(bmr, 0)
                val idealWeight = CalculatorUtils.calculateIdealWeightRange(height, isMale)

                // Get current profile ID or use 1 as default (created in GalaxyApp)
                val currentProfile = repository.getCurrentProfile()
                val profileId = currentProfile?.id ?: 1L
                Log.d("roy93~", "saveToHistory: profileId=$profileId, weight=$weight, height=$height, bmi=$result")

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

                val insertedId = repository.insertRecord(record)
                Log.d("roy93~", "saveToHistory: inserted record id=$insertedId")
            } catch (e: Exception) {
                Log.e("roy93~", "saveToHistory error", e)
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
