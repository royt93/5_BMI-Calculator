package com.samsunggalaxy.utils

import android.content.Context
import android.content.res.Configuration
import android.os.Build
import java.util.Locale

object LocaleHelper {
    private const val SELECTED_LANGUAGE = "Locale.Helper.Selected.Language"

    fun setLanguage(context: Context, language: String) {
        val preferences = context.getSharedPreferences("app_preferences", Context.MODE_PRIVATE)
        preferences.edit().putString(SELECTED_LANGUAGE, language).commit()
    }

    fun getLanguage(context: Context): String {
        val preferences = context.getSharedPreferences("app_preferences", Context.MODE_PRIVATE)
        return preferences.getString(SELECTED_LANGUAGE, "en") ?: "en"
    }

    fun onAttach(context: Context): Context {
        val lang = getLanguage(context)
        return setLocale(context, lang)
    }

    private fun setLocale(context: Context, language: String): Context {
        val locale = Locale(language)
        Locale.setDefault(locale)

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            updateResourcesForN(context, locale)
        } else {
            updateResourcesLegacy(context, locale)
        }
    }

    private fun updateResourcesForN(context: Context, locale: Locale): Context {
        val configuration = Configuration(context.resources.configuration)
        configuration.setLocale(locale)

        val newContext = context.createConfigurationContext(configuration)

        // Also update the application context resources for compatibility
        @Suppress("DEPRECATION")
        context.resources.updateConfiguration(configuration, context.resources.displayMetrics)

        return newContext
    }

    private fun updateResourcesLegacy(context: Context, locale: Locale): Context {
        val resources = context.resources
        val configuration = Configuration(resources.configuration)

        @Suppress("DEPRECATION")
        configuration.locale = locale

        @Suppress("DEPRECATION")
        resources.updateConfiguration(configuration, resources.displayMetrics)

        return context
    }
}
