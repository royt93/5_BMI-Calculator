# EPIC-08 — Engagement features ✨ (P2, 21 pts)

> **Status (2026-08-25): T08.1–T08.4 ĐÃ XONG + có test, đã push.**
> **T08.1 Reminder**: `ReminderScheduler`/`ReminderWorker`/`NotificationHelper` mới (`com.samsunggalaxy.notification`), dùng WorkManager periodic (1 lần/ngày) thay AlarmManager. Toggle "Daily weigh-in reminder" trong Settings, mặc định **tắt** (opt-in, đúng khuyến nghị chống spam của doc), giờ mặc định 08:00 chỉnh được qua TimePickerDialog. Xin `POST_NOTIFICATIONS` runtime permission (Android 13+) đúng lúc bật toggle, không xin trước.
> **T08.2 CSV export**: `CsvExporter` (utils, pure `buildCsvContent()` tách khỏi I/O) ghi file vào `getExternalFilesDir(null)/exports/` — không cần `WRITE_EXTERNAL_STORAGE`, share qua `FileProvider` đã khai báo sẵn trong manifest + share sheet `ACTION_SEND`. Nút export mới trên toolbar `HistoryActivity`. **PDF export**: deferred theo đúng khuyến nghị của doc ("CSV trước, PDF sau nếu cần") — chưa làm.
> **T08.3 Body measurements**: bảng Room mới `body_measurements` (waist/neck/hip/chest, `MIGRATION_2_3`), gắn vào flow "Save to History" sẵn có của `BodyFatCalculatorActivity` (không cần màn hình nhập riêng). Thêm tab thứ 4 "Measurements" trong Weight Dashboard (`HistoryActivity`) — multi-line chart waist/neck/hip dùng chung `styledLineDataSet()` helper với 3 tab kia.
> **T08.4 Badge mới**: `MEASURE_TAKER` (đo cơ thể lần đầu) + `DATA_EXPORTER` (export lần đầu) — 2 icon vector mới (`ic_badge_ruler`, `ic_badge_export`). "First goal reached"/"30-day trend logged" mà doc gợi ý ban đầu hoá ra đã trùng với `GOAL_CRUSHER`/`MONTHLY_MASTER` có sẵn từ EPIC-00, nên chọn 2 badge mới thực sự khác biệt, gắn với chính 2 tính năng vừa làm trong epic này.
>
> **Giới hạn đã ghi nhận** (deferred, ngoài scope epic này): PDF export (T08.2), field `chest` trong `BodyMeasurement` chưa có UI nhập (chỉ sẵn schema cho tương lai — Navy body-fat formula không cần chest nên không thêm input riêng).
>
> **Audit pass** (`/code-review high`): 10 finding, đã fix 6 bug/rủi ro thật —
> 1. **Crash khi upgrade DB** (nghiêm trọng nhất): `MIGRATION_2_3` khai báo `profileId ... DEFAULT 0` trong raw SQL nhưng entity `BodyMeasurement` không có `@ColumnInfo(defaultValue)` tương ứng — Room validate schema sau migration sẽ throw `IllegalStateException`, crash app cho MỌI user nâng cấp (fresh install không bị vì Room tự tạo bảng từ entity, không qua raw SQL). `BodyMeasurementDaoTest` (Room in-memory) không bắt được vì in-memory DB luôn tạo mới từ entity, bỏ qua hẳn đường migration. Fix: bỏ `DEFAULT 0` khỏi SQL. Thêm test mới `AppDatabaseMigrationTest` dựng DB version 1 thật rồi chạy migration chain qua Room để bắt lại đúng lỗi này.
> 2. **Tab Measurements bị ẩn sau "No data yet"**: `updateEmptyState()` chỉ xét `records` (bmi_records) dù tab Measurements dùng nguồn dữ liệu độc lập (`body_measurements`) — xoá hết BMI record (không cascade sang measurements) làm tab Measurements còn dữ liệu thật nhưng bị che vĩnh viễn. Fix: empty-state xét theo tab đang chọn.
> 3. **Đổi giờ nhắc không có tác dụng**: `ExistingPeriodicWorkPolicy.UPDATE` không đảm bảo áp dụng initial delay mới cho periodic work đã chạy ít nhất 1 lần — đổi giờ trong Settings có thể im lặng giữ giờ cũ. Fix: `CANCEL_AND_REENQUEUE`.
> 4. **Double-tap export CSV**: race 2 coroutine export đồng thời → duplicate badge snackbar + 2 share sheet. Fix: cờ `isExportingCsv` chặn re-entrancy.
> 5. **Trùng code** dedupe: `BadgeManager.checkAll()`'s inline `tryUnlock` giờ gọi chung `tryUnlockSingle()`; `updateMeasurementsChart()`'s `buildSet()` giờ dùng chung `styledLineDataSet()` với `updateChart()` — tránh 2 nơi phải sửa đồng bộ khi đổi style/logic.
>
> Không fix (chấp nhận là giới hạn có chủ đích): `lateinit` 3 field mới trong `SettingsActivity` (khớp pattern `lateinit` đã dùng sẵn cho 5 field khác cùng file, không phải regression mới); 2 nhánh `Series.MEASUREMENTS` placeholder trong `when` exhaustiveness (an toàn compile-time, không phải bug runtime); 5 lần đọc DataStore tuần tự trong `loadPersistedToggleStates()` (perf nit rất nhỏ, không đáng đánh đổi độ phức tạp `async`/`awaitAll`).
>
> Test: `ReminderSchedulerTest` (4 test, pure delay-math), `CsvExporterTest` (6 test, pure CSV row-building — bao gồm regression test cho lỗi locale dấu phẩy giống EPIC-06), `AppDatabaseMigrationTest` (1 test, migration path thật) + `BodyMeasurementDaoTest` (5 test), `EngagementFeaturesTest` (5 test: export CSV round-trip, badge Data Exporter, Measurements tab render, reminder toggle persist) + mở rộng `CalculatorHubIntegrationTest`'s Body Fat test để assert measurement + badge Measure Taker — **46/46 instrumented + 61/61 unit test pass** (toàn bộ suite, gồm cả các epic trước) trên `Pixel_10_Pro_XL(AVD)`. Smoke test tay xác nhận cả 4 tính năng trên device thật: bật reminder toggle (time picker hoạt động), tab Measurements vẽ đúng waist/neck line, Body Fat "Save to History" gắn badge Measure Taker, export CSV mở đúng share sheet + gắn badge Data Exporter.
>
> **Sự cố môi trường trong lúc test** (không phải bug code): thiết bị thật (Pixel 7 Pro) bị khoá màn hình giữa phiên test — chuyển sang emulator theo yêu cầu user. Một phiên emulator chạy quá lâu (nhiều lượt cài lại APK liên tiếp) từng gây 3 test flake không liên quan tới EPIC-08 (`RootViewWithoutFocusException`) — khởi động lại emulator sạch thì hết. Một lần tự làm sai quy trình: chạy `compileDevDebugAndroidTestKotlin` (chỉ biên dịch `.class`) rồi cài `.apk` cũ chưa đóng gói lại — khiến 1 test tưởng như fail do "đọc nhầm file cũ trong thư mục exports/", thực ra là APK test chưa được rebuild; fix bằng `assembleDevDebugAndroidTest` trước khi cài lại.

## T08.1 — Nhắc nhở cân nặng hàng ngày/tuần (8 pts)
Hiện app **chưa có bất kỳ notification/reminder nào** (grep xác nhận không có `NotificationManager`/`WorkManager` trong source). Local notification nhắc user cân + nhập vào app, giờ tuỳ chỉnh, có thể tắt.
**Kỹ thuật**: `WorkManager` (periodic, chịu được Doze/battery optimization) thay vì `AlarmManager` thô. Cần xin `POST_NOTIFICATIONS` runtime permission (Android 13+).
**Rủi ro**: notification spam → uninstall. Cần tần suất mặc định hợp lý (1 lần/ngày, giờ user chọn) + easy opt-out.

## T08.2 — Export CSV/PDF lịch sử (5 pts)
Đã được note là "Future" trong cả `doc/BUGS_FIXED.md` và `doc/FEATURES_ADDED.md` từ đầu, chưa từng làm. CSV đơn giản hơn (không cần lib), PDF cần thêm dependency (`iText`/Android `PdfDocument` API có sẵn, không cần lib ngoài).
**Liên quan T02.3**: quyết định permission storage — dùng `MediaStore`/`FileProvider` scoped, không xin `WRITE_EXTERNAL_STORAGE` full.
**Khuyến nghị**: làm CSV trước (đơn giản, 2 pts), PDF sau nếu user cần chia sẻ bác sĩ/HLV (3 pts, đẹp hơn để in).

## T08.3 — Body measurements time-series (5 pts)
Hiện `BodyFatCalculatorActivity` nhập waist/hip/neck **one-shot**, không lưu. Thêm bảng đo lường riêng (waist/hip/neck/chest theo thời gian) → mở khoá thêm 1 chart trong EPIC-07 Weight Dashboard (series "Measurements"). Phù hợp nhóm user tập gym quan tâm vóc dáng hơn chỉ số cân nặng thuần.

## T08.4 — Achievement/Badge mở rộng theo Weight Dashboard mới (3 pts)
Sau khi EPIC-07 xong, thêm badge mới liên quan (vd. "First goal reached", "30-day trend logged") — tái sử dụng `BadgeManager` pattern đã có, không cần kiến trúc mới.
