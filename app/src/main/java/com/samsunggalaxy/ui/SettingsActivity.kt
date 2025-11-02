package com.samsunggalaxy.ui

import android.os.Bundle
import android.view.View
import android.widget.TextView
import com.samsunggalaxy.BaseActivity
import com.samsunggalaxy.R
import com.samsunggalaxy.adt.LanguageAdapter
import com.samsunggalaxy.sdkadbmob.UIUtils
import com.samsunggalaxy.utils.LocaleHelper

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
            recreate() // Recreate activity to apply language change
        }
        bottomSheet.show(supportFragmentManager, "LanguageBottomSheet")
    }
}
