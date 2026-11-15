# Trang dự đoán từ AI Lab

`/analyze` đọc các bộ năm vé AI Lab đã lưu cho đúng loại MEGA/POWER và kỳ tới. Trang không gọi luồng tạo hai thuật toán, ghép bộ số hoặc chốt vé người dùng. Có nút sao chép từng dãy, tải lại và liên kết sang lịch sử AI Lab.

`GET /api/ai-lab/predictions?category=MEGA&async=true` dùng hàng đợi `LAB_PREDICTIONS`, health check và polling hiện có. Worker chỉ đọc snapshot/settlement đã lưu. Không huấn luyện, tạo vé mới hoặc tạo bù quá khứ khi mở trang. Bỏ `async` để đọc trực tiếp; loại không hợp lệ trả HTTP 400. Không cần migration database.

Mỗi nhóm trả `model`, `modelVersion`, `cohort`, `targetDate`, `tickets`, `performance` và `history`. Chỉ hiển thị bộ đúng kỳ mục tiêu, đủ năm vé khác nhau, đủ slot, cùng lịch sử/cohort và còn chờ mở thưởng. Dữ liệu đầu vào phải qua kiểm tra snapshot dự đoán trước kỳ hiện có.

Tỷ lệ ≥k = số kỳ có ít nhất một vé khớp từ k số / toàn bộ kỳ đã đối soát của thuật toán. Năm vé cùng kỳ chỉ tạo một đơn vị trong mẫu số. Các mức ≥3, ≥2, ≥1 tính lũy kế. Các kỳ không khớp vẫn nằm trong mẫu số; không đủ lịch sử trả tỷ lệ null, khác với 0%. Loại cả bộ khi thiếu vé, trùng slot, có nhiều cohort cho cùng mô hình/ngày, kết quả bị rút lại, chưa có kết quả hoặc năm vé có kết quả không thống nhất.

Xếp giảm dần theo tỷ lệ ≥3, rồi ≥2, ≥1, số kỳ đã đánh giá, cuối cùng tên model để ổn định thứ tự. Nhóm chưa có kỳ đánh giá nằm cuối. Tỷ lệ dùng toàn bộ lịch sử mô hình, gồm các phiên bản; `history` giữ riêng mẫu số và tỷ lệ từng cohort. Giao diện ghi rõ phạm vi này và không coi tỷ lệ lịch sử là xác suất trúng kỳ tới hay tự động nâng mô hình.

Trang tự tải lại mỗi phút khi hiển thị, không tạo lượt chồng trong khi một tác vụ còn chạy. Đổi MEGA/POWER hủy polling cũ và xóa dữ liệu khác loại. Các dãy đã qua 18:00 giờ Việt Nam không được hiển thị lại khi tải lỗi.
