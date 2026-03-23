package com.samsunggalaxy.ui

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.animation.AnimationUtils
import com.roy.sdkadbmob.AdManager
import com.samsunggalaxy.BaseActivity
import com.samsunggalaxy.R
import com.samsunggalaxy.sdkadbmob.UIUtils

@SuppressLint("CustomSplashScreen")
class SplashAct : BaseActivity() {

    private val handler = Handler(Looper.getMainLooper())
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

        // SDK tự lo: load App Open Ad, show, timeout 8s → callback khi xong
        AdManager.initSplashScreen(this) {
            goToMain()
        }
    }

    private fun startAnimations() {
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
        }, 500)

        handler.postDelayed({
            appSubtitle.startAnimation(fadeInAnim)
            appSubtitle.alpha = 1f
        }, 700)

        handler.postDelayed({
            bottomSection.startAnimation(fadeInAnim)
            bottomSection.alpha = 1f
        }, 900)

        animateFloatingCircles()
    }

    private var circle1: View? = null
    private var circle2: View? = null
    private var circle3: View? = null

    private fun animateFloatingCircles() {
        circle1 = circle1 ?: findViewById(R.id.circle1)
        circle2 = circle2 ?: findViewById(R.id.circle2)
        circle3 = circle3 ?: findViewById(R.id.circle3)

        circle1?.animate()
            ?.translationX(50f)
            ?.translationY(50f)
            ?.setDuration(3000)
            ?.withEndAction {
                if (!isDestroyed) {
                    circle1?.animate()
                        ?.translationX(0f)
                        ?.translationY(0f)
                        ?.setDuration(3000)
                        ?.withEndAction { if (!isDestroyed) animateFloatingCircles() }
                        ?.start()
                }
            }
            ?.start()

        circle2?.animate()
            ?.translationY(-100f)
            ?.setDuration(2500)
            ?.withEndAction {
                if (!isDestroyed) {
                    circle2?.animate()
                        ?.translationY(0f)
                        ?.setDuration(2500)
                        ?.start()
                }
            }
            ?.start()

        circle3?.animate()
            ?.translationX(30f)
            ?.translationY(-30f)
            ?.setDuration(2000)
            ?.withEndAction {
                if (!isDestroyed) {
                    circle3?.animate()
                        ?.translationX(0f)
                        ?.translationY(0f)
                        ?.setDuration(2000)
                        ?.start()
                }
            }
            ?.start()
    }

    private fun goToMain() {
        val intent = Intent(this, MainAct::class.java)
        startActivity(intent)
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        handler.postDelayed(finishRunnable, 300)
    }

    override fun onDestroy() {
        circle1?.animate()?.cancel()
        circle2?.animate()?.cancel()
        circle3?.animate()?.cancel()
        circle1 = null
        circle2 = null
        circle3 = null
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }
}
