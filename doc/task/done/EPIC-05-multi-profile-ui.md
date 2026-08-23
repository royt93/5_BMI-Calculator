# EPIC-05 — Multi-profile UI (gia đình) ⚙️ (P1, 13 pts)

> **Status (2026-08-23): T05.1–T05.4 ĐÃ XONG + có test.** `MainAct` có profile chip (toolbar) → `ProfileSwitcherBottomSheet` (switch/create/rename/delete, cascade xoá `BmiRecord`, chặn xoá profile cuối cùng). `StreakManager`/`BadgeManager` scope theo `profileId` (quyết định (a) đã chọn ở turn trước) + migrate 1 lần dữ liệu cũ từ SharedPrefs global vào profile đầu tiên đọc được. Onboarding rename profile "Default" — 1 lần duy nhất, tách khỏi luồng first-run của `SplashAct` để tránh rủi ro. Test: 9 instrumented test mới (`StreakManagerProfileScopingTest`, `BadgeManagerProfileScopingTest`, `ProfileCrudTest`, `ProfileSwitcherE2ETest`, `OnboardingProfileRenameTest`) — tất cả pass trên `Pixel_10_Pro_XL(AVD)` cùng test cũ (24/24 tổng).

## Audit pass (2026-08-23) — điểm 9.5/10, đã push

`/code-review high` tìm 9 finding, verify từng cái:
- **6 bug thật, đã fix + có test**: dialog onboarding thiếu `isFinishing/isDestroyed` guard (rủi ro `BadTokenException`), xoá profile không dọn `streak_prefs_<id>`/`badge_prefs_<id>` (file mồ côi vĩnh viễn), race condition `currentProfileId` (field dùng chung bị 2 coroutine ghi đè nhau — sửa dùng local val), `isTodayChecked`/`getEarnedDate` thiếu gọi `migrateLegacyIfNeeded()` (2/4 hàm quên, dữ liệu cũ có thể mất nếu gọi sai thứ tự), submit tên rỗng trong onboarding tiêu luôn flag "đã hỏi" vĩnh viễn (sửa: chỉ set flag khi Skip hoặc Save thành công).
- **2 code duplication giữ nguyên** (rủi ro thấp, đã cân nhắc): dialog đặt tên profile lặp code giữa `MainAct` (onboarding) và `ProfileSwitcherBottomSheet`; skeleton migration lặp giữa `StreakManager`/`BadgeManager` (khác cấu trúc key nên gộp sẽ phức tạp hơn lợi ích).
- **1 performance nit bỏ qua**: migration guard chạy lại mỗi lần mở Achievements — rẻ (SharedPrefs cache), không đáng thêm memoization.

Sau fix: 24/24 test xanh, `assembleDevDebug` sạch. Đã commit + push `origin/dev`.

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
