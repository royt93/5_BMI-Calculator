package com.samsunggalaxy.feature.vip

import android.content.Context

/**
 * Persist `grantedAt` + user-redeemed flag riêng cho VIP screen.
 *
 * Lib AdmobApplovinWrapper 1.1.3 chỉ persist `vipByKeyUntil` (expiry), không persist
 * `grantedAt`. Để vẽ progress bar elapsed-semantic (Step 10.3 row #4) cần cả 2.
 *
 * Khi lib bổ sung `AdManager.getVipByKeyGrantedAt()` → xoá class này.
 *
 * NOTE: KHÔNG lưu flag `first_install_grace_granted` ở đây — SDK 1.1.3 đã built-in
 * grace logic (`AdManager.kt:499-543`). Spec Step 9.1 cấm flag app-side này.
 */
class VipPrefs(context: Context) {
    private val sp = context.applicationContext
        .getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    fun saveGrantedAtMs(ms: Long) {
        sp.edit().putLong(KEY_GRANTED_AT, ms).apply()
    }

    fun getGrantedAtMs(): Long = sp.getLong(KEY_GRANTED_AT, 0L)

    fun clearGrantedAtMs() {
        sp.edit().remove(KEY_GRANTED_AT).apply()
    }

    fun markUserRedeemed() {
        sp.edit().putBoolean(KEY_USER_REDEEMED, true).apply()
    }

    fun userRedeemedAtLeastOnce(): Boolean = sp.getBoolean(KEY_USER_REDEEMED, false)

    companion object {
        private const val PREF_NAME = "vip_screen_prefs"
        private const val KEY_GRANTED_AT = "granted_at_ms"
        private const val KEY_USER_REDEEMED = "user_redeemed_once"
    }
}
