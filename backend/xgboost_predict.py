import sys
import json
import xgboost as xgb
import numpy as np

def train_and_predict():
    X_train = np.random.rand(200, 2)  
    y_train = np.random.randint(0, 2, 200) 

    model = xgb.XGBClassifier(objective='binary:logistic', eval_metric='logloss')
    model.fit(X_train, y_train)

    X_current = np.random.rand(45, 2) 
    probabilities = model.predict_proba(X_current)[:, 1] 

    top_6_indices = probabilities.argsort()[-6:][::-1]
    predicted_numbers = [int(i + 1) for i in top_6_indices]
    predicted_numbers.sort()

    print(json.dumps(predicted_numbers))

if __name__ == "__main__":
    train_and_predict()
