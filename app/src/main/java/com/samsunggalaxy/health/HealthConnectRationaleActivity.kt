package com.samsunggalaxy.health

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import com.samsunggalaxy.common.const.AdKeys

/**
 * EPIC-09 T09.2 — Health Connect's own permission screen (system PermissionsActivity on
 * Android 14+, standalone HC app on older OS) requires the requesting app to declare a
 * "rationale" entry point before it will show the grant UI at all. Without one, it logs
 * "App should support rationale intent, finishing!" and returns an EMPTY granted set
 * immediately — no UI, no user interaction, permission request effectively always denied.
 * This activity IS that entry point (see the two manifest declarations pointing at it): it has
 * no UI of its own, just hands off to the existing privacy policy URL, since that already
 * covers what data this app reads/writes via Health Connect.
 */
class HealthConnectRationaleActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(AdKeys.PRIVACY_POLICY_URL)))
        } catch (e: Exception) {
            // No browser available — nothing sane to show instead, just don't crash.
        }
        finish()
    }
}
