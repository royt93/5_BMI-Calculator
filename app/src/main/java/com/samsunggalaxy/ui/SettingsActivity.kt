package com.samsunggalaxy.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Process
import android.view.View
import android.widget.TextView
import com.samsunggalaxy.BaseActivity
import com.samsunggalaxy.R
import com.samsunggalaxy.sdkadbmob.UIUtils
import com.samsunggalaxy.utils.LocaleHelper
import kotlin.system.exitProcess

class SettingsActivity : BaseActivity() {
    private lateinit var tvCurrentLanguage: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        UIUtils.setupEdgeToEdge1(window)
        setContentView(R.layout.a_settings)
        UIUtils.setupEdgeToEdge2(
            rootView = findViewById(R.id.layoutRoot),
            paddingTop = true,
            paddingBottom = true
        )

        tvCurrentLanguage = findViewById(R.id.tvCurrentLanguage)

        findViewById<View>(R.id.ivBack)?.setOnClickListener {
            finish()
        }

        findViewById<View>(R.id.languageContainer)?.setOnClickListener {
            showLanguageBottomSheet()
        }

        updateLanguageDisplay()
    }

    override fun onResume() {
        super.onResume()
        updateLanguageDisplay()
    }

    private fun updateLanguageDisplay() {
        val currentLanguage = LocaleHelper.getLanguage(this)
        tvCurrentLanguage.text = getLanguageName(currentLanguage)
    }

    private fun getLanguageName(code: String): String {
        return when (code) {
            "en" -> "English"
            "vi" -> "Vietnamese"
            "fr" -> "French"
            "de" -> "German"
            "ja" -> "Japanese"
            "ko" -> "Korean"
            "th" -> "Thai"
            else -> "English"
        }
    }

    private fun showLanguageBottomSheet() {
        val bottomSheet = LanguageBottomSheet { languageCode ->
            LocaleHelper.setLanguage(this, languageCode)
            restartApp(this)
        }
        bottomSheet.show(supportFragmentManager, "LanguageBottomSheet")
    }

    private fun restartApp(context: Context) {
        // Give a small delay to ensure SharedPreferences are fully written to disk
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)
            intent?.apply {
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK)
            }
            context.startActivity(intent)

            // Kill the current process to ensure clean restart
            Process.killProcess(Process.myPid())
            exitProcess(0)
        }, 200) // 200ms delay to ensure SharedPreferences are flushed
    }
}
