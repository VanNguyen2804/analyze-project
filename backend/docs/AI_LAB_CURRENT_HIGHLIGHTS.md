# Dự đoán mới từ thuật toán AI Lab từng trúng ≥3 số

Đầu `/ai-lab` và `/analyze` hiển thị **dãy dự đoán mới đã lưu cho kỳ mục tiêu hiện tại**, từ thuật toán Lab từng có vé dự đoán trước kỳ và sau đó đối soát trúng ít nhất ba số chính. Không lấy các số của vé thắng cũ làm dự đoán mới.

Mỗi vé ghi tên thuật toán bên cạnh mức trúng tốt nhất trong quá khứ, ngày kỳ sắp tới, số vé/số kỳ từng đạt ≥3 và ngày gần nhất đạt mức này. Chỉ sáu vé đầu hiển thị ban đầu; có nút xem tất cả. Đây là bộ lọc theo thành tích lịch sử của thuật toán, có thể gồm phiên bản cũ, không phải xác nhận độ chính xác hay thứ hạng xác suất của phiên bản hiện tại.

Backend dùng snapshot đã xác minh prospective và settlement mới nhất. Thành tích phải thuộc kỳ trước kỳ mục tiêu; kết quả pending, bị rút hoặc sửa còn dưới ba số không đủ điều kiện. Thuật toán có thành tích nhưng chưa lưu dự đoán đúng kỳ mới sẽ không có vé nổi bật; UI báo chờ cập nhật nếu danh sách rỗng. Không sinh lại, chỉnh sửa hoặc chuyển vé lịch sử vào vé mới. Category và cutoff dùng `ForecastProtocol.target` hiện có.

`GET /api/ai-lab` thêm `highlights` độc lập với phân trang `rows`; `GET /api/ai-lab/highlights?category=MEGA` trả danh sách tương tự cho trang dự đoán. Hai đường đọc dùng chung compact cache 30 giây; target hiện tại được kiểm tra mỗi request. Run/đối soát giữ cơ chế invalidation của Lab. API không capture, train hoặc ghi dữ liệu. Trang dự đoán refresh mỗi phút khi tab đang hiện, timeout 15 giây, hủy request/dữ liệu cũ khi đổi MEGA/POWER. AI Lab dùng highlights của request overview sẵn có.

Kiểm thử bao gồm: thành tích nằm ngoài 50 dòng hiện tại; chỉ trả dãy mới đúng kỳ, loại snapshot sai và model chưa có thành tích; không dùng số cũ khi chưa có dự đoán mới; loại thành tích đính chính/rút sau refresh; tên thuật toán/ngày cũ và ngày mới tách biệt; số mới không tô trúng; đổi category hủy request, GET không chạy training.

Kiểm chứng ngày 07/11/2026: Angular typecheck/formatter, 80 test trình duyệt và production build đạt. Maven install đạt: 171 test phát hiện, 164 đạt, bảy optional bỏ qua; gồm PostgreSQL thật. Toàn bộ 26 assets Angular khớp byte với JAR cuối. Còn cảnh báo stylesheet AI Lab 13,20 kB so với budget cảnh báo 10 kB từ trước; không thay stylesheet đó. Chưa deploy production.

Số lần đạt ≥3 số được đặt nổi bật ngay dưới tên thuật toán trên từng dãy mới: “Thuật toán đã trúng ≥3 số: X lần (X vé / Y kỳ)”. Mỗi vé lịch sử đủ điều kiện tính một lần; nhiều vé cùng kỳ vẫn chỉ tính một kỳ trong Y. Đây là thành tích của thuật toán, không phải số lần chính dãy số mới đã trúng.
