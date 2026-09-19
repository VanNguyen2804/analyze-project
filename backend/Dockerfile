# Dockerfile for Spring Boot Backend on Render
FROM maven:3.9.6-eclipse-temurin-17 AS builder
WORKDIR /app

ENV MAVEN_OPTS="-Xmx512m -XX:+TieredCompilation -XX:TieredStopAtLevel=1"

COPY pom.xml ./
COPY src ./src

RUN mvn clean package -DskipTests -B

FROM eclipse-temurin:17-jre-jammy
WORKDIR /app

COPY --from=builder /app/target/analyzeproject-*.jar /app/app.jar

ENV PORT=8080
EXPOSE 8080

ENTRYPOINT ["sh", "-c", "java -Dserver.port=${PORT:-8080} -jar /app/app.jar"]

