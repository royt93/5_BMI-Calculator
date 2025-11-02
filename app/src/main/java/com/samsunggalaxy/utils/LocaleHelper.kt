package com.samsunggalaxy.utils

import android.content.Context
import android.content.res.Configuration
import android.os.Build
import java.util.Locale

object LocaleHelper {
    private const val SELECTED_LANGUAGE = "Locale.Helper.Selected.Language"

    fun setLanguage(context: Context, language: String) {
        android.util.Log.d("roy93~", "LocaleHelper.setLanguage: Setting language to $language")
        persist(context, language)
        updateResources(context, language)
        android.util.Log.d("roy93~", "LocaleHelper.setLanguage: Language set complete")
    }

    fun getLanguage(context: Context): String {
        val preferences = context.getSharedPreferences("app_preferences", Context.MODE_PRIVATE)
        val lang = preferences.getString(SELECTED_LANGUAGE, "en") ?: "en"
        android.util.Log.d("roy93~", "LocaleHelper.getLanguage: Retrieved language = $lang")
        return lang
    }

    private fun persist(context: Context, language: String) {
        val preferences = context.getSharedPreferences("app_preferences", Context.MODE_PRIVATE)
        preferences.edit().putString(SELECTED_LANGUAGE, language).apply()
    }

    private fun updateResources(context: Context, language: String) {
        val locale = Locale(language)
        Locale.setDefault(locale)

        val resources = context.resources
        val configuration = Configuration(resources.configuration)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            configuration.setLocale(locale)
            context.createConfigurationContext(configuration)
        } else {
            @Suppress("DEPRECATION")
            configuration.locale = locale
        }

        @Suppress("DEPRECATION")
        resources.updateConfiguration(configuration, resources.displayMetrics)
    }

    fun onAttach(context: Context): Context {
        val lang = getLanguage(context)
        return setLocale(context, lang)
    }

    private fun setLocale(context: Context, language: String): Context {
        val locale = Locale(language)
        Locale.setDefault(locale)

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            updateResourcesLegacy(context, language)
        } else {
            updateResourcesLegacy(context, language)
        }
    }

    private fun updateResourcesLegacy(context: Context, language: String): Context {
        val locale = Locale(language)
        Locale.setDefault(locale)

        val resources = context.resources
        val configuration = Configuration(resources.configuration)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            configuration.setLocale(locale)
        } else {
            @Suppress("DEPRECATION")
            configuration.locale = locale
        }

        @Suppress("DEPRECATION")
        resources.updateConfiguration(configuration, resources.displayMetrics)

        return context
    }
}
