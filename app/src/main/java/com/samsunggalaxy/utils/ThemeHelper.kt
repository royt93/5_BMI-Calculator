package com.samsunggalaxy.utils

import androidx.appcompat.app.AppCompatDelegate

/** EPIC-04 T04.2 — maps the persisted theme_mode string to an AppCompatDelegate night-mode constant. */
object ThemeHelper {
    const val SYSTEM = "system"
    const val LIGHT = "light"
    const val DARK = "dark"

    fun apply(mode: String) {
        AppCompatDelegate.setDefaultNightMode(
            when (mode) {
                LIGHT -> AppCompatDelegate.MODE_NIGHT_NO
                DARK -> AppCompatDelegate.MODE_NIGHT_YES
                else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
            }
        )
    }
}
