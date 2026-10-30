# 第五周作业：Docker 容器化与数据持久化

## 1. Dockerfile 与多阶段构建
本工程编写了基于多阶段构建的 Dockerfile。第一阶段使用 `maven:3.9-eclipse-temurin-21` 镜像进行编译和打包，分离依赖下载层以利用缓存。第二阶段使用轻量级的 `eclipse-temurin:21-jre-alpine` 镜像运行 JAR，极大减小了最终镜像体积。
构建命令：`docker build -t my-project:week05 ./monolith`
*(此处插入构建成功、docker images 的截图)*

## 2. Compose 编排与一键启动
在根目录编写了 `compose.yaml` 和 `.env.example`，定义了 MySQL 服务和 App 服务，并配置了自定义 bridge 网络。
启动命令：`docker compose up -d --build`
*(此处插入 docker compose 启动成功的截图)*

## 3. 业务 API 验证
应用启动后，容器内的 Spring Boot 成功连接至 db 容器。使用 REST Client 调用接口验证。
*(此处插入 POST 创建成功的截图)*

## 4. 数据持久化验证
通过 `volumes` 将 MySQL 数据映射到 `db-data` 卷。
执行 `docker compose down` 后重新 `up`，再次查询接口，数据依旧存在，证明持久化生效。
*(此处插入两次查询的对比截图)*