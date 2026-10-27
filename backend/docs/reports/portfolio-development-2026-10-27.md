# Thử nghiệm giảm trùng lặp vé ngày 27/10/2026

Kết luận: tăng độ phủ nhưng chưa cải thiện ổn định số khớp hoặc số kỳ có vé khớp từ ba số. Giữ bộ tạo vé hiện tại làm mặc định; bộ mới chỉ là lựa chọn thử nghiệm. Không chỉnh tham số theo kết quả báo cáo này.

## Phương pháp

- Dữ liệu: snapshot `src/main/resources/data/lottery_numbers.json`; chỉ các revision đủ điều kiện qua `DrawQuality.history`. SHA-256 đầu vào nằm trong báo cáo JSON cùng tên.
- Loại riêng MEGA/POWER, sắp ngày tăng dần; dùng 10 kỳ đầu làm lịch sử khởi tạo. Mỗi dự đoán chỉ nhận các kỳ trước ngày đích. Loại ngày 27/10/2026 trở đi khỏi toàn bộ đánh giá để không tối ưu trực tiếp vào kỳ người dùng vừa phản ánh.
- Mỗi phương pháp có cùng ngân sách 3 vé/kỳ. Xếp hạng chính là `temporal_learning` với tham số mặc định, không đọc database/config production. Tái tạo đúng ba vé đầu của wheel cũ từ cùng nhóm 10 ứng viên để so sánh.
- Ứng viên `rank-portfolio-v1`: giữ top 6 làm vé đầu, lấy 1.600 ứng viên theo trọng số `0.5 + rankUtility`, chọn tiếp bằng tổng `rankUtility - 0.8 * exposure`. Hai vé bất kỳ không trùng quá 3 số. Rank utility giảm tuyến tính từ 1 theo thứ hạng; seed cố định theo miền số và ranking. Không loại số theo tổng, chẵn/lẻ, vùng hoặc kết quả kỳ trước. Ngân sách tối đa vẫn 25 như cũ, người dùng tự chọn số vé.
- Đối chứng: cùng bộ tạo vé mới với xếp hạng tần suất; và 3 vé ngẫu nhiên đều khác nhau với seed cố định theo loại/ngày. Đối chứng ngẫu nhiên chỉ có một lần lấy mẫu mỗi kỳ.

## Kết quả phát triển

Mỗi loại có 39 kỳ hợp lệ, đánh giá 29 kỳ sau khởi tạo.

| Loại | Phương pháp | Số khớp trung bình/vé | Kỳ có vé khớp ≥3 | Độ phủ trung bình/3 vé |
| --- | --- | ---: | ---: | ---: |
| POWER | Wheel cũ | 0.782 | 3/29 | 9.00 |
| POWER | Giảm trùng lặp | 0.782 | 1/29 | 18.00 |
| POWER | Tần suất + giảm trùng | 0.724 | 1/29 | 17.90 |
| POWER | Ngẫu nhiên đều | 0.644 | 0/29 | 16.66 |
| MEGA | Wheel cũ | 0.943 | 1/29 | 9.00 |
| MEGA | Giảm trùng lặp | 0.839 | 2/29 | 17.90 |
| MEGA | Tần suất + giảm trùng | 0.874 | 2/29 | 17.83 |
| MEGA | Ngẫu nhiên đều | 0.747 | 3/29 | 15.79 |

Đây là phát lại trên lịch sử phát triển đã được xem trước, mẫu nhỏ, không phải tập kiểm tra giữ kín hoặc dự đoán đã chốt trước kỳ. Không chứng minh lợi thế với ngẫu nhiên; tăng độ phủ không có nghĩa là gom đủ ba số vào cùng một vé. Chưa có kiểm định xác nhận hay khoảng tin cậy để kết luận mô hình hơn nhau.

## Tái lập

Từ thư mục `backend`:

```powershell
mvn.cmd test '-Dportfolio.report=docs/reports/portfolio-development-2026-10-27.json'
```

Lệnh chạy test và xuất báo cáo phát triển; không kết nối hoặc ghi database production. Test xuất báo cáo mặc định được bỏ qua nếu không đặt `portfolio.report`. JSON chứa số liệu từng kỳ; không tự thay thuật toán mặc định hoặc ghi đè forecast đã chốt. Vé thử nghiệm trong giao diện cũng chưa được lưu thành ledger dự đoán tương lai riêng.

## Sửa lỗi liên quan

Request dự đoán cũ bị hủy khi chọn loại xổ số/thuật toán mới. Giao diện từ chối response có category khác request, xóa payload cũ khi bắt đầu tải, và hủy request biến lệch cũ. Đây là lỗi có thể khiến kết quả trả chậm của MEGA ghi đè POWER; chưa xác định đây là nguồn gốc ba vé người dùng đã nhập.

## Bước xác nhận tiếp theo

Muốn cân nhắc thay mặc định cần ledger riêng cho nhóm vé thử nghiệm, chốt trước kỳ, cùng ngân sách vé và đối chứng; ghi trước chỉ số và thời điểm đánh giá trên kỳ tương lai mới. Không gọi lại mẫu phát triển này là dữ liệu kiểm tra độc lập.
