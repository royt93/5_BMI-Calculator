package com.samsunggalaxy.feature.vip

import android.util.Base64

/**
 * VIP secret shared với `AdSdkConfig.vipKeySecret` — dùng CHỐNG-TAMPER SharedPreferences
 * (đối xứng, cùng máy). KHÔNG còn dùng để verify redeem key (lib 1.6.21 dùng token ECDSA qua
 * `AdManager.activateVipByToken`, xem `AdSdkConfig.vipTokenPublicKey` ở `GalaxyApp`).
 *
 * Base64-encode để tránh hiện thẳng trong decompiled APK — KHÔNG phải encrypt, chỉ speed-bump.
 */
object VipKeys {
    private const val VIP_SECRET_B64 = "OWZBMHE3ZU4hMjdjTHgwNEAyMTk5M1kydTBJNyNRMA=="

    val VIP_SECRET: String by lazy {
        String(Base64.decode(VIP_SECRET_B64, Base64.NO_WRAP))
    }
}
