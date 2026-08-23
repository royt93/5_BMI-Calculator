# EPIC-00 — Critical correctness bugs 🐛 (P0, 10 pts)

> **Status (2026-08-23): T00.1–T00.5 ĐÃ FIX + có test.** T00.6 (doc sync) chờ EPIC-03/07 xong. Chi tiết implementation: `CalculatorUtils.kt` (getBMICategoryInfo, calculateGoalProgress, gender-code overloads), `ResultAct.kt`, `MainAct.kt`, `StreakManager.kt`, `TdeeCalculatorActivity.kt`, `BmiDao.kt`/`BmiRepository.kt` (getFirstRecordWeight, getMostRecentRecord). Test: 22 unit test (`app/src/test/.../CalculatorUtilsTest.kt`, `StreakManagerLogicTest.kt`) + 7 instrumented test (`app/src/androidTest/.../BmiDaoTest.kt`, `ResultActGoalCardTest.kt`, `ResultActGenderPersistenceTest.kt`) — tất cả pass trên `Pixel_10_Pro_XL(AVD)`.

> Phát hiện qua second-opinion: 3 AI Agent độc lập (codex, gemini/agy, claude) review codebase riêng biệt, không đọc chéo nhau trước. Các bug dưới đây được **≥2/3 agent phát hiện độc lập** (đã tôi tự đọc code xác nhận lại từng dòng), hoặc chỉ 1 agent phát hiện nhưng nghiêm trọng nên vẫn liệt kê (đánh dấu rõ). File review gốc: `doc/task/review_codex.md`, `review_gemini.md`, `review_claude.md`.
> Ưu tiên P0 vì đây là bug **hiển thị sai dữ liệu sức khỏe cho user** — nghiêm trọng hơn mọi task enhance/new feature khác, và EPIC-07 (Weight Dashboard) sẽ build trên đúng những chỗ này nên cần fix trước.

## T00.1 — Goal weight "đạt mục tiêu" sai khi mục tiêu là TĂNG cân ✅ CONFIRMED (2/3 agent + tự verify)
**File**: `ui/ResultAct.kt:269-279` (`updateGoalUI`)
```kotlin
val diff = weight - goalWeight
if (diff <= 0) { progressBar.progress = 100; "Achieved" }
```
Code giả định mục tiêu luôn là **giảm cân** (`goalWeight < weight`). Nếu user đặt mục tiêu cao hơn cân nặng hiện tại (mục tiêu tăng cân), `diff` âm ngay từ đầu → hiện "Đã đạt mục tiêu 100%" ngay ngày đầu tiên dù chưa tăng gram nào.
**Đối chiếu**: `BadgeManager.kt:64-66` xử lý đúng cả 2 chiều (`abs(currentWeight-goalWeight)<=1.0`) — 2 nơi tính "đạt mục tiêu" khác công thức, không đồng bộ.
**Fix**: tính theo hướng mục tiêu thật (so `goalWeight` với cân nặng ban đầu lúc đặt goal để biết đang giảm hay tăng), dùng `abs(diff)` cho progress, không giả định 1 chiều.
**Points**: 3

## T00.2 — TDEE lưu vào DB và hiển thị luôn giả định "Sedentary" (activity=0) ✅ CONFIRMED (1 agent phát hiện, tự verify nghiêm trọng hơn báo cáo)
**File**: `ui/ResultAct.kt:508` (hiển thị) **và** `ui/ResultAct.kt:527` (lưu vào `BmiRecord`)
```kotlin
val tdee = CalculatorUtils.calculateTDEE(bmr, 0)  // 0 = Sedentary, hardcode
```
Không chỉ hiển thị sai — **giá trị TDEE lưu vào lịch sử cũng luôn tính theo mức vận động thấp nhất**, bất kể user thật sự active thế nào. Đây chính là dead-pref `activity_level` (EPIC-04 T04.3) nhưng hậu quả nặng hơn dự kiến ban đầu: không chỉ là "chưa cá nhân hoá", mà là **dữ liệu sai lưu vĩnh viễn vào Room** cho mọi user không sedentary.
**Fix**: đọc `PreferencesManager.activityLevel` (đã có sẵn, chỉ chưa có UI ghi — xem T04.3) làm tham số thay vì hardcode `0`; cần làm **trước hoặc cùng lúc** T04.3 để T04.3 có tác dụng thật.
**Points**: 2

## T00.3 — Ngưỡng phân loại BMI không nhất quán trong CÙNG 1 màn hình ✅ CONFIRMED (1 agent phát hiện, tự verify)
**File**: `ui/ResultAct.kt`
- `showResult()` dòng 493-500: healthy = `< 24.9`
- `setupHealthTips()` dòng 164-169: healthy = `< 25.0`
- `showGoalDialog()` dòng 299-303 và text-watcher preview dòng 322-325: healthy = `< 25.0`
→ Với BMI = 24.95: dòng kết quả chính hiện **"Overweight"**, nhưng health tips + goal dialog bên dưới cùng màn hình lại xếp **"Healthy"**. User thấy 2 kết luận trái ngược trên cùng 1 màn hình.
**Nguyên nhân gốc**: `CalculatorUtils.getBMICategory()` (đã viết sẵn, đúng chuẩn WHO) **không có nơi nào gọi tới** (grep xác nhận 0 caller) — mỗi chỗ tự viết lại `when`/`if` ngưỡng riêng, dễ lệch nhau như trên.
**Fix**: xoá hết bản `when`/`if` rải rác, gọi thống nhất `CalculatorUtils.getBMICategory()` ở mọi nơi (showResult, setupHealthTips, showGoalDialog, HistoryActivity, TrackerBottomSheet). Dọn luôn 1 lần khi làm T01.1 (i18n hoá showResult) vì đụng cùng chỗ code.
**Points**: 2

## T00.4 — Streak card hiện số cũ (stale) trước khi user thao tác lại ⚠️ REPORTED (1/3 agent, tự verify: đúng nhưng mức độ nhẹ hơn báo cáo ban đầu)
**File**: `ui/StreakManager.kt:39-46` (`getStreakData`)
Tự verify: `getStreakData()` chỉ đọc thẳng giá trị đã lưu, không so `lastDate` với hôm nay/hôm qua để phát hiện streak đã "gãy". `recordCheck()` (dòng 14-37) thì tính đúng — khi user **thực sự tính BMI lại**, streak reset về 1 đúng như thiết kế. Vấn đề chỉ là: nếu user mở app nhưng **chưa** tính BMI (chỉ xem Main screen), card vẫn hiện streak cũ dù thực tế đã gãy từ lâu — sai lệch hiển thị tạm thời, không sai lệch dữ liệu lưu trữ.
**Fix**: `MainAct` khi hiện streak card, tự so `lastDate` với hôm nay/hôm qua (không sửa `StreakManager`, chỉ thêm hàm "peek trạng thái hiển thị" ở tầng UI) để hiện đúng "đã gãy" ngay cả trước khi user tính BMI lại.
**Points**: 1

## T00.5 — Gender "Other" bị xử lý như Female ✅ FIXED (đã verify đúng như codex báo cáo)
`MainAct.kt` (`titlesOfGender = ["F","O","M"]`) chỉ map `'M'`→0, còn lại (kể cả `'O'`) rơi vào `else`→1 (Female) — verify xác nhận đúng 100%. Đã fix: `MainAct.kt` map `'O'`→2 riêng; `CalculatorUtils.calculateBMR`/`calculateIdealWeightRange` có thêm overload nhận `genderCode: Int` (0/1/2), "Other" dùng công thức trung bình M/F thay vì mặc định về Female. Chỉ áp dụng cho luồng MainAct→ResultAct (4 calculator độc lập chỉ có radio M/F, không có "Other" nên không bị ảnh hưởng).

## T00.5b — Còn lại chưa làm trong pass này (verify khi implement epic liên quan)
Liệt kê để không bỏ sót — chưa confirmed đầy đủ, verify lại khi động vào file liên quan:
- **Share ảnh rò rỉ file vào Gallery vĩnh viễn** (codex, `FileExt.kt` `saveBitmap` không cleanup sau khi share xong) — verify khi làm T02.2 (FileProvider path).
- **Body Fat Navy method: input không hợp lệ (neck ≥ waist) hiện ra `0.0%`** thay vì báo lỗi rõ ràng (codex, `CalculatorUtils.kt:96-102` + `BodyFatCalculatorActivity.kt:48-54`) — đây là hệ quả phụ của fix cũ trong `doc/BUGS_FIXED.md` #5 (chỉ chặn crash, chưa xử lý UX báo lỗi).
- **Weight picker giới hạn cứng 151kg** loại trừ user béo phì nặng (gemini, `MainAct.kt:329` `getData(151)`) — nên gộp sửa cùng lúc với T04.1 (unit system) vì đụng cùng logic wheel picker.
- **Magic number index 15 = tuổi mặc định 25** (gemini, `MainAct.kt:374`) — đổi sang tính từ hằng số `DEFAULT_AGE` thay vì index cứng, tránh vỡ âm thầm nếu đổi mảng tuổi sau này.

## T00.6 — Dọn nợ tài liệu (doc hygiene, không phải code)
`CLAUDE.md` hiện mô tả "Reward Ad on ResultAct 'Get Detailed Plan' button" như tính năng **đang hoạt động** (claude agent phát hiện) — thực tế đang là code chết (xem EPIC-03). Cập nhật `CLAUDE.md` sau khi EPIC-03 chốt hướng, tránh session Claude Code tương lai bị hiểu nhầm tính năng đã có.
**Points**: kèm theo EPIC-03, không tính điểm riêng.
