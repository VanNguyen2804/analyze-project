# AI Lab — cập nhật 10/11/2026

Thêm mô hình thử nghiệm `logistic_feedback_coverage`, hiển thị là **Logistic học phản hồi + độ phủ (10/11)**.

Mô hình dùng cùng Logistic cơ sở và dữ liệu phản hồi hợp lệ của bản Logistic hiện tại, sau đó ghép 5 vé bằng `quad-coverage-v1`. Đặc trưng phản hồi phải được lưu trước kỳ quay; kết quả sửa hoặc rút được kiểm tra lại. Nếu chưa có phản hồi hợp lệ, mô hình chỉ học lịch sử và báo 0 kỳ phản hồi.

Mỗi mô hình vẫn có ngân sách 5 vé/kỳ. Tổng số vé toàn Lab tăng vì thêm một mô hình; không dùng tổng số vé trúng toàn Lab để kết luận cải thiện. Bản mới được so sánh ghép cặp với `logistic_coverage` không feedback, chỉ khi cùng kỳ, phiên bản mã, lịch sử và cấu hình ngoài feedback. Snapshot cũ giữ nguyên; mô hình mới có ID riêng và không chốt lại khi chạy lặp.

Kiểm thử cố định thời gian tối 10/11 xác nhận tạo đúng 5 vé MEGA cho 11/11/2026 ngay cả khi các mô hình cũ đã chốt. Kiểm thử phản hồi xác nhận trọng số bản mới thực sự dùng feedback; kiểm thử ghép cặp loại lịch sử không khớp. Đây là kiểm chứng hành vi, chưa phải bằng chứng tăng khả năng trúng.

## Đưa lên môi trường chạy

Triển khai cả backend và frontend từ cùng bản mã, trước cutoff kỳ 11/11 (18:00 giờ Việt Nam theo giao thức hiện tại). Giữ `AI_LAB_ENABLED=true`, `AI_LAB_CHALLENGERS_ENABLED=true`. Scheduler hiện có chạy AI Lab; có thể dùng nút **Chốt kỳ & đối soát** rồi kiểm tra trạng thái `SAVED` của mô hình mới, ngày đích 11/11/2026 và đủ 5 vé. Không sửa snapshot nếu đã lưu.

Nếu triển khai sau cutoff, hệ thống sẽ chọn kỳ hợp lệ tiếp theo; không tạo bù dự đoán cho 11/11. Chưa triển khai production trong phiên làm việc này.
