# Hoàn Thiện Tối Ưu Hóa Trang Dự Đoán: Biểu Đồ Liên Kết Dãy Số CSDL Riêng Cho Từng Category

Tài liệu này ghi nhận việc triển khai thành công biểu đồ trực quan hóa mối liên kết giữa các dãy số kết quả dựa trên Cơ sở dữ liệu (CSDL) thực tế đặt ngay ở đầu trang Dự Đoán, đáp ứng trọn vẹn yêu cầu của người dùng.

---

## 1. Yêu Cầu Của Người Dùng
1. Ở trang Dự Đoán, ngay ở **đầu trang**, hiển thị chart thể hiện **sự liên kết giữa các dãy số kết quả dựa vào database**.
2. **Mỗi category thể hiện chart riêng của category đó**:
   - `🔴 MEGA 6/45`: Phân tích 30 kỳ CSDL thực tế (từ 1 đến 45).
   - `🔵 POWER 6/55`: Phân tích 24 kỳ CSDL thực tế (từ 1 đến 55, bao gồm cả bóng Jackpot 2 ★).
3. Không hiển thị dãy kết hợp của 2 thuật toán nữa; hiển thị các dãy khả thi có thể chọn nguyên thủy và có biến số (đã hoàn thành ở các lượt trước).

---

## 2. Các Thành Phần Biểu Đồ Đã Triển Khai

### A. Vị Trí & Bộ Chuyển Đổi Category Độc Lập
- Đặt ngay sau banner kỳ mở thưởng mục tiêu tiếp theo và nằm trên phần cấu hình thuật toán.
- Có bộ nút chuyển đổi danh mục riêng: `🔴 MEGA 6/45` vs `🔵 POWER 6/55`, tự động đồng bộ CSDL tương ứng.

### B. 3 Chế Độ Trực Quan Hóa Chuyên Sâu
1. **🌊 Dòng Chảy Chuyển Dịch Kỳ (Sequence Transition Flow)**:
   - Dựng timeline các kỳ mở thưởng gần nhất của category được chọn.
   - Mỗi kỳ hiển thị đầy đủ 6 bóng (và bóng ★ đối với Power 6/55), tổng điểm và tỷ lệ Chẵn/Lẻ.
   - Thể hiện các mối liên kết chuyển dịch:
     - **Lặp số chính xác ($\Delta = 0$)**: Màu xanh lá (Emerald).
     - **Lệch lân cận ($\Delta = \pm 1$)**: Màu vàng cam (Amber) - hóa giải lệch 1 nút.
     - **Cùng đuôi hàng đơn vị ($* \text{mod } 10$)**: Màu tím (Purple).
     - **Di cư bóng Jackpot 2 (★)**: Màu hồng (Rose) - áp dụng cho Power 6/55.
   - Tương tác: Rê chuột vào bất kỳ quả bóng nào sẽ sáng bừng toàn bộ đường dẫn liên kết của bóng đó với kỳ trước và kỳ sau.
2. **🕸️ Đồ Thị Mạng Lưới Đồng Xuất Hiện (Circular Co-occurrence Chord Graph)**:
   - Dựng SVG vành tròn 45 số (Mega) hoặc 55 số (Power).
   - Các dây cung cong Bézier kết nối các cặp số cùng nhau nổ trong cùng 1 kỳ CSDL:
     - Đỏ đậm: $\ge 4$ kỳ cùng về.
     - Vàng cam: 3 kỳ cùng về.
     - Xanh ngọc: 2 kỳ cùng về.
   - Khi hover vào một số trên vành tròn: Sáng bừng tất cả các bạn đồng hành mạnh nhất của số đó.
3. **💎 Top Cặp Số & Cụm Hạt Nhân (Pairs & Cliques)**:
   - Bảng xếp hạng Top 10 cặp số đồng hành nhiều nhất kèm chỉ số nâng Lift index.
   - Nhóm Cliques 3 số xuất hiện đồng thời trong CSDL.

### C. Dải Thống Kê Chỉ Số CSDL Thực Tế (Metric Strip)
- Tỷ lệ Lệch lân cận $\pm 1$ qua các kỳ.
- Tỷ lệ Lặp số kỳ sau ($\Delta = 0$).
- Tỷ lệ Cùng đuôi đơn vị.
- Cặp hạt nhân mạnh nhất kèm tần suất.

---

## 3. Kết Quả Kiểm Thử & Biên Dịch
- `bun install --cwd frontend && npm --prefix frontend run build:sync`: Thành công 100%, bundles Angular cập nhật vào `backend/src/main/resources/static/`.
- `compile_applet`: Build succeeded - the applet is compiled.
- `lint_applet`: Linting completed successfully (0 errors).
- Server dev chạy ổn định trên cổng 3000.
