# EPIC-09 — Platform extensions ✨ (P3, 21 pts)

> **Status (2026-08-26): ĐÃ XONG — T09.1 Widget + T09.2 Health Connect bidirectional sync. Full test suite pass, audit `/code-review high` 6 finding đều fix, bonus fix 1 bug crash pre-existing nghiêm trọng (java.time thiếu desugaring). Đã push.**

## T09.1 — Home-screen Widget (8 pts)
`widget/BmiWidgetProvider.kt` (AppWidgetProvider, RemoteViews — không dùng Jetpack Glance vì project 100% View-based, không có Compose sẵn), `widget/WidgetUpdateHelper.kt` (build RemoteViews từ Room + DataStore), `widget/SparklineRenderer.kt` (vẽ sparkline 7 ngày bằng Canvas thuần, pure-function `normalize()` unit test được), `widget/WidgetRefreshWorker.kt` + `WidgetRefreshScheduler.kt` (periodic 6h, theo pattern `ReminderScheduler`).

Hiển thị: cân nặng mới nhất (unit-aware), BMI + category (màu theo `CalculatorUtils.getBMICategoryInfo`), sparkline 7 ngày, empty state khi chưa có data. Tap mở `MainAct`. Update trigger: sau mỗi lần lưu record mới (`RecordSaveHelper`), xoá record (`HistoryActivity.deleteRecord`), clear history (`SettingsActivity`), và periodic 6h.

Query mới: `BmiDao.getRecordsSince(profileId, sinceTimestampMs)` — trước đó chỉ có LIMIT-based query, không đủ cho cửa sổ "7 ngày gần nhất" chính xác khi user không log đều mỗi ngày.

**Smoke test**: widget provider xuất hiện đúng trong system widget picker (tên "BMI Calculator 2026 DEV", size 4×2, description đã dịch, preview icon đúng) — xác nhận qua thao tác tay trên `emulator-5554`. Đặt thật lên home screen qua kéo-thả bằng `adb input swipe` không mô phỏng chính xác được (launcher cần long-press-hold thật, không phải 1 swipe) — giới hạn công cụ test, không phải bug; code path "no widget bound" (`getAppWidgetIds()` rỗng → early return) đã được exercise an toàn qua hàng chục lần gọi `RecordSaveHelper` trong bộ instrumented test.

## T09.2 — Health Connect integration (13 pts)
`health/HealthConnectManager.kt` (availability check, permission, sync logic), `health/HealthConnectSyncWorker.kt` + `HealthConnectSyncScheduler.kt` (periodic 6h + enqueue one-time sau mỗi save). Migration `MIGRATION_3_4`: thêm `BmiRecord.source` ("APP"/"HEALTH_CONNECT") và `healthConnectRecordId` (link tới record Health Connect tương ứng).

**Quyết định kiến trúc đã chốt qua AskUserQuestion**:
- Conflict resolution: **last-write-wins** (so `local.timestamp` vs `hc.metadata.lastModifiedTime`).
- Fallback khi không có Health Connect: **hiện mục disabled + nút "Install Health Connect"** (không ẩn hoàn toàn).
- `androidx.health.connect:connect-client:1.1.0` yêu cầu minSdk 26 nhưng app minSdk 24 → dùng `tools:overrideLibrary` + gate mọi entry point qua `HealthConnectManager.isAvailable()` (annotate `@ChecksSdkIntAtLeast`/`@RequiresApi` để lint verify được, không chỉ tin bằng mắt).

**Audit `/code-review high`** — 6 finding, tất cả đã fix:
1. **Update-in-place dùng sai clientRecordId** → tạo record trùng lặp trong Health Connect mỗi lần sync giá trị mới hơn từ app. Fix: dùng `client.updateRecords()` với `Metadata.manualEntryWithId(existingHealthConnectRecordId)` (API đúng để update theo platform id) thay vì `insertRecords()` với id sai chỗ.
2. **Xoá record/clear history không xoá theo bên Health Connect** → record "đã xoá" bị đồng bộ ngược lại (resurrect) ở lần sync kế tiếp. Fix: `HealthConnectManager.deleteRecords()` mới, gọi từ `HistoryActivity.deleteRecord()` (1 record) và `SettingsActivity.clearHistory()` (bulk, dùng `BmiDao.getHealthConnectRecordIds()` mới thêm).
3. **Timestamp dùng `lastModifiedTime` thay vì `hc.time`** → ngày cân nặng hiển thị sai (ngày ghi vào Health Connect, không phải ngày cân thật) cho record backfill/nhập từ app khác. Fix: dùng `hc.time.toEpochMilli()` khi lưu, giữ `lastModifiedTime` chỉ cho so sánh last-write-wins.
4. **UI hiện "đã sync" dù thất bại** — `syncHealthConnectNow()`/`HealthConnectSyncWorker` ghi timestamp + hiện "Last synced" bất kể kết quả. Fix: chỉ ghi/hiện khi `SyncResult.Success`; thêm Toast riêng cho `MissingPermissions`.
5. **Sync chặn đường lưu record (blocking)** — mỗi lần save/quick-log phải chờ `syncNow()` xong (đọc tới 30 ngày Health Connect + nhiều round-trip DB) mới thấy xác nhận lưu thành công. Fix: chuyển sang `HealthConnectSyncScheduler.enqueueOneTimeSync()` (WorkManager, fire-and-forget) thay vì await inline.
6. **FQN thay vì import** (nit) — đã sửa dùng import nhất quán.

**Bonus fix ngoài scope** (phát hiện qua `lintDevDebug`, user xác nhận fix ngay): `StreakManager.kt`/`MainAct.kt` dùng `java.time.LocalDate` không gate + không desugaring → crash thật trên Android 7.0/7.1 (API 24-25, đúng minSdk app claim support) mỗi khi tính streak. Fix: bật `isCoreLibraryDesugaringEnabled = true` + `coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.4")`. Zero behavior change trên API 26+, không đổi logic app.

**Smoke test tay** (`emulator-5554`, API 37): Settings hiện card "HEALTH CONNECT" đúng (switch enabled vì máy có Health Connect module hệ thống `com.google.android.healthconnect.controller`, không phải app rời `com.google.android.apps.healthdata` như code check qua `getSdkStatus` — vẫn detect đúng available=true). Bật switch → trigger permission request qua `PermissionController.createRequestPermissionResultContract()` → denied (máy không có UI thật) → Toast "permission denied" hiện đúng, switch tự revert về OFF, không crash. `Log` không FATAL ở bất kỳ bước nào.

## Test & verify
- Unit test mới: `SparklineRendererTest` (6 test), `HealthConnectManagerTest` (2 test, verify SDK gate + required permissions set).
- Instrumented test mới: `BmiDaoTest` +7 test (`getRecordsSince`, `linkHealthConnectRecord`/`getRecordByHealthConnectId`, `getRecordsBySource`, `updateWeightFromSync`, default source/link null), `AppDatabaseMigrationTest` +1 test (`migrate3To4_addsSourceAndHealthConnectColumns`, verify backfill `source='APP'` cho row cũ).
- Tổng: 69/69 unit test, 56/56 instrumented test pass trên `emulator-5554` (mạng tắt lúc chạy, bật lại sau).
- `lintDevDebug`: 0 lỗi liên quan EPIC-09 (chỉ còn 1 lỗi pre-existing không liên quan, VIP feature `Vibrator.vibrate` thiếu khai báo `VIBRATE` permission — để backlog riêng).
- `assembleDevDebug`/`compileDevDebugKotlin` sạch.
