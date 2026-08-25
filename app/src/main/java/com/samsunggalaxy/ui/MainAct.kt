package com.samsunggalaxy.ui

import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.app.Dialog
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.samsunggalaxy.BuildConfig
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.animation.OvershootInterpolator
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.updatePadding
import androidx.databinding.DataBindingUtil
import androidx.fragment.app.setFragmentResultListener
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearSnapHelper
import androidx.recyclerview.widget.SnapHelper
import com.cncoderx.wheelview.OnWheelChangedListener
import com.roy.sdkadbmob.AdManager
import com.samsunggalaxy.BaseActivity
import com.samsunggalaxy.R
import com.samsunggalaxy.adt.WeightPickerAdt
import com.samsunggalaxy.data.AppDatabase
import com.samsunggalaxy.data.BmiRepository
import com.samsunggalaxy.databinding.AMainBinding
import com.samsunggalaxy.ext.moreApp
import com.samsunggalaxy.ext.openBrowserPolicy
import com.samsunggalaxy.ext.rateApp
import com.samsunggalaxy.ext.shareApp
import com.samsunggalaxy.feature.vip.VipActivity
import com.samsunggalaxy.sdkadbmob.UIUtils
import com.samsunggalaxy.utils.PreferencesManager
import com.samsunggalaxy.utils.UnitFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import travel.ithaka.android.horizontalpickerlib.PickerLayoutManager

const val REQUEST_CODE = 69
const val REQUEST_RESULT = "REQUEST_RESULT"
private const val KEY_ONBOARDING_PROFILE_ASKED = "onboarding_profile_asked"

private const val MAX_WEIGHT_KG = 151.0
private const val MAX_HEIGHT_CM = 229.0

/**
 * Pure — no Android dependency — so directly unit-testable (EPIC-04 T04.1).
 * The imperial upper bound is DERIVED from the metric one (via UnitFormatter), not a
 * separately hand-picked number — a hardcoded "2..330" undershot 151kg's true lbs
 * equivalent (~333), so switching to imperial near max weight left no matching wheel
 * label and silently snapped the display back to the minimum (audit-found regression).
 */
fun weightWheelLabels(unitSystem: String): List<String> {
    val range = if (unitSystem == UnitFormatter.IMPERIAL) {
        val maxLbs = Math.ceil(UnitFormatter.weightToDisplay(MAX_WEIGHT_KG, UnitFormatter.IMPERIAL)).toInt()
        2..maxLbs
    } else {
        1..MAX_WEIGHT_KG.toInt()
    }
    return range.map { it.toString() }
}

/** Pure — no Android dependency — so directly unit-testable (EPIC-04 T04.1). Same
 * derive-from-metric approach as [weightWheelLabels] for the same reason. */
fun heightWheelLabels(unitSystem: String): List<String> {
    val range = if (unitSystem == UnitFormatter.IMPERIAL) {
        val maxIn = Math.ceil(UnitFormatter.heightToDisplay(MAX_HEIGHT_CM, UnitFormatter.IMPERIAL)).toInt()
        1..maxIn
    } else {
        1..MAX_HEIGHT_CM.toInt()
    }
    return range.map { it.toString() }
}

class MainAct : BaseActivity() {
    private lateinit var binding: AMainBinding
    private val _binding get() = binding
    private lateinit var weightAdapter: WeightPickerAdt
    private lateinit var repository: BmiRepository
    private var gender = 'M'
    var height = 160 // Default to 160cm
    private var weight = 50
    private var age = 25
    private var doubleBackToExitPressedOnce = false

    // Multi-profile (EPIC-05): every profile-scoped screen fetches this fresh via
    // repository.getCurrentProfile() rather than caching a stale value across Activities.
    private var currentProfileId: Long = 1L

    // Unit system (EPIC-04 T04.1): wheels build metric first (fast, matches pre-existing
    // cold-start behavior), then loadCurrentProfileAndRefresh() (onResume) rebuilds them if
    // the persisted preference is imperial. `weight`/`height` fields ALWAYS stay metric —
    // the wheel listeners convert on every selection — so nothing downstream (navigationRunnable,
    // ResultAct, Room) needs to know about units at all.
    private var unitSystem: String = UnitFormatter.METRIC

    private val handler = Handler(Looper.getMainLooper())
    private val exitResetRunnable = Runnable { doubleBackToExitPressedOnce = false }

    // Banner view ref — restored để destroy khi user activate VIP mid-session.
    // SDK autoManageLifecycle xử lý lifecycle activity, nhưng KHÔNG biết VIP state đổi.
    private var adView: View? = null

    // VIP pill pulse animator — nullable + cancelled ở onPause/onDestroy (no leak).
    private var vipBadgePulseAnimator: ObjectAnimator? = null
    private var vipBadgePulseHasBouncedIn = false

    // BUG-11: Use ActivityResultLauncher instead of deprecated startActivityForResult
    private val resultLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            val shouldReset = result.data?.getBooleanExtra(REQUEST_RESULT, false)
            if (shouldReset == true) {
                animationView()
                _binding.startButton.alpha = 1f
            }
        }
    }

    private val navigationRunnable = Runnable {
        val intent = Intent(this, ResultAct::class.java)
        intent.putExtra("Height", height.toDouble())
        intent.putExtra("Weight", weight.toDouble())
        intent.putExtra("Age", age)
        // Gender code: 0=Male, 1=Female, 2=Other. "Other" used to silently fall into the
        // `else` branch and get treated as Female for BMR/ideal-weight — see EPIC-00 T00.5.
        when (gender) {
            'M' -> intent.putExtra("Gender", 0)
            'O' -> intent.putExtra("Gender", 2)
            else -> intent.putExtra("Gender", 1)
        }
        // BUG-11: use resultLauncher instead of deprecated startActivityForResult
        resultLauncher.launch(intent)
        overridePendingTransition(0, 0)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        UIUtils.setupEdgeToEdge1(window)
        binding = DataBindingUtil.setContentView(this, R.layout.a_main)
        UIUtils.setupEdgeToEdge2(
            rootView = findViewById(R.id.layoutRoot),
            paddingTop = true,
            paddingBottom = true
        )

        // Setup OnBackPressedDispatcher
        onBackPressedDispatcher.addCallback(this, object : androidx.activity.OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (doubleBackToExitPressedOnce) {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                } else {
                    doubleBackToExitPressedOnce = true
                    Toast.makeText(this@MainAct, getString(R.string.press_back_again_to_exit), Toast.LENGTH_SHORT).show()
                    handler.postDelayed(exitResetRunnable, 2000)
                }
            }
        })

        val database = AppDatabase.getDatabase(this)
        repository = BmiRepository(database.bmiDao(), database.profileDao(), database.bodyMeasurementDao())

        // Refresh profile-scoped UI whenever ProfileSwitcherBottomSheet reports a switch/
        // create/rename/delete — the sheet itself doesn't know about MainAct's chip/streak.
        supportFragmentManager.setFragmentResultListener(
            ProfileSwitcherBottomSheet.REQUEST_KEY, this
        ) { _, _ -> loadCurrentProfileAndRefresh() }

        animationView()
        setupViews()
    }

    override fun onResume() {
        super.onResume()
        // Banner lifecycle: SDK 1.1.3 auto-manage qua ActivityLifecycleCallbacks (no manual call).
        // NHƯNG: khi user activate/revoke VIP mid-session, SDK không tự destroy banner đã load.
        // → app-side phải manual refresh banner theo VIP state ở mỗi onResume.
        syncBannerWithVipState()
        loadCurrentProfileAndRefresh()
        refreshVipBadge()
        startVipBadgePulse()
    }

    /** Fetches the current profile, then refreshes every profile-scoped UI element. */
    private fun loadCurrentProfileAndRefresh() {
        lifecycleScope.launch(Dispatchers.IO) {
            val profile = repository.getCurrentProfile()
            // Local vals (not the shared `currentProfileId` field) for this invocation's own
            // UI calls — onResume() and the ProfileSwitcherBottomSheet fragment-result listener
            // can both trigger this concurrently, and two IO coroutines racing to write/read
            // the same mutable field could hand one invocation's Main block the other's id.
            val profileId = profile?.id ?: 1L
            val profileName = profile?.name ?: "Default"
            val fetchedUnitSystem = PreferencesManager(this@MainAct).unitSystem.first()
            currentProfileId = profileId // still kept in sync for other synchronous readers
            withContext(Dispatchers.Main) {
                updateProfileChip(profileName)
                updateStreakUI(profileId)
                maybeShowOnboardingRename(profileId, profileName)
                if (fetchedUnitSystem != unitSystem) {
                    unitSystem = fetchedUnitSystem
                    refreshWeightPickerForUnit()
                    refreshHeightPickerForUnit()
                    updateUnitLabels()
                }
            }
        }
    }

    private fun updateProfileChip(name: String) {
        _binding.tvProfileBadge.text = "👤 $name"
        _binding.tvProfileBadge.setOnClickListener {
            ProfileSwitcherBottomSheet().show(supportFragmentManager, ProfileSwitcherBottomSheet.TAG)
        }
    }

    /**
     * One-time prompt to name the auto-created "Default" profile (EPIC-05 T05.4) — decoupled
     * from SplashAct's first-run language flow to avoid touching that fragile sequencing.
     * Gated by a SharedPrefs flag so it never nags twice, regardless of whether the user
     * renamed or skipped.
     */
    private fun maybeShowOnboardingRename(profileId: Long, profileName: String) {
        val prefs = getSharedPreferences("main_prefs", MODE_PRIVATE)
        if (prefs.getBoolean(KEY_ONBOARDING_PROFILE_ASKED, false)) return
        if (profileName != "Default") {
            // Already personalized (e.g. via the switcher) — nothing to ask, consume now.
            prefs.edit().putBoolean(KEY_ONBOARDING_PROFILE_ASKED, true).apply()
            return
        }
        if (isFinishing || isDestroyed) return // profile fetch is async — Activity may be gone by now

        fun markAsked() = prefs.edit().putBoolean(KEY_ONBOARDING_PROFILE_ASKED, true).apply()

        val dialogView = android.view.LayoutInflater.from(this).inflate(R.layout.dialog_profile_name, null)
        val etName = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.etProfileName)

        com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.onboarding_profile_title))
            .setView(dialogView)
            .setCancelable(false)
            .setPositiveButton(getString(R.string.save)) { _, _ ->
                val name = etName.text.toString().trim()
                if (name.isEmpty()) {
                    // Don't consume the "asked" flag — a dismissed-but-empty submit means the
                    // dialog (AlertDialog always closes on button click) will simply reappear
                    // next resume, instead of permanently losing this one-time prompt.
                    Toast.makeText(this, getString(R.string.profile_name_empty_error), Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                markAsked()
                lifecycleScope.launch(Dispatchers.IO) {
                    // Fetch-then-copy (not a bare `Profile(id=..., name=...)`) so
                    // createdAt/goalWeight/isCurrent aren't silently reset to defaults.
                    val current = repository.getCurrentProfile()
                    val saved = current != null && current.id == profileId
                    if (saved) repository.updateProfile(current!!.copy(name = name))
                    // Only reflect the typed name in the chip if it was actually persisted —
                    // otherwise (profile switched/deleted mid-dialog) the chip would show a
                    // name that silently isn't backed by any DB write.
                    if (saved) withContext(Dispatchers.Main) { updateProfileChip(name) }
                }
            }
            .setNegativeButton(getString(R.string.onboarding_skip)) { _, _ -> markAsked() }
            .show()
    }

    override fun onPause() {
        super.onPause()
        stopVipBadgePulse()
    }

    /**
     * VIP pill animation — 2 phases:
     *  1) One-shot bounce-in (scale 0.85 → overshoot → 1.0) khi MainAct lần đầu visible.
     *  2) Continuous gentle scale pulse, intensity tăng khi VIP active.
     * Cancel ở onPause để dừng khi user navigate đi, restart ở onResume.
     */
    private fun startVipBadgePulse() {
        val target = _binding.tvVipBadge
        vipBadgePulseAnimator?.cancel()

        if (!vipBadgePulseHasBouncedIn) {
            // One-shot entrance animation: shrink + overshoot back.
            target.scaleX = 0.85f
            target.scaleY = 0.85f
            target.alpha = 0f
            target.animate()
                .scaleX(1.0f)
                .scaleY(1.0f)
                .alpha(1.0f)
                .setDuration(550L)
                .setInterpolator(OvershootInterpolator(1.4f))
                .withEndAction { launchContinuousPulse(target) }
                .start()
            vipBadgePulseHasBouncedIn = true
        } else {
            launchContinuousPulse(target)
        }
    }

    private fun launchContinuousPulse(target: View) {
        val active = AdManager.isVipByKeyActive()
        // VIP active: pulse mạnh hơn (1.0 → 1.12) + nhanh hơn (900ms).
        // Free: pulse nhẹ (1.0 → 1.05) + chậm (1500ms) — subtle attention grab.
        val scaleMax = if (active) 1.12f else 1.05f
        val pulseDuration = if (active) 900L else 1500L
        vipBadgePulseAnimator = ObjectAnimator.ofPropertyValuesHolder(
            target,
            PropertyValuesHolder.ofFloat(View.SCALE_X, 1.0f, scaleMax),
            PropertyValuesHolder.ofFloat(View.SCALE_Y, 1.0f, scaleMax),
        ).apply {
            duration = pulseDuration
            repeatMode = ValueAnimator.REVERSE
            repeatCount = ValueAnimator.INFINITE
            start()
        }
    }

    private fun stopVipBadgePulse() {
        vipBadgePulseAnimator?.cancel()
        // Reset scale để không bị stuck ở mid-frame khi pause (defensive).
        _binding.tvVipBadge.apply {
            scaleX = 1.0f
            scaleY = 1.0f
            animate().cancel()
        }
    }

    /**
     * Đồng bộ banner với VIP state:
     * - VIP active: destroy adView (nếu có) + hide flAd container.
     * - Free: load banner nếu chưa load (idempotent qua adView nullable check).
     */
    private fun syncBannerWithVipState() {
        try {
            val isVip = AdManager.isVipByKeyActive()
            if (isVip) {
                adView?.let { AdManager.bannerDestroy(it) }
                adView = null
                binding.flAd.visibility = View.GONE
            } else {
                binding.flAd.visibility = View.VISIBLE
                if (adView == null) {
                    adView = AdManager.loadBanner(
                        context = this,
                        container = binding.flAd.findViewById(R.id.bannerContainer),
                        tvLabelAd = binding.flAd.findViewById(R.id.tvLabelAd),
                    )
                }
            }
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) Log.w("roy93~", "syncBannerWithVipState failed", e)
        }
    }

    private fun refreshVipBadge() {
        try {
            val active = AdManager.isVipByKeyActive()
            // VIP pill luôn hiển thị (quick access). Đổi bg + text color theo state.
            _binding.tvVipBadge.visibility = View.VISIBLE
            if (active) {
                _binding.tvVipBadge.setBackgroundResource(R.drawable.bg_vip_badge)
                _binding.tvVipBadge.setTextColor(
                    androidx.core.content.ContextCompat.getColor(this, R.color.vip_text_on_gold)
                )
            } else {
                _binding.tvVipBadge.setBackgroundResource(R.drawable.bg_vip_badge_free)
                _binding.tvVipBadge.setTextColor(
                    androidx.core.content.ContextCompat.getColor(this, R.color.textColor)
                )
            }
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) Log.w("roy93~", "refreshVipBadge failed", e)
        }
    }

    private fun updateStreakUI(profileId: Long) {
        if (BuildConfig.DEBUG) Log.d("roy93~", "updateStreakUI() called, profileId=$profileId")
        try {
            val streakCard = _binding.root.findViewById<View>(R.id.streakCard)
            if (BuildConfig.DEBUG) Log.d("roy93~", "updateStreakUI: streakCard=${streakCard != null}")
            if (streakCard == null) return

            val tvTitle = streakCard.findViewById<TextView>(R.id.tvStreakTitle)
            val tvBest = streakCard.findViewById<TextView>(R.id.tvStreakBest)
            val tvMotivation = streakCard.findViewById<TextView>(R.id.tvStreakMotivation)
            if (BuildConfig.DEBUG) Log.d("roy93~", "updateStreakUI: tvTitle=${tvTitle != null}, tvBest=${tvBest != null}, tvMotivation=${tvMotivation != null}")
            if (tvTitle == null || tvBest == null || tvMotivation == null) return

            val data = StreakManager.getDisplayStreak(this, profileId)
            if (BuildConfig.DEBUG) Log.d("roy93~", "updateStreakUI: current=${data.current}, best=${data.best}, lastDate=${data.lastDate}")

            if (data.current > 0) {
                tvTitle.text = getString(R.string.streak_title, data.current)
                tvMotivation.text = getString(R.string.streak_motivation)
            } else {
                tvTitle.text = getString(R.string.streak_start)
                tvMotivation.text = getString(R.string.streak_motivation)
            }
            tvBest.text = if (data.best > 0) getString(R.string.streak_best, data.best) else ""
            tvBest.visibility = if (data.best > 0) View.VISIBLE else View.GONE

            // Update day circles — show checked days based on actual streak count
            val dayIds = intArrayOf(R.id.tvDay0, R.id.tvDay1, R.id.tvDay2, R.id.tvDay3, R.id.tvDay4, R.id.tvDay5, R.id.tvDay6)
            val dayLabels = arrayOf("M", "T", "W", "T", "F", "S", "S")
            val todayIndex = (java.time.LocalDate.now().dayOfWeek.value - 1) // 0=Mon
            val todayChecked = StreakManager.isTodayChecked(this, profileId)
            if (BuildConfig.DEBUG) Log.d("roy93~", "updateStreakUI: todayIndex=$todayIndex, todayChecked=$todayChecked")

            // How many past days in this week are part of the streak?
            val streakDays = data.current
            val checkedToday = if (todayChecked) 1 else 0
            val pastDaysCovered = minOf(streakDays - checkedToday, todayIndex)
            if (BuildConfig.DEBUG) Log.d("roy93~", "updateStreakUI: streakDays=$streakDays, pastDaysCovered=$pastDaysCovered")

            for (i in 0..6) {
                val tv = streakCard.findViewById<TextView>(dayIds[i]) ?: continue
                when {
                    i == todayIndex -> tv.text = if (todayChecked) "✓" else "◉"
                    i < todayIndex && i >= (todayIndex - pastDaysCovered) -> tv.text = "✓"
                    else -> tv.text = dayLabels[i]
                }
            }
            if (BuildConfig.DEBUG) Log.d("roy93~", "updateStreakUI: DONE, title=${tvTitle.text}")
        } catch (e: Exception) {
            Log.e("roy93~", "updateStreakUI error", e)
        }
    }

    private fun setupViews() {
        //        Gender
        val titlesOfGender: List<String> = listOf("F", "O", "M")

        _binding.genderWheelView.apply {
            titles = titlesOfGender
            elevation = 0f
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                isFocusedByDefault = true
            }
            isSelected = true
            focusedIndex = 2
        }
        _binding.genderWheelView.selectListener = {
            gender = titlesOfGender[it][0]
        }

//        Weight
        val pickerLayoutManager = PickerLayoutManager(this, PickerLayoutManager.HORIZONTAL, false)
        pickerLayoutManager.apply {
            isChangeAlpha = true
            scaleDownBy = 0.99f
            scaleDownDistance = 0.8f
            initialPrefetchItemCount = 3
            isSmoothScrollbarEnabled = true
            scrollToPosition(49)
        }

        val snapHelper: SnapHelper = LinearSnapHelper()
        snapHelper.attachToRecyclerView(_binding.weightRecyclerBtn)

        // ML-04: Removed recyclerView reference from adapter constructor
        // Built metric-first (matches pre-existing cold-start behavior); loadCurrentProfileAndRefresh()
        // (onResume) rebuilds to imperial if that's the persisted preference — see EPIC-04 T04.1.
        weightAdapter = WeightPickerAdt(this, weightWheelLabels(unitSystem))

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            _binding.weightRecyclerBtn.defaultFocusHighlightEnabled = true
        }
        _binding.weightRecyclerBtn.apply {
            layoutManager = pickerLayoutManager
            adapter = weightAdapter
            isSelected = true
            requestFocus()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                isFocusedByDefault = true
            }
        }
        pickerLayoutManager.setOnScrollStopListener { view ->
            // `weight` always stays metric regardless of what unit the wheel is displaying —
            // nothing downstream (navigationRunnable, ResultAct, Room) needs to know about units.
            val selectedDisplay = Integer.parseInt((view as TextView).text.toString())
            weight = Math.round(UnitFormatter.weightToMetric(selectedDisplay.toDouble(), unitSystem)).toInt()
        }

//        Height
        _binding.heightWheelView.onWheelChangedListener =
            OnWheelChangedListener { view, _, newIndex ->
                val text = view.getItem(newIndex)
                val selectedDisplay = Integer.parseInt(text.toString())
                height = Math.round(UnitFormatter.heightToMetric(selectedDisplay.toDouble(), unitSystem)).toInt()
            }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            _binding.heightWheelView.apply {
                defaultFocusHighlightEnabled = true
            }
        }
        _binding.heightWheelView.currentIndex = 159//160cm

        // Prevent ScrollView from intercepting touch events for height wheel
        _binding.heightWheelView.setOnTouchListener { v, event ->
            v.parent.requestDisallowInterceptTouchEvent(true)
            false
        }

        // Age setup - using programmatic approach since layout is complex
        val ageWheelView = _binding.root.findViewById<com.cncoderx.wheelview.WheelView>(R.id.ageWheelView)
        if (ageWheelView != null) {
            ageWheelView.onWheelChangedListener = OnWheelChangedListener { view, _, newIndex ->
                val text = view.getItem(newIndex)
                age = Integer.parseInt(text.toString())
            }
            ageWheelView.currentIndex = 15 // Default 25 years (index 15 in age_array starting from 10)

            // Prevent ScrollView from intercepting touch events
            ageWheelView.setOnTouchListener { v, event ->
                v.parent.requestDisallowInterceptTouchEvent(true)
                false
            }
        } else {
            // Fallback: age = 25 already set as default
            if (BuildConfig.DEBUG) android.util.Log.w("MainAct", "Age wheel view not found, using default age: $age")
        }

        _binding.startButton.setOnActiveListener {
            animationViewUp()
            _binding.startButton.alpha = 0f
            handler.postDelayed(navigationRunnable, 500)
        }

        _binding.ivBack.setOnClickListener {
            // BUG-01: Use onBackPressedDispatcher instead of deprecated onBackPressed()
            onBackPressedDispatcher.onBackPressed()
        }
        _binding.ivMenu.setOnClickListener {
            showMenu()
        }
        _binding.tvVipBadge.setOnClickListener {
            startActivity(Intent(this, VipActivity::class.java))
        }

        // Banner load: delegated tới `syncBannerWithVipState()` (gọi từ onResume).
        // Tránh load duplicate trong setupViews — onResume xử lý cả initial state + state changes.
    }

    /** EPIC-04 T04.1: rebuild the weight wheel for the given unit, preserving the current selection. */
    private fun refreshWeightPickerForUnit() {
        val labels = weightWheelLabels(unitSystem)
        weightAdapter.swapData(labels)
        val displayValue = Math.round(UnitFormatter.weightToDisplay(weight.toDouble(), unitSystem))
        val idx = labels.indexOf(displayValue.toString()).let { if (it >= 0) it else 0 }
        _binding.weightRecyclerBtn.scrollToPosition(idx)
    }

    /** EPIC-04 T04.1: rebuild the height wheel for the given unit, preserving the current selection. */
    private fun refreshHeightPickerForUnit() {
        val labels = heightWheelLabels(unitSystem)
        _binding.heightWheelView.setEntries(labels)
        val displayValue = Math.round(UnitFormatter.heightToDisplay(height.toDouble(), unitSystem))
        val idx = labels.indexOf(displayValue.toString()).let { if (it >= 0) it else 0 }
        _binding.heightWheelView.currentIndex = idx
    }

    private fun updateUnitLabels() {
        _binding.root.findViewById<TextView>(R.id.tvWeightUnit)?.text =
            "(${UnitFormatter.weightUnitLabel(unitSystem).uppercase()})"
        _binding.root.findViewById<TextView>(R.id.tvHeightUnit)?.text =
            "(${UnitFormatter.heightUnitLabel(unitSystem).uppercase()})"
    }

    private fun animationView() {
        _binding.apply {
            // Enhanced glassmorphism animations
            bodyContainer.scaleX = 0.9f
            bodyContainer.scaleY = 0.9f
            bodyContainer.alpha = 0f
            bodyContainer.translationY = 50f

            footerContainer.scaleX = 0.9f
            footerContainer.scaleY = 0.9f
            footerContainer.alpha = 0f
            footerContainer.translationY = 30f

            // Animate bodyContainer with bounce
            bodyContainer.animate()
                .scaleX(1f)
                .scaleY(1f)
                .translationY(0f)
                .alpha(1f)
                .setDuration(600)
                .setStartDelay(200)
                .setInterpolator(android.view.animation.OvershootInterpolator())
                .start()

            // Animate footerContainer
            footerContainer.animate()
                .scaleX(1f)
                .scaleY(1f)
                .translationY(0f)
                .alpha(1f)
                .setDuration(600)
                .setStartDelay(400)
                .setInterpolator(android.view.animation.OvershootInterpolator())
                .start()
        }
    }

    private fun animationViewUp() {
        _binding.apply {
            textView.animate()
                .alpha(0f)
                .setDuration(200)
                .start()

            bodyContainer.animate()
                .scaleX(0.85f)
                .scaleY(0.85f)
                .translationY(-100f)
                .alpha(0f)
                .setDuration(400)
                .setInterpolator(android.view.animation.AccelerateInterpolator())
                .start()

            footerContainer.animate()
                .scaleX(0.85f)
                .scaleY(0.85f)
                .translationY(-80f)
                .alpha(0f)
                .setDuration(400)
                .setStartDelay(50)
                .setInterpolator(android.view.animation.AccelerateInterpolator())
                .start()
        }
    }


    private fun showMenu() {
        val dialog = Dialog(this, android.R.style.Theme_Dialog)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setContentView(R.layout.dialog_menu)

        // Make background transparent and dimmed
        dialog.window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            setDimAmount(0.6f)
            attributes?.width = ViewGroup.LayoutParams.MATCH_PARENT
            attributes?.height = ViewGroup.LayoutParams.WRAP_CONTENT
        }

        val menuHistory = dialog.findViewById<LinearLayout>(R.id.menuHistory)
        val menuCalculators = dialog.findViewById<LinearLayout>(R.id.menuCalculators)
        val menuRateApp = dialog.findViewById<LinearLayout>(R.id.menuRateApp)
        val menuMoreApp = dialog.findViewById<LinearLayout>(R.id.menuMoreApp)
        val menuShareApp = dialog.findViewById<LinearLayout>(R.id.menuShareApp)
        val menuSettings = dialog.findViewById<LinearLayout>(R.id.menuSettings)
        val menuPolicy = dialog.findViewById<LinearLayout>(R.id.menuPolicy)

        menuHistory.setOnClickListener {
            dialog.dismiss()
            startActivity(Intent(this, HistoryActivity::class.java))
        }

        val menuTracker = dialog.findViewById<LinearLayout>(R.id.menuTracker)
        menuTracker?.setOnClickListener {
            dialog.dismiss()
            TrackerBottomSheet().show(supportFragmentManager, TrackerBottomSheet.TAG)
        }

        val menuAchievements = dialog.findViewById<LinearLayout>(R.id.menuAchievements)
        menuAchievements?.setOnClickListener {
            dialog.dismiss()
            AchievementsBottomSheet().show(supportFragmentManager, AchievementsBottomSheet.TAG)
        }

        val menuVip = dialog.findViewById<LinearLayout>(R.id.menuVip)
        menuVip?.setOnClickListener {
            dialog.dismiss()
            startActivity(Intent(this, VipActivity::class.java))
        }

        menuCalculators.setOnClickListener {
            dialog.dismiss()
            startActivity(Intent(this, CalculatorsActivity::class.java))
        }

        menuRateApp.setOnClickListener {
            dialog.dismiss()
            rateApp(packageName)
        }

        menuMoreApp.setOnClickListener {
            dialog.dismiss()
            moreApp()
        }

        menuShareApp.setOnClickListener {
            dialog.dismiss()
            shareApp()
        }

        menuSettings.setOnClickListener {
            dialog.dismiss()
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        menuPolicy.setOnClickListener {
            dialog.dismiss()
            openBrowserPolicy()
        }

        dialog.show()

        // Animate dialog content with scale and fade
        val cardMenu = dialog.findViewById<View>(R.id.cardMenu)
        cardMenu?.apply {
            scaleX = 0.7f
            scaleY = 0.7f
            alpha = 0f
            animate()
                .scaleX(1f)
                .scaleY(1f)
                .alpha(1f)
                .setDuration(300)
                .setInterpolator(android.view.animation.OvershootInterpolator(1.2f))
                .start()
        }
    }

    override fun onDestroy() {
        handler.removeCallbacks(exitResetRunnable)
        handler.removeCallbacks(navigationRunnable)
        // Memory leak guards — explicit cleanup even if SDK autoManageLifecycle handles too.
        vipBadgePulseAnimator?.cancel()
        vipBadgePulseAnimator?.removeAllUpdateListeners()
        vipBadgePulseAnimator = null
        _binding.tvVipBadge.animate().cancel()
        adView?.let { AdManager.bannerDestroy(it) }
        adView = null
        super.onDestroy()
    }
}
