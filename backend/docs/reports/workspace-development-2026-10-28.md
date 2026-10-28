# Phát lại cách kết hợp — 28/10/2026

Kết luận: chưa có lợi thế ổn định từ ba trọng số thử nghiệm. Giữ mặc định 0,5; cho người dùng chỉnh và lưu quyết định trước kỳ. Đợt này cải thiện khả năng chọn/chốt/đo đúng, chưa chứng minh tăng xác suất trúng.

## Phương pháp

- Nguồn: bản xuất chỉ đọc `/api/numbers` ngày 28/10, 81 bản ghi, 40 kỳ MEGA và 40 kỳ POWER đủ điều kiện sau `DrawQuality.history`. Một bản ghi POWER cách ly không được sử dụng. SHA-256 đầu vào và phiên bản mã nằm trong JSON cùng tên.
- Từng loại dùng 10 kỳ đầu khởi tạo, đánh giá 30 kỳ tiếp theo. Dự đoán mỗi kỳ chỉ nhận lịch sử trước kỳ đó; kiểm tra đủ kỳ liền trước theo lịch. Không dùng kết quả kỳ đích trong features hoặc lựa chọn seed.
- Hai thuật toán cố định: `temporal_learning` và `bayesian_graph`, cấu hình mặc định hiện tại. Hợp nhất nghịch đảo thứ hạng trên toàn miền số với trọng số thuật toán đầu 0,25 / 0,5 / 0,75; không giữ/bỏ số thủ công trong báo cáo.
- Mỗi nhóm gồm vé top sáu từ hai thuật toán và vé kết hợp, loại trùng. Vì có thể trùng, ngân sách thực tế là một đến ba vé; mỗi đối chứng dùng đúng ngân sách của nhóm tương ứng tại cùng kỳ. Báo cáo JSON lưu số vé từng kỳ, từng trọng số.
- Đối chứng tần suất: vé top sáu và các vé lấy mẫu theo tần suất; đối chứng ngẫu nhiên đều: các vé khác nhau. Seed cố định theo protocol/category/date/budget, không chọn từ kết quả.

## Mốc tham khảo ở trọng số mặc định 0,5

| Loại | Nhóm | Trung bình theo kỳ của số khớp TB/vé | Kỳ có vé ≥3 | Độ phủ TB số kết quả |
| --- | --- | ---: | ---: | ---: |
| MEGA | Hai thuật toán + kết hợp | 0,822 | 2/30 | 1,333 |
| MEGA | Tần suất, cùng ngân sách | 0,794 | 2/30 | 1,900 |
| MEGA | Ngẫu nhiên, cùng ngân sách | 1,000 | 3/30 | 2,400 |
| POWER | Hai thuật toán + kết hợp | 0,956 | 1/30 | 1,200 |
| POWER | Tần suất, cùng ngân sách | 0,822 | 1/30 | 1,767 |
| POWER | Ngẫu nhiên, cùng ngân sách | 0,828 | 3/30 | 1,900 |

Độ phủ là số kết quả xuất hiện trong hợp các vé, không phải số lượng ứng viên hay tổng lượt khớp lặp. Trung bình trên được tính bằng trung bình chỉ số mỗi kỳ, không gộp mọi vé thành một mẫu độc lập. Không có nhóm nào ở các trọng số thử nghiệm có vé ≥4 trên cửa sổ này.

## Giới hạn và tái lập

Đây là dữ liệu phát triển đã được xem, mẫu nhỏ và dùng cấu hình mặc định hiện tại; không tái hiện cấu hình production hoặc bộ kết hợp đã hiển thị trong quá khứ. Không đánh giá được can thiệp thủ công trước kỳ từ lịch sử dò vé cũ. Đối chứng ngẫu nhiên chỉ có một nhóm seed cố định mỗi kỳ; chưa có kiểm định xác nhận hoặc khoảng bất định. Không chọn trọng số/seed hay đổi mặc định dựa trên báo cáo này; cần đóng băng ứng viên và xác nhận ở các kỳ tương lai mới.

Tại `backend/`, với bản xuất JSON còn nguyên dữ liệu quality:

```powershell
mvn.cmd test '-Dworkspace.report.input=../tmp/live-draws-audit-2026-10-28.json'
```

`WorkspaceDevelopmentReportTest` chỉ chạy khi đặt thuộc tính trên, ghi JSON báo cáo, không đọc/ghi database production. Các lượt `mvn install` thông thường không chạy lại phát lại này. Có thể đặt `-Dworkspace.report.output=...` để đổi file báo cáo.
