package com.samsunggalaxy.ui

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.widget.PopupMenu
import androidx.core.app.ActivityCompat.startActivityForResult
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
import travel.ithaka.android.horizontalpickerlib.PickerLayoutManager

const val REQUEST_CODE = 69
const val REQUEST_RESULT = "REQUEST_RESULT"

class MainAct : BaseActivity() {
    private lateinit var binding: AMainBinding
    private val _binding get() = binding
    private lateinit var weightAdapter: WeightPickerAdt
    private var gender = 'M'
    var height = 1
    private var weight = 50
    private var doubleBackToExitPressedOnce = false

    //    private var adView: MaxAdView? = null
    private var adView: AdView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = DataBindingUtil.setContentView(this, R.layout.a_main)

        animationView()
        setupViews()
    }

    override fun onResume() {
        super.onResume()
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

        _binding.startButton.setOnActiveListener {
            animationViewUp()
            _binding.startButton.alpha = 0f
            Handler(Looper.getMainLooper()).postDelayed({
                val intent = Intent(this, ResultAct::class.java)
                intent.putExtra("Height", height.toDouble())
                intent.putExtra("Weight", weight.toDouble())
                if (gender == 'M') {
                    intent.putExtra("Gender", 0)
                } else {
                    intent.putExtra("Gender", 1)
                }
                startActivityForResult(intent, REQUEST_CODE)
                overridePendingTransition(0, 0)
            }, 500)
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

    private fun getData(count: Int): List<String> {
        val data: MutableList<String> = ArrayList()
        for (i in 0 until count) {
            data.add(i.toString())
        }
        return data
    }

    private fun animationView() {
        _binding.apply {

            bodyContainer.translationY = 150f
            footerContainer.translationY = 150f
            heightWheelView.translationY = 150f
            weightRecyclerBtn.translationX = 150f


            bodyContainer.alpha = 0f
            footerContainer.alpha = 0f
            heightWheelView.alpha = 0f
            weightRecyclerBtn.alpha = 0f

            bodyContainer.animate().translationY(0f).alpha(1f).setDuration(500).setStartDelay(300)
                .start()
            footerContainer.animate().translationY(0f).alpha(1f).setDuration(500).setStartDelay(400)
                .start()
            heightWheelView.animate().translationY(0f).alpha(1f).setDuration(500).setStartDelay(450)
                .start()
            weightRecyclerBtn.animate().translationX(0f).alpha(1f).setDuration(500)
                .setStartDelay(500).start()

        }
    }

    private fun animationViewUp() {
        _binding.apply {
            textView.animate().translationY(0f).alpha(0f).setDuration(50).setStartDelay(0).start()
            bodyContainer.animate().translationY(-250f).alpha(0f).setDuration(500).setStartDelay(0)
                .start()
            footerContainer.animate().translationY(-250f).alpha(0f).setDuration(500)
                .setStartDelay(50).start()
            heightWheelView.animate().translationY(-250f).alpha(0f).setDuration(500)
                .setStartDelay(100)
                .start()
            weightRecyclerBtn.animate().translationX(-250f).alpha(0f).setDuration(500)
                .setStartDelay(150).start()

        }
    }

    @SuppressLint("MissingSuperCall")
    override fun onBackPressed() {
//        super.onBackPressed()
        if (doubleBackToExitPressedOnce) {
            onBackPressedDispatcher.onBackPressed()
        }

        this.doubleBackToExitPressedOnce = true
        Toast.makeText(this, "Please click BACK again to exit", Toast.LENGTH_SHORT).show()

        Handler(Looper.getMainLooper()).postDelayed({ doubleBackToExitPressedOnce = false }, 2000)
    }

    private fun showMenu() {
        val pm = PopupMenu(this, _binding.ivMenu)
        pm.menuInflater.inflate(R.menu.menu_option, pm.menu)
        pm.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.menuRateApp -> {
                    rateApp(packageName)
                    true
                }

                R.id.menuMoreApp -> {
                    moreApp()
                    true
                }

                R.id.menuShareApp -> {
                    shareApp()
                    true
                }

                R.id.menuPolicy -> {
                    openBrowserPolicy()
                    true
                }

                else -> super.onOptionsItemSelected(item)
            }
        }
        pm.show()
    }

    override fun onDestroy() {
//        binding.flAd?.destroyAdBanner(adView)
        adView?.destroy()
        super.onDestroy()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_CODE && resultCode == RESULT_OK) {
            val result = data?.getBooleanExtra(REQUEST_RESULT, false)
//            Log.d("roy93~", "result $result")
            if (result == true) {
                animationView()
                _binding.startButton.alpha = 1f
            }
        }
    }
}
