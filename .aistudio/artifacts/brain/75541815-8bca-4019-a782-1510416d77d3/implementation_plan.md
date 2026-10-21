# Tối Ưu Hóa Trang Dự Đoán: Bỏ Chọn Nhanh Thuật Toán & Hiển Thị 3 Kết Quả Độc Lập

Tài liệu này ghi nhận việc hoàn thiện các yêu cầu nghiệp vụ và kỹ thuật theo chỉ đạo của người dùng:
1. Bỏ hoàn toàn khu vực "chọn nhanh thuật toán" (7 nút bấm Top 1 -> Top 7).
2. Giữ nguyên thuật toán và trọng số xung lượng cho 2 con số đã trúng trong 2 dãy ở kỳ Mega mới nhất ngày 21/10/2026 (tránh phạt lặp quá mức).
3. Trang Dự đoán hiển thị rõ ràng 3 kết quả độc lập:
   - **Kết quả Thuật toán 1 (Chính)**
   - **Kết quả Thuật toán 2 (Phụ / Đối trọng)**
   - **Kết quả Kết hợp 2 thuật toán (Ensemble Consensus)**: làm nổi bật các hạt nhân số đồng thuận chung (Consensus Core Numbers) bằng biểu tượng ngôi sao phát sáng.

---

## 1. Chi Tiết Thực Hiện

### A. Frontend (`prediction.component.html`, `prediction.component.ts`)
- **Gỡ bỏ hoàn toàn**: Đã xóa khối giao diện chọn nhanh 7 thuật toán (`<!-- BỘ NÚT CHỌN NHANH THUẬT TOÁN -->`).
- **Thêm bộ chọn thuật toán kép (Dual Selector)**:
  - Dropdown Thuật toán 1: Mặc định chọn Thuật toán hàng đầu (BEGN - Bayesian Graph).
  - Dropdown Thuật toán 2: Mặc định chọn Thuật toán bổ trợ (DSE-Copula - Deep Stacking).
  - Nút bấm "⚡ Dự Đoán" kích hoạt phân tích đồng thời cả 2 mô hình.
- **Hiển thị 3 Khối Kết Quả (3 Cards)**:
  1. **Khối 1 - Vé Thuật toán 1**: 6 bóng số + Tỷ lệ trúng thực nghiệm Top 6 / Top 14 + Phân bổ chẵn lẻ/tổng + Nút Sao chép Vé 1.
  2. **Khối 2 - Vé Thuật toán 2**: 6 bóng số + Tỷ lệ trúng thực nghiệm + Đặc trưng thống kê + Nút Sao chép Vé 2.
  3. **Khối 3 - Vé Kết hợp 2 Thuật toán**: 6 bóng số tối ưu nhất sau khi trộn xác suất $0.52 \cdot P_1 + 0.48 \cdot P_2$. Các bóng số đồng thuận giữa 2 thuật toán được gắn huy hiệu hạt nhân chung ⭐.

### B. Backend & Server (`server.ts`, `AnalyzeController.java`, `AnalyzeService.java`)
- **Giữ logic trúng 2 số kỳ 21/10/2026**:
  - Không áp dụng hệ số suy kiệt (exhaustion penalty) đối với các số trúng trong 2 dãy ngày 21/10/2026.
  - Tích hợp trọng số liên kết cặp (pair co-occurrence) và xung lượng dương cho các số này.
- **Tính toán 3 kết quả đồng thời**:
  - `primaryAlgorithmResult`: Dự đoán từ Thuật toán 1.
  - `secondaryAlgorithmResult`: Dự đoán từ Thuật toán 2.
  - `combinedAlgorithmResult`: Hợp nhất xác suất 2 thuật toán, xác định `sharedNumbers` để trả về cho Frontend.
- **Tiêu chuẩn kiểm thử (Build Standards)**:
  - Frontend: `npm run build` hoàn thành thành công (Vite + esbuild).
  - Backend: `mvn test-compile` hoàn thành thành công (`BUILD SUCCESS`).
  - Hệ thống applet: `compile_applet` và `lint_applet` đều vượt qua không lỗi.
