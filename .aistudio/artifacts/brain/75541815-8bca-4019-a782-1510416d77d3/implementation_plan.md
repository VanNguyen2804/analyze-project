# Hoàn Thiện Tối Ưu Hóa Trang Dự Đoán: Thay Thế 2 Biểu Đồ Nhận Định Mô Hình Dự Đoán

Tài liệu này ghi nhận việc tái cấu trúc hệ thống biểu đồ ở đầu trang Dự Đoán theo yêu cầu người dùng: **Loại bỏ "Chord Graph" và "Pairs & Cliques"**, thay thế bằng **2 biểu đồ trực tiếp phục vụ nhận định chuyên sâu cho các thuật toán dự đoán**.

---

## 1. Yêu Cầu Thay Đổi
- Người dùng nhận xét: *"Chord graph" và "Pairs & Cliques", 2 chart này có vẻ không phù hợp lắm để đưa ra nhận định , thay đổi 2 chart này bằng chart khác phù hợp với mô hình dự đoán*.
- Cần cung cấp các biểu đồ mang tính hành động cao (Actionable Analytics), giúp người chơi và mô hình AI định vị:
  1. Những con số nào sắp tới điểm rơi phục hồi xác suất (Poisson Gap & Mean Reversion).
  2. Dãy số kỳ tiếp theo nên có cấu trúc dãn cách Delta như thế nào và tổng điểm rơi vào khoảng bao nhiêu.

---

## 2. Các Biểu Đồ Mới Đã Triển Khai

### A. Giữ Nguyên Chế Độ 1: 🌊 Dòng Chảy Chuyển Dịch Kỳ (Transition Flow & Drift ±1)
- Trực quan hóa timeline các kỳ CSDL gần nhất.
- Bắt trọn các liên kết: Lặp số ($\Delta = 0$), Lệch lân cận ($\pm 1$), Cùng đuôi đơn vị, Di cư bóng Jackpot 2 (★ đối với Power 6/55).

### B. Biểu Đồ Thay Thế 1: ⚡ Áp Suất Chu Kỳ & Điểm Rơi Poisson (Gap Pressure Matrix)
- **Cơ sở khoa học**:
  - Dựa trên phân phối xác suất Poisson Inter-Arrival và giải thuật hồi quy Mean Reversion của thuật toán Top 4 (Poisson & Lô Gan) và Top 2 (DSE-Copula).
  - Chu kỳ kỳ vọng lý thuyết $\lambda$: Mega 6/45 là 7.5 kỳ/lần; Power 6/55 là 9.17 kỳ/lần.
- **Tính năng trực quan**:
  - **Top 6 Bóng Áp Suất Phục Hồi Cao**: Các con số có độ trễ vượt ngưỡng $\lambda$, tích lũy áp suất xác suất cực đại, có xác suất nổ phục hồi cao nhất ở kỳ này.
  - **Top 6 Bóng Quán Tính Nóng (Hot Momentum)**: Các con số đang có nhịp về dày đặc (trễ 0 - 2 kỳ), duy trì lực kéo theo chuỗi Markov.
  - **Ma Trận Phân Bổ Áp Suất 45/55 Số**:
    - Bộ lọc trạng thái: `Tất cả` | `🟢 Đang có đà (Gap 0-2)` | `🟡 Nhịp chuẩn` | `🟠 Áp suất tới hạn` | `🔴 Lô gan tích lũy`.
    - Thanh đo áp suất tiến trình từ 0% đến 100% kèm Z-Score độ trễ cho từng quả bóng.
    - Nhấp chọn từng số để xem thẻ thanh tra chi tiết.

### C. Biểu Đồ Thay Thế 2: 📐 Phổ Dãn Cách Delta & Đường Cong Tổng Gauss (Delta Spacing & Bell Distribution)
- **Cơ sở khoa học**:
  - Dựa trên ma trận Wheeling System & Delta Spacing của thuật toán Top 5 (Delta & Wheeling System).
  - Kiểm soát khoảng cách giữa các số liền kề $\Delta = n_{i+1} - n_i$ và phân phối Gauss của tổng 6 số.
- **Tính năng trực quan**:
  - **Phổ Dãn Cách Delta**: Thống kê 4 dải dãn cách thực tế trong CSDL:
    - $\Delta = 1$: Cặp số kẹp sát / liền kề (ví dụ 14-15, 31-32).
    - $\Delta = 2 - 4$: Dãn cách bước ngắn.
    - $\Delta = 5 - 8$: Dãn cách vàng Jackpot bảo toàn độ phủ tối ưu.
    - $\Delta \ge 9$: Bước nhảy dãn cách xa.
  - **Đường Cong Tổng Điểm (Sum Gauss)**:
    - Biểu đồ phân phối chuẩn hình chuông xác định **Đỉnh Vàng Gauss** (Mega: 126 - 150 điểm; Power: 156 - 185 điểm).
  - **Tỷ Lệ Chẵn / Lẻ**:
    - Thống kê tỷ trọng 3C-3L (áp đảo $\approx 35-40\%$), 4C-2L, 2C-4L.
  - **Banner Khuyến Nghị Chiến Thuật**:
    - Đưa ra nhận định cấu trúc vé tối ưu cho kỳ tiếp theo: Nên giữ ít nhất 1 cặp liền kề ($\Delta=1$), dãn cách trung bình 5-8 nút và tổng điểm rơi vào vùng Đỉnh Vàng.

---

## 3. Kết Quả Kiểm Thử
- `npm --prefix frontend run build:sync`: Thành công 100%, bundles Angular cập nhật vào `backend/src/main/resources/static/`.
- `compile_applet`: Build succeeded - the applet is compiled.
- `lint_applet`: 0 errors.
- Dev server hoạt động bình thường trên cổng 3000.
