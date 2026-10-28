# Kiểm tra dữ liệu phục vụ dự đoán ngày 28/10/2026

Đọc dữ liệu production qua `https://analyze-project.onrender.com/api/numbers` và `/api/analyze/user-history`. Không truy vấn SQL trực tiếp, không sửa database, thuật toán hoặc giao diện. Chi tiết thống kê và SHA-256 của bản xuất API trong JSON cùng tên.

## Dữ liệu hiện có

| Dữ liệu | MEGA | POWER |
| --- | ---: | ---: |
| Bản ghi kết quả | 40 | 41 |
| Kỳ hợp lệ theo DrawQuality | 40 | 40 |
| Khoảng ngày hợp lệ | 29/07–28/10/2026 | 28/07–27/10/2026 |
| Kỳ thiếu theo lịch trong khoảng trên | 0 | 0 |
| Vé đã dò | 13 | 19 |
| Vé hợp lệ, không trùng theo loại/ngày/bộ số | 13 | 17 |
| Số ngày có vé đã dò | 4 | 5 |

Không có revision trùng ngày trong dữ liệu kết quả đã xuất. 80 kỳ đủ điều kiện có `SOURCE_MATCHED` và fingerprint khớp; đây là khớp feed cộng đồng đã cấu hình, không phải xác minh trực tiếp với Vietlott. POWER ngày 28/09/2026, id 42, đang cách ly; không đưa vào phân tích.

Hai vé POWER không hợp lệ: id 36 ngày 24/10 chứa số 113; id 29 ngày 22/10 gồm sáu số 0. Không tự sửa hoặc xóa chúng. API dò vé hiện chưa kiểm tra đầy đủ miền số và tính khác nhau trước khi lưu; frontend chuyển ô trống thành 0.

## Đối chiếu vé đã chơi

Đếm lại số chính từ bộ số vé và kết quả hợp lệ, không sử dụng nhãn giải đã lưu làm nhãn huấn luyện:

| Số chính khớp | MEGA, 13 vé hợp lệ | POWER, 17 vé hợp lệ |
| --- | ---: | ---: |
| 0 | 7 | 9 |
| 1 | 4 | 6 |
| 2 | 2 | 1 |
| 3 | 0 | 1 |
| 4–6 | 0 | 0 |

Vé POWER id 39 ngày 24/10: `06 07 11 13 18 25`, khớp `06 07 18` với kết quả `06 07 18 41 44 50`. Không có một vé đã lưu khớp bốn số. Người dùng đã làm rõ trường hợp MEGA 21/10: vé id 26 khớp `10 22`, vé id 27 khớp `14 25`, tổng độ phủ là bốn số khác nhau trên hai vé. Người dùng cho biết hai vé từ hai thuật toán; database chưa lưu nguồn để kiểm chứng riêng thông tin đó. Không diễn giải độ phủ này thành một vé kết hợp khớp bốn số.

`UserTicket` chỉ có id, category, drawDate, numbers, prize, checkedAt. `checkedAt` được gán khi dò vé sau khi đã có kết quả; không chứng minh thời điểm mua hoặc chốt dự đoán. Không có trường nguồn vé, thuật toán, cặp thuật toán kết hợp, cấu hình hoặc liên kết snapshot. Prediction không dùng repository vé người dùng để học; dữ liệu này hiện phục vụ dò vé và lịch sử.

Ledger chính lưu vé đầu tiên cho từng thuật toán/kỳ; bộ kết hợp và các biến thể chưa được lưu riêng. Ba vé thuật toán đã chốt MEGA 28/10 đều khớp 0/6; không được coi vé `deep_stacking` là bộ kết hợp đang hiển thị trên trang.

## Khả năng sử dụng và bước tiếp theo

40 kỳ mỗi loại đủ bắt đầu một đánh giá thử nghiệm theo thời gian, nhưng phải dành lịch sử khởi tạo nên số kỳ đánh giá còn ít hơn 40. 30 vé hợp lệ thuộc chín cặp loại/ngày không phải 30 kỳ độc lập, và chưa xác định nguồn từng vé. Chưa đủ bằng chứng rằng học thêm từ vé người dùng hoặc một lần khớp bốn số sẽ nâng khả năng dự đoán.

1. Chặn vé sai trước khi lưu; loại vé sai khỏi thống kê và giữ nguyên bản cũ để đối chiếu.
2. Lưu cả ba bộ hiển thị trước kỳ, gồm bộ kết hợp, với nguồn/cặp thuật toán, cấu hình, thời điểm và history hash; giữ nguyên bản đầu tiên. Không sinh bù snapshot quá khứ.
3. Đánh giá riêng từng loại trên cùng kỳ và cùng ngân sách ba vé: từng vé, vé tốt nhất, tỷ lệ kỳ có vé khớp từ ba/bốn số; so với tần suất và ngẫu nhiên. Mọi dự đoán chỉ dùng lịch sử trước kỳ đích. Dữ liệu đã xem là mẫu phát triển, cần xác nhận trên kỳ tương lai mới.
4. Nếu bổ sung lịch sử kết quả, giữ provenance và kiểm tra xung đột/chất lượng trước khi nhập. Không coi tăng lượng dữ liệu là bảo đảm tăng số khớp.

Giữ bộ kết hợp để đánh giá khả năng khai thác hai ranking. Kỳ 21/10 đã được làm rõ là độ phủ của hai vé; xem [kế hoạch sau rà soát](../PREDICTION_WORKSPACE_PLAN.md). Chưa thay thuật toán hoặc tuyên bố cải thiện độ chính xác.
