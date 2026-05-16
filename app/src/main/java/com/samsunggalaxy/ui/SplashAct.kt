package com.samsunggalaxy.ui

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.view.animation.AnimationUtils
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.lifecycleScope
import com.roy.sdkadbmob.AdManager
import com.samsunggalaxy.BaseActivity
import com.samsunggalaxy.BuildConfig
import com.samsunggalaxy.R
import com.samsunggalaxy.sdkadbmob.UIUtils
import com.samsunggalaxy.utils.LocaleHelper
import com.samsunggalaxy.utils.PreferencesManager
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@SuppressLint("CustomSplashScreen")
class SplashAct : BaseActivity() {

    private val handler = Handler(Looper.getMainLooper())
    private val finishRunnable = Runnable { finish() }

    private val prefsManager by lazy { PreferencesManager(applicationContext) }

    /**
     * Đánh dấu khi user đã tap chọn ngôn ngữ. Set bởi FragmentResult listener
     * (chạy đồng bộ trước khi sheet dismiss). Đọc bởi FragmentLifecycleCallbacks
     * để phân biệt user-đã-pick vs user-tap-outside.
     */
    private var languageHandled = false

    private val fragmentLifecycleCallbacks = object : FragmentManager.FragmentLifecycleCallbacks() {
        override fun onFragmentDestroyed(fm: FragmentManager, f: Fragment) {
            // Safety net: nếu sheet bị dismiss (back / tap outside) mà chưa pick →
            // fallback "en" để user không bị kẹt trên Splash.
            if (f !is LanguageBottomSheet || languageHandled) return
            if (isFinishing || isDestroyed) return
            languageHandled = true
            LocaleHelper.setLanguage(this@SplashAct, "en")
            lifecycleScope.launch {
                prefsManager.markLanguageSelected()
                if (!isFinishing && !isDestroyed) goToMain()
            }
        }
    }

    public override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        UIUtils.setupEdgeToEdge1(window)
        setContentView(R.layout.activity_splash)
        UIUtils.setupEdgeToEdge2(
            rootView = findViewById(R.id.root_layout),
            paddingTop = true,
            paddingBottom = true
        )

        // Đăng ký FragmentResult listener trong onCreate — tồn tại trước khi sheet trả result,
        // đồng thời tự huỷ theo lifecycle (no leak). Đăng ký sớm để sống sót qua rotate
        // và process restore (DialogFragment được FragmentManager khôi phục qua tag).
        supportFragmentManager.setFragmentResultListener(
            LanguageBottomSheet.REQUEST_KEY,
            this
        ) { _, bundle ->
            languageHandled = true
            val langCode = bundle.getString(LanguageBottomSheet.RESULT_LANGUAGE) ?: "en"
            LocaleHelper.setLanguage(this, langCode)
            lifecycleScope.launch {
                prefsManager.markLanguageSelected()
                if (!isFinishing && !isDestroyed) goToMain()
            }
        }

        // Bảo vệ trường hợp user dismiss sheet mà chưa pick ngôn ngữ.
        supportFragmentManager.registerFragmentLifecycleCallbacks(fragmentLifecycleCallbacks, false)

        startAnimations()

        // UMP consent (GDPR/EEA). Block App Open + Banner + Interstitial cho tới khi resolved.
        // - Non-EEA: SDK skip dialog, canRequestAds=true ngay.
        // - EEA accept: canRequestAds=true sau khi user choose.
        // - EEA deny: canRequestAds=false → skip ads, vào main ngay.
        AdManager.requestConsentInfoUpdate(this) { canRequestAds ->
            if (isFinishing || isDestroyed) return@requestConsentInfoUpdate
            if (canRequestAds) {
                // SDK tự lo: load App Open Ad, show, timeout 8s → callback khi xong.
                AdManager.initSplashScreen(this) {
                    checkFirstRunAndProceed()
                }
            } else {
                if (BuildConfig.DEBUG) Log.d("roy93~", "Consent denied → skip ads, navigate")
                checkFirstRunAndProceed()
            }
        }
    }

    /**
     * Kiểm tra first-run language selection.
     * Dùng lifecycleScope (auto-cancel khi Activity destroy — không leak).
     * Đọc DataStore bằng .first() — lấy 1 lần, không subscribe liên tục.
     */
    private fun checkFirstRunAndProceed() {
        lifecycleScope.launch {
            val isSelected = prefsManager.isLanguageSelected.first()
            if (isFinishing || isDestroyed) return@launch
            if (!isSelected) {
                showFirstRunLanguageSheet()
            } else {
                goToMain()
            }
        }
    }

    /**
     * Hiển thị bottom sheet chọn ngôn ngữ lần đầu.
     * Dùng chung component với Settings → UI 100% giống nhau.
     * Listener đã được đăng ký trong onCreate → KHÔNG giữ reference trực tiếp, no memory leak.
     */
    private fun showFirstRunLanguageSheet() {
        if (!isFinishing && !isDestroyed &&
            supportFragmentManager.findFragmentByTag(LanguageBottomSheet.TAG) == null) {
            LanguageBottomSheet.newInstance()
                .show(supportFragmentManager, LanguageBottomSheet.TAG)
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

    private var isNavigating = false

    private fun goToMain() {
        if (isNavigating) return
        isNavigating = true
        val intent = Intent(this, MainAct::class.java)
        startActivity(intent)
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        handler.postDelayed(finishRunnable, 300)
    }

    override fun onDestroy() {
        supportFragmentManager.unregisterFragmentLifecycleCallbacks(fragmentLifecycleCallbacks)
        // Circles được animate qua ViewPropertyAnimator (view.animate()) — phải dùng
        // animate().cancel() để huỷ. View.clearAnimation() chỉ huỷ legacy Animation
        // framework (do startAnimation() tạo), không động đến ViewPropertyAnimator.
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
