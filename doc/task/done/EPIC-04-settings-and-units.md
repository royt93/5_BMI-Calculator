# EPIC-04 — Settings screen + Unit system kg⇄lbs ⚙️ (P1, 13 pts)

> **Status (2026-08-23): T04.1–T04.4 ĐÃ XONG + có test. Điểm audit 9.5/10, đã push.**
> `UnitFormatter`/`ThemeHelper` (utils, pure) + Settings screen (unit toggle Metric/Imperial, theme System/Light/Dark, Clear History). `MainAct` wheel picker chuyển đổi đơn vị (metric-first render, rebuild khi đổi unit ở Settings), nội bộ luôn lưu metric. `ResultAct`/`HistoryActivity` (Dashboard)/`TrackerBottomSheet`/4 calculator hiển thị + nhận input theo unit hiện tại. **Giới hạn đã ghi nhận**: goal-weight card (`goal_weight_current/target/remaining` template dịch sẵn có "kg" nhúng cứng trong 17 locale, và `suffixText="kg"` của dialog set-goal) vẫn chỉ metric — cần 1 epic riêng để làm imperial-variant cho các template đó.
>
> **Audit pass** (`/code-review high`): 4 finding — 2 bug thật đã fix (wheel imperial weight range hụt mất 151kg≈333lbs do hardcode "2..330", giờ tự tính từ metric bound; theme áp dụng async trong `GalaxyApp` gây flash+recreate SplashAct lúc cold start, giờ dùng `runBlocking` đọc đồng bộ trước khi Activity đầu tiên tạo — an toàn vì chạy trước khi có frame nào render), 1 nit nhỏ (fallback `"metric"` hardcode → dùng `UnitFormatter.METRIC`), 1 gap i18n (12 string mới đã dịch đủ 14 locale còn thiếu).
>
> Test: unit test cho `UnitFormatter`/`ThemeHelper`/wheel-label generation (bao gồm regression test cho bug range đã fix) + instrumented test cho Settings toggle/clear-history/Dashboard hiển thị theo unit — 29/29 pass trên `Pixel_10_Pro_XL(AVD)`.

## Hiện trạng (Explore agent xác nhận)
`SettingsActivity.kt` chỉ có **1/6** setting đã khai báo trong `PreferencesManager.kt`: language. 5 pref còn lại là "dead scaffolding":
| Key | Trạng thái |
|---|---|
| `THEME_MODE` | không đọc/ghi ở đâu ngoài file khai báo |
| `UNIT_SYSTEM` | chết hoàn toàn — mọi weight/height hardcode "kg"/"cm" (`TrackerBottomSheet.kt:90`, `ResultAct.kt:517`, `IdealWeightCalculatorActivity.kt:40`, wheel picker `MainAct.kt:314-365` range 1-151 chỉ hiểu kg) |
| `LANGUAGE` | không dùng — language thật sự đi qua `LocaleHelper`/`IS_LANGUAGE_SELECTED` |
| `CURRENT_PROFILE_ID` | chết — repo đọc cờ `isCurrent` trong Room, không đọc key này |
| `ACTIVITY_LEVEL` | chết — `TdeeCalculatorActivity.kt:51` tự giữ local state, không ghi lại |

## T04.1 — Unit system kg⇄lbs, cm⇄inches (P0 trong epic, 8 pts)
**Scope**: toggle Metric/Imperial trong Settings → áp dụng xuyên suốt: MainAct wheel picker, ResultAct kết quả + goal card, TrackerBottomSheet chart trục Y + label, 4 calculator activities, HistoryActivity list.
**Rủi ro kỹ thuật**: `BmiRecord`/`Profile` lưu **metric only** trong DB (đúng, không đổi) — chỉ convert ở lớp hiển thị (`CalculatorUtils` đã có sẵn hàm convert kg/lbs, cm/inches theo `doc/FEATURES_ADDED.md` — tái sử dụng, không viết lại).
**AC**: đổi toggle → mọi màn hình hiện lbs/inches ngay, không cần restart app (trừ khi quyết định cần restart giống đổi ngôn ngữ — cân nhắc dùng `LiveData`/Flow từ `PreferencesManager.unitSystem` thay vì restart).
**Cảnh báo UX**: MainAct wheel picker hiện range cứng 1-151 (kg) — đổi sang lbs cần range khác (vd. 2-330 lbs), không phải chỉ đổi label.

## T04.2 — Theme toggle Light/Dark/System (P1, 2 pts)
App đã có đầy đủ `res/layout-night/` + `res/drawable-night/` (theo CLAUDE.md) nhưng theo Android system setting mặc định — chưa có UI override trong app. Thêm 3-way toggle trong Settings, dùng `AppCompatDelegate.setDefaultNightMode()` + lưu `THEME_MODE`.

## T04.3 — Activity level mặc định (P2, 1 pt)
Lưu lựa chọn activity level cuối cùng của user (từ TdeeCalculator) vào `PreferencesManager.activityLevel`, dùng làm giá trị mặc định lần sau thay vì luôn reset — giảm friction nhập lại.

## T04.4 — Data management trong Settings (P2, 2 pts)
Thêm mục "Clear history" (xoá toàn bộ `BmiRecord` của profile hiện tại, có dialog xác nhận) — hiện chỉ xoá được từng record qua swipe trong HistoryActivity, không có "xoá tất cả" nhanh.
