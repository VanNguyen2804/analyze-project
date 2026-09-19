# ==========================================
# STAGE 1: Build Angular Frontend
# ==========================================
FROM node:20-alpine AS frontend-builder
WORKDIR /app/frontend

COPY frontend/package*.json ./
RUN npm install

COPY frontend/ ./
RUN npm run build -- --configuration production

# ==========================================
# STAGE 2: Build Spring Boot Backend
# ==========================================
FROM maven:3.9.6-eclipse-temurin-17-alpine AS backend-builder
WORKDIR /app/backend

COPY backend/pom.xml ./
RUN mvn dependency:go-offline -B || true

COPY backend/src ./src
RUN mvn clean package -DskipTests -B

# ==========================================
# STAGE 3: Final Unified Production Container
# ==========================================
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app

# Copy Spring Boot executable JAR
COPY --from=backend-builder /app/backend/target/*.jar /app/app.jar

# Copy compiled Angular assets into public directory served by Spring Boot
COPY --from=frontend-builder /app/frontend/dist/frontend /app/public

# Default environment configuration for Render
ENV PORT=8080
ENV SPRING_PROFILES_ACTIVE=prod

EXPOSE 8080

ENTRYPOINT ["sh", "-c", "java -Dserver.port=${PORT:-8080} -jar /app/app.jar"]
