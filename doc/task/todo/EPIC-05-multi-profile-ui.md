# EPIC-05 — Multi-profile UI (gia đình) ⚙️ (P1, 13 pts)

## Hiện trạng
`data/Profile.kt` + `data/ProfileDao.kt` + `BmiRepository.setCurrentProfileAtomic()` (transaction-safe, xem CLAUDE.md) **hỗ trợ đầy đủ** multi-profile ở backend. Nhưng grep toàn bộ `ui/*.kt` cho `ProfileDao|insertProfile|getAllProfiles|setCurrentProfile` → **0 kết quả**. `GalaxyApp.initializeDefaultProfile()` chỉ tạo đúng 1 profile "Default" và app không bao giờ tạo thêm. Đây là backend 100%, UI 0% — đúng như `doc/BUGS_FIXED.md` đã note "Future: need UI to create/switch profiles" từ lâu, vẫn chưa làm.

## Vì sao ưu tiên P1 (không phải chỉ nice-to-have)
BMI Calculator dùng theo hộ gia đình rất phổ biến (cha/mẹ/con theo dõi riêng). Không có UI chọn profile → dữ liệu của mọi người trộn chung vào 1 profile "Default", làm hỏng luôn chart/history/badge cá nhân hoá — ảnh hưởng trực tiếp tới chất lượng EPIC-07 (Weight Dashboard) vì chart sẽ lẫn dữ liệu nhiều người.

## T05.1 — Profile switcher entry point (3 pts)
Thêm UI chọn profile (dropdown/avatar chip trên toolbar `MainAct`, tương tự VIP badge đã có) → mở bottom sheet danh sách profile.

## T05.2 — Create/Rename/Delete profile (5 pts)
Bottom sheet CRUD profile (tên, có thể thêm icon/màu đại diện). Delete cần dialog xác nhận + cảnh báo xoá toàn bộ `BmiRecord` liên kết (cascade).

## T05.3 — Switch profile → refresh toàn app state (3 pts)
Đổi profile hiện tại (`setCurrentProfileAtomic`) → History, Result, Weight Dashboard (EPIC-07), Goal card, Streak/Badge (cân nhắc: streak/badge hiện lưu SharedPrefs **không** theo profileId — xem rủi ro dưới) phải load lại đúng theo profile mới.
**Rủi ro cần quyết định**: `StreakManager`/`BadgeManager` dùng SharedPrefs global, không phân theo `profileId`. Nếu multi-profile ra mắt, streak/badge sẽ bị **chia sẻ nhầm** giữa các thành viên gia đình. Cần chọn: (a) scope lại key SharedPrefs theo profileId, hoặc (b) giữ streak/badge là "app-level" (theo dõi việc dùng app, không theo dõi riêng từng người) — quyết định UX, nên hỏi user.

## T05.4 — Onboarding cho profile đầu tiên (2 pts)
Đổi tên "Default" → hỏi tên thật khi first-run (hiện `FirstRunLanguageSheet` chỉ hỏi ngôn ngữ, có thể mở rộng hỏi tên luôn, hoặc thêm bước riêng).
