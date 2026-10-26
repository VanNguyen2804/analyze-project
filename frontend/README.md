# Frontend Angular

Frontend dùng Angular với standalone components. Mã nguồn chia thành `src/app/core/`, `feature/`, `share/`; route cấu hình trong `src/app/app.routes.ts`.

Chạy trong thư mục `frontend/`, với Node tương thích dependencies đã khai báo:

```powershell
npm.cmd install
npm.cmd start
npm.cmd run build
```

API do Spring Boot trong `../backend/` cung cấp. Cấu hình địa chỉ API ở `src/environments/environment.ts` và `environment.prod.ts`; giá trị rỗng gọi API cùng origin. Khi chạy Angular dev server riêng, cấu hình địa chỉ backend tương ứng.

Trang `/analyze` tạo dự đoán; `/forecasts` xem bản đã chốt và đối soát. Không suy ra xác suất trúng từ điểm xếp hạng heuristic.

Để đóng gói chung, build Angular trước rồi chạy Maven trong `backend/`. Xem [hướng dẫn toàn dự án](../README.md).
