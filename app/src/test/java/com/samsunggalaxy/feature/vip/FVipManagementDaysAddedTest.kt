package com.samsunggalaxy.feature.vip

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Pure-logic unit test cho `FVipManagement.daysAdded` — số ngày token vừa cấp THỰC SỰ cộng thêm,
 * không phải tổng hạn còn lại. Bug gốc (round review độc lập tìm thấy): tính tổng thay vì delta
 * khiến dialog báo sai số ngày khi redeem lúc VIP đang active + token không kéo dài hạn hiện tại
 * (`activateVipByToken` dùng `maxOf` nội bộ, không cộng dồn).
 */
class FVipManagementDaysAddedTest {

    @Test
    fun freshActivation_wholeNumberOfDays_returnsExact() {
        val baseline = 1_000_000_000_000L
        val expiry = baseline + 5 * 86_400_000L
        assertEquals(5, FVipManagement.daysAdded(baseline, expiry))
    }

    @Test
    fun freshActivation_partialDay_roundsUp() {
        val baseline = 1_000_000_000_000L
        val expiry = baseline + 5 * 86_400_000L + 1L // 5 ngày + 1ms
        assertEquals(6, FVipManagement.daysAdded(baseline, expiry))
    }

    @Test
    fun tokenDoesNotExtendExistingLongerVip_returnsZero_notNegative() {
        // maxOf non-stacking: token vừa dùng có hạn NGẮN hơn VIP đang active — baseline (hạn cũ)
        // đứng SAU expiry (hạn mới, giữ nguyên vì maxOf) → delta âm phải kẹp về 0, không phải số âm.
        val expiry = 1_000_000_000_000L
        val baselineAfterExpiry = expiry + 3 * 86_400_000L
        assertEquals(0, FVipManagement.daysAdded(baselineAfterExpiry, expiry))
    }

    @Test
    fun baselineEqualsExpiry_returnsZero() {
        val ts = 1_000_000_000_000L
        assertEquals(0, FVipManagement.daysAdded(ts, ts))
    }

    @Test
    fun latencyDrift_stillRoundsUpToFullDays() {
        // Giữa lúc activateVipByToken() ghi hạn (now + 5d) và UI thread đọc lại (maxOf baseline,
        // now), vài chục ms trôi qua — delta thực tế là "5 ngày trừ 1 khoảng nhỏ", ceil() phải vẫn
        // trả 5, không phải 4 (independent review đề xuất case này).
        val baseline = 1_000_000_000_000L
        val expiry = baseline + 5 * 86_400_000L - 100L
        assertEquals(5, FVipManagement.daysAdded(baseline, expiry))
    }

    @Test
    fun subDayDelta_roundsUpToOne() {
        val baseline = 1_000_000_000_000L
        val expiry = baseline + 3_600_000L // +1 giờ
        assertEquals(1, FVipManagement.daysAdded(baseline, expiry))
    }
}
