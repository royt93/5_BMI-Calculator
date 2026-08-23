# EPIC-08 — Engagement features ✨ (P2, 21 pts)

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
