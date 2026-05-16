package com.samsunggalaxy.feature.vip

import android.util.Base64

/**
 * VIP key whitelist + secret shared với `AdSdkConfig.vipKeySecret`.
 *
 * Plain keys được Base64-encode để tránh hiện thẳng trong decompiled APK.
 * Base64 KHÔNG phải encrypt — chỉ là speed-bump cho casual reverse engineering.
 *
 * Lib design: `AdManager.activateVipByKey(context, key, days)` validate `key` vs
 * `AdSdkConfig.vipKeySecret`. App-side dùng `lookupDays(input)` để map plain key
 * → số ngày, sau đó luôn gọi `activateVipByKey(context, VIP_SECRET, days)`.
 */
object VipKeys {
    // Base64 của plain key 30-ngày (xem Section 0 của doc/AD_PROMPT_AOS.MD ở lib reference)
    private const val VIP_30D_B64 = "OWZBMHE3ZU4hMjdjTHgwNEAyMTk5M1kydTBJNyNRMA=="

    // Base64 của plain key 3-ngày
    private const val VIP_3D_B64 = "ZVE3QDkzTDBmITJZMjcwN3hOMDQwMjE5OTN1MEkjMmFL"

    val VIP_30D_KEY: String by lazy {
        String(Base64.decode(VIP_30D_B64, Base64.NO_WRAP))
    }

    val VIP_3D_KEY: String by lazy {
        String(Base64.decode(VIP_3D_B64, Base64.NO_WRAP))
    }

    /** Single secret cho `AdSdkConfig.vipKeySecret` — dùng 30-day key. */
    val VIP_SECRET: String get() = VIP_30D_KEY

    private val KEY_TO_DAYS: Map<String, Int> by lazy {
        mapOf(
            VIP_30D_KEY to 30,
            VIP_3D_KEY to 3,
        )
    }

    /** Trả số ngày nếu key hợp lệ, hoặc null. Auto trim trước khi lookup. */
    fun lookupDays(rawInput: String): Int? = KEY_TO_DAYS[rawInput.trim()]
}
