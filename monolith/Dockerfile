# ============================
# 阶段 1：使用 Maven 镜像进行构建
# ============================
FROM maven:3.9-eclipse-temurin-25-alpine AS builder
WORKDIR /app

# 先拷贝 pom.xml 并下载依赖，利用 Docker 缓存机制加速后续构建
COPY pom.xml .
RUN mvn dependency:go-offline -B

# 拷贝源代码并打包
COPY src ./src
RUN mvn clean package -DskipTests

# ============================
# 阶段 2：使用轻量级 JRE 镜像运行
# ============================

FROM eclipse-temurin:25-jre-alpine
WORKDIR /app

# 从构建阶段拷贝打包好的 JAR 文件
COPY --from=builder /app/target/*.jar app.jar

# 暴露 8080 端口
EXPOSE 8080

# 启动命令
ENTRYPOINT ["java", "-jar", "app.jar"]