# Đối chiếu POWER 29/10/2026

Kiểm tra chỉ đọc API production và code hiện tại; không thay thuật toán, giao diện, cấu hình hoặc dữ liệu production. Các JSON đầu vào được lưu cục bộ tại `tmp/power-review-2026-10-29/` (không đưa vào Git).

## Kết quả và năm vé đã dò

API trả kết quả `09 29 33 38 45 46`, số phụ `53`. Bản ghi id 115 có trạng thái `SOURCE_MATCHED`, khớp feed cộng đồng `vietvudanh/vietlott-data`, kỳ `01417`; chưa xác minh độc lập với Vietlott. Scheduler đã nhận kết quả mới, không báo nguồn POWER chậm. Trạng thái tổng `CONFLICT` liên quan bản ghi 28/09 id 42 đang cách ly vì không tìm thấy trong nguồn, không phải xung đột kỳ 29/10.

| Id vé | Dãy đã lưu | Số chính khớp |
| --- | --- | --- |
| 79 | 07 11 14 18 23 27 | 0 |
| 80 | 07 09 18 27 31 50 | 1: 09 |
| 81 | 07 14 18 27 31 50 | 0 |
| 82 | 09 12 14 36 37 51 | 1: 09 |
| 83 | 08 15 29 33 43 49 | 2: 29, 33 |

Tính lại bằng giao tập số vé với kết quả: bốn lượt khớp, ba số khác nhau `09 29 33`; độ phủ 3/6, vé tốt nhất 2/6. Năm vé dùng 19 số khác nhau, không có `38 45 46`. Không có vé nào khớp từ ba số. Ba vé đầu dùng chung chỉ chín số; vé 80 và 81 trùng 5/6. Các số `07 14 18 27` xuất hiện trên ba vé mỗi số. Đây là mức tập trung quan sát được, chưa chứng minh giảm trùng sẽ tăng số khớp trong các kỳ tới.

## Snapshot đã lưu trước kỳ

Ledger chính có ba bản ngày 29/10, tạo tối 27/10 theo giờ Việt Nam: `temporal_learning` là vé 79, khớp 0; `bayesian_graph` và `deep_stacking` cùng là vé 80, khớp 1. Hai thuật toán sau đưa ra cùng một vé, không bổ sung độ phủ cho nhau trong kỳ này. Vé 80 chứa bốn số chính kỳ 27/10 (`09 18 27 31`) và số phụ kỳ đó (`50`); kỳ 29/10 chỉ lặp lại số chính `09`.

AI Lab đã lưu từ 27/10: `logistic-0` trùng vé 83, khớp hai số; `recency-1` trùng vé 82, khớp một số. Đây là đối chiếu bộ số trùng snapshot có trước kỳ, không chứng minh provenance quyết định của người dùng. Cả mười vé Lab ngày 29/10 không có vé khớp từ ba số; không chọn riêng vé tốt nhất để tuyên bố mô hình thắng.

`/api/prediction-workspace/history?category=POWER` trả `batches: []`. Năm vé nằm trong lịch sử dò sau khi có kết quả, chưa có bản chốt workspace tương ứng; không gán vé 81 cho bộ kết hợp hoặc suy ra thời điểm mua/chốt.

## Lịch sử và giới hạn đánh giá

42 bản ghi POWER, gồm 41 kỳ đủ điều kiện `SOURCE_MATCHED` và một bản cách ly. Có 40 kỳ trước 29/10. Trong mười kỳ trước kỳ đích, `29 33 38 46` đều không xuất hiện. Số kỳ vắng sau lần xuất hiện gần nhất tương ứng 25, 22, 13, 15. Điều này mô tả vì sao tập trung số gần đây có thể bỏ sót kết quả kỳ này, không phải bằng chứng số gan sắp ra.

API relationship-learning tính đến hết 29/10 báo phát lại theo thời gian trên 36 kỳ: số khớp trung bình của chính sách 0,778/vé, bằng tần suất nền 0,778; một kỳ có vé khớp từ ba số, không có vé khớp từ bốn. Đây là đánh giá nội bộ dùng cấu hình hiện tại trên lịch sử đã xem, không phải 36 snapshot production đã chốt. `predictedNumbers` của response này dùng cả kết quả 29/10, không dùng làm dự đoán cho chính kỳ 29/10.

## Lỗi ý nghĩa của phần giải thích trên /analyze

Gọi `/api/analyze/official-draw-analysis` với cùng `category=POWER&date=2026-10-29` và lần lượt `temporal_learning`, `bayesian_graph`, `deep_stacking`: payload giống hoàn toàn sau khi bỏ `algorithmName`. Code `AnalyzeService.analyzeOfficialDraw` chỉ dùng `algorithm` để đặt nhãn; điểm/xếp hạng tính bằng công thức tần suất và momentum chung, không dùng ranking hoặc snapshot của thuật toán được chọn.

Lý do cho số đã ra được ghép từ hai mẫu theo điều kiện `drawGap > 10`; các câu như “điểm rơi cực tốt” hoặc “nhịp dao động điều hòa” không phải kết quả kiểm định. Trường `probabilityPercent` là sigmoid của điểm thủ công, chưa phải xác suất trúng được hiệu chuẩn. API không trả `allNumberDetails`, trong khi phần phân tích số trượt ở frontend đọc trường này; vì vậy các chỉ số số trượt thiếu dữ liệu và dùng fallback. Chưa sửa các lỗi này trong lần phân tích.

Ưu tiên tiếp theo: làm phần giải thích bám snapshot/ranking đúng thuật toán, đo mức trùng của nhóm vé và tiếp tục đối chứng cùng ngân sách trên kỳ tương lai. Không đổi trọng số hoặc chuyển sang ưu tiên số gan dựa vào riêng kỳ này.

## Nguồn và kiểm chứng

- GET `/api/numbers?category=POWER`, `/api/analyze/history?category=POWER`, `/api/analyze/user-history`.
- GET `/api/forecasts/overview?category=POWER&size=50`, `/api/ai-lab?category=POWER`, `/api/prediction-workspace/history?category=POWER`.
- GET `/api/analyze/official-draw-analysis`, `/api/analyze/relationship-learning?category=POWER&date=2026-10-29`, `/api/lottery-sync/status`.
- SHA-256 `numbers.json`: `6ad01864893952ca84fab68b7901cd898f0e107386d6a7bf34e1bf1d83b970e0`.
- SHA-256 `user-history.json`: `652a11b199797995da5a7a3afe618f2ef4c56da017c97e83074697ad3fa1a0b9`.
- SHA-256 `analysis-algorithm-comparison.json`: `4d1cbaea2f06a925511068788d795805a433a51cb05020bc3208c207bc879c9b`.

Kiểm tra tập hợp số bằng Python, so sánh ba response API và đối chiếu code. Chỉ thêm tài liệu, không chạy build hoặc gọi đây là kiểm thử PostgreSQL trực tiếp.
