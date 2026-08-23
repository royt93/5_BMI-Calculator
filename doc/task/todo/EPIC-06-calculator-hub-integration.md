# EPIC-06 — Calculator Hub integration ⚙️ (P2, 8 pts)

## Hiện trạng
4 calculator (`BmrCalculatorActivity`, `TdeeCalculatorActivity`, `IdealWeightCalculatorActivity`, `BodyFatCalculatorActivity`) hoàn toàn **one-shot, stateless**: không import `BmiRepository`/`BmiDao`, không đọc profile hiện tại, không lưu kết quả, không liên kết ngược History/ResultAct. User phải gõ lại height/weight/age mỗi lần mở.
Song song đó, `ResultAct.calculateAndDisplayInsights()` (dòng 505-520) **tự tính lại** BMR/TDEE/ideal-weight/water-intake bằng cùng `CalculatorUtils` — 2 luồng code độc lập tính cùng công thức, không có source of truth chung. Body Fat là số duy nhất tính ở calculator riêng nhưng **không bao giờ** lưu vào `BmiRecord.bodyFatPercentage` (luôn `null` — `ResultAct.kt:545`).

## T06.1 — Prefill từ profile hiện tại (3 pts)
Cả 4 calculator đọc `BmiRepository.getCurrentProfile()` + bản ghi BMI gần nhất → prefill height/weight/age mặc định (user vẫn sửa được), giảm friction nhập liệu lặp lại.

## T06.2 — Lưu kết quả Body Fat vào BmiRecord (3 pts)
Khi tính Body Fat từ `BodyFatCalculatorActivity`, cho phép "Lưu vào lịch sử" → ghi `bodyFatPercentage` vào bản ghi mới hoặc update bản ghi gần nhất cùng ngày. Mở khoá được trend body-fat theo thời gian (liên quan N5 EPIC-08).

## T06.3 — Cross-link điều hướng (2 pts)
Từ `ResultAct` insight card (BMR/TDEE/Ideal Weight hiện đang chỉ hiển thị số) → thêm nút "Xem chi tiết" điều hướng sang calculator tương ứng đã prefill sẵn, thay vì 2 nơi hiển thị con số tách biệt không liên kết.
