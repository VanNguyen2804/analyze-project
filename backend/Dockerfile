FROM eclipse-temurin:17-jdk-jammy

RUN apt-get update && apt-get install -y python3 python3-pip
RUN pip3 install xgboost numpy pandas scikit-learn

WORKDIR /app
COPY . /app

RUN ./mvnw clean package -DskipTests
COPY xgboost_predict.py /app/xgboost_predict.py

EXPOSE 8080
# Đã đổi tên file jar xuất ra thành analyzeproject
CMD ["java", "-Xmx256m", "-jar", "target/analyzeproject-0.0.1-SNAPSHOT.jar"]
