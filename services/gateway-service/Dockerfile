# 阶段 1：构建
FROM maven:3.9-eclipse-temurin-25-alpine AS builder
WORKDIR /app
COPY pom.xml .
COPY settings.xml /tmp/settings.xml
RUN mvn -s /tmp/settings.xml dependency:go-offline -B
COPY src ./src
RUN mvn -s /tmp/settings.xml clean package -DskipTests

# 阶段 2：运行
FROM eclipse-temurin:25-jre-alpine
WORKDIR /app
COPY --from=builder /app/target/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
