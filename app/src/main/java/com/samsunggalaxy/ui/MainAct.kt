package com.samsunggalaxy.ui

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
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.ActivityCompat.startActivityForResult
import androidx.core.view.updatePadding
import androidx.databinding.DataBindingUtil
import androidx.recyclerview.widget.LinearSnapHelper
import androidx.recyclerview.widget.SnapHelper
import com.cncoderx.wheelview.OnWheelChangedListener
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.samsunggalaxy.BaseActivity
import com.samsunggalaxy.BuildConfig
import com.samsunggalaxy.R
import com.samsunggalaxy.adt.WeightPickerAdt
import com.samsunggalaxy.databinding.AMainBinding
import com.samsunggalaxy.ext.moreApp
import com.samsunggalaxy.ext.openBrowserPolicy
import com.samsunggalaxy.ext.rateApp
import com.samsunggalaxy.ext.shareApp
import com.samsunggalaxy.sdkadbmob.AdMobManager
import com.samsunggalaxy.sdkadbmob.UIUtils
import travel.ithaka.android.horizontalpickerlib.PickerLayoutManager

const val REQUEST_CODE = 69
const val REQUEST_RESULT = "REQUEST_RESULT"
const val SETTINGS_REQUEST_CODE = 100

class MainAct : BaseActivity() {
    private lateinit var binding: AMainBinding
    private val _binding get() = binding
    private lateinit var weightAdapter: WeightPickerAdt
    private var gender = 'M'
    var height = 160 // Default to 160cm
    private var weight = 50
    private var age = 25
    private var doubleBackToExitPressedOnce = false

    //    private var adView: MaxAdView? = null
    private var adView: AdView? = null
    private val handler = Handler(Looper.getMainLooper())
    private val exitResetRunnable = Runnable { doubleBackToExitPressedOnce = false }
    private val navigationRunnable = Runnable {
        val intent = Intent(this, ResultAct::class.java)
        intent.putExtra("Height", height.toDouble())
        intent.putExtra("Weight", weight.toDouble())
        intent.putExtra("Age", age)
        if (gender == 'M') {
            intent.putExtra("Gender", 0)
        } else {
            intent.putExtra("Gender", 1)
        }
        startActivityForResult(intent, REQUEST_CODE)
        overridePendingTransition(0, 0)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d("roy93~", "MainActivity.onCreate: called")
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
                    Toast.makeText(this@MainAct, "Please click BACK again to exit", Toast.LENGTH_SHORT).show()
                    handler.postDelayed(exitResetRunnable, 2000)
                }
            }
        })

        animationView()
        setupViews()
    }

    override fun onResume() {
        super.onResume()
        Log.d("roy93~", "MainActivity.onResume: called")
        adView?.resume()
    }

    override fun onPause() {
        adView?.pause()
        super.onPause()
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

        weightAdapter = WeightPickerAdt(this, getData(151), _binding.weightRecyclerBtn)

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
            weight = Integer.parseInt((view as TextView).text.toString())
        }

//        Height
        _binding.heightWheelView.onWheelChangedListener =
            OnWheelChangedListener { view, _, newIndex ->
                val text = view.getItem(newIndex)
                height = Integer.parseInt(text.toString())
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
            android.util.Log.w("MainAct", "Age wheel view not found, using default age: $age")
        }

        _binding.startButton.setOnActiveListener {
            animationViewUp()
            _binding.startButton.alpha = 0f
            handler.postDelayed(navigationRunnable, 500)
        }

        _binding.ivBack.setOnClickListener {
            onBackPressed()
        }
        _binding.ivMenu.setOnClickListener {
            showMenu()
        }

//        adView = this.createAdBanner(
//            logTag = MainAct::class.simpleName,
//            viewGroup = binding.flAd,
//            isAdaptiveBanner = true,
//        )
        adView = binding.flAd.let {
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

    private fun getData(count: Int): List<String> {
        val data: MutableList<String> = ArrayList()
        for (i in 0 until count) {
            data.add(i.toString())
        }
        return data
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
            startActivityForResult(Intent(this, SettingsActivity::class.java), SETTINGS_REQUEST_CODE)
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
//        binding.flAd?.destroyAdBanner(adView)
        adView?.destroy()
        super.onDestroy()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        Log.d("roy93~", "MainActivity.onActivityResult: requestCode=$requestCode, resultCode=$resultCode")
        if (requestCode == REQUEST_CODE && resultCode == RESULT_OK) {
            val result = data?.getBooleanExtra(REQUEST_RESULT, false)
            Log.d("roy93~", "MainActivity.onActivityResult: result $result")
            if (result == true) {
                animationView()
                _binding.startButton.alpha = 1f
            }
        } else if (requestCode == SETTINGS_REQUEST_CODE && resultCode == RESULT_OK) {
            // Language was changed in Settings, recreate to apply
            Log.d("roy93~", "MainActivity.onActivityResult: Language changed, recreating MainActivity")
            recreate()
        }
    }
}
