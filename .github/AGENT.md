# Project Architecture & Technical Standards

## 1. Backend Project
* **Core Framework:** Spring Framework.
* **Database:** Sử dụng **H2 Database** cho mục tiêu gọn nhẹ (lightweight) trong quá trình phát triển. 
  * *Khuyến nghị mở rộng:* Có thể chuyển đổi linh hoạt sang PostgreSQL hoặc MySQL nếu H2 không đáp ứng đủ điều kiện về dung lượng hoặc hiệu suất truy vấn thống kê phức tạp.
* **Algorithm & Analysis Skills:** Tập trung chuyên sâu vào phân tích dữ liệu để đưa ra các gợi ý có tỷ lệ chính xác cao nhất:
  * **Thuật toán hiện tại (Current Algorithm):** Hệ thống đang kết hợp **Frequency Counting** (Thống kê tần suất cơ bản) và thuật toán học máy **XGBoost**. Mô hình này sẽ phân tích các đặc trưng (features) như "số nóng" (hay ra), "lô gan" (lâu chưa ra), và các cụm số hay đi liền nhau để chấm điểm xác suất xuất hiện cho từng con số.
  * **Định hướng tham khảo (Global Benchmarking):** Nghiên cứu và tham khảo các mô hình thống kê, phân tích xác suất từ các giải xổ số khổng lồ của Mỹ như **Powerball** và **Mega Millions**. Bằng cách phân tích chéo các tập dữ liệu lịch sử cực lớn từ Mỹ, hệ thống có thể đối chiếu các điểm kỳ dị của tính ngẫu nhiên, từ đó tối ưu hóa lại trọng số (weight) và thuật toán đang dùng cho Vietlott.

## 2. Frontend Project
* **Core Framework:** Angular.
* **UI/UX Design:** Trọng tâm vào **Responsive Design**, đảm bảo trải nghiệm hiển thị liền mạch trên mọi thiết bị.
* **Quy tắc UI:** Không tự thay đổi UI khi chưa có sự cho phép của tôi.
* **Main Layout:** Giao diện được cấu trúc theo 3 phân vùng: `Header`, `Left menu`, và `Content`.
* **Project Skeleton:** Mã nguồn được module hóa chặt chẽ thành 3 phần:
  * `core/`: Nơi lưu trữ các Singleton Services và Components lõi (chỉ khởi tạo một lần duy nhất cho toàn ứng dụng).
  * `feature/`: Nơi lưu trữ và đóng gói các tính năng chính của trang (đảm bảo tính độc lập nghiệp vụ).
  * `share/`: Nơi lưu trữ các Shared Services, Pipes, và Components để tái sử dụng xuyên suốt dự án.
