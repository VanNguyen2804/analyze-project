# Dự đoán mới từ thuật toán AI Lab từng trúng ≥3 số

Đầu `/ai-lab` và `/analyze` hiển thị **dãy dự đoán mới đã lưu cho kỳ mục tiêu hiện tại**, từ thuật toán Lab từng có vé dự đoán trước kỳ và sau đó đối soát trúng ít nhất ba số chính. Không lấy các số của vé thắng cũ làm dự đoán mới.

Mỗi tiêu đề nhóm ghi tên thuật toán và phiên bản hiện tại; bên dưới là các dãy mới đánh số 1–5 cho cùng kỳ. Thành tích chung (vé/kỳ đạt ≥3, mức tốt nhất, ngày gần nhất) chỉ xuất hiện một lần ở đầu nhóm, không cộng lại theo số dãy mới. Có thể có thành tích từ phiên bản cũ; đây không phải xác nhận độ chính xác hay thứ hạng xác suất của phiên bản hiện tại.

Phần “Thành tích theo nhóm đánh giá” tách vé/kỳ trúng theo đúng khóa `category|model|modelVersion|codeVersion|configurationHash` của AI Lab. Mỗi nhóm có mã ngắn ổn định để đối chiếu với thẻ mô hình trong Lab. Hai cấu hình cùng tên/phiên bản vẫn có hàng riêng; tổng số kỳ tính distinct ngày trên toàn thuật toán, không cộng số kỳ của các nhóm khi cùng ngày. Mã ngắn chỉ hỗ trợ đọc; API giữ khóa đầy đủ.

Backend dùng snapshot đã xác minh prospective và settlement mới nhất. Thành tích phải thuộc kỳ trước kỳ mục tiêu; kết quả pending, bị rút hoặc sửa còn dưới ba số không đủ điều kiện. Thuật toán có thành tích nhưng chưa lưu dự đoán đúng kỳ mới sẽ không có vé nổi bật; UI báo chờ cập nhật nếu danh sách rỗng. Không sinh lại, chỉnh sửa hoặc chuyển vé lịch sử vào vé mới. Category và cutoff dùng `ForecastProtocol.target` hiện có.

`GET /api/ai-lab` thêm `highlights` độc lập với phân trang `rows`; `GET /api/ai-lab/highlights?category=MEGA` trả danh sách tương tự cho trang dự đoán. Hai đường đọc dùng chung compact cache 30 giây; target hiện tại được kiểm tra mỗi request. Run/đối soát giữ cơ chế invalidation của Lab. API không capture, train hoặc ghi dữ liệu. Trang dự đoán refresh mỗi phút khi tab đang hiện, timeout 15 giây, hủy request/dữ liệu cũ khi đổi MEGA/POWER. AI Lab dùng highlights của request overview sẵn có.

Kiểm thử bao gồm: thành tích nằm ngoài 50 dòng hiện tại; chỉ trả dãy mới đúng kỳ, loại snapshot sai và model chưa có thành tích; không dùng số cũ khi chưa có dự đoán mới; loại thành tích đính chính/rút sau refresh; tên thuật toán/ngày cũ và ngày mới tách biệt; số mới không tô trúng; đổi category hủy request, GET không chạy training.

Kiểm chứng ngày 07/11/2026: Angular typecheck/formatter, 82 test trình duyệt và production build đạt. Maven install đạt: 172 test phát hiện, 165 đạt, bảy optional bỏ qua; gồm PostgreSQL thật. Toàn bộ 26 assets Angular khớp byte với JAR cuối. Còn cảnh báo stylesheet AI Lab 13,20 kB so với budget cảnh báo 10 kB từ trước; không thay stylesheet đó. Chưa deploy production.


Kiểm tra gom nhóm: năm dãy chỉ có một tiêu đề/thành tích, các thuật toán có nhóm riêng, hai cấu hình cùng phiên bản có breakdown riêng và khóa/mã nhóm khớp báo cáo AI Lab.
