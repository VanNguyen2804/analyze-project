# Hướng dẫn Deploy Analyze Project trên Render (render.com)

Dự án này bao gồm:
* **Frontend:** Angular (TypeScript)
* **Backend:** Spring Boot (Java 17, Maven)
* **Database:** H2 Database
* **Mô hình phân tích:** XGBoost AI Scoring

Tôi đã chuẩn bị sẵn đầy đủ cấu hình **Docker multi-stage (`Dockerfile`)**, **Blueprint (`render.yaml`)** và cấu hình **dynamic port (`${PORT}`)** để bạn có thể triển khai lên Render nhanh chóng và hoàn toàn miễn phí.

---

## 🚀 Cách 1 (Khuyên dùng): Deploy Tất cả trong 1 Web Service duy nhất (Tối ưu Free Tier)

Đây là phương pháp tốt nhất và tiết kiệm nhất trên Render:
* Chỉ sử dụng **1 Web Service Free** (Render miễn phí 750 giờ/tháng).
* Frontend Angular và Backend Spring Boot chạy chung một domain, **không phát sinh lỗi CORS**.
* Tự động build Angular & Spring Boot thông qua multi-stage Dockerfile.

### Các bước thực hiện:

1. **Đẩy mã nguồn lên GitHub:**
   * Tạo một repository trên GitHub (ví dụ: `analyze-project`).
   * Commit và push toàn bộ mã nguồn lên repository đó.

2. **Tạo Web Service trên Render:**
   * Truy cập [https://dashboard.render.com](https://dashboard.render.com) và đăng nhập (bằng GitHub).
   * Bấm vào nút **New +** ở góc trên cùng bên phải &rarr; Chọn **Web Service**.
   * Chọn repository GitHub của bạn &rarr; Bấm **Connect**.

3. **Cấu hình thông số dịch vụ:**
   * **Name:** `analyze-project` (hoặc tên tùy bạn đặt)
   * **Region:** Chọn `Singapore` (để có tốc độ kết nối nhanh nhất về Việt Nam) hoặc `Oregon (US West)`
   * **Branch:** `main` (hoặc branch bạn đã push)
   * **Runtime:** Chọn **Docker**
   * **Dockerfile Path:** `./Dockerfile` (mặc định)
   * **Instance Type:** Chọn **Free** ($0/tháng)

4. **Biến môi trường (Environment Variables) - Tùy chọn:**
   * Render sẽ tự động cấp phát biến `PORT` (Spring Boot đã được cấu hình tự động lắng nghe theo cổng này).
   * Bạn có thể thêm:
     * `SPRING_PROFILES_ACTIVE`: `prod`

5. **Bấm "Create Web Service":**
   * Render sẽ tự động kéo code về, chạy Docker build (biên dịch Angular, biên dịch Spring Boot bằng Maven, đóng gói vào container JRE 17).
   * Sau khi build hoàn tất (khoảng 2–3 phút), trạng thái sẽ chuyển thành **Live**.
   * Bạn có thể truy cập ngay vào đường dẫn do Render cung cấp: `https://analyze-project-xxxx.onrender.com`.

---

## ⚙️ Cách 2: Deploy bằng tính năng Render Blueprint (Tự động 100%)

Nếu bạn thích cấu hình tự động qua Infrastructure-as-Code:

1. Đẩy code lên GitHub (đã có file `render.yaml` ở thư mục gốc).
2. Trên Render Dashboard, bấm **New +** &rarr; Chọn **Blueprint**.
3. Chọn repository của bạn.
4. Render sẽ tự động đọc file `render.yaml`, phát hiện dịch vụ Docker Web Service và cấu hình đầy đủ từ A-Z.
5. Bấm **Apply** để bắt đầu triển khai.

---

## 🌐 Cách 3: Tách riêng Backend (Web Service) và Frontend (Static Site)

Nếu bạn muốn tách độc lập 2 dịch vụ:

### 1. Deploy Backend:
* Bấm **New +** &rarr; **Web Service**.
* **Runtime:** Docker.
* **Dockerfile Path:** `backend/Dockerfile`
* **Docker Context:** `backend`
* Sau khi deploy xong, bạn sẽ có URL của Backend, ví dụ: `https://analyze-backend.onrender.com`.

### 2. Cập nhật URL Backend vào Frontend:
* Mở file `frontend/src/environments/environment.prod.ts`, cập nhật:
  ```typescript
  export const environment = {
    production: true,
    apiUrl: 'https://analyze-backend.onrender.com' // Thay bằng URL Render của bạn
  };
  ```
* Commit và push lên GitHub.

### 3. Deploy Frontend (Static Site):
* Bấm **New +** &rarr; **Static Site**.
* **Build Command:** `cd frontend && npm install && npm run build`
* **Publish Directory:** `frontend/dist/frontend`
* Vào mục **Redirects/Rewrites**: Thêm rule:
  * **Type:** Rewrite
  * **Source:** `/*`
  * **Destination:** `/index.html`

---

## 📌 Lưu ý quan trọng về cơ sở dữ liệu H2:
* Mặc định dự án dùng **H2 In-Memory Database** (`jdbc:h2:mem:lotterydb`). Điều này có nghĩa là khi instance của gói Free chuyển sang chế độ ngủ (sleep sau 15 phút không có request), dữ liệu trong RAM sẽ được làm mới.
* **Nếu bạn muốn dữ liệu được lưu vĩnh viễn:**
  1. **Tùy chọn 1 (H2 File-based + Render Disk):** Đổi cấu hình `spring.datasource.url=jdbc:h2:file:/var/data/lotterydb` và gắn một Render Persistent Disk vào thư mục `/var/data`.
  2. **Tùy chọn 2 (PostgreSQL Miễn phí trên Render):** Tạo 1 database PostgreSQL miễn phí ngay trên Render (bấm **New +** &rarr; **PostgreSQL**), sau đó copy chuỗi kết nối dán vào biến môi trường `SPRING_DATASOURCE_URL` của Web Service.
