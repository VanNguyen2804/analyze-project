# Cập nhật kế hoạch vị thế và giao diện chứng khoán

Đã triển khai theo `../STOCK_POSITION_PLAN_AND_UI.md` ngày 06/11/2026.

- Nhận định theo giá vốn, lượng đang giữ, lượng có thể bán và giới hạn lỗ do người dùng nhập; bỏ ngưỡng 10% bị ép trước đây.
- Hai bước bình giá có điều kiện, giới hạn ngân sách mua thêm, tổng vốn và lỗ bằng tiền. Mốc dừng gốc không hạ theo giá vốn mới; vượt giới hạn rủi ro ưu tiên giảm vị thế.
- Hai bước chốt lời theo phần trăm hoặc giá mục tiêu; trailing tùy chọn sau khi đã ghi nhận chốt lời. Tổng lượng bán không vượt lượng được xác nhận có thể bán.
- Mô phỏng giá vốn, lượng giữ, lãi/lỗ và vốn sau mỗi bước. Chỉ cập nhật giao dịch khi người dùng nhập giá và số lượng thực tế; không tự coi chạm ngưỡng là khớp lệnh.
- Giữ vị thế riêng theo mã trong bộ nhớ phiên. Đổi mã, đổi chế độ và tải lại dữ liệu không tự xóa kế hoạch; bỏ phản hồi cũ nếu snapshot hoặc đầu vào đã thay đổi.
- Phân biệt giá định giá mới nhất với giá nến đóng dùng xác nhận. Dữ liệu chưa xác minh, không RAW hoặc không mới chỉ tạo kịch bản tham khảo.
- Giao diện navy, xanh ngọc và tím; thẻ chỉ số vị thế, kế hoạch từng bước, các mốc trên biểu đồ có thể bật/tắt, bảng nhận định ngày chuyển thành thẻ trên mobile.

## Kiểm chứng

- 31 test backend đạt trong StockPositionPlanTest, StockServiceTest, StockRecommendationTest, StockDailyDeskTest, StockControllerTest và EntradeStockAdapterTest.
- 17 test frontend chứng khoán đạt; kiểm tra TypeScript và production build đạt.
- Kiểm tra trình duyệt với dữ liệu giả lập ở 1440, 768 và 390 px; không tràn ngang hoặc lỗi JavaScript. Đây là kiểm tra giao diện, không phải kiểm chứng dữ liệu thị trường thực.
- Build còn cảnh báo dung lượng CSS có sẵn ở AI Lab, ngoài phạm vi thay đổi này.

## Giới hạn của phiên bản

Dữ liệu vị thế chỉ giữ trong bộ nhớ phiên, mất khi tải lại toàn bộ trang. Không tích hợp đặt lệnh hoặc lưu danh mục trên server. Số liệu lãi/lỗ là trước phí và thuế; lượng cổ phiếu nguyên là mô phỏng do nguồn chưa cung cấp quy tắc lô/bước giá. Mỗi bước giao dịch được ghi nhận một lần; muốn lập lại các bước cho phần còn giữ, dùng thao tác tạo kế hoạch mới. Các ngưỡng là cấu hình kịch bản, không phải bảo đảm kết quả giao dịch.
