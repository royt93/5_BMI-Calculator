package com.samsunggalaxy.ui

import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.samsunggalaxy.R
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Regression coverage for EPIC-01 (doc/task/done/EPIC-01-i18n-hygiene-fixes.md): a handful of
 * user-visible labels were hardcoded English literals instead of @string references, so they
 * never changed when the user picked a non-English locale — an audit found the age-picker
 * header, the ad-banner label, and the splash-screen title all had this bug. These tests pin
 * each one to its string resource so a future edit can't silently re-hardcode it.
 */
@RunWith(AndroidJUnit4::class)
class I18nHygieneTest {

    @Test
    fun mainAct_ageLabel_usesStringResource_notHardcodedLiteral() {
        ActivityScenario.launch(MainAct::class.java).use { scenario ->
            Thread.sleep(500)
            scenario.onActivity { activity ->
                // item_age_picker.xml's header TextView has no android:id — match by the
                // expected localized text instead. If the fix regresses to the old hardcoded
                // "Age" literal, no view will match getString(R.string.age) ("AGE"), and this
                // fails with a clear error instead of silently passing.
                val root = activity.findViewById<android.view.View>(android.R.id.content)
                val ageLabel = findFirstTextViewMatching(root) { it == activity.getString(R.string.age) }
                    ?: error("no TextView showing the localized age label was found in MainAct")
                assertEquals(activity.getString(R.string.age), ageLabel.text.toString())
            }
        }
    }

    private fun findFirstTextViewMatching(view: android.view.View?, predicate: (String) -> Boolean): TextView? {
        if (view == null) return null
        if (view is TextView && predicate(view.text.toString())) return view
        if (view is android.view.ViewGroup) {
            for (i in 0 until view.childCount) {
                val found = findFirstTextViewMatching(view.getChildAt(i), predicate)
                if (found != null) return found
            }
        }
        return null
    }

    @Test
    fun mainAct_adBannerLabel_usesStringResource_notHardcodedLiteral() {
        ActivityScenario.launch(MainAct::class.java).use { scenario ->
            Thread.sleep(500)
            scenario.onActivity { activity ->
                val label = activity.findViewById<TextView>(R.id.tvLabelAd)
                assertEquals(activity.getString(R.string.ad_label), label.text.toString())
            }
        }
    }

    @Test
    fun splashAct_titleLabel_usesAppNameStringResource() {
        ActivityScenario.launch(SplashAct::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val title = activity.findViewById<TextView>(R.id.appTitle)
                assertEquals(activity.getString(R.string.app_name), title.text.toString())
            }
        }
    }
}
