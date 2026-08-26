# Ideas độc quyền / khác biệt hoá 💡

> Chưa xếp epic, chờ user chọn qua AskUserQuestion. Đã bổ sung ý kiến từ codex/gemini/claude độc lập (xem mục "Second opinion" cuối file, nếu có).

Đa số BMI calculator trên Play Store chỉ tính 1 lần, không giữ chân user. App này đã có nền tốt hơn mặt bằng chung (Room history + chart + streak/badge/gamification + multi-profile backend) nhưng chưa có 1 tính năng "phải mở app mỗi ngày". Các ý tưởng dưới nhắm vào việc đó.

## I1 — Ảnh tiến trình ("Progress Photo Timeline")
Gắn ảnh (chụp local, không upload cloud) vào mỗi lần cân, xem lại dạng before/after slider trong Weight Dashboard.
- Ưu: hiệu ứng "wow" trực quan hơn số liệu thuần, chia sẻ mạng xã hội tốt (viral tiềm năng), giữ chân user vì cần tích luỹ ảnh theo thời gian.
- Nhược: cần xin quyền Camera + Storage (thêm attack surface, đối lập với hướng giảm permission ở T02.3), cần cam kết rõ "chỉ lưu local" trong Privacy Policy để tránh lo ngại quyền riêng tư (ảnh cơ thể là dữ liệu nhạy cảm).

## ✅ I2 — Smart Insights on-device (không cần API/AI trả phí) — ĐÃ XONG (2026-08-26)
Phân tích thống kê đơn giản trên dữ liệu đã có: tuần giảm/tăng nhanh nhất, ngày trong tuần cân nặng ổn định nhất, tương quan streak ↔ xu hướng cân nặng ("Những tuần bạn duy trì streak, cân nặng giảm trung bình X kg nhanh hơn").
- Ưu: **chi phí 0** (không gọi LLM API), tận dụng 100% dữ liệu đã có sẵn trong Room, khác biệt vì hầu hết app đối thủ chỉ hiện số BMI thô.
- Nhược: cần đủ dữ liệu lịch sử mới có insight hay (cold-start problem cho user mới).

**Implementation**: `utils/InsightsEngine.kt` (pure functions, unit test đầy đủ) — 3 loại insight:
1. **Best week**: tuần giảm cân nhiều nhất (first-vs-last trong tuần, chỉ hiện khi thật sự là giảm cân, không claim sai khi user chỉ tăng cân).
2. **Most stable day-of-week**: ngày trong tuần có độ lệch chuẩn cân nặng thấp nhất (dùng `java.time.DayOfWeek` — an toàn nhờ core library desugaring đã bật ở EPIC-09).
3. **Streak correlation**: so trung bình delta cân nặng/tuần giữa "tuần giữ streak đủ 7 ngày" vs "tuần log ít hơn".
Card mới trong `HistoryActivity`'s Weight Dashboard, dưới Progress card — ẩn insight nào chưa đủ data thay vì hiện số sai lệch, hiện toàn bộ cold-start message nếu chưa có insight nào.

Audit `/code-review high`: 3 finding, cả 3 đều fix — (1) card hardcode nền trắng đục thay vì `bg_glass_card_main` theme-aware như card khác cùng màn hình → text gần như vô hình ở dark mode (bug nghiêm trọng nhất, đã verify lại bằng smoke test cả light/dark mode sau fix); (2) 2 field UI mới dùng `lateinit` vi phạm rule "No lateinit" của CLAUDE.md → đổi sang nullable + safe-call; (3) map `records` 2 lần dư thừa → gộp 1 lần.

Test: 9 unit test mới (`InsightsEngineTest`) + 83 unit total + 56 instrumented pass. Smoke test tay 2 lần (trước và sau fix dark mode) trên `emulator-5554` với data giả lập 2 tuần (insert qua sqlite3 vì UI wizard MainAct dùng SwipeButton khó mô phỏng qua adb) — card hiện đúng cả 3 insight, không crash, dark mode đọc được rõ ràng sau fix. i18n: 5 string mới dịch đủ 17 locale.

## ✅ I3 — Share Progress Card (ảnh tổng kết đẹp để đăng story/Zalo/Facebook) — ĐÃ XONG (2026-08-26)
Xuất 1 ảnh card (kiểu Spotify Wrapped/Strava) tóm tắt: "Trong 30 ngày, giảm 2.3kg, streak 18 ngày" kèm mini chart.
- Ưu: **kênh marketing tự nhiên** — mỗi lần user share là quảng cáo miễn phí cho app; tận dụng lại chart component từ EPIC-07.
- Nhược: cần thiết kế UI card đẹp (không chỉ chart thô), có thể cần thêm font/asset.

**Implementation**: `share/ShareProgressCardRenderer.kt` (Canvas/Bitmap, 1080×1350, gradient trùng `bg_splash_gradient` để đồng bộ brand) + `share/ShareProgressCardExporter.kt` (FileProvider, cùng pattern `CsvExporter`, dọn card cũ mỗi lần lưu mới — khác CSV export vì tính năng này khuyến khích tap lặp lại). Icon share mới (`ivShareProgress`, `ic_share`) thêm vào toolbar `HistoryActivity`; icon export CSV cũ đổi sang `ic_badge_export` để 2 icon không còn trùng hình (bug nhỏ phát hiện thêm: icon cũ dùng hình share nhưng lại là hành động export CSV). `CalculatorUtils.calculateWeightChange()` mới (pure, unit test) tính delta cân nặng đầu-cuối trong cửa sổ 30 ngày (dùng `BmiDao.getRecordsSince` có sẵn từ EPIC-09).

Audit `/code-review high`: 5 finding — fix constant `FILE_PROVIDER_AUTHORITY` dùng chung (trước lặp ở 2 nơi), fix dọn file card cũ (tránh tích luỹ vô hạn), bỏ field `recordCount` không dùng (YAGNI). 2 finding còn lại (race điều kiện cờ `isSharingProgress` non-volatile, `catch (Exception)` nuốt `CancellationException`) khớp đúng pattern đã có sẵn trong `exportCsv`/`quickLogWeight` — không sửa lẻ để tránh inconsistency, để lại cho 1 lượt dọn dẹp riêng nếu cần.

Test: 78 unit test (`CalculatorUtilsTest` +5) + 56 instrumented test pass. Smoke test tay trên `emulator-5554`: insert 2 record test qua sqlite3 (WeightWheel/SwipeButton của MainAct khó mô phỏng qua adb), mở History → tap Share Progress → share sheet "Sharing image" mở đúng → pull file PNG về xem — card render đúng data ("-2.0 kg", "🔥 9 day streak", sparkline, branding). i18n: 7 string mới dịch đủ 17 locale (en có sẵn + 16 locale khác).

## I4 — Family Challenge Mode
Dùng chung multi-profile backend (EPIC-05): so sánh streak/tiến độ giữa các profile trong cùng thiết bị (gia đình cùng dùng 1 máy hoặc 1 người quản lý nhiều profile), leaderboard nhẹ nhàng không cần server/tài khoản.
- Ưu: tận dụng lại đúng backend Profile đã có sẵn 100%, chỉ cần thêm UI so sánh — chi phí kỹ thuật thấp so với giá trị tạo ra.
- Nhược: chỉ có giá trị nếu EPIC-05 (multi-profile UI) đã xong; use-case hẹp hơn nếu đa số user chỉ dùng 1 profile cho bản thân.

## I5 — Wear OS / Quick-log Tile
Log cân nặng nhanh 1 chạm từ Wear OS tile hoặc notification action, không cần mở app.
- Ưu: giảm friction logging — nguyên nhân số 1 khiến user bỏ theo dõi cân nặng dài hạn là "phải mở app, chọn số".
- Nhược: chi phí phát triển cao (module Wear OS riêng, ít user sở hữu đồng hồ), ROI thấp hơn so với các ý tưởng khác trong danh sách này — **khuyến nghị hoãn xa**.

## I6 — Quick-log Notification Action
Bản rút gọn của I5 không cần Wear OS: notification nhắc cân (T08.1) có sẵn nút "+" / "-" hoặc input nhanh ngay trên notification, không cần mở app đầy đủ.
- Ưu: chi phí thấp hơn I5 nhiều (chỉ cần `RemoteInput`/actions trên notification có sẵn từ T08.1), cùng mục tiêu giảm friction.
- Nhược: nhập số cân trên notification nhỏ khó chính xác — cần UX thử nghiệm (vd. "log lại số gần nhất ±0.1kg" thay vì nhập tự do).

## I7 — AI-personalized nutrition tips (dùng LLM API)
Thay 350 tips tĩnh hiện có (`doc/TODO.md` Health Tips) bằng gợi ý sinh động theo đúng số liệu user.
- Ưu: cá nhân hoá thật, không lặp lại tip.
- Nhược: **tốn phí API mỗi request**, cần backend riêng (app hiện 100% local/no-backend — đổi kiến trúc lớn), rủi ro AI đưa lời khuyên y tế sai/nhạy cảm (cần disclaimer pháp lý mạnh). **Không khuyến nghị** trong giai đoạn này — 350 tips tĩnh hiện tại đã đủ tốt với chi phí bằng 0.

---

## Khuyến nghị ưu tiên (trước khi hỏi ý kiến ngoài)
1. **I3 (Share Progress Card)** — chi phí thấp, tận dụng lại EPIC-07, có khả năng viral/marketing tự nhiên. **Recommended.**
2. **I2 (Smart Insights)** — chi phí 0, khác biệt hoá rõ so với đối thủ.
3. **I4 (Family Challenge)** — chi phí thấp nếu EPIC-05 đã làm, giá trị tuỳ use-case.
4. I1, I6 — cân nhắc theo roadmap sau.
5. I5, I7 — hoãn, ROI thấp / rủi ro cao ở giai đoạn hiện tại.

**User đã chọn (AskUserQuestion)**: I3 + I2 + I4 đưa vào roadmap gần nhất — trùng khớp 100% với khuyến nghị ban đầu.

---

## Second opinion — tổng hợp từ 3 AI Agent độc lập

Đã chạy song song 3 agent đọc thẳng source code (không đọc chéo file này trước khi kết luận): `codex exec` (OpenAI), `agy --model gemini-3.1-pro-high` (Google Gemini), `claude --print` (Anthropic, phiên bản CLI riêng biệt). Toàn văn: `doc/task/review_codex.md`, `review_gemini.md`, `review_claude.md`. Các bug họ tìm thấy đã tách sang `todo/EPIC-00-critical-bugs.md`; phần dưới đây chỉ tổng hợp góc nhìn sản phẩm/ý tưởng.

### Đồng thuận cao (≥2/3 agent độc lập đề xuất giống nhau)
- **Weight Dashboard hợp nhất + quick-log** — đúng hướng EPIC-07 đã chọn từ trước khi hỏi ý kiến ngoài. Cả 3 đều gọi đây là nền tảng nên làm trước. → đã thêm T07.5 (quick-log FAB).
- **ETA / trend projection cục bộ (không cần AI/API)** — trùng khớp hoàn toàn với T07.3 đã có sẵn trong backlog.
- **KHÔNG nên build**: cả 3 agent đều tự nêu, độc lập, cùng 1 kết luận — **không làm social feed / cloud sync / tài khoản**. Lý do trùng nhau: kiến trúc hiện tại 100% local (Room+DataStore, không backend), thêm social/cloud đòi hỏi hạ tầng mới + rủi ro pháp lý (dữ liệu sức khỏe) trong khi ROI thấp cho 1 app BMI calculator. → xác nhận lại quyết định hoãn I5/I7 ở trên là đúng hướng.

### Ý tưởng mới, đáng cân nhắc thêm vào roadmap (chưa có trong danh sách I1-I7 gốc)
- **I8 — Export báo cáo PDF cho bác sĩ/HLV** (gemini + codex cùng đề xuất độc lập, claude gọi là "flagship differentiator"): biến app từ "công cụ tính BMI cho vui" thành "công cụ theo dõi sức khỏe nghiêm túc". Khác N4 (EPIC-08, đã có CSV) ở chỗ đây nhấn vào **PDF trình bày đẹp, dùng để đưa cho chuyên gia y tế** — định vị sản phẩm khác, không chỉ là tính năng xuất dữ liệu thô.
- **I9 — "Coach" cục bộ dựa trên rule, không phải AI thật** (codex: "Adaptive weekly plan"; gemini: "Offline-first Insights Engine"): mở rộng của I2, nhưng cụ thể hơn — gợi ý khoảng calories + tần suất check-in tuần tới dựa trên TDEE + xu hướng thực đo, thuần rule-based nên **chi phí vẫn bằng 0** giống I2 (không phải I7 — không gọi LLM API).
- **I10 — Health Connect sync** (gemini + claude cùng nêu, xếp rõ là "cần hạ tầng mới"): đã có sẵn trong backlog ở EPIC-09 T09.2, nhưng 2/3 agent độc lập cùng nhắc tới củng cố đây là hướng dài hạn đáng cân nhắc hơn Wear OS (I5).

### Đánh giá lại độ ưu tiên sau second opinion
Không đổi lựa chọn user đã chọn (I3/I2/I4) — nhưng khuyến nghị bổ sung **I8 (Export PDF)** vào nhóm cân nhắc kế tiếp, vì được 2/3 agent độc lập đề xuất và không tốn thêm hạ tầng (build trên EPIC-08 T08.2 đã có).
