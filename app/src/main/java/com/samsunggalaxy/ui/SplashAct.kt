package com.samsunggalaxy.ui

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.animation.AnimationUtils
import androidx.lifecycle.lifecycleScope
import com.samsunggalaxy.BaseActivity
import com.samsunggalaxy.BuildConfig
import com.samsunggalaxy.R
import com.samsunggalaxy.sdkadbmob.AdMobManager
import com.samsunggalaxy.sdkadbmob.UIUtils
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.jvm.java

@SuppressLint("CustomSplashScreen")
class SplashAct : BaseActivity() {

    private val handler = android.os.Handler(android.os.Looper.getMainLooper())
    private val finishRunnable = Runnable { finish() }

    public override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        UIUtils.setupEdgeToEdge1(window)
        setContentView(R.layout.activity_splash)
        UIUtils.setupEdgeToEdge2(
            rootView = findViewById(R.id.root_layout),
            paddingTop = true,
            paddingBottom = true
        )

        startAnimations()

        AdMobManager.initSplashScreen(this, {
            goToMain()
        })
    }

    private fun startAnimations() {
        // Pulse animation cho app icon
        val appIcon = findViewById<View>(R.id.appIcon)
        val pulseAnim = AnimationUtils.loadAnimation(this, R.anim.pulse_scale)
        appIcon.startAnimation(pulseAnim)

        // Fade in animation cho title và subtitle
        val appTitle = findViewById<View>(R.id.appTitle)
        val appSubtitle = findViewById<View>(R.id.appSubtitle)
        val bottomSection = findViewById<View>(R.id.bottomSection)

        val fadeInAnim = AnimationUtils.loadAnimation(this, R.anim.fade_in_up)

        appTitle.alpha = 0f
        appSubtitle.alpha = 0f
        bottomSection.alpha = 0f

        handler.postDelayed({
            appTitle.startAnimation(fadeInAnim)
            appTitle.alpha = 1f
        }, 300)

        handler.postDelayed({
            appSubtitle.startAnimation(fadeInAnim)
            appSubtitle.alpha = 1f
        }, 500)

        handler.postDelayed({
            bottomSection.startAnimation(fadeInAnim)
            bottomSection.alpha = 1f
        }, 700)

        // Floating animation cho background circles
        animateFloatingCircles()
    }

    private fun animateFloatingCircles() {
        val circle1 = findViewById<View>(R.id.circle1)
        val circle2 = findViewById<View>(R.id.circle2)
        val circle3 = findViewById<View>(R.id.circle3)

        // Circle 1 - Move diagonally
        circle1.animate()
            .translationX(50f)
            .translationY(50f)
            .setDuration(3000)
            .withEndAction {
                circle1.animate()
                    .translationX(0f)
                    .translationY(0f)
                    .setDuration(3000)
                    .withEndAction { animateFloatingCircles() }
                    .start()
            }
            .start()

        // Circle 2 - Move vertically
        circle2.animate()
            .translationY(-100f)
            .setDuration(2500)
            .withEndAction {
                circle2.animate()
                    .translationY(0f)
                    .setDuration(2500)
                    .start()
            }
            .start()

        // Circle 3 - Move horizontally
        circle3.animate()
            .translationX(30f)
            .translationY(-30f)
            .setDuration(2000)
            .withEndAction {
                circle3.animate()
                    .translationX(0f)
                    .translationY(0f)
                    .setDuration(2000)
                    .start()
            }
            .start()
    }

    private fun goToMain() {
        val intent = Intent(this, MainAct::class.java)
        startActivity(intent)
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        // Trì hoãn finish để đợi animation hoàn tất
        handler.postDelayed(finishRunnable, 300)
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }
}
