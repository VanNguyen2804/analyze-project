# AI Lab: học từ vé khớp 1–2 số

Ngày cập nhật: 03/11/2026. Phiên bản Logistic mới: `ai-lab-dense-feedback-v2`.

## Thay đổi người dùng nhận được

Trang `/ai-lab` ghi nhận ngay vé khớp 1 số và 2 số: trung bình số khớp trên mỗi vé, tổng lượt khớp tích lũy, phân bố đúng 0–6 số, ba mức ≥1/≥2/≥3 và lịch sử lũy kế theo kỳ. Mặc định so sánh mức ≥1; chọn mức khác để so với tần suất và ngẫu nhiên trên cùng ngân sách năm vé/kỳ. Các mức ≥1/≥2/≥3 chồng lấp; phân bố đúng 0–6 không chồng lấp.

Logistic mới thực sự cập nhật trọng số từ kết quả các vé đã chốt, kể cả vé khớp 0, 1 và 2 số. Đây không chỉ là đổi giao diện. Mô hình trước đã học nhãn từng số trong lịch sử, nhưng chưa có bước học phản hồi từ các vé do chính nó chốt.

## Cách học phản hồi

1. Huấn luyện Logistic cơ sở trên lịch sử đã qua kiểm tra chất lượng, trước kỳ mục tiêu, như trước.
2. Chọn tối đa 60 kỳ phản hồi gần nhất của cùng loại MEGA/POWER và mô hình Logistic. Mỗi kỳ phải có đủ năm slot khác nhau, cùng phiên bản/cấu hình/lịch sử, snapshot chốt trước cutoff và có bộ đặc trưng đã đóng băng cho toàn bộ 45/55 số.
3. Đối soát lại từ dữ liệu kết quả hợp lệ hiện tại. Chỉ nhận kết quả đã đến 19:00 giờ Việt Nam, có ngày trước kỳ đích và nằm trong lịch sử huấn luyện hợp lệ. Không dùng settlement cũ khi kết quả đã bị rút lại. Snapshot lịch sử thiếu đặc trưng không được tự dựng đặc trưng mới để thay thế.
4. Mỗi kỳ tạo một bộ nhãn cho toàn bộ số chính: số xuất hiện mang nhãn 1, số không xuất hiện mang nhãn 0. Dùng **đặc trưng đã lưu trước kỳ quay**, không dùng đặc trưng tính sau kết quả. Số xuất hiện trên nhiều vé chỉ là một mẫu trong kỳ.
5. Replay gradient cross-entropy theo thứ tự thời gian, một bước/kỳ. Trọng số mất mát mặc định bằng 1; số thuộc ít nhất một vé đã chốt có thêm 0,25. Tốc độ học 0,05. Vì số đúng và sai cùng tham gia, một vé khớp 1–2 số vừa có phản hồi tích cực vừa có phản hồi sai; không gán cả vé là thành công hoặc chờ đủ ba số mới cập nhật.
6. Mỗi lần chạy bắt đầu lại từ mô hình cơ sở rồi replay có giới hạn. Polling/retry không cộng thưởng lặp; kết quả sửa hoặc rút lại được phản ánh khi tạo snapshot tiếp theo. Đây là tái trọng số trên dữ liệu đã biết, không phải tạo thêm các quan sát độc lập.

Snapshot mới đóng băng trọng số cuối cùng, chính sách/hệ số phản hồi, số kỳ/vé đã học, histogram khớp 0–6, lineage snapshot/số đã chọn/history hash/result fingerprint/feature hash và data hash. API overview chỉ trả tóm tắt phản hồi, không nhân bản toàn bộ lineage cho từng hàng. Số kỳ phản hồi bằng 0 nghĩa là chỉ có phần học lịch sử, chưa có feedback hợp lệ.

Các phiên bản cũ giữ nguyên. ID kỳ/mô hình/slot đã tồn tại không được chốt lại chỉ vì đổi code; phiên bản mới bắt đầu ở kỳ chưa có bản chốt. Tần suất suy giảm và hai đối chứng không dùng bước Logistic feedback. Cohort đánh giá vẫn tách loại, mô hình, modelVersion, codeVersion và configurationHash. Không sửa migration hoặc thuật toán xổ số chính.

## Cấu hình

| Biến | Mặc định | Miền hợp lệ |
| --- | --- | --- |
| `AI_LAB_FEEDBACK_LEARNING_RATE` | `0.05` | Hữu hạn, >0 và ≤0,2 |
| `AI_LAB_FEEDBACK_SELECTED_EXTRA_WEIGHT` | `0.25` | Hữu hạn, từ 0 đến 1 |
| `AI_LAB_FEEDBACK_MAXIMUM_DRAWS` | `60` | Số nguyên từ 1 đến 120 |

Hệ số được lưu trong configuration snapshot; thay đổi tạo cohort khác. Đây là cấu hình ban đầu, chưa được tối ưu hoặc chứng minh tăng số khớp.

## Ý nghĩa tiến bộ và giới hạn

Chỉ những kỳ có đủ năm vé đã đối soát hợp lệ được tính vào thống kê. Một kết quả bị rút lại loại cả kỳ khỏi mẫu so sánh và tính lại lũy kế. Tổng lượt số khớp đếm trên mọi vé, không phải số khác nhau, không phải một vé ghép từ nhiều kỳ, và không phải tiền thưởng. Trung bình số khớp/vé giúp tránh nhìn tổng tăng chỉ vì có nhiều kỳ hơn.

Các mốc 1–2 số là mô tả kết quả quan sát và đầu vào học. Không tự coi chúng là bằng chứng mô hình tốt hơn hoặc tự nâng mô hình thành ứng viên. Tiêu chí sàng lọc cũ ở mức ≥3 với ít nhất 60 kỳ liên tiếp và hai đối chứng vẫn được giữ ở phần đánh giá chi tiết. Kết quả khớp có thể tăng hoặc giảm qua các kỳ; hiện chưa có bằng chứng bước feedback cải thiện khả năng trúng.

Kiểm thử bao gồm vé chỉ khớp 0/1/2 vẫn làm thay đổi trọng số; tái lập và đảo thứ tự input; hủy hiệu lực kết quả; thiếu batch/đặc trưng; cutoff/ngày tương lai; khác category; không đếm lặp số qua nhiều vé; đóng băng lineage/weights khi capture; lũy kế và mẫu số thống kê; thao tác chọn mức trên giao diện và không nâng trạng thái ứng viên từ mốc hai số.

Kiểm chứng ngày 03/11: Angular production build và Maven `clean install`/`install` đạt; 52 kiểm thử Angular trên Edge headless đạt. Java phát hiện 118 kiểm thử, 110 đạt và tám kiểm thử có điều kiện bỏ qua. Typecheck, Prettier trên frontend thay đổi và `git diff --check` đạt; 24 assets Angular khớp byte trong JAR. Chưa chạy PostgreSQL thật hoặc triển khai production trong thay đổi này; không có migration mới. Còn cảnh báo budget CSS AI Lab/Java unchecked có sẵn. Các kiểm thử xác minh hành vi học, chưa đo được hiệu quả dự đoán tương lai.
