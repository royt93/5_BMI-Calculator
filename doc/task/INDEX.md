# Task Backlog — BMI Calculator 2026

> Nguồn: đọc toàn bộ source (`app/src/main/java`, 41 file .kt) + 6 doc kỹ thuật (`TODO.md`, `BUGS_FIXED.md`, `Security.md`, `FEATURES_ADDED.md`, `memory_leak.md`, `AD.MD`) + Explore agent quét sâu weight-tracking/goal/settings/multi-profile.
> Convention: file task nằm `doc/task/todo/`, chuyển sang `doc/task/inprogress/` khi bắt đầu, `doc/task/done/` khi xong. Mỗi file = 1 Epic, gồm nhiều Story/Task con.

## Priority legend
P0 = làm ngay (blocker/flagship) · P1 = sprint tới · P2 = sprint sau · P3 = backlog/nice-to-have

## Category legend
🐛 FIX · ⚙️ ENHANCE (wire dead code / hoàn thiện nửa vời) · ✨ NEW · 💡 IDEA (chưa quyết, cần user chọn)

## Epic overview

| Epic | Category | Priority | Points | File |
|---|---|---|---|---|
| ✅ EPIC-00 Critical correctness bugs (đồng thuận 2-3 AI) | 🐛 | P0 | 10 | `done/EPIC-00-critical-bugs.md` |
| EPIC-01 i18n & code hygiene fixes | 🐛 | P1 | 5 | `todo/EPIC-01-i18n-hygiene-fixes.md` |
| EPIC-02 Security hardening (leftover M/L items) | 🐛 | P1/P2 | 8 | `todo/EPIC-02-security-hardening.md` |
| EPIC-03 Reward-ad "Detailed Plan" — hướng đã chốt, chưa implement | 🐛/⚙️ | P1 | 3 | `todo/EPIC-03-reward-ad-detailed-plan.md` |
| ✅ EPIC-04 Settings screen + Unit system (kg⇄lbs) | ⚙️ | P1 | 13 | `done/EPIC-04-settings-and-units.md` |
| ✅ EPIC-05 Multi-profile UI (gia đình) | ⚙️ | P1 | 13 | `done/EPIC-05-multi-profile-ui.md` |
| EPIC-06 Calculator Hub integration | ⚙️ | P2 | 8 | `todo/EPIC-06-calculator-hub-integration.md` |
| ✅ **EPIC-07 Weight Dashboard hợp nhất (FLAGSHIP)** | ✨ | P0 | 16 | `done/EPIC-07-weight-dashboard.md` |
| EPIC-08 Engagement features (reminder/export/measurements) | ✨ | P2 | 21 | `todo/EPIC-08-engagement-features.md` |
| EPIC-09 Platform extensions (widget/Health Connect) | ✨ | P3 | 21 | `todo/EPIC-09-platform-extensions.md` |
| Ideas độc quyền — **CHỐT: I3+I2+I4 vào roadmap gần nhất** | 💡 | — | — | `IDEAS.md` |

**Tổng**: 10 epic, ~45 task con, ~118 story points. **Đã xong: EPIC-00 + EPIC-07 (26 pts).**

## Đã implement (2026-08-23) — chi tiết

**EPIC-00** (`CalculatorUtils.kt`, `ResultAct.kt`, `MainAct.kt`, `StreakManager.kt`, `TdeeCalculatorActivity.kt`, `BmiDao.kt`/`BmiRepository.kt`): fix goal-weight gain-direction, TDEE hardcoded Sedentary, ngưỡng BMI category lệch nhau, streak stale display, gender "Other" bị tính như Female.

**EPIC-07** (`HistoryActivity.kt` nâng cấp thành Weight Dashboard, `a_history.xml`, `dialog_quick_log.xml` mới): series switcher BMI/Weight/Height, goal row (nguồn sự thật duy nhất, gộp từ `ResultAct`), ETA prediction (`CalculatorUtils.estimateGoalEtaDays` — linear regression), empty state, quick-log FAB. **Chưa làm**: rewarded-ad unlock Advanced Insights (EPIC-03).

**Test**: 25 unit test (`CalculatorUtilsTest.kt`, `StreakManagerLogicTest.kt`) + 10 instrumented test (`BmiDaoTest.kt`, `ResultActGoalCardTest.kt`, `ResultActGenderPersistenceTest.kt`, `HistoryActivityDashboardTest.kt`) — tất cả pass trên `Pixel_10_Pro_XL(AVD)`. Gradle test dependencies (JUnit4, kotlinx-coroutines-test, Espresso 3.7.0, Room-testing) được thêm mới vào `app/build.gradle.kts` — project trước đó **0 test**.

## Audit pass (2026-08-23, sau khi implement) — điểm 9→10/10

Chạy `/code-review high` (4 sub-agent song song) audit lại toàn bộ diff EPIC-00+07. 10 finding, verify từng cái:
- **4 bug thật, đã fix + có test**: goal overshoot (vượt mục tiêu vẫn báo "99% remaining" thay vì "đã đạt"), ETA gộp nhầm "chưa đủ data" với "xu hướng sai hướng", coroutine ngoài trong `ResultAct` thiếu try/catch (rủi ro crash nếu DataStore lỗi), `BadgeManager` hardcode ngưỡng riêng thay vì dùng chung `CalculatorUtils.GOAL_ACHIEVED_TOLERANCE_KG`.
- **1 code duplication đã dedup**: tách `RecordSaveHelper.saveAndCheckBadges()` dùng chung giữa `ResultAct.saveToHistory()` và `HistoryActivity.quickLogWeight()` — trước đó lặp code insert+streak+badge+snackbar ở 2 nơi.
- **1 gap i18n đã đóng**: 14/16 locale còn thiếu 15 string key mới của Dashboard — đã dịch đầy đủ cho `ar de es fr hi id it ja ko nl pt ru th tr zh` (cùng `en`+`vi` đã có từ trước → đủ 17 locale).
- **2 finding false positive** (đã verify bằng grep, không phải bug): `@string/progress` không "dead" (vẫn dùng làm tiêu đề card trong `a_history.xml`); standalone Calculator không cần genderCode 3 chiều vì UI chỉ có radio M/F, không có "Other".
- **1 duplication nhẹ giữ nguyên**: goal dialog (`showGoalDialog`) lặp code UI thuần giữa `ResultAct`/`HistoryActivity` — rủi ro thấp, chưa dedup.
- **1 nợ kỹ thuật có sẵn từ trước** (không phải regression turn này): `ResultAct` tính BMR/TDEE 2 lần độc lập ở `calculateAndDisplayInsights()`/`saveToHistory()` — đã track sẵn ở EPIC-06.

Sau fix: rebuild sạch (`assembleDevDebug`) + rerun toàn bộ 25 unit + 10 instrumented test — 100% xanh. **Đã push lên `origin/dev`.**

**Phát hiện phụ trong lúc test**: instrumented test đầu tiên bị App Open/Interstitial ad che UI khi launch Activity trực tiếp qua `ActivityScenario` (không qua `SplashAct`) — đã xử lý bằng cách tắt network emulator lúc chạy test (`adb shell svc wifi/data disable`, bật lại sau khi xong). Cũng phát hiện và fix lỗi cô lập test: các test trước đó ghi thẳng vào profile thật (`bmi_database` là DB thật, không phải in-memory) gây ô nhiễm dữ liệu giữa các lần chạy — mọi test giờ dùng profile tạm riêng, dọn dẹp ở `@After`.

## Quyết định user đã chốt (qua AskUserQuestion, 2026-08-23)
- **Sprint tới**: làm song song nhiều epic (không dồn 1 hướng duy nhất).
- **EPIC-03**: gộp rewarded-ad vào Weight Dashboard (EPIC-07), không làm màn hình riêng, không xoá.
- **EPIC-05**: streak/badge tính riêng theo từng profile khi multi-profile UI ra mắt (cần sửa `StreakManager`/`BadgeManager` từ SharedPrefs global sang scope theo `profileId`).
- **Ideas**: I3 (Share Progress Card), I2 (Smart Insights on-device), I4 (Family Challenge Mode) — đưa vào roadmap gần nhất.

## Second opinion đã tích hợp
3 AI Agent độc lập (`codex exec`, `agy --model gemini-3.1-pro-high`, `claude --print`) đã review codebase riêng biệt — không đọc chéo backlog trước khi kết luận. Phát hiện chung → `EPIC-00` (bug mới, P0). Ý tưởng chung → `IDEAS.md` mục "Second opinion". Toàn văn review gốc: `review_codex.md`, `review_gemini.md`, `review_claude.md` (cùng thư mục `doc/task/`).

## Vì sao EPIC-07 là P0

User xác nhận thích nhất tính năng **quản lý cân nặng + chart cân nặng**. Explore agent phát hiện đây đang là điểm yếu rõ nhất: 2 chart tách rời không liên kết nhau —
- `HistoryActivity.kt:114-152` vẽ **BMI** theo thời gian, có 1 `LimitLine` xấp xỉ "goal BMI" nhưng tính sai (luôn dùng chiều cao của **bản ghi đầu tiên** làm mẫu số cho mọi điểm — `HistoryActivity.kt:139-151`).
- `TrackerBottomSheet.kt:76-179` vẽ **weight/height** riêng, không có goal line, không có BMI.
- Goal card set/xem mục tiêu nằm ở màn hình thứ 3 (`ResultAct.kt:219-348`), tách khỏi cả 2 chart trên.

→ 3 nơi rời rạc cho cùng 1 nhu cầu "theo dõi cân nặng". Đây là cơ hội lớn nhất để nâng trải nghiệm cốt lõi.

## Đã xác nhận KHÔNG còn mở (Explore agent re-check 2026-08-23, khác với doc/Security.md 2026-05-11)

C-01 (AppLovin key lộ manifest), H-01 (log PII), H-02 (backup rules rỗng), H-03 (MainAct exported) — **cả 4 đã fix**. Không đưa vào backlog nữa, chỉ còn M-01→M-04, L-01→L-04 (xem EPIC-02).
