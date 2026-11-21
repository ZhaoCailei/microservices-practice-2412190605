# 第九周大作业实践：API 网关与认证授权

> 本次实践共 35 分，包含「API Gateway」与「JWT 认证授权」两部分。
> 本文档只记录 **① API Gateway** 的实现、运行命令与验证结果；JWT 登录/签签/鉴权规则另项提交。

## 1. API Gateway

### 1.1 验收点对照

| 要求 | 实现 | 证据 |
|---|---|---|
| 新增 `gateway-service` | `services/gateway-service/` 独立工程 | 目录结构、`pom.xml` |
| 统一对外端口 8080 | `server.port: 8080`；compose 映射 `8080:8080` | §1.6 实测 |
| 至少 3 条路由 | `/api/users/**`、`/api/lost-items/**`、`/api/admin/**` 三条 | §1.4 路由表 |
| 路由目标使用服务名 | 三条都用 `lb://<服务名>`，不写实例 IP/端口 | §1.4 |
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
| 1 | `user-service-route` | `/api/users/**` | `lb://user-service` | `GET /api/users/{id}` |
| 2 | `lost-service-route` | `/api/lost-items/**` | `lb://lost-service` | `POST /api/lost-items` |
| 3 | `admin-service-route` | `/api/admin/**` | `lb://admin-service` | `GET/POST /api/admin/audits` |

说明：

- `lb://` 表示“按服务名做客户端负载均衡”：网关向 Nacos 查 `user-service` 等服务的实例列表，再由 LoadBalancer 选一个实例转发，**配置里没有出现任何实例 IP 或端口**；
- 没有加 `StripPrefix`，因为后端接口本身就是 `/api/users/...`、`/api/lost-items/...`、`/api/admin/...`，路径原样转发；
- 三条路由覆盖了三个业务的真实入口，不是为凑数写的假路由（§1.6 均实测通过）。

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

### 1.9 截图清单（提交 1–5 张）

| # | 文件名建议 | 截图内容 |
|---|---|---|
| 1 | `gateway-route-config.png` | IDEA 中打开 `services/gateway-service/src/main/resources/application.yml`，显示三条 `routes` 与 `lb://` |
| 2 | `gateway-health.png` | 同屏两个健康入口：`/api/health` 的 JSON 与 `/actuator/health` 的 `{"status":"UP"}`，状态码 200 |
| 3 | `gateway-routes.png` | Postman 或终端：三个接口都访问 `http://localhost:8080/...`，显示 200/200/200 及响应体 |
| 4 | `gateway-400.png` | 经网关发 `userId=9999`，显示 400 与 `{"error":"发布失败：用户 9999 不存在..."}`（证明网关透传业务状态码） |
| 5 | `gateway-nacos-lb.png` | Nacos 控制台服务列表（`gateway-service` 已注册、`user-service` 两个实例），配一张终端日志（两个实例各有 `instance=` 命中） |

截图保存到 `docs/homework/week-09/screenshots/`。命名不必完全一致，能对应上即可。

### 1.10 提交清单

| 项 | 内容 |
|---|---|
| `gateway-service` 代码 | `services/gateway-service/`（启动类、健康 Controller、路由配置、测试） |
| 路由配置 | `services/gateway-service/src/main/resources/application.yml` |
| 服务编排 | 根目录 `compose.yaml` 新增 `gateway-service`（端口 8080、依赖 Nacos 与三个服务） |
| 健康检查入口 | `/api/health`、`/actuator/health` |
| 文档 | 本文件 |

### 1.11 与后续「JWT 认证授权」的衔接

- 网关目前只做**路由**，未做认证：所有请求都会转发给后端；
- 后续项会在网关加全局过滤器/认证服务：登录签发 JWT → 网关校验签名与角色 → 按路径/角色放行或返回 401/403；
- 届时 `compose.yaml` 中的网关配置会新增认证相关环境变量（如 `JWT_SECRET`），真实密钥不写进仓库，只写在本地 `.env` 或运行时注入。
