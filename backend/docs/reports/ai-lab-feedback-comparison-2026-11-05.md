# AI Lab: đối chứng feedback và đánh giá xác suất — 05/11/2026

- Thêm `logistic_no_feedback`: cùng mô hình Logistic học lịch sử, bỏ bước replay feedback. Mỗi bản chốt 5 vé, dùng cùng seed lấy mẫu. Giữ bản Logistic có feedback và recency để theo dõi.
- So sánh ghép cặp chỉ nhận kỳ đủ 5 vé mỗi bản đã đối soát, cùng loại, ngày, modelVersion, codeVersion, historyHash và cấu hình sau khi bỏ riêng feedbackPolicy. Kết quả thực tế phải giống nhau. Báo số kỳ ghép cặp, số khớp trung bình mỗi vé và chênh lệch có/không feedback; đây là thống kê mô tả.
- Brier score, log loss và bảng hiệu chuẩn dùng toàn bộ điểm Logistic đã đóng băng trước kỳ cùng kết quả đối soát mới nhất. Mỗi số tính một lần mỗi kỳ, không nhân theo năm vé. Đối chứng xác suất đồng đều là 6/45 hoặc 6/55. Bỏ kỳ thiếu điểm, trùng số hoặc điểm không hợp lệ; không suy diễn điểm cho snapshot cũ. Không áp các chỉ số xác suất lên điểm recency.
- Bảng hiệu chuẩn chia 10 khoảng xác suất; giá trị 1 thuộc khoảng cuối. Log loss chặn xác suất tại 1e-15 và 1-1e-15 để tránh vô hạn. Các số cùng kỳ không phải mẫu độc lập.
- Giữ nguyên tiêu chí ứng viên hiện tại, diễn giải rõ 50% là ngưỡng sàng lọc cấu hình sẵn. Không tự đưa mô hình thử nghiệm vào thuật toán chính.
- Giữ nguyên snapshot cũ, không tạo bù quá khứ. Fingerprint mã đổi nên kết quả phiên bản mới được tách nhóm; so sánh ghép cặp tích lũy từ các kỳ mới đủ điều kiện. Không có kết luận tăng khả năng dự đoán từ thay đổi này.

Kiểm tra: 19 kiểm thử backend thuộc AiLabTest, AiLabFeedbackTest, AiLabProgressTest, AiLabProbabilityTest và AiLabHttpTest đạt. Angular production build và typecheck đạt, còn cảnh báo CSS AI Lab vượt budget có sẵn. Chưa triển khai production.
