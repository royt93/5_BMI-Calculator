package com.samsunggalaxy.ui

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.samsunggalaxy.BuildConfig
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ShareCompat
import androidx.core.content.ContextCompat
import androidx.core.content.ContextCompat.startActivity
import androidx.core.view.drawToBitmap
import androidx.core.view.isVisible
import androidx.core.view.setPadding
import androidx.databinding.DataBindingUtil
import androidx.lifecycle.lifecycleScope
import com.roy.sdkadbmob.AdManager
import com.samsunggalaxy.BaseActivity
import com.samsunggalaxy.R
import com.samsunggalaxy.databinding.AResultBinding
import com.samsunggalaxy.ext.displayToast
import com.samsunggalaxy.ext.saveBitmap
import com.samsunggalaxy.rateAppInApp
import com.samsunggalaxy.sdkadbmob.UIUtils
import com.samsunggalaxy.utils.CalculatorUtils
import com.samsunggalaxy.data.AppDatabase
import com.samsunggalaxy.data.BmiRecord
import com.samsunggalaxy.data.BmiRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.first
import com.samsunggalaxy.utils.PreferencesManager
import android.widget.EditText
import kotlin.jvm.java

class ResultAct : BaseActivity() {
    private lateinit var binding: AResultBinding
    private val _binding get() = binding
    private var weight: Double = 1.0
    private var height: Double = 1.0
    private var result: Double = 0.0
    private var gender: Int = 0
    private var age: Int = 25
    private lateinit var repository: BmiRepository

    private val handler = Handler(Looper.getMainLooper())

    // Banner view ref — restored để destroy khi user activate VIP mid-session.
    private var adView: View? = null
    private val backRunnable = Runnable {
        val resultIntent = Intent()
        resultIntent.putExtra(REQUEST_RESULT, true)
        setResult(RESULT_OK, resultIntent)
        finish()
        overridePendingTransition(0, 0)
    }

    private var tipAutoScrollRunnable: Runnable? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        UIUtils.setupEdgeToEdge1(window)
        binding = DataBindingUtil.setContentView(this, R.layout.a_result)
        UIUtils.setupEdgeToEdge2(
            rootView      = binding.root,
            paddingTop    = true,
            paddingBottom = true,
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

        // Load Interstitial ngầm — sẵn sàng khi user bấm Delete
        AdManager.loadInterstitial(this)

        setupViews()
    }

    override fun onResume() {
        super.onResume()
        rateAppInApp(com.samsunggalaxy.BuildConfig.DEBUG)
        // Sync banner với VIP state — handle activate/revoke mid-session.
        syncBannerWithVipState()
    }

    private fun syncBannerWithVipState() {
        try {
            val isVip = AdManager.isVipByKeyActive()
            if (isVip) {
                adView?.let { AdManager.bannerDestroy(it) }
                adView = null
                _binding.flAd.visibility = View.GONE
            } else {
                _binding.flAd.visibility = View.VISIBLE
                if (adView == null) {
                    adView = AdManager.loadBanner(
                        context = this,
                        container = _binding.flAd.findViewById(R.id.bannerContainer),
                        tvLabelAd = _binding.flAd.findViewById(R.id.tvLabelAd),
                    )
                }
            }
        } catch (e: Exception) {
            if (com.samsunggalaxy.BuildConfig.DEBUG) Log.w("roy93~", "syncBannerWithVipState failed", e)
        }
    }

    override fun onPause() {
        super.onPause()
    }

    private fun setupViews() {
        weight = intent.getDoubleExtra("Weight", 50.0)
        height = intent.getDoubleExtra("Height", 1.0)
        gender = intent.getIntExtra("Gender", 0)
        age = intent.getIntExtra("Age", 25)

        bmiCal()
        animationView()
        loadGoalCard()
        setupHealthTips()

        // Fetch real activity level before computing/saving TDEE — a hardcoded Sedentary(0)
        // used to be baked into both the display AND the persisted BmiRecord (EPIC-00 T00.2).
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val activityLevel = PreferencesManager(this@ResultAct).activityLevel.first()
                withContext(Dispatchers.Main) { calculateAndDisplayInsights(activityLevel) }
                saveToHistory(activityLevel)
            } catch (e: Exception) {
                // Matches the defensive try/catch saveToHistory() itself used to wrap the
                // whole fetch+compute+save flow in before activityLevel was pulled out —
                // a DataStore IOException here must not crash ResultAct.
                Log.e("roy93~", "insights/save flow error", e)
            }
        }

        _binding.cvReload.setOnClickListener {
            backPreviousPage(false)
        }
        _binding.ivDeleteBtn.setOnClickListener {
            // Show Interstitial Ad khi user xoá/back
            backPreviousPage(true)
        }
        _binding.ivShare.setOnClickListener {
            shareImage()
        }

        // Reward Ad — deferred until SDK adds showRewardedAd support
        // setupRewardButton()

        // Banner load: delegated tới `syncBannerWithVipState()` (gọi từ onResume).
    }

    // ---- Health Tips Feature ----
    private fun setupHealthTips() {
        try {
            val vpTips = _binding.root.findViewById<androidx.viewpager2.widget.ViewPager2>(R.id.vpHealthTips) ?: return
            val dotsContainer = _binding.root.findViewById<android.widget.LinearLayout>(R.id.dotsIndicator) ?: return

            val categoryInfo = CalculatorUtils.getBMICategoryInfo(result)
            val allTips = resources.getStringArray(categoryInfo.tipsArrayRes)
            if (allTips.isEmpty()) return

            val dayOfYear = java.util.Calendar.getInstance().get(java.util.Calendar.DAY_OF_YEAR)
            val startIdx = dayOfYear % allTips.size
            val selectedTips = (0 until 3).map { i -> allTips[(startIdx + i) % allTips.size] }

            val categoryLabel = "${getString(R.string.daily_tip_for)}: ${getString(categoryInfo.labelRes)}"
            val adapter = HealthTipAdapter(selectedTips, categoryLabel, categoryInfo.colorRes)
            vpTips.adapter = adapter

            dotsContainer.removeAllViews()
            val dots = Array(selectedTips.size) { i ->
                val dot = TextView(this).apply {
                    text = "●"
                    textSize = 10f
                    setPadding(8, 0, 8, 0)
                    setTextColor(ContextCompat.getColor(this@ResultAct,
                        if (i == 0) R.color.textColor else R.color.textColorAdditional))
                }
                dotsContainer.addView(dot)
                dot
            }

            vpTips.registerOnPageChangeCallback(object : androidx.viewpager2.widget.ViewPager2.OnPageChangeCallback() {
                override fun onPageSelected(position: Int) {
                    dots.forEachIndexed { idx, dot ->
                        dot.setTextColor(ContextCompat.getColor(this@ResultAct,
                            if (idx == position) R.color.textColor else R.color.textColorAdditional))
                    }
                }
            })

            tipAutoScrollRunnable = object : Runnable {
                override fun run() {
                    if (!isFinishing && !isDestroyed) {
                        val next = ((vpTips.currentItem + 1) % selectedTips.size)
                        vpTips.setCurrentItem(next, true)
                        handler.postDelayed(this, 5000)
                    }
                }
            }
            handler.postDelayed(tipAutoScrollRunnable!!, 5000)
        } catch (e: Exception) {
            Log.e("roy93~", "setupHealthTips error", e)
        }
    }

    // ---- Goal Weight Feature ----
    private fun loadGoalCard() {
        lifecycleScope.launch(Dispatchers.IO) {
            val profile = repository.getCurrentProfile()
            val goalWeight = profile?.goalWeight
            val profileId = profile?.id ?: 1L
            // Baseline for direction-aware progress — earliest tracked weight for this
            // profile, falling back to the current weight if this is the first record.
            val startWeight = repository.getFirstRecordWeight(profileId) ?: weight
            withContext(Dispatchers.Main) {
                setupGoalCard(goalWeight, profileId, startWeight)
            }
        }
    }

    private fun setupGoalCard(goalWeight: Double?, profileId: Long, startWeight: Double) {
        val goalCardView = _binding.root.findViewById<View>(R.id.goalCard) ?: return
        val tvCurrent = goalCardView.findViewById<TextView>(R.id.tvGoalCurrent)
        val tvTarget = goalCardView.findViewById<TextView>(R.id.tvGoalTarget)
        val progressBar = goalCardView.findViewById<ProgressBar>(R.id.progressGoal)
        val tvRemaining = goalCardView.findViewById<TextView>(R.id.tvGoalRemaining)
        val ivEdit = goalCardView.findViewById<View>(R.id.ivEditGoal)

        goalCardView.visibility = View.VISIBLE
        goalCardView.alpha = 0f
        goalCardView.animate().alpha(1f).setDuration(400).start()

        updateGoalUI(goalWeight, startWeight, tvCurrent, tvTarget, progressBar, tvRemaining)

        val editClickListener = View.OnClickListener {
            showGoalDialog(profileId, startWeight, tvCurrent, tvTarget, progressBar, tvRemaining)
        }
        ivEdit.setOnClickListener(editClickListener)
        if (goalWeight == null) goalCardView.setOnClickListener(editClickListener)
    }

    private fun updateGoalUI(
        goalWeight: Double?,
        startWeight: Double,
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
        // Direction-aware: handles both loss goals (goalWeight < startWeight) and gain
        // goals (goalWeight > startWeight) — see EPIC-00 T00.1 / CalculatorUtils.calculateGoalProgress.
        val progress = CalculatorUtils.calculateGoalProgress(startWeight, weight, goalWeight)
        progressBar.progress = progress.percent
        if (progress.achieved) {
            tvRemaining.text = getString(R.string.goal_weight_achieved)
            tvRemaining.setTextColor(ContextCompat.getColor(this, R.color.bmi_healthy))
        } else {
            tvRemaining.text = getString(R.string.goal_weight_remaining, progress.remainingKg)
            tvRemaining.setTextColor(ContextCompat.getColor(this, R.color.textColorAdditional))
        }
    }

    private fun showGoalDialog(
        profileId: Long,
        startWeight: Double,
        tvCurrent: TextView,
        tvTarget: TextView,
        progressBar: ProgressBar,
        tvRemaining: TextView
    ) {
        val dialogView = android.view.LayoutInflater.from(this)
            .inflate(R.layout.dialog_goal_weight, null)

        val currentBmi = weight / ((height / 100.0) * (height / 100.0))
        dialogView.findViewById<TextView>(R.id.tvDialogCurrentBmi).text =
            String.format("%.1f", currentBmi)
        dialogView.findViewById<TextView>(R.id.tvDialogCurrentWeight).text =
            getString(R.string.goal_weight_current, weight)

        val tvCategory = dialogView.findViewById<TextView>(R.id.tvDialogBmiCategory)
        val categoryInfo = CalculatorUtils.getBMICategoryInfo(currentBmi)
        tvCategory.text = getString(categoryInfo.labelRes)
        tvCategory.setTextColor(ContextCompat.getColor(this, categoryInfo.colorRes))

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
                    val previewInfo = CalculatorUtils.getBMICategoryInfo(goalBmi)
                    tvPreview.setTextColor(ContextCompat.getColor(this@ResultAct, previewInfo.colorRes))
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
                        updateGoalUI(input, startWeight, tvCurrent, tvTarget, progressBar, tvRemaining)
                    }
                }
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    private fun shareImage() {
        try {
            val adContainer = _binding.flAd
            val originalVisibility = adContainer?.visibility
            adContainer?.visibility = View.GONE

            val imageURI = _binding.layoutRoot.drawToBitmap().let { bitmap ->
                saveBitmap(this, bitmap)
            } ?: run {
                displayToast("Error occurred!")
                adContainer?.visibility = originalVisibility ?: View.VISIBLE
                return
            }

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
                // AdSafety tự throttle — nếu không đủ điều kiện, callback vẫn được gọi với adShown=false
                AdManager.showInterstitial(this) { adShown ->
                    if (BuildConfig.DEBUG) Log.d("roy93~", "showInterstitial adShown=$adShown")
                    handler.postDelayed(backRunnable, 100)
                }
            }, 600)
        } else {
            handler.postDelayed(backRunnable, 600)
        }
    }

    private fun animationView() {
        _binding.apply {
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

            ivDeleteBtn.parent.let { (it as? View)?.animate()?.alpha(0f)?.setDuration(200)?.start() }
            cvReload.animate().alpha(0f).setDuration(200).start()
            ivShare.parent.let { (it as? View)?.animate()?.alpha(0f)?.setDuration(200)?.start() }
        }
    }

    private fun bmiCal() {
        if (height > 0 && weight > 0) {
            val heightInMeters = height / 100.0
            result = weight / (heightInMeters * heightInMeters)
            showResult()
        }
    }

    @SuppressLint("DefaultLocale")
    private fun showResult() {
        val solution = String.format("%.1f", result)
        _binding.tvResult.text = solution
        // Unified with setupHealthTips()/showGoalDialog() via CalculatorUtils.getBMICategoryInfo —
        // previously this used its own 18.5/24.9/30 thresholds (vs 25.0 elsewhere) AND hardcoded
        // English literals instead of getString(), so the same BMI could show "Overweight" here
        // while every other card on the same screen said "Healthy". See EPIC-00 T00.3.
        _binding.tvBmi.text = getString(CalculatorUtils.getBMICategoryInfo(result).labelRes)
    }

    private fun calculateAndDisplayInsights(activityLevel: Int) {
        val bmr = CalculatorUtils.calculateBMR(weight, height, age, gender)
        val tdee = CalculatorUtils.calculateTDEE(bmr, activityLevel)
        val idealWeight = CalculatorUtils.calculateIdealWeightRange(height, gender)
        val water = CalculatorUtils.calculateWaterIntake(weight)

        _binding.root.findViewById<TextView>(R.id.tvBmrValue)?.text =
            "${String.format("%.0f", bmr)} ${getString(R.string.cal_per_day)}"
        _binding.root.findViewById<TextView>(R.id.tvTdeeValue)?.text =
            "${String.format("%.0f", tdee)} ${getString(R.string.cal_per_day)}"
        _binding.root.findViewById<TextView>(R.id.tvIdealWeightValue)?.text =
            "${String.format("%.0f", idealWeight.first)}-${String.format("%.0f", idealWeight.second)} kg"
        _binding.root.findViewById<TextView>(R.id.tvWaterValue)?.text =
            "${String.format("%.1f", water)} ${getString(R.string.l_per_day)}"
    }

    /** Must be called from an IO-dispatcher coroutine — see the launch in setupViews(). */
    private suspend fun saveToHistory(activityLevel: Int) {
        try {
            val bmr = CalculatorUtils.calculateBMR(weight, height, age, gender)
            val tdee = CalculatorUtils.calculateTDEE(bmr, activityLevel)
            val idealWeight = CalculatorUtils.calculateIdealWeightRange(height, gender)

            val currentProfile = repository.getCurrentProfile()
            val profileId = currentProfile?.id ?: 1L
            if (BuildConfig.DEBUG) Log.d("roy93~", "saveToHistory: profileId=$profileId, weight=$weight, height=$height, bmi=$result, activityLevel=$activityLevel")

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

            try {
                val newlyEarned = RecordSaveHelper.saveAndCheckBadges(
                    context = this@ResultAct,
                    repository = repository,
                    record = record,
                    goalWeight = currentProfile?.goalWeight
                )

                if (newlyEarned.isNotEmpty()) {
                    val badge = newlyEarned.first()
                    withContext(Dispatchers.Main) {
                        if (!isFinishing && !isDestroyed) {
                            com.google.android.material.snackbar.Snackbar
                                .make(_binding.root, "🎉 ${getString(badge.titleRes)}!", com.google.android.material.snackbar.Snackbar.LENGTH_LONG)
                                .setBackgroundTint(ContextCompat.getColor(this@ResultAct, R.color.bmi_healthy))
                                .show()
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("roy93~", "saveAndCheckBadges error", e)
            }
        } catch (e: Exception) {
            Log.e("roy93~", "saveToHistory error", e)
        }
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        // Defensive: SDK autoManageLifecycle xử lý destroy banner, nhưng manual destroy
        // idempotent (per SDK contract) đảm bảo không leak refs giữa app + lib.
        adView?.let { AdManager.bannerDestroy(it) }
        adView = null
        super.onDestroy()
    }
}
