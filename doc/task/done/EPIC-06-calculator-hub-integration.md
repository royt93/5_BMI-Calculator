# EPIC-06 — Calculator Hub integration ⚙️ (P2, 8 pts)

> **Status (2026-08-23): T06.1–T06.3 ĐÃ XONG + có test, đã push.**
> Cả 4 calculator (`BmrCalculatorActivity`/`TdeeCalculatorActivity`/`IdealWeightCalculatorActivity`/`BodyFatCalculatorActivity`) giờ prefill từ `BmiRepository.getCurrentProfileMostRecentRecord()` (helper mới, dùng chung để tránh lặp code 4 nơi), vẫn cho sửa tay. `BodyFatCalculatorActivity` có nút "Save to History" — nếu profile hiện tại có record cùng ngày hôm nay thì update `bodyFatPercentage` vào record đó (`BmiDao.updateBodyFatPercentage`, trả về số dòng bị ảnh hưởng), ngược lại báo "hãy ghi cân nặng hôm nay trước". `ResultAct`'s insight card (BMR/TDEE/Ideal Weight) giờ có thể tap để mở calculator tương ứng đã prefill sẵn (`item_health_insights.xml` — 3 row mới `rowBmr`/`rowTdee`/`rowIdealWeight`, background `selectableItemBackground` + chevron "›").
>
> **Giới hạn đã ghi nhận** (deferred, không trong scope epic này): T06.2 chỉ implement nhánh "update bản ghi cùng ngày" — nhánh "ghi bản ghi mới" (khi user mở Body Fat Calculator mà chưa cân hôm nay) chưa làm, vì cần replicate toàn bộ wizard nhập weight/height/age của `MainAct` — scope creep vượt 8pt của epic này. Gender "Other" (genderCode=2) trên 4 calculator standalone vẫn collapse về Male khi prefill vì UI chỉ có RadioGroup Nam/Nữ, không có lựa chọn thứ 3 — kế thừa design đã xác nhận không phải bug từ audit EPIC-00.
>
> **Audit pass** (`/code-review high`): 10 finding, đã fix 7 bug/rủi ro thật —
> 1. **Locale decimal separator**: prefill dùng `String.format` mặc định locale render "70,5" trên ngôn ngữ dùng dấu phẩy (de/fr/ru/...), nhưng đọc lại bằng `toDoubleOrNull()` chỉ hiểu dấu chấm → validation fail âm thầm dù field trông có giá trị hợp lệ. Fix: `String.format(Locale.US, ...)` ở cả 4 calculator.
> 2. **Sentinel 0.0 bị lưu như kết quả thật**: `CalculatorUtils.calculateBodyFat()` trả `0.0` cho cả "input không hợp lệ" lẫn (lý thuyết) "kết quả đúng bằng 0" — trước fix, nút Save vẫn hiện và ghi thẳng 0% vào lịch sử sức khoẻ. Fix: gate `bodyFat <= 0` → báo lỗi input thay vì cho save.
> 3. **Race điều kiện ở `ResultAct`**: 3 row insight trở nên clickable ngay trong `calculateAndDisplayInsights()`, chạy TRƯỚC `saveToHistory()` — tap quá nhanh mở calculator prefill từ record CŨ thay vì record vừa hiện. Fix: tách `wireInsightRowLinks()`, gọi sau khi `saveToHistory()` xong.
> 4. **`lateinit var repository`** trong `BodyFatCalculatorActivity` vi phạm rule "No lateinit" của CLAUDE.md. Fix: đổi thành `val` local trong `onCreate`, đóng closure bình thường — không cần field.
> 5. **Save không kiểm tra rows-affected**: nếu record bị xoá (qua History) giữa lúc Calculate và lúc bấm Save, `UPDATE` khớp 0 dòng nhưng UI vẫn báo "đã lưu". Fix: `updateBodyFatPercentage` trả `Int` (rows affected), UI phân biệt 2 thông báo.
> 6. **Race double-tap Calculate**: listener của nút Save bị rebind mỗi lần Calculate, 2 tap nhanh có thể khiến IO coroutine hoàn thành sai thứ tự → Save ghi nhầm giá trị của tap trước. Fix: đăng ký listener 1 lần + `calculationToken` để coroutine cũ tự huỷ nếu đã có tap mới hơn.
> 7. **Duplicate code 4 nơi**: logic "current profile → record gần nhất" lặp y hệt ở cả 4 Activity. Fix: gom vào `BmiRepository.getCurrentProfileMostRecentRecord()`.
>
> Không fix (đã ghi chú ở trên là giới hạn có chủ đích): nhánh "ghi bản ghi mới" của T06.2, gender "Other" trên calculator standalone.
>
> Test: 3 unit test mới cho `CalculatorUtils.isSameCalendarDay` (28 tổng trong `CalculatorUtilsTest.kt`) + `CalculatorHubIntegrationTest.kt` mới (5 test: prefill BMR/TDEE/IdealWeight/BodyFat, Body Fat save-to-history end-to-end, cross-link `ResultAct` → `BmrCalculatorActivity` bằng `Instrumentation.ActivityMonitor`) + 2 test mới trong `BmiDaoTest.kt` cho `updateBodyFatPercentage` (rows-affected) — 36/36 instrumented + 28/28 unit test pass trên `Pixel_10_Pro_XL(AVD)`. Smoke test tay xác nhận cả 3 luồng trên device thật: prefill từ MainAct record, Body Fat "Save to History" ghi thành công, tap insight row mở đúng calculator đã prefill.

## Hiện trạng
4 calculator (`BmrCalculatorActivity`, `TdeeCalculatorActivity`, `IdealWeightCalculatorActivity`, `BodyFatCalculatorActivity`) hoàn toàn **one-shot, stateless**: không import `BmiRepository`/`BmiDao`, không đọc profile hiện tại, không lưu kết quả, không liên kết ngược History/ResultAct. User phải gõ lại height/weight/age mỗi lần mở.
Song song đó, `ResultAct.calculateAndDisplayInsights()` (dòng 505-520) **tự tính lại** BMR/TDEE/ideal-weight/water-intake bằng cùng `CalculatorUtils` — 2 luồng code độc lập tính cùng công thức, không có source of truth chung. Body Fat là số duy nhất tính ở calculator riêng nhưng **không bao giờ** lưu vào `BmiRecord.bodyFatPercentage` (luôn `null` — `ResultAct.kt:545`).

## T06.1 — Prefill từ profile hiện tại (3 pts)
Cả 4 calculator đọc `BmiRepository.getCurrentProfile()` + bản ghi BMI gần nhất → prefill height/weight/age mặc định (user vẫn sửa được), giảm friction nhập liệu lặp lại.

## T06.2 — Lưu kết quả Body Fat vào BmiRecord (3 pts)
Khi tính Body Fat từ `BodyFatCalculatorActivity`, cho phép "Lưu vào lịch sử" → ghi `bodyFatPercentage` vào bản ghi mới hoặc update bản ghi gần nhất cùng ngày. Mở khoá được trend body-fat theo thời gian (liên quan N5 EPIC-08).

## T06.3 — Cross-link điều hướng (2 pts)
Từ `ResultAct` insight card (BMR/TDEE/Ideal Weight hiện đang chỉ hiển thị số) → thêm nút "Xem chi tiết" điều hướng sang calculator tương ứng đã prefill sẵn, thay vì 2 nơi hiển thị con số tách biệt không liên kết.
