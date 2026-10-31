## 服务拆分设计

### 1. 实现思路
*   **业务边界划分**：将原单体工程按业务线拆分为三个核心服务：`user-service`（用户服务，8081）、`lost-service`（失物服务，8082）和 `admin-service`（审核服务，8083）。
*   **数据私有化**：遵循微服务架构原则，每个服务配置独立的数据源（`user_db`、`lost_db`、`admin_db`），在代码层面取消了实体间的物理外键，仅保留关联 ID。
*   **跨服务通信**：`lost-service` 在发布失物时，通过 `RestTemplate` 发送 HTTP 请求至 `user-service` 校验用户合法性；并针对网络调用增加了 `try-catch` 异常捕获，防止因下游服务宕机引发雪崩。

### 2. 运行命令
由于工程被拆分为多模块，需分别在各自的目录下启动：

**启动用户服务 (端口 8081):**
```bash
cd services/user-service
./mvnw spring-boot:run

```

## 代码拆分

### 实现思路
*   在 `services/` 目录下为各个微服务建立独立的 Spring Boot 工程。
*   每个工程包含独立的 `pom.xml`、配置文件、Controller、Service 和测试类。
*   **服务调用与容错**：`lost-service` 将 `user-service` 的地址提取到 `application.yml` 中，通过 `@Value` 注入并支持环境变量覆盖。在 `RestTemplateConfig` 中配置了 3 秒的读写超时，并在 Service 层进行严格校验。如果校验未通过，直接抛出异常，拦截本地数据库写入。

### 验证结果
调用接口测试，当用户存在时发布成功（200），当用户不存在时返回明确错误（400），证明跨服务校验和超时机制生效。
## 本地编排

### 实现思路
*   更新根目录的 `compose.yaml`，实现了 3 个微服务和 3 个独立数据库（user-db, lost-db, admin-db）的一键启动。
*   **数据隔离**：每个服务通过独立账号连接各自的数据库，配置了命名数据卷（Volumes）防止数据丢失。
*   **网络通信**：配置了 `micro-net` 自定义网络，微服务间通过服务名（如 `user-service`、`user-db`）通信，去除了 `localhost` 依赖。

### 运行命令
```bash
docker compose up -d --build
```