# EPIC-03 — Reward-ad "Get Detailed Health Plan" — quyết định số phận 🐛/⚙️ (P1, 3 pts)

> **Status (2026-08-23): Đã CHỐT hướng (Option C — gộp vào Weight Dashboard) nhưng CHƯA implement.** EPIC-07 (Weight Dashboard) đã xong phần khung UI; phần "rewarded ad → unlock Advanced Insights" chưa làm trong pass này — lý do: cần test thật với Ad SDK (mạng, mediation, fill-rate) trong khi môi trường test tự động vừa phát hiện ads có thể che UI giữa lúc chạy instrumented test (xem ghi chú network-disable trong `HistoryActivityDashboardTest.kt`), nên cần thiết kế test riêng (mock/stub AdManager) thay vì test trên network thật. Còn nguyên trong backlog, chưa bị bỏ.

## Hiện trạng
- `ResultAct.kt:152-153`: gọi `setupRewardButton()` bị **comment out** với ghi chú "deferred until SDK adds showRewardedAd support".
- String `btn_detailed_plan` = "🎁 Get Detailed Health Plan" tồn tại trong `values/strings.xml:531-533` nhưng **không được reference ở bất kỳ layout nào** — dead resource.
- Theo `doc/AD.MD` mục "TOUCHPOINT TABLE": SDK 1.1.3 (đang dùng) **đã hỗ trợ** `AdManager.loadRewarded` + `showRewarded` — comment "deferred" trong code đã **outdated**, feature có thể làm được ngay bây giờ.
- VIP flow (`FVipManagement.kt`) đã dùng rewarded ad cho "watch ad → 3 days VIP" thành công → đã có pattern tham khảo sẵn trong repo, không phải làm từ đầu.

## Quyết định cần chốt (đưa vào AskUserQuestion cho user)
**Option A — Implement thật**: nút "Get Detailed Health Plan" trên ResultAct, xem rewarded ad xong → mở bottom sheet/dialog chi tiết hơn (vd. macro breakdown, weekly meal suggestion template, xu hướng dài hạn). Tận dụng lại `HealthTipAdapter`/`item_health_insights` đã có làm nền.
- Ưu: có thêm 1 rewarded ad touchpoint = tăng revenue; tận dụng string đã dịch sẵn 17 locale (đỡ công dịch).
- Nhược: cần thiết kế nội dung "detailed plan" thật sự có giá trị (không chỉ lặp lại BMR/TDEE đã hiện sẵn) — nếu nội dung nghèo nàn, user thấy bị lừa xem ad vô ích → review xấu.

**Option B — Xoá bỏ hẳn**: gỡ string `btn_detailed_plan` (17 locale) + xoá comment chết trong `ResultAct.kt`.
- Ưu: dọn code sạch, không nợ kỹ thuật.
- Nhược: mất 1 cơ hội rewarded-ad touchpoint tiềm năng.

**Option C (khuyến nghị)** — Gộp vào EPIC-07 Weight Dashboard: rewarded ad mở khoá "Advanced Insights" trong Weight Dashboard mới (trend prediction N2, so sánh giai đoạn) thay vì 1 màn hình rời. Tận dụng chung 1 nút CTA, tránh rời rạc thêm 1 màn hình nữa.

**Points**: 3 (chưa tính effort thực thi theo option được chọn — sẽ re-estimate sau khi chốt).
