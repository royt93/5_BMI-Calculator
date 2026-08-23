package com.samsunggalaxy.utils

import androidx.appcompat.app.AppCompatDelegate
import org.junit.Assert.assertEquals
import org.junit.Test

/** EPIC-04 T04.2 — theme_mode string -> AppCompatDelegate night-mode constant mapping. */
class ThemeHelperTest {

    @Test
    fun light_mapsToModeNightNo() {
        ThemeHelper.apply(ThemeHelper.LIGHT)
        assertEquals(AppCompatDelegate.MODE_NIGHT_NO, AppCompatDelegate.getDefaultNightMode())
    }

    @Test
    fun dark_mapsToModeNightYes() {
        ThemeHelper.apply(ThemeHelper.DARK)
        assertEquals(AppCompatDelegate.MODE_NIGHT_YES, AppCompatDelegate.getDefaultNightMode())
    }

    @Test
    fun system_mapsToFollowSystem() {
        ThemeHelper.apply(ThemeHelper.SYSTEM)
        assertEquals(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM, AppCompatDelegate.getDefaultNightMode())
    }

    @Test
    fun unknown_fallsBackToFollowSystem() {
        ThemeHelper.apply("garbage")
        assertEquals(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM, AppCompatDelegate.getDefaultNightMode())
    }
}
