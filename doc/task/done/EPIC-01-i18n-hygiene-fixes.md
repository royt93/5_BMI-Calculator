# EPIC-01 — i18n & code hygiene fixes 🐛 (P1, 5 pts)

> **Status (2026-08-25): T01.1–T01.4 ĐÃ XONG (T01.1 hoá ra đã fix từ EPIC-00) + 5 finding mới, có test, đã push.**
> User yêu cầu audit lại toàn bộ multi-language sau khi nghi ngờ "sót quá nhiều". Audit lại: key parity 17 locale đã ổn từ trước (chỉ thiếu đúng `_21_2` — số vô hại, chính là T01.3), nhưng quét sâu layout XML + Kotlin phát hiện **5 chỗ hardcode literal tiếng Anh chưa từng có trong doc gốc**:
> - `item_age_picker.xml` — label "Age" hardcode dù `@string/age` đã tồn tại + dịch đủ 17 locale từ lâu, layout này chỉ quên dùng.
> - `layout_ad_banner.xml` — nhãn "Ad" hardcode, hiện mọi lúc banner quảng cáo xuất hiện (MainAct + ResultAct) → string mới `ad_label`.
> - `activity_splash.xml` — text "BMI Calculator" hardcode thay vì `@string/app_name` (splash luôn tiếng Anh dù app_name đã dịch riêng từng locale).
> - `ResultAct.kt` — 2 Toast lỗi ("Error occurred!", "Share failed: ...") hardcode → string mới `error_occurred`, `share_failed`.
>
> **T01.1** (BMI category text) — xác nhận đã fix từ EPIC-00 T00.3, không cần làm lại.
> **T01.2** (Toast "Please click BACK again to exit") — string mới `press_back_again_to_exit`.
> **T01.3** (orphan `_21_2`) — xoá khỏi 17 locale, `a_result.xml`'s `tvResult` chuyển sang `tools:text="21.2"` (placeholder chỉ hiện trong Android Studio preview, không đóng gói vào APK).
> **T01.4** (VIP raw-URL toast) — `FVipManagement.openPrivacyPolicy()`'s fallback khi không có browser giờ hiện thông báo lỗi đã dịch (`could_not_open_link`) thay vì raw URL.
>
> **Audit pass** (`/code-review high`): 2 finding, cả 2 đều liên quan tới hệ quả phụ của việc đổi text trong layout —
> 1. **Splash title có thể wrap 2 dòng**: `@string/app_name` cho flavor dev/production dài hơn literal cũ ("BMI Calculator 2026 DEV" / "BMI Calculator 2026" so với "BMI Calculator"), `appTitle` TextView 32sp bold không có `maxLines`/`gravity` — trên màn hình hẹp có thể wrap không đẹp. Fix: thêm `android:gravity="center"` + `android:maxLines="2"`. Đối chiếu: `a_main.xml`'s toolbar title đã dùng cùng `@string/app_name` dài này từ trước, không phát sinh vấn đề — pattern đã được chấp nhận trong app.
> 2. **`tools:text` mất "fail-safe" hiển thị placeholder khi `tvResult` chưa kịp set** — xác nhận không phải regression thật: `bmiCal()`'s guard (`height > 0 && weight > 0`) luôn đúng ở caller hiện tại (`MainAct`), và giả sử guard fail trong tương lai, hiện trống còn trung thực hơn hiện "21.2" giả (một số bịa đặt trông như kết quả thật) — giữ nguyên fix, không revert.
>
> Test: `I18nHygieneTest` mới (3 test instrumented: age label/ad banner label/splash title đều lấy đúng từ string resource, không còn literal) — 49/49 instrumented + toàn bộ unit test suite pass trên `Pixel_10_Pro_XL(AVD)`. Smoke test tay: chuyển ngôn ngữ app sang Tiếng Việt trên device thật — xác nhận splash title ("Máy Tính BMI"), toolbar, và nhãn "TUỔI" (Age) đều đổi đúng theo locale.

## T01.1 — BMI category text không localize (P0 trong epic này)
**File**: `ui/ResultAct.kt:494-501` (`showResult()`)
**Vấn đề**: 4 chuỗi kết quả BMI hardcode tiếng Anh literal (`"You are Under Weight"`, `"You are Healthy"`, `"You are Overweight"`, `"You are Suffering from Obesity"`) thay vì `getString()`. App có 17 locale nhưng màn hình quan trọng nhất (kết quả BMI) không dịch được.
**Đối chiếu**: `HistoryActivity.kt:254-257` và `ResultAct.kt:298-306` (dialog) đã dùng đúng string res `bmi_category_healthy`,... → chỉ cần tái sử dụng cùng key ở `showResult()`.
**AC**: đổi locale VI/EN/JA... → text kết quả BMI đổi theo. Không còn literal string tiếng Anh trong hàm.
**Points**: 2

## T01.2 — Toast "Please click BACK again to exit" hardcode
**File**: `ui/MainAct.kt:113`
**Fix**: thêm string res (đã có sẵn key tương tự pattern trong project?, nếu chưa thì thêm `toast_press_back_exit` vào **toàn bộ 17 locale**).
**Points**: 1

## T01.3 — Orphaned string resource `_21_2`
**File**: `values/strings.xml:9`, dùng ở `res/layout/a_result.xml:93` (`android:text="@string/_21_2"`)
**Vấn đề**: tên resource là số ("21.2") — rõ ràng debug/placeholder cruft còn sót, không phải chuỗi có nghĩa để dịch. Là lý do duy nhất khiến check "mọi locale phải đủ key" báo thiếu (locale nào cũng thiếu đúng key này).
**Fix**: xác nhận layout có cần placeholder tĩnh không; nếu không, xoá resource + reference. Nếu cần, đổi tên có nghĩa (vd. `sample_bmi_value`) và thêm vào layout của tương lai không phụ thuộc string res số.
**Points**: 1

## T01.4 — FVipManagement.kt:429 toast lộ raw debug URL
**File**: `feature/vip/FVipManagement.kt:429`
**Vấn đề**: Explore agent flag đây là toast hiện raw URL — nghi là code debug sót lại trong flow VIP purchase feedback. Cần xác minh có phải debug-only hay chạy cả production.
**AC**: nếu là debug leftover → bọc `if (BuildConfig.DEBUG)` hoặc xoá; nếu là feature thật (vd. hiện link privacy policy) → đổi thành dialog/CustomTabs thay vì Toast raw URL.
**Points**: 1
