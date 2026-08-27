package com.samsunggalaxy.feature.vip

import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.NoMatchingViewException
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.closeSoftKeyboard
import androidx.test.espresso.action.ViewActions.replaceText
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.roy.sdkadbmob.AdManager
import com.samsunggalaxy.R
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * `VipActivity`/`FVipManagement` redeem flow qua UI thật — không dùng seam nội bộ SDK
 * (`@InternalAdApi`, chỉ dành cho test suite của chính SDK, không phải API public cho consumer
 * app). Case verify được từ ngoài mà không cần seam đó: token rác → reject an toàn, không crash,
 * không cấp VIP — vẫn là đúng loại lỗi (gọi sai/không xử lý đúng kết quả `activateVipByToken`)
 * mà round review độc lập từng tìm thấy ở logic này.
 *
 * Nhánh `isVipTokenPending`/"no effect" (xem `FVipManagement.handleRedeemClicked`) cần race
 * SDK-internal (token nộp đúng lúc `initialize()` chưa xong) không dựng lại đáng tin cậy được từ
 * UI test — verify thủ công trên device thay vào đó (xem `doc/AD.MD`).
 *
 * Needs a connected device/emulator: ./gradlew connectedDevDebugAndroidTest
 */
@RunWith(AndroidJUnit4::class)
class FVipManagementRedeemInstrumentedTest {

    @Before
    fun setUp() {
        AdManager.clearVipByKey()
    }

    @After
    fun tearDown() {
        AdManager.clearVipByKey()
    }

    @Test
    fun garbageToken_rejectedSafely_noVipGranted() {
        ActivityScenario.launch(VipActivity::class.java).use {
            onView(withId(R.id.etRedeem)).perform(replaceText("not-a-real-token"), closeSoftKeyboard())
            onView(withId(R.id.btnRedeem)).perform(click())
            // activateVipByToken() chạy trên background thread (FVipManagement.kt) — Espresso không
            // track được raw Thread, nên poll thay vì fixed Thread.sleep (independent review: fixed
            // sleep fail oan trên máy/CI chậm, hoặc lãng phí thời gian trên máy nhanh). Assert đúng
            // title "Thất bại", không chỉ nút OK — dialog "Đang xử lý…" cũng có nút OK, garbage
            // token không bao giờ đi qua nhánh queued nhưng assert chặt hơn để rõ ràng.
            waitForViewWithText(R.string.vip_failed_title, timeoutMs = 10_000L)
            onView(withText(R.string.ok)).perform(click()) // đóng dialog "Thất bại"
        }
        assertFalse(AdManager.isVipByKeyActive())
    }

    /** Poll cho tới khi view có text [textRes] hiện trên màn hình, hoặc hết [timeoutMs]. */
    private fun waitForViewWithText(textRes: Int, timeoutMs: Long, pollMs: Long = 200L) {
        val deadline = System.currentTimeMillis() + timeoutMs
        var lastError: Throwable? = null
        while (System.currentTimeMillis() < deadline) {
            try {
                onView(withText(textRes)).check(matches(isDisplayed()))
                return
            } catch (e: NoMatchingViewException) {
                lastError = e
                Thread.sleep(pollMs)
            } catch (e: AssertionError) {
                lastError = e
                Thread.sleep(pollMs)
            }
        }
        throw lastError ?: AssertionError("Timed out waiting for view with text $textRes")
    }
}
