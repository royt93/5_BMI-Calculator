package com.samsunggalaxy.common.const

import com.samsunggalaxy.BuildConfig
import com.samsunggalaxy.feature.vip.VipKeys

/**
 * Centralize const ad-related cho consumer app.
 * Ad unit IDs đọc trực tiếp từ `BuildConfig.*` ở site sử dụng.
 */
object AdKeys {
    val PRIVACY_POLICY_URL: String get() = BuildConfig.PRIVACY_POLICY_URL

    /** Single VIP secret dùng cho `AdSdkConfig.vipKeySecret`. */
    val VIP_SECRET: String get() = VipKeys.VIP_SECRET
}
