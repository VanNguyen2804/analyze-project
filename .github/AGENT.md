# Project Architecture & Technical Standards

## 0. Vị trí File Cấu hình (Configuration Files Location)
* **Quy tắc vị trí bắt buộc:** Cả 2 file **`SKILL.md`** và **`AGENT.md`** **PHẢI** luôn nằm trong thư mục **`.github/`**:
  * Đường dẫn: `.github/SKILL.md`
  * Đường dẫn: `.github/AGENT.md`
* **Nghiêm cấm:** Tuyệt đối KHÔNG tạo, sao chép hoặc để file `AGENT.md`, `AGENTS.md`, hay `SKILL.md` ở thư mục gốc (root folder). Mọi cập nhật và tham chiếu quy chuẩn dự án bắt buộc thực hiện tại `.github/`.

## 1. Backend Project
* **Core Framework:** Spring Framework.
* **Database:** Sử dụng **PostgreSQL** làm cơ sở dữ liệu chính.
* **Algorithm & Analysis Skills:** Tập trung chuyên sâu vào phân tích dữ liệu để đưa ra các gợi ý có tỷ lệ chính xác cao nhất:
  * **Thuật toán hiện tại (Current Algorithm):** Hệ thống đang kết hợp **Frequency Counting** (Thống kê tần suất cơ bản) và thuật toán học máy **XGBoost**. Mô hình này sẽ phân tích các đặc trưng (features) theo từng dãy số theo ngày cho mỗi category và đề xuất số cho từng category khác nhau, bao gồm "số nóng" (hay ra), "lô gan" (lâu chưa ra), và các cụm số hay đi liền nhau để chấm điểm xác suất xuất hiện cho từng con số.
  * **Định hướng tham khảo (Global Benchmarking):** Nghiên cứu và tham khảo các mô hình thống kê, phân tích xác suất từ các giải xổ số khổng lồ của Mỹ như **Powerball** và **Mega Millions**. Bằng cách phân tích chéo các tập dữ liệu lịch sử cực lớn từ Mỹ, hệ thống có thể đối chiếu các điểm kỳ dị của tính ngẫu nhiên, từ đó tối ưu hóa lại trọng số (weight) và thuật toán đang dùng cho Vietlott.

## 2. Frontend Project
* **Core Framework:** Angular.
* **UI/UX Design:** Trọng tâm vào **Responsive Design**, đảm bảo trải nghiệm hiển thị liền mạch trên mọi thiết bị.
* **Quy tắc UI:** Không tự thay đổi UI khi chưa có sự cho phép của tôi. Không hiển thị thông tin về loại database trên giao diện người dùng.
* **Main Layout:** Giao diện được cấu trúc theo 3 phân vùng: `Header`, `Left menu`, và `Content`.
* **Project Skeleton:** Mã nguồn được module hóa chặt chẽ thành 3 phần:
  * `core/`: Nơi lưu trữ các Singleton Services và Components lõi (chỉ khởi tạo một lần duy nhất cho toàn ứng dụng).
  * `feature/`: Nơi lưu trữ và đóng gói các tính năng chính của trang (đảm bảo tính độc lập nghiệp vụ).
  * `share/`: Nơi lưu trữ các Shared Services, Pipes, và Components để tái sử dụng xuyên suốt dự án.

## 3. Quy trình Build & Kiểm thử (Build Standards)
* **Quy tắc Build (Build Mandate):** Mỗi lần thay đổi code, bắt buộc phải build từng phần riêng lẻ thành công trước khi hoàn thành:
  * **Frontend:** Chạy lệnh build trong thư mục `frontend`:
    ```bash
    npm run build
    ```
    (hoặc `npm build`)
  * **Backend:** Chạy lệnh build trong thư mục `backend`:
    ```bash
    mvn install
    ```
    (hoặc `mvn clean install -DskipTests`)
  * **Tiêu chuẩn hoàn thành:** Cả hai phần Frontend và Backend đều phải build thành công (`Build Success`) không có lỗi.
