import sys
import json
import xgboost as xgb
import numpy as np

def train_and_predict(lottery_type):
    # Xác định giới hạn số dựa vào loại xổ số
    max_number = 55 if lottery_type == "POWER" else 45

    # 1. Giả lập tập huấn luyện
    # Trong thực tế, bạn sẽ select DB theo loại xổ số tương ứng ở đây
    X_train = np.random.rand(200, 2)  
    y_train = np.random.randint(0, 2, 200) 

    # Khởi tạo mô hình
    model = xgb.XGBClassifier(objective='binary:logistic', eval_metric='logloss')
    model.fit(X_train, y_train)

    # 2. Dự đoán cho max_number (45 hoặc 55) con số hiện tại
    X_current = np.random.rand(max_number, 2) 
    probabilities = model.predict_proba(X_current)[:, 1] 

    # 3. Lấy 10 số có xác suất cao nhất 
    top_10_indices = probabilities.argsort()[-10:][::-1]
    predicted_numbers = [int(i + 1) for i in top_10_indices]
    
    # Sắp xếp lại
    predicted_numbers.sort()

    # Xuất ra JSON
    print(json.dumps(predicted_numbers))

if __name__ == "__main__":
    # Nhận loại xổ số từ Argument do Java truyền vào
    if len(sys.argv) > 1:
        lottery_type = sys.argv[1].upper()
    else:
        lottery_type = "MEGA" # Mặc định
        
    train_and_predict(lottery_type)