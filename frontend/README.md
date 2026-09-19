# Frontend (Angular Framework)

Dự án Frontend được tổ chức theo cấu trúc chuẩn của framework Angular (Angular 18+).

## Cấu trúc thư mục

```
frontend/
├── angular.json               # Cấu hình Angular CLI workspace
├── package.json               # Danh sách thư viện và dependencies của Angular
├── tsconfig.json              # Cấu hình TypeScript gốc
├── tsconfig.app.json          # Cấu hình TypeScript cho ứng dụng
├── tsconfig.spec.json         # Cấu hình TypeScript cho Unit Test
├── src/
│   ├── index.html             # Tệp HTML chính
│   ├── main.ts                # Điểm khởi động bootstrap của Angular
│   ├── styles.css             # CSS toàn cục
│   └── app/
│       ├── app.module.ts      # Module chính của ứng dụng
│       ├── app.component.ts   # Root Component
│       ├── app.component.html # Root Template
│       ├── app.component.css  # Root Styles
│       ├── models/            # Chứa các interface/type dữ liệu
│       │   └── prediction.model.ts
│       ├── services/          # Chứa các Service kết nối API
│       │   ├── prediction.service.ts
│       │   └── prediction.service.spec.ts
│       └── components/        # Chứa các UI Components
│           └── prediction/
│               ├── prediction.component.ts
│               ├── prediction.component.html
│               ├── prediction.component.css
│               └── prediction.component.spec.ts
```

## Lệnh cài đặt và khởi chạy

```bash
# 1. Di chuyển vào thư mục frontend
cd frontend

# 2. Cài đặt các gói phụ thuộc
npm install

# 3. Khởi chạy máy chủ phát triển
npm start
# hoặc
ng serve

# 4. Build sản phẩm cho môi trường production
npm run build
```

## Kết nối API Backend
Service `PredictionService` gửi HTTP GET request tới endpoint `/api/analyze/predict` được cung cấp bởi Backend để nhận về danh sách 6 con số dự đoán từ mô hình phân tích XGBoost.
