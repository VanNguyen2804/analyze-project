# Hướng dẫn Deploy Analyze Project trên Render (render.com) với PostgreSQL

Dự án này bao gồm:
* **Frontend:** Angular (TypeScript)
* **Backend:** Spring Boot (Java 17, Maven)
* **Database:** PostgreSQL Database
* **Mô hình phân tích:** XGBoost AI Scoring

Tôi đã chuẩn bị sẵn đầy đủ cấu hình **Docker multi-stage (`Dockerfile`)**, **Blueprint (`render.yaml`)** kết nối tự động với **PostgreSQL**, và cấu hình **dynamic port (`${PORT}`)** để bạn có thể triển khai lên Render nhanh chóng.

---

## 🚀 Triển khai Web Service kết nối Database đã tạo trên Render

Vì bạn đã tạo sẵn database PostgreSQL `analyze_project_db` trên Render, cấu hình `render.yaml` đã được cập nhật chỉ deploy Web Service và kết nối trực tiếp vào database hiện có của bạn (tránh lỗi giới hạn 1 database free tier của Render).

1. **Lấy chuỗi kết nối từ Database đã tạo trên Render:**
   * Vào Render Dashboard &rarr; Mở database `analyze_project_db` bạn đã tạo.
   * Cuộn xuống phần **Connections** &rarr; Sao chép **Internal Database URL** (dạng `postgres://...` hoặc `postgresql://...`).

2. **Triển khai Web Service qua Blueprint:**
   * Vào Render &rarr; **New +** &rarr; **Blueprint** &rarr; Chọn repo GitHub.
   * Render sẽ yêu cầu bạn nhập các thông tin bảo mật cho môi trường (do được cấu hình `sync: false`):
     - `SPRING_DATASOURCE_URL`: Dán Internal Database URL (hoặc JDBC URL) của PostgreSQL.
     - `SPRING_DATASOURCE_USERNAME`: Nhập User của Database (ví dụ: `analyze_project_db_user`).
     - `SPRING_DATASOURCE_PASSWORD`: Nhập Password của Database.
   * Bấm **Apply** để hoàn tất triển khai.

---

## 🛠️ Hoặc Tạo Web Service thủ công trên Render:

Nếu bạn muốn tạo từng dịch vụ thủ công trên Render Dashboard:

### Bước 1: Tạo cơ sở dữ liệu PostgreSQL
1. Trên Render Dashboard, bấm **New +** &rarr; **PostgreSQL**.
2. Đặt tên: `analyze_project_db` (Database name: `analyze_project_db`, User: `analyze_project_db_user`).
3. Chọn Region: `Singapore` (tốc độ nhanh nhất về Việt Nam) hoặc `Oregon`.
4. Instance Type: Chọn gói phù hợp (Free).
5. Bấm **Create Database**.
6. Sau khi tạo xong, cuộn xuống mục **Connections**, sao chép giá trị **Internal Database URL** (hoặc External Database URL).

### Bước 2: Tạo Web Service
1. Bấm **New +** &rarr; **Web Service**.
2. Chọn repository GitHub của bạn &rarr; Bấm **Connect**.
3. Cấu hình:
   * **Name:** `analyze-project`
   * **Region:** Cùng vùng với Database (ví dụ `Singapore`)
   * **Branch:** `main`
   * **Runtime:** Chọn **Docker**
   * **Dockerfile Path:** `./Dockerfile`
   * **Instance Type:** Chọn **Free**
4. Trong mục **Environment Variables**, thêm:
   * `SPRING_DATASOURCE_URL`: Dán chuỗi kết nối URL từ Bước 1 (dạng: `jdbc:postgresql://host:port/lotterydb`)
   * `SPRING_PROFILES_ACTIVE`: `prod`
5. Bấm **Create Web Service**.
6. Sau khoảng 2–3 phút, dịch vụ sẽ chuyển sang trạng thái **Live** và bạn có thể truy cập ngay đường link web.

---

## 📌 Cấu hình kết nối PostgreSQL trong Spring Boot

Trong file `backend/src/main/resources/application.properties`:
```properties
spring.datasource.url=${SPRING_DATASOURCE_URL:${DATABASE_URL:jdbc:postgresql://localhost:5432/lotterydb}}
spring.datasource.driver-class-name=org.postgresql.Driver
spring.datasource.username=${SPRING_DATASOURCE_USERNAME:${DATABASE_USERNAME:postgres}}
spring.datasource.password=${SPRING_DATASOURCE_PASSWORD:${DATABASE_PASSWORD:postgres}}

spring.jpa.database-platform=org.hibernate.dialect.PostgreSQLDialect
spring.jpa.hibernate.ddl-auto=update
```
Spring Boot sẽ tự động tạo bảng `lottery_numbers` và cập nhật cấu trúc bảng khi khởi động.

---

## 💡 Lưu ý khi cập nhật giao diện (UI) lên Render

1. **Cơ chế đóng gói tự động (`Dockerfile`):**
   - File `Dockerfile` đã được cấu hình tự động:
     - **Stage 1**: Biên dịch mã nguồn Angular (`frontend/`).
     - **Stage 2**: Sao chép toàn bộ file tĩnh mới từ Stage 1 vào thư mục `src/main/resources/static/` của Spring Boot trước khi đóng gói JAR (`mvn package`).
     - **Stage 3**: Chạy `app.jar` chứa toàn vẹn giao diện Angular mới nhất.
2. **Xóa bộ nhớ đệm khi deploy (Clear build cache):**
   - Nếu Render sử dụng bộ nhớ đệm cũ (Docker cache), hãy vào Render Dashboard:
     - Chọn Web Service của bạn &rarr; Bấm nút **Manual Deploy** &rarr; Chọn **Clear build cache & deploy**.
   - Thao tác này buộc Render tải mới và build lại toàn bộ từ đầu, đảm bảo giao diện mới được hiển thị ngay lập tức.
