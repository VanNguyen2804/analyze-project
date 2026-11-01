# Đối chiếu MEGA 01/11/2026 — Bảy lượt dò và thử nghiệm giảm trùng

Kiểm tra chỉ đọc API production sau khi người dùng phản ánh bảy dãy đã chơi không trúng. Không sửa thuật toán, cấu hình, vé hoặc kết quả production. Các bản xuất API và phép tính được lưu cục bộ trong `tmp/mega-review-2026-11-01/`.

## Kết quả và các vé người dùng đã dò

API đã nhận kết quả MEGA `05 09 17 18 34 36`, kỳ nguồn `01583`, trạng thái `SOURCE_MATCHED` với feed cộng đồng `vietvudanh/vietlott-data`. Đây là khớp nguồn đã cấu hình, chưa phải xác minh độc lập với Vietlott. Scheduler MEGA báo `SUCCESS`, không chậm nguồn. Trạng thái `CONFLICT` của POWER liên quan bản ghi cũ 28/09 và không ảnh hưởng kết quả MEGA này.

| Id | Dãy đã lưu | Số chính khớp |
| --- | --- | --- |
| 90 | 09 15 22 27 40 41 | 1: 09 |
| 91 | 06 07 21 22 40 41 | 0 |
| 92 | 06 15 21 22 40 41 | 0 |
| 93 | 04 15 21 38 39 44 | 0 |
| 94 | 09 15 22 27 40 41 | 1: 09 |
| 95 | 02 03 11 16 33 36 | 1: 36 |
| 96 | 06 10 25 38 43 44 | 0 |

Bảy lượt dò chứa sáu bộ số khác nhau: id 90 và 94 trùng nhau. Chưa xác định người dùng mua trùng hay dò lại cùng vé; không tự xóa bản ghi hoặc suy ra chi phí mua. Toàn bộ dùng 22 số khác nhau nhưng chỉ phủ `09 36`, thiếu `05 17 18 34`; không có vé khớp từ ba số.

## Đối chứng đã chốt trước kỳ

Production hiện đã có `mega-portfolio-v1`, khác trạng thái chưa triển khai ghi trong báo cáo phát triển trước đó. Snapshot kỳ 01/11 được tạo lúc `2026-11-01T10:43:07.467186198Z` (17:43 giờ Việt Nam), học đến 30/10 và đã đối soát. Cả bốn nhóm cùng ba vé, cùng kỳ và cùng thời điểm chốt:

| Nhóm | Số khớp từng vé | TB/vé | Phủ số kết quả | Số đã chọn khác nhau |
| --- | --- | ---: | ---: | ---: |
| Giảm trùng | 1, 1, 0 | 0,667 | 2/6 | 18 |
| Cách ghép cũ | 1, 2, 1 | 1,333 | 2/6 | 9 |
| Tần suất + giảm trùng | 0, 1, 1 | 0,667 | 2/6 | 18 |
| Ngẫu nhiên | 0, 0, 1 | 0,333 | 1/6 | 15 |

Giảm trùng kém cách ghép cũ ở số khớp trung bình và vé tốt nhất trong kỳ này, dù phủ nhiều số đã chọn hơn; cả hai chỉ phủ hai số kết quả. Mọi nhóm đều không có vé khớp từ ba số. Đây là một kỳ đo, chưa xác nhận lợi thế hoặc bất lợi dài hạn; đối chứng ngẫu nhiên chỉ là một bộ seed đã định trước. Không chọn lại seed/trọng số từ kết quả đã biết.

Ba vé thử nghiệm khớp chính xác các bộ id 94–96. Bộ đầu trùng dãy gốc của `temporal_learning` (id 90); không cộng việc cùng một bộ số xuất hiện ở hai nguồn thành hai dự đoán độc lập. Workspace vẫn trả `batches: []`; lịch sử dò và việc bộ số khớp snapshot chưa chứng minh nguồn quyết định mua của người dùng.

## Xếp hạng trước kỳ và giới hạn của thay đổi phân bổ

Báo cáo đọc đúng snapshot `MEGA:2026-11-01:temporal_learning`, tạo 30/10, lịch sử đến 30/10; không tính lại ranking bằng kết quả 01/11. Thứ hạng của các số đã ra: `05: 41`, `09: 6`, `17: 35`, `18: 34`, `34: 40`, `36: 10`. Bốn số bị bỏ sót nằm ở thứ hạng 34–41 của mô hình gốc. Đây là mô tả ranking đã lưu, không phải bằng chứng số xếp thấp sẽ có lợi thế trong kỳ tới, và không mặc nhiên coi ranking gốc là toàn bộ ranking của snapshot thử nghiệm.

`rank-portfolio-v1` giữ nguyên nguồn xếp hạng, chỉ đổi cách chọn ba vé để giảm trùng. Trong kỳ này, việc tăng độ phủ số đã chọn không bù được các số kết quả bị bỏ sót; không thể gọi thay đổi này là cải thiện chất lượng dự đoán. Chưa có cơ sở tăng trọng số, ưu tiên số gan hoặc đổi mô hình chỉ theo một kỳ.

AI Lab đã đối soát đủ năm vé mỗi mô hình cho kỳ 01/11: Logistic khớp `2, 2, 0, 2, 1`; Recency khớp `1, 0, 1, 1, 0`. Không mô hình nào có vé khớp từ ba số. Vé id 93 trùng `logistic-2`, nhưng không được dùng riêng vé này hoặc chọn vé tốt nhất sau kỳ để đại diện cho mô hình.

Kết luận hiện hành: thử nghiệm chưa chứng minh cải thiện khả năng dự đoán. Ghi nhận thất bại kỳ đầu trong ledger hiện có, tiếp tục đo bằng snapshot tự động; không cần người dùng mua vé thật để cung cấp nhãn đánh giá. Phân tích ứng viên mới phải tách chất lượng ranking khỏi cách phân bổ vé và kiểm tra cùng ngân sách trên các kỳ tương lai, không tối ưu lại vào kỳ vừa xem.

## Nguồn và kiểm chứng

- GET `/api/numbers`, `/api/analyze/user-history`, `/api/mega-portfolio?page=0`, `/api/prediction-workspace/history?category=MEGA`.
- GET `/api/ai-lab?category=MEGA`, `/api/forecasts/overview?category=MEGA&page=0&size=50`, `/api/lottery-sync/status`.
- GET `/api/analyze/draw-report?category=MEGA&date=2026-11-01&algorithm=temporal_learning`.
- Python tính lại giao tập từng vé, loại trùng khi tính số bộ khác nhau và xác nhận số khớp của cả bốn nhóm khớp API. Không gọi POST hoặc tính bù dự đoán quá khứ.
- SHA-256 `numbers.json`: `8a54af9bcafd02f5acf369cd4a6b5e25a31061ac788db8a98ee18fb5e0cd853f`.
- SHA-256 `tickets.json`: `a18aa80dbeaad64d7ac5ced2d7eb594a6cf65e1d90870fbfdd14b270d42c26d5`.
- SHA-256 `experiment.json`: `4de20bd0ce2f93f693403d82b8610fdd84c3a0faa1ef6cc2a42eddfa2c4ece1d`.
- SHA-256 `draw-report.json`: `5de070147e911482ac52a0277f39716a6af3457e6b114d35ffdb115ab6ab22e1`.

Chỉ bổ sung tài liệu, không chạy lại build ứng dụng hoặc gọi đây là kiểm thử PostgreSQL trực tiếp.
