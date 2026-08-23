# EPIC-09 — Platform extensions ✨ (P3, 21 pts)

> Backlog dài hạn — không làm trước khi EPIC-04/05/07 ổn định, vì đều phụ thuộc dữ liệu/profile/unit-system đã đúng.

## T09.1 — Home-screen Widget (8 pts)
App Widget hiện cân nặng/BMI mới nhất + sparkline mini xu hướng 7 ngày. Cần `AppWidgetProvider` + layout riêng + xử lý dark/light widget theme (Android 12+ dynamic).
**Rủi ro**: widget update cần đọc Room từ process riêng — cẩn thận không block main thread, dùng `WorkManager`/`glance` (Jetpack Glance nếu muốn Compose-based widget, thêm dependency).

## T09.2 — Health Connect integration (13 pts)
Đồng bộ cân nặng 2 chiều với Health Connect (API hợp nhất Google Fit/Samsung Health trên Android). Đây là hạng mục có thể làm app **khác biệt thật sự** so với các BMI calculator đơn giản khác trên Play Store.
**Rủi ro kỹ thuật cao**: cần `minSdk` xử lý fallback (app hiện `minSdk=24`, Health Connect cần app riêng cài trên máy hoặc built-in Android 14+) + quyền runtime phức tạp + conflict resolution khi data 2 chiều lệch nhau (app ghi 70kg, Health Connect ghi 71kg cùng ngày — nguồn nào thắng?).
**Khuyến nghị**: làm sau khi EPIC-07 (Weight Dashboard) đã ổn định là nguồn dữ liệu chính, tránh vừa xây dashboard vừa xử lý sync conflict cùng lúc.
