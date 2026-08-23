# EPIC-07 — Weight Dashboard hợp nhất ✨ FLAGSHIP (P0, 13 pts)

> **Status (2026-08-23): T07.1–T07.5 ĐÃ XONG + có test.** `HistoryActivity` giờ là dashboard hợp nhất (series switcher BMI/Weight/Height, goal row + ETA, empty state, quick-log FAB). Goal-line bug (dùng chiều cao bản ghi đầu tiên) đã fix — chuyển sang dùng chiều cao bản ghi **mới nhất**, và series Weight vẽ thẳng `goalWeight` không cần convert. Phần rewarded-ad "Advanced Insights" (gộp từ EPIC-03) **CHƯA làm** — xem `EPIC-03-reward-ad-detailed-plan.md` để biết lý do hoãn. Test: 6 unit test ETA regression (`CalculatorUtilsTest.kt`) + 3 instrumented test (`HistoryActivityDashboardTest.kt`) — tất cả pass trên `Pixel_10_Pro_XL(AVD)`.

> User xác nhận đây là tính năng thích nhất. Ưu tiên cao nhất toàn backlog.

## Vấn đề cốt lõi (3 màn hình rời rạc cho cùng 1 nhu cầu)
1. `HistoryActivity.kt:114-152` — chart **BMI** theo thời gian. Có `LimitLine` xấp xỉ "goal BMI" nhưng **tính sai**: luôn dùng chiều cao của bản ghi **đầu tiên** làm mẫu số cho mọi điểm (dòng 139-151) → nếu user có nhiều bản ghi với chiều cao khác nhau (trẻ em đang lớn, hoặc nhập sai 1 lần) thì goal-line lệch.
2. `TrackerBottomSheet.kt:76-179` — chart **weight/height** riêng qua tab, có min/max/latest/trend 30 ngày. Không có goal line, không có BMI, không liên kết với #1.
3. Goal card (set/xem mục tiêu cân nặng) nằm ở `ResultAct.kt:219-348` — màn hình thứ 3, tách khỏi cả 2 chart trên.

## T07.1 — Thiết kế 1 màn hình Weight Dashboard duy nhất (5 pts)
Thay vì 2 UI riêng, gộp thành 1 màn hình (có thể là bản nâng cấp của `HistoryActivity` hoặc màn hình mới) với:
- Chọn series hiển thị: Weight / BMI / Body Fat% (nếu có, từ T06.2) / Height — segmented control hoặc chip, không cần bottom sheet riêng nữa.
- Goal-weight line vẽ đúng: convert BMI↔weight tại **đúng chiều cao của từng bản ghi**, không dùng chiều cao cố định.
- Vùng màu theo BMI category (xanh/vàng/đỏ) nền chart, giúp nhìn trực quan hơn con số LimitLine đơn thuần.

## T07.2 — Gộp Goal Card vào Dashboard (3 pts)
Chuyển "set/xem goal weight" từ `ResultAct` sang chính Weight Dashboard (giữ shortcut set nhanh ở ResultAct nếu cần, nhưng nguồn sự thật + progress bar hiển thị đầy đủ nằm ở đây).

## T07.3 — Trend & ETA prediction (3 pts)
Linear regression đơn giản trên N bản ghi gần nhất (không cần ML/API) → "Với tốc độ hiện tại, đạt mục tiêu trong khoảng N ngày/tuần". Hiện tại `TrackerBottomSheet` chỉ có diff `latest - first`, chưa có dự đoán.
**Cảnh báo UX**: cần đủ ≥3-5 điểm dữ liệu trải dài đủ ngày mới dự đoán đáng tin — có state rỗng/thiếu dữ liệu rõ ràng, tránh dự đoán vô nghĩa từ 2 điểm.

## T07.4 — Empty/loading state nhất quán (2 pts)
Cả 2 UI cũ hiện xử lý "chưa có dữ liệu" khác nhau (Explore không xác nhận chi tiết — cần audit khi build). Dashboard mới cần 1 empty-state chuẩn: "Chưa có dữ liệu, hãy tính BMI lần đầu".

## T07.5 — Quick-log cân nặng (không qua wizard đầy đủ) (NEW — 3/3 AI agent độc lập đều đề xuất, ưu tiên cao)
**Nguồn**: cả 3 review (codex, gemini, claude) đều độc lập đề xuất ý này ở dạng khác nhau ("goal-aware weight log", "manual weight-log entry"). Đồng thuận 3/3 hiếm khi xảy ra → tín hiệu mạnh.
**Vấn đề**: hiện tại **cách duy nhất** để thêm 1 điểm dữ liệu vào chart là chạy lại toàn bộ wizard MainAct → ResultAct → Save (nhập lại tuổi/chiều cao/cân nặng/giới tính). Muốn cân nhanh mỗi sáng, user phải qua đủ các bước không cần thiết (tuổi/chiều cao hiếm khi đổi).
**Fix**: thêm nút "+" nổi (FAB) ngay trong Weight Dashboard → dialog chỉ hỏi cân nặng (dùng lại height/age/gender của bản ghi gần nhất), lưu thẳng `BmiRecord` mới. Đây là thay đổi có tác động lớn nhất tới việc chart "có dữ liệu để vẽ" — trực tiếp giải quyết gốc rễ vấn đề "2 chart rời rạc, ít dữ liệu" nêu ở đầu epic này.
**Points**: 3 (cộng vào tổng epic → 16 pts, cập nhật `INDEX.md`)

## Phụ thuộc / liên quan
- **EPIC-00 T00.1, T00.3** (bug goal-weight tăng cân + ngưỡng BMI lệch nhau): **PHẢI fix trước** khi build Dashboard — nếu không, dashboard mới sẽ hiển thị lại đúng các bug này ở quy mô lớn hơn (goal line + vùng màu BMI category dùng chung logic đang sai).
- **Tô màu xu hướng không được giả định "giảm luôn tốt"** (codex phát hiện, `HistoryActivity.kt:264-275` + `TrackerBottomSheet.kt:101-117` hiện luôn tô xanh khi giảm/đỏ khi tăng bất kể hướng mục tiêu) — T07.1 khi thiết kế vùng màu theo BMI category cần tính luôn theo **hướng goal** (giảm cân → giảm=xanh; tăng cân/gain muscle → tăng=xanh), không hardcode 1 chiều.
- EPIC-05 (multi-profile): Dashboard phải lọc đúng theo profile đang chọn — nếu multi-profile UI chưa xong, dashboard vẫn hoạt động đúng với profile mặc định hiện tại (không block nhau, nhưng nên làm EPIC-05 trước hoặc song song để tránh phải sửa lại query 2 lần).
- EPIC-06 T06.2 (lưu body-fat): cần xong trước nếu muốn series "Body Fat%" trong dashboard có dữ liệu thật thay vì luôn trống.
