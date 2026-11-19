# Template xử lý lỗi chức năng

## Hiện tượng và cách tái hiện
- URL, thao tác, dữ liệu đầu vào, kết quả mong đợi và kết quả thực tế.

## Bằng chứng trước khi sửa
- Kiểm tra API thực tế mà giao diện gọi; không suy luận từ tên lớp.
- Ghi HTTP status và response của thao tác ghi.
- Đọc lại theo ID/ngày/loại từ API dữ liệu gốc và API giao diện.
- Kiểm tra bộ lọc chất lượng, cache, bản triển khai và trạng thái tác vụ nền.
- Phân biệt đã lưu, đã xác minh nguồn và đã tạo dự đoán.

## Nguyên nhân và phạm vi sửa
- Chỉ kết luận theo bằng chứng; không đổi quy tắc xác minh để che lỗi hiển thị.
- Liệt kê tất cả nơi gọi API bị ảnh hưởng và giữ tương thích.

## Kiểm chứng bắt buộc
- Test hồi quy tái hiện lỗi trước khi sửa, gồm lưu rồi đọc lại.
- Build frontend và backend thành công.
- Chạy toàn bộ test frontend/backend; ghi số đạt, lỗi và bỏ qua.
- Kiểm tra nguồn thật khi thay đổi đồng bộ.
- Khi đã triển khai: lưu, đọc lại sau tải trang, kiểm tra trạng thái xác minh và AI Lab.
- Không báo đã sửa trên production khi chưa triển khai và kiểm chứng.

## Kết quả bàn giao
- Nguyên nhân, thay đổi, bằng chứng kiểm tra, giới hạn còn lại.
