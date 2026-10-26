# Dữ liệu ứng dụng Node/Vite đã ngừng sử dụng

Ngày 26/10/2026, ứng dụng Node/Vite ở root được loại bỏ theo yêu cầu chỉ giữ Angular frontend và Spring Boot backend.

`legacy-node-data/` giữ nguyên dữ liệu JSON trước khi dọn, bao gồm lịch sử kết quả, vé, cấu hình, dữ liệu H2 cũ và 6 snapshot dự đoán thực tế cho POWER 27/10, MEGA 28/10. Một số file khác bản trong `src/main/resources/data/`, nên không xóa hoặc ghi đè bản backend.

Đây là dữ liệu lưu trữ để đối chiếu, không nằm trong classpath ứng dụng và không được tự nhập vào PostgreSQL. Snapshot của ứng dụng Spring được quản lý bằng `forecast_snapshots` / `forecast_settlements` qua migration V11. Không đổi thời điểm tạo hoặc sinh lại snapshot cũ khi chuyển đổi dữ liệu sau này.
