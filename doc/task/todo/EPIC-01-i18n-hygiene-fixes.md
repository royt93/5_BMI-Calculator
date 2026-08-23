# EPIC-01 — i18n & code hygiene fixes 🐛 (P1, 5 pts)

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
