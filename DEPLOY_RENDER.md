# Hướng dẫn Deploy Analyze Project trên Render (render.com) với PostgreSQL

Dự án này bao gồm:
* **Frontend:** Angular (TypeScript)
* **Backend:** Spring Boot (Java 17, Maven)
* **Database:** PostgreSQL Database
* **Mô hình phân tích:** XGBoost AI Scoring

Tôi đã chuẩn bị sẵn đầy đủ cấu hình **Docker multi-stage (`Dockerfile`)**, **Blueprint (`render.yaml`)** kết nối tự động với **PostgreSQL**, và cấu hình **dynamic port (`${PORT}`)** để bạn có thể triển khai lên Render nhanh chóng.

---

## 🚀 Cách 1 (Khuyên dùng): Deploy Tự động bằng Render Blueprint (Đầy đủ App & PostgreSQL)

Với file `render.yaml` đã được thiết lập sẵn, Render sẽ tự động khởi tạo đồng thời cả **Web Service** và **PostgreSQL Database** rồi tự động gắn chuỗi kết nối:

1. **Đẩy mã nguồn lên GitHub:**
   * Tạo repository trên GitHub và commit/push code lên.

2. **Tạo Blueprint trên Render:**
   * Truy cập [https://dashboard.render.com](https://dashboard.render.com).
   * Bấm vào nút **New +** ở góc trên bên phải &rarr; Chọn **Blueprint**.
   * Chọn repository GitHub của bạn &rarr; Bấm **Connect**.
   * Render sẽ tự động phát hiện cấu hình trong `render.yaml`:
     - 1 Web Service Docker: `analyze-project`
     - 1 PostgreSQL Database: `analyze-postgres` (database: `lotterydb`)
     - Tự động truyền biến môi trường `SPRING_DATASOURCE_URL` từ PostgreSQL sang Spring Boot.
   * Bấm **Apply** để bắt đầu triển khai tự động.

---

## 🛠️ Cách 2: Tạo thủ công Web Service & PostgreSQL Database

Nếu bạn muốn tạo từng dịch vụ thủ công trên Render Dashboard:

### Bước 1: Tạo cơ sở dữ liệu PostgreSQL
1. Trên Render Dashboard, bấm **New +** &rarr; **PostgreSQL**.
2. Đặt tên: `analyze-postgres` (Database name: `lotterydb`, User: `postgres`).
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
