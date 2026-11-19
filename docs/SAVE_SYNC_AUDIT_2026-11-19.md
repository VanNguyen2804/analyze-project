# Kiểm tra luồng lưu và đồng bộ kết quả — 19/11/2026

## Bằng chứng trên Render (chỉ đọc)
- GET /api/numbers?category=POWER&date=2026-11-17 trả các bản ghi 131, 133, 134, 135, 136 với số 6,20,36,41,49,52 và số phụ 34. quality=null.
- GET /api/analyze/history?category=POWER có kỳ mới nhất 14/11/2026.
- getRecentDraws sử dụng lịch sử đã qua DrawQuality; dữ liệu chưa xác minh bị loại.
- EntryComponent gọi AnalyzeService.addOfficialResult -> /api/analyze/add-result, khác với /api/numbers có tác vụ LAB_RUN.

## Thay đổi
- Trang nhập đọc dữ liệu đã lưu từ /api/numbers; biểu đồ phân tích giữ API lịch sử xác minh.
- Lưu/sửa qua /api/numbers để kích hoạt tác vụ nền có sẵn.
- Sau lưu/sửa, đọc lại đúng ngày/loại/ID và so sánh dãy số, số phụ trước khi trả thành công cho giao diện.
- Bảng dữ liệu hiển thị trạng thái đối chiếu nguồn.
- Các API cũ được giữ để tương thích; không đổi quy tắc lựa chọn dữ liệu huấn luyện.

## Phạm vi rà soát
- Nơi gọi getHistory, addOfficialResult, editOfficialResult trong frontend.
- API ghi/đọc kết quả, lọc chất lượng, bộ tải nguồn chính/dự phòng và tác vụ AI Lab.
- Build và bộ test toàn dự án được chạy; không thay cho kiểm chứng triển khai thực tế.

## Chưa xác minh
- Bản sửa chưa được triển khai lên Render.
- Không thực hiện ghi/xóa trên database production trong phiên kiểm tra này.
- Cần xác nhận lại thao tác lưu và tải lại trang sau khi triển khai.
