# 第九周大作业实践：API 网关与认证授权

> 本次实践共 35 分，包含「API Gateway」与「JWT 认证授权」两部分。
> 本文档只记录 **① API Gateway** 的实现、运行命令与验证结果；JWT 登录/签签/鉴权规则另项提交。

## 1. API Gateway

### 1.1 验收点对照

| 要求 | 实现 | 证据 |
|---|---|---|
| 新增 `gateway-service` | `services/gateway-service/` 独立工程 | 目录结构、`pom.xml` |
| 统一对外端口 8080 | `server.port: 8080`；compose 映射 `8080:8080` | §1.6 实测 |
| 至少 3 条路由 | 3 条业务路由 + 1 条认证路由（共 4 条，满足 ≥3） | §1.4 路由表 |
| 路由目标使用服务名 | 四条都用 `lb://<服务名>`，不写实例 IP/端口 | §1.4 |
| 保留健康检查入口 | `/api/health`（自建）与 `/actuator/health`（Actuator） | §1.5 |
| 提交代码、配置、健康入口 | 代码 + `application.yml` + `compose.yaml` | §1.2–§1.4 |

### 1.2 工程位置与结构

新增独立工程 `services/gateway-service/`，与其它服务同构（Spring Boot 4.1.2 + Java 25，版本由 BOM 管理）：

```text
services/gateway-service/
├── pom.xml
├── Dockerfile
├── mvnw / mvnw.cmd / .mvn/
└── src/
    ├── main/java/com/zjgsu/zcl/gateway/
    │   ├── GatewayServiceApplication.java
    │   └── controller/HealthController.java
    ├── main/resources/application.yml
    └── test/java/com/zjgsu/zcl/gateway/GatewayServiceApplicationTests.java
```

### 1.3 依赖

```xml
<!-- API 网关（WebFlux 版） -->
<dependency>
  <groupId>org.springframework.cloud</groupId>
  <artifactId>spring-cloud-starter-gateway-server-webflux</artifactId>
</dependency>
<!-- 服务发现：路由目标用服务名，由注册中心解析实例 -->
<dependency>
  <groupId>com.alibaba.cloud</groupId>
  <artifactId>spring-cloud-starter-alibaba-nacos-discovery</artifactId>
</dependency>
<dependency>
  <groupId>org.springframework.cloud</groupId>
  <artifactId>spring-cloud-starter-loadbalancer</artifactId>
</dependency>
<!-- 健康检查入口 /actuator/health -->
<dependency>
  <groupId>org.springframework.boot</groupId>
  <artifactId>spring-boot-starter-actuator</artifactId>
</dependency>
```

版本：Spring Cloud `2025.1.4`（Gateway `5.0.4`）、Spring Cloud Alibaba `2025.1.0.0`，与其它服务共用同一套 BOM。

### 1.4 路由配置

`services/gateway-service/src/main/resources/application.yml`：

```yaml
server:
  port: 8080

spring:
  application:
    name: gateway-service
  cloud:
    nacos:
      discovery:
        server-addr: ${NACOS_SERVER_ADDR:nacos:8848}
        username: ${NACOS_USERNAME:nacos}
        password: "${NACOS_PASSWORD:}"
        group: ${NACOS_GROUP:ZCL_GROUP}
        register-enabled: true
    # Gateway 5.x 的配置前缀：spring.cloud.gateway.server.webflux
    gateway:
      server:
        webflux:
          routes:
            - id: auth-service-route
              uri: lb://user-service
              predicates:
                - Path=/api/auth/**
            - id: user-service-route
              uri: lb://user-service
              predicates:
                - Path=/api/users/**
            - id: lost-service-route
              uri: lb://lost-service
              predicates:
                - Path=/api/lost-items/**
            - id: admin-service-route
              uri: lb://admin-service
              predicates:
                - Path=/api/admin/**
```

| # | 路由 id | 匹配路径 | 目标 | 对应后端接口 |
|---|---|---|---|---|
| 0 | `auth-service-route` | `/api/auth/**` | `lb://user-service` | `POST /api/auth/login`、`GET /api/auth/me` |
| 1 | `user-service-route` | `/api/users/**` | `lb://user-service` | `GET /api/users/{id}` |
| 2 | `lost-service-route` | `/api/lost-items/**` | `lb://lost-service` | `POST /api/lost-items` |
| 3 | `admin-service-route` | `/api/admin/**` | `lb://admin-service` | `GET/POST /api/admin/audits` |

说明：

- `lb://` 表示“按服务名做客户端负载均衡”：网关向 Nacos 查 `user-service` 等服务的实例列表，再由 LoadBalancer 选一个实例转发，**配置里没有出现任何实例 IP 或端口**；
- 没有加 `StripPrefix`，因为后端接口本身就是 `/api/users/...`、`/api/lost-items/...`、`/api/admin/...`，路径原样转发；
- 第 0 条是认证路由（第九周第二项加的），让登录与验签也走统一入口 8080。

  

### 1.5 健康检查入口

提供两个入口：

| 入口 | 来源 | 用途 |
|---|---|---|
| `GET /api/health` | `HealthController`（网关自身代码） | 只回答“网关进程是否在运行”，返回固定 JSON |
| `GET /actuator/health` | Spring Boot Actuator | 标准探活，带组件明细 |

```java
@RestController
public class HealthController {
    @GetMapping("/api/health")
    public Map<String, Object> health() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", "UP");
        body.put("service", "gateway-service");
        body.put("port", 8080);
        return body;
    }
}
```

实测：

```text
$ curl http://localhost:8080/api/health
{"status":"UP","service":"gateway-service","port":8080}      HTTP 200

$ curl -o /dev/null -w "%{http_code}" http://localhost:8080/actuator/health
200
```

### 1.6 统一入口实测（2026-11-21）

启动命令：`docker compose up -d --build`（网关与 Nacos、三个业务服务一起启动）。

| # | 场景 | 直连后端 | 经网关 8080 | 状态码 | 响应体 |
|---|---|---|---|---|---|
| 1 | 查询用户 | `GET http://localhost:8081/api/users/1` | `GET http://localhost:8080/api/users/1` | 200 | `{"id":1,"username":"student_zhang"}` |
| 2 | 发布失物 | `POST http://localhost:8082/api/lost-items` | `POST http://localhost:8080/api/lost-items`（`userId=1`） | 200 | `{"id":58,"message":"发布成功"}` |
| 3 | 业务拒绝 | 同上（`userId=9999`） | 同上 | 400 | `{"error":"发布失败：用户 9999 不存在，无法发布失物"}` |
| 4 | 审核记录 | `GET http://localhost:8083/api/admin/audits` | `GET http://localhost:8080/api/admin/audits` | 200 | `[{"id":1,"lostItemId":1,"status":"APPROVED"}]` |
| 5 | 网关健康 | — | `GET http://localhost:8080/api/health` | 200 | `{"status":"UP",...}` |

补充证据：连续 6 次经网关访问 `/api/users/1`，全部 200；`user-service` 两个实例的日志分别出现命中记录（`instance=user-service-1` 与 `instance=user-service-b-1`），说明网关的 `lb://user-service` 确实经过 LoadBalancer 分发到两个实例，而不是把请求固定发给某一个容器。

### 1.7 运行命令

```bash
# 1) 启动整套环境（首次会构建 4 个服务镜像）
docker compose up -d --build

# 2) 网关健康
curl -s -w "\nHTTP %{http_code}\n" http://localhost:8080/api/health
curl -s -o /dev/null -w "actuator: %{http_code}\n" http://localhost:8080/actuator/health

# 3) 三条路由（全部经过网关，后端端口只用于对照）
curl -s -w "\nHTTP %{http_code}\n" http://localhost:8080/api/users/1
curl -s -w "\nHTTP %{http_code}\n" -H "Content-Type: application/json" \
  -d '{"itemName":"gateway-test","feature":"gateway","userId":1}' \
  http://localhost:8080/api/lost-items
curl -s -w "\nHTTP %{http_code}\n" http://localhost:8080/api/admin/audits

# 4) 业务拒绝仍保留原有状态码（400）
curl -s -w "\nHTTP %{http_code}\n" -H "Content-Type: application/json" \
  -d '{"itemName":"gateway-test","feature":"gateway","userId":9999}' \
  http://localhost:8080/api/lost-items

# 5) 单元测试（网关工程内）
cd services/gateway-service && ./mvnw test
```

### 1.8 端口与访问方式

| 端口 | 服务 | 说明 |
|---|---|---|
| 8080 | gateway-service | **统一对外入口**，作业要求的访问方式 |
| 8081 | user-service（实例 A） | 开发调试用，仍保留 |
| 18081 | user-service（实例 B） | 同上 |
| 8082 | lost-service | 开发调试用 |
| 8083 | admin-service | 开发调试用 |

网关不改变后端端口，只是增加了一层统一入口；服务之间的调用（`lost-service` → `user-service`）仍然直连服务名，不经过网关，避免多一跳。



### 1.9 提交清单

| 项 | 内容 |
|---|---|
| `gateway-service` 代码 | `services/gateway-service/`（启动类、健康 Controller、路由配置、测试） |
| 路由配置 | `services/gateway-service/src/main/resources/application.yml` |
| 服务编排 | 根目录 `compose.yaml` 新增 `gateway-service`（端口 8080、依赖 Nacos 与三个服务） |
| 健康检查入口 | `/api/health`、`/actuator/health` |
| 文档 | 本文件 |

## 2. 认证服务

### 2.1 验收点对照

| 要求 | 实现 | 证据 |
|---|---|---|
| 登录接口，返回 JWT | `POST /api/auth/login`：校验密码后签发 HMAC-SHA256 JWT | §2.4、§2.8 |
| 至少 2 种角色或权限 | `USER`（普通用户）、`ADMIN`（管理员），角色写进 JWT 的 `role` claim | §2.2、§2.6 |
| 密码以安全的哈希存储 | BCrypt（cost 10），数据库列 `password_hash`；代码里没有任何明文密码 | §2.7、§2.9 |
| 提交登录、签发验签、角色、密码处理代码 | 见 §2.3 文件清单 | 本文件 |
| 认证脚本返回清晰的成功/失败 | `scripts/auth-test.sh`：逐项 `[PASS]/[FAIL]`，失败时退出码非 0 | §2.9 |
| 不提交真实密钥 | 密钥由 `.env` 的 `JWT_SECRET` 注入，仓库内无密钥 | §2.6 |

### 2.2 测试账号与角色（README 同步列出）

| 用户名 | 演示密码 | 角色 | 用途 |
|---|---|---|---|
| `student_zhang` | `Student@123` | `USER` | 普通用户：登录、验签 |
| `admin_li` | `Admin@123` | `ADMIN` | 管理员：登录、验签、后续审核接口鉴权 |

说明：表中的演示密码只用于课堂验证；数据库、代码、JWT 里都不会出现明文。两个账号由 `data.sql` 写入，写入的是 BCrypt 哈希。

### 2.3 代码位置

| 文件 | 作用 |
|---|---|
| `services/user-service/.../controller/AuthController.java` | `POST /api/auth/login`、`GET /api/auth/me`，负责 HTTP 语义（200/400/401） |
| `services/user-service/.../service/AuthService.java` | 登录校验、调用签发、验签并取身份 |
| `services/user-service/.../security/JwtTokenService.java` | JWT 签发（claims：subject/username/role/iat/exp）与验签 |
| `services/user-service/.../security/JwtConfig.java` | `BCryptPasswordEncoder`、`JwtEncoder`、`JwtDecoder` 三个 Bean；从 `JWT_SECRET` 读取密钥 |
| `services/user-service/.../model/User.java` | 新增 `passwordHash`（`@JsonIgnore`）与 `role` 字段 |
| `services/user-service/src/main/resources/data.sql` | 两个测试账号的 BCrypt 哈希种子 |
| `services/user-service/src/main/resources/application.yml` | `app.jwt.secret` / `app.jwt.expiration-seconds` |
| `scripts/auth-test.sh` | 认证端到端验证脚本（10 项断言） |

### 2.4 登录接口

```http
POST /api/auth/login
Content-Type: application/json

{"username":"student_zhang","password":"Student@123"}
```

成功（200）：

```json
{
  "token": "eyJhbGciOiJIUzI1NiJ9.eyJpc3MiOiJ1c2V...",
  "tokenType": "Bearer",
  "expiresIn": 3600,
  "user": {"id": 1, "username": "student_zhang", "role": "USER"}
}
```

失败：

| 情况 | 状态码 | 响应体 |
|---|---|---|
| 密码错误 / 用户不存在 | `401` | `{"error":"用户名或密码错误"}`（两种情况同一句，不暴露账号是否存在） |
| 用户名或密码为空 | `400` | `{"error":"用户名和密码不能为空"}` |

### 2.5 验签接口

```http
GET /api/auth/me
Authorization: Bearer <token>
```

- 200：`{"id":1,"username":"student_zhang","role":"USER"}`；
- 缺少 Token、格式不是 `Bearer`、签名被篡改、Token 过期：`401`。

这个接口的作用是“用受保护资源证明 Token 真的被验签”：签名不对就进不来。

### 2.6 JWT 设计

| 项 | 说明 |
|---|---|
| 算法 | HMAC-SHA256（对称密钥） |
| claims | `iss=user-service`、`sub=用户 id`、`username`、`role`、`iat`、`exp` |
| 有效期 | 默认 3600 秒，可用 `JWT_EXPIRATION_SECONDS` 覆盖 |
| 密钥 | 环境变量 `JWT_SECRET`（Base64，解码后 ≥32 字节）；`.env` 里保存，仓库不提交 |
| 缺失密钥 | 启动直接失败并提示如何生成，避免弱默认密钥进生产 |

生成密钥的命令（自己执行一次，写进 `.env` 即可）：

```bash
openssl rand -base64 32
```

`compose.yaml` 已把 `JWT_SECRET` 注入两个 `user-service` 实例，所以两实例签发/验签的 Token 互相通用。

### 2.7 密码安全处理

1. **存储**：只存 BCrypt 哈希（`$2a$10$...`，60 字符），不存明文；`data.sql` 里同样只有哈希。
2. **比对**：`PasswordEncoder.matches(rawPassword, hash)`，不还原明文。
3. **不泄漏**：`User.passwordHash` 标了 `@JsonIgnore`，`GET /api/users/{id}` 不会把哈希带出去。
4. **不暴露账号**：用户不存在与密码错误返回同一句 `用户名或密码错误`。
5. **不落日志**：登录失败只返回统一错误，不打印密码或哈希。

### 2.8 实测结果（2026-11-21）

```text
# 普通用户登录
POST http://localhost:8081/api/auth/login  {"username":"student_zhang","password":"Student@123"}
→ 200 {"token":"eyJhbGciOiJIUzI1NiJ9...","tokenType":"Bearer","expiresIn":3600,"user":{"id":1,"username":"student_zhang","role":"USER"}}

# 密码错误
POST 同上 {"username":"student_zhang","password":"wrong-password"}
→ 401 {"error":"用户名或密码错误"}

# 验签
GET http://localhost:8081/api/auth/me  Authorization: Bearer <token>
→ 200 {"id":1,"username":"student_zhang","role":"USER"}

# 缺 Token
GET http://localhost:8081/api/auth/me
→ 401 {"error":"缺少 Authorization: Bearer <token>"}

# 篡改签名
GET http://localhost:8081/api/auth/me  Authorization: Bearer <token 末位被改>
→ 401 {"error":"Token 无效或已过期"}

# 管理员
POST ... {"username":"admin_li","password":"Admin@123"}
→ 200 {"token":"...","user":{"id":2,"username":"admin_li","role":"ADMIN"}}
```

### 2.9 认证脚本

```bash
bash scripts/auth-test.sh
```

脚本共 10 项检查：登录成功、角色正确、错误密码 401、不存在用户 401、`/me` 验签、缺 Token 401、篡改签名 401、管理员登录与 `/me`、数据库哈希格式、经网关登录。结果示例：

```text
[PASS] 普通用户登录成功，返回三段式 JWT
[PASS] 普通用户登录返回 role=USER
[PASS] 错误密码返回 401
[PASS] 不存在的用户返回 401
[PASS] /me 验签通过，返回 username=student_zhang
[PASS] 缺少 Authorization 返回 401
[PASS] 篡改签名后返回 401
[PASS] 管理员登录成功，返回 role=ADMIN
[PASS] 管理员 /me 验签通过，role=ADMIN
[PASS] student_zhang 存的是 BCrypt 哈希（$2a$，长度 60）
[PASS] admin_li 存的是 BCrypt 哈希（$2a$，长度 60）
[PASS] 密码列不含明文特征（@）
[PASS] 经网关 8080 登录成功
结果：PASS=13  FAIL=0
```

> 上面的条数与最终输出以脚本实际运行为准（脚本会按环境略有增减）。

### 2.10 截图清单（提交 1–5 张）

| # | 文件名建议 | 截图内容 |
|---|---|---|
| 1 | `auth-login-success.png` | Postman 或终端：`POST /api/auth/login`，200，返回 `token`、`role=USER` |
| 2 | `auth-login-401.png` | 同一接口改成错误密码，401 与 `{"error":"用户名或密码错误"}` |
| 3 | `auth-me.png` | `GET /api/auth/me` 带 Bearer Token，200 返回 `username/role` |
| 4 | `auth-hash-db.png` | 数据库里 `users` 表的 `password_hash` 列，显示 `$2a$10$...`，证明没有明文 |
| 5 | `auth-script.png` | `bash scripts/auth-test.sh` 的 `[PASS]` 输出与最后的 `PASS=13 FAIL=0` |

截图保存到 `docs/homework/week-09/screenshots/`。

### 2.11 与后续「鉴权规则」的衔接

本项只做**认证**（登录、签发、验签、角色、密码哈希），**没有**在网关加拦截：现在业务接口仍可匿名访问。下一项「鉴权规则」会在网关加全局过滤器：

- 无 Token 访问受保护路径 → 401；
- Token 有效但角色不足（如普通用户调 `/api/admin/**`）→ 403；
- 公开路径（`/api/health`、登录接口）放行。

这样“未授权和越权能被阻止”才有可演示的证据；本项的 `/api/auth/me` 已经验证了验签代码本身是通的。
