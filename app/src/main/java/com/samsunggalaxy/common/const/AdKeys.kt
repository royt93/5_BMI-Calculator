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

    /**
     * Hash test-device AdMob (Bước 3b, `AdManager.setTestDeviceIds()`) — KHÔNG PHẢI GAID, khác
     * hoàn toàn với GAID (xem cảnh báo trong doc/AD_PROMPT_AOS.MD). Android ID (nguồn của hash
     * này) scope theo signing-key + app từ Android 8+ — hash của app KHÁC trên cùng máy KHÔNG
     * dùng lại được ở đây. Source-of-truth đầy đủ (kèm GAID, model, ngày thu thập):
     * @mckimquyen/myKeyStore/com.samsunggalaxy.bmicalculator/test_device_ids.md
     *
     * ⚠️ 11 hash bên dưới (trừ SM-A115F) là COPY nguyên từ hash của app KHÁC
     * (`com.mckimquyen.notes`, cùng fleet máy) theo yêu cầu tường minh của user 2026-08-26,
     * CHƯA verify sống cho bmicalculator — biết trước có thể sai/không có tác dụng (chính README
     * gốc mô tả đúng lỗi này từng xảy ra thật: hash không transfer giữa app khác nhau). Bằng chứng
     * ngay trong file này: dòng SM-A115F verify-thật (`43A93B95...`) ≠ dòng SM-A115F copy-từ-notes
     * bị comment out bên dưới (`EB7B65...`) — CÙNG 1 máy, 2 app khác nhau, hash khác hẳn.
     * Set không hại gì nếu sai (chỉ là whitelist rỗng tác dụng, không mất an toàn) — nhưng đừng tin
     * là "đã đăng ký" cho tới khi verify sống từng dòng (cắm máy, chạy app, xem log đổi thành
     * "This request is sent from a test device.", xoá dòng `// CHƯA VERIFY` tương ứng).
     *
     * QUYẾT ĐỊNH (2026-08-27): giữ nguyên nguyên trạng có chủ ý, KHÔNG phải bỏ sót — hash sai
     * fail-safe (whitelist rỗng tác dụng, không mở lỗ hổng), risk thật chỉ phát sinh nếu QA dùng
     * đúng 1 trong 11 máy chưa verify để test ad thật. Reviewer khác đọc code: đây là rủi ro đã
     * được cân nhắc, không cần block review vì lý do này.
     */
    val QA_TEST_DEVICE_HASHES: Array<String> = arrayOf(
        "43A93B959FECD421A4914A04F7565808", // Samsung SM-A115F — VERIFY THẬT 2026-08-26 cho bmicalculator
        // "EB7B6504801B5E518C4CE6D519ED325C", // ← hash CÙNG máy SM-A115F nhưng của app "notes" — SAI cho app này, không dùng
        "813DCF48B3E486F15A60676D49A2AB09", // Samsung SM-A507FN (A50s) — CHƯA VERIFY, copy từ app "notes"
        "E165942547A491D06E43E24870B990B2", // OPPO CPH1989 (Reno2) — CHƯA VERIFY, copy từ app "notes"
        "C3632968623F0B44E87CE401A06AC8F9", // TCL 9032X — CHƯA VERIFY, copy từ app "notes"
        "4A2AA8832A7FE9D7805081AD03C9CE68", // Xiaomi 23028RN4DG — CHƯA VERIFY, copy từ app "notes"
        "DEE1D0C6AEA4CA5C94FA4D709087A3AC", // vivo V2352A — CHƯA VERIFY, copy từ app "notes"
        "5D2E85389997C743F9CC33DF5F70D736", // ZTE Blade A52 — CHƯA VERIFY, copy từ app "notes"
        "FED3CA82141FF6113F2D069F8395B966", // Samsung SM-A507FN (unit 2) — CHƯA VERIFY, copy từ app "notes"
        "96E61CBFCE6BC0BDCA1612F1BACB56BE", // OPPO CPH1989 (unit 2) — CHƯA VERIFY, copy từ app "notes"
        "5B409111AF01C6BB9F9FF77AEEB44275", // TECNO BG6 — CHƯA VERIFY, copy từ app "notes"
        "D1B50484E250B064A9BF6F7CAE29A941", // Samsung SM-S928B — CHƯA VERIFY, copy từ app "notes"
        "322285166ACB542864828826D2D92491", // Google Pixel 7 Pro — CHƯA VERIFY, copy từ app "notes"
    )
}
