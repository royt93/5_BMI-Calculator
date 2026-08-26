package com.samsunggalaxy.feature.vip

import android.content.Context

/**
 * Persist flag "user đã tự redeem/earn VIP ít nhất 1 lần" — dùng phân biệt grace entry
 * (first-install auto-trial) với VIP do user chủ động kích hoạt (Step 9.3).
 *
 * `grantedAtMs` KHÔNG còn persist ở đây (SDK 1.6.21 expose `AdManager.getVipGrantedAtMs()`,
 * tự lưu riêng sẽ lệch khi VIP được cấp qua đường khác — xem `AD_PROMPT_AOS.MD:1726`).
 */
class VipPrefs(context: Context) {
    private val sp = context.applicationContext
        .getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    fun markUserRedeemed() {
        sp.edit().putBoolean(KEY_USER_REDEEMED, true).apply()
    }

    fun userRedeemedAtLeastOnce(): Boolean = sp.getBoolean(KEY_USER_REDEEMED, false)

    companion object {
        private const val PREF_NAME = "vip_screen_prefs"
        private const val KEY_USER_REDEEMED = "user_redeemed_once"
    }
}
