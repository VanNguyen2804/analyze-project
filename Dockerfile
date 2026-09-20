# ==========================================
# STAGE 1: Build Angular Frontend
# ==========================================
FROM node:22-slim AS frontend-builder
WORKDIR /app/frontend

# Configure Node memory limit to prevent OOM on Render Free Tier (512MB RAM limit)
ENV NODE_OPTIONS="--max-old-space-size=450"
ENV NG_FORCE_COMPAT_CHECK=false

COPY frontend/package*.json ./
RUN npm install

COPY frontend/ ./
RUN npm run build

# Ensure compatibility with both classic browser builder and application builder
RUN if [ -d "/app/frontend/dist/analyzeproject/browser" ]; then \
      cp -r /app/frontend/dist/analyzeproject/browser /app/frontend-dist; \
    elif [ -d "/app/frontend/dist/analyzeproject" ]; then \
      cp -r /app/frontend/dist/analyzeproject /app/frontend-dist; \
    elif [ -d "/app/frontend/dist/frontend/browser" ]; then \
      cp -r /app/frontend/dist/frontend/browser /app/frontend-dist; \
    else \
      cp -r /app/frontend/dist/frontend /app/frontend-dist; \
    fi

# ==========================================
# STAGE 2: Build Spring Boot Backend
# ==========================================
FROM maven:3.9.6-eclipse-temurin-17 AS backend-builder
WORKDIR /app/backend

# Configure JVM memory limits for Maven to run smoothly within container limits (Render free tier)
ENV MAVEN_OPTS="-Xmx512m -XX:+TieredCompilation -XX:TieredStopAtLevel=1"

# Copy pom.xml and source code
COPY backend/pom.xml ./
COPY backend/src ./src

# Inject freshly built Angular frontend from Stage 1 into backend's static directory before packaging JAR
RUN rm -rf ./src/main/resources/static/*
COPY --from=frontend-builder /app/frontend-dist/ ./src/main/resources/static/

# Build production jar skipping tests
RUN mvn clean package -DskipTests -B

# ==========================================
# STAGE 3: Final Unified Production Container
# ==========================================
FROM eclipse-temurin:17-jre-jammy
WORKDIR /app

# Copy Spring Boot executable JAR
COPY --from=backend-builder /app/backend/target/analyzeproject-*.jar /app/app.jar

# Copy compiled Angular assets into public directory served by Spring Boot
COPY --from=frontend-builder /app/frontend-dist /app/public

# Default environment configuration for Render
ENV PORT=8080
ENV SPRING_PROFILES_ACTIVE=prod

EXPOSE 8080

ENTRYPOINT ["sh", "-c", "java -Dserver.port=${PORT:-8080} -jar /app/app.jar"]
