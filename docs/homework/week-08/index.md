# 第八周作业：服务间通信与负载均衡

> 本周提交两项：① **选择一个跨服务业务流程**；② **使用 OpenFeign**。
> 大作业实践（多实例部署 + 负载均衡验证 + `scripts/e2e-test.sh`）本周**不提交**。

---

## 1. 选择一个跨服务业务流程

### 1.1 流程与理由

**流程：发布失物前校验发布者是否存在。**

一个用户发失物帖时，`lost-service` 必须先确认这个 `userId` 真的存在，而用户数据归 `user-service`、失物数据归 `lost-service`，两个服务各有一个私有库（`user_db` / `lost_db`），无法用 SQL 直接关联，所以这条链路**必须跨服务完成**。

### 1.2 调用链路图

```text
[客户端]
   │  POST /api/lost-items   {"itemName","feature","userId":1}
   ▼
[lost-service :8082]  LostItemController → LostItemService（@Transactional）
   │ ① 调用前由 LoadBalancer 从 Nacos（public / ZCL_GROUP）取 user-service 实例
   │ ② Feign 用服务名发出：GET http://<实例IP>:8081/api/users/{id}（超时 3s）
   │      200 → 用户存在，继续      404 → 返回 400，不写库
   │      无实例 / 超时 / 5xx → 返回 503，不写库
   │ ③ 校验通过才 INSERT
   ▼
[lost_db · lost_items] ──▶ [客户端] 200 发布成功，失物ID：n
```

完整版见 [`docs/service-communication.md`](../../service-communication.md)。

### 1.3 调用方、被调用方与接口路径

| 角色 | 服务 | 接口路径 | 说明 |
|---|---|---|---|
| 调用方 | `lost-service` :8082 | `POST /api/lost-items` | 对外入口，请求体含 `itemName`、`feature`、`userId` |
| 被调用方 | `user-service` :8081 | `GET /api/users/{id}` | 存在 200 + 用户 JSON，不存在 404 |
| 注册中心 | Nacos | — | 服务名 `user-service`，命名空间 `public`，分组 `ZCL_GROUP` |

### 1.4 实现思路

1. **寻址**：`UserServiceClient` 上用 `@FeignClient(name = "user-service", path = "/api/users")`，代码里只有服务名，没有任何 IP/端口；由 LoadBalancer 从 Nacos 取实例。
2. **调用**：`UserClient.checkUserExists(userId)` 调 `findById(userId)`，返回非空且含 `id` 即视为存在。
3. **失败处理**：见第 2 节，404 与“依赖不可用”分开处理。
4. **事务边界**：校验在 `entityManager.persist()` 之前，方法整体在 `@Transactional` 内。
5. **数据边界**：`lost_items` 只存 `user_id`，不建跨库外键，不直连 `user_db`。

### 1.5 运行命令

```bash
docker compose up -d
curl -s -o /dev/null -w "%{http_code}\n" http://localhost:8081/api/users/1

# 正常发布
curl -s -w "\nHTTP %{http_code}\n" -H "Content-Type: application/json" \
  -d '{"itemName":"黑色折叠伞","feature":"伞柄贴有蓝色标签","userId":1}' \
  http://localhost:8082/api/lost-items

# 用户不存在
curl -s -w "\nHTTP %{http_code}\n" -H "Content-Type: application/json" \
  -d '{"itemName":"测试失物","feature":"不存在用户","userId":9999}' \
  http://localhost:8082/api/lost-items

# 依赖不可用
docker compose stop user-service && sleep 4
curl -s -w "\nHTTP %{http_code}\n" -H "Content-Type: application/json" \
  -d '{"itemName":"黑色折叠伞","feature":"伞柄贴有蓝色标签","userId":1}' \
  http://localhost:8082/api/lost-items
```

> PowerShell 下内联 JSON 的引号会被吞掉，先写文件再用 `--data-binary "@body.json"`。

### 1.6 运行结果（2026-11-15 实测）

| # | 场景 | 状态码 | 返回 |
|---|---|---|---|
| 1 | 正常发布（`userId=1`） | 200 | `发布成功，失物ID：6` |
| 2 | 用户不存在（`userId=9999`） | 400 | `发布失败：用户 9999 不存在，无法发布失物` |
| 3 | 停掉 `user-service` 后再发 | 503 | `依赖服务不可用：调用 user-service 失败：无法连接到 user-service（注册中心无可用实例、连接失败或超时）` |
| 4 | 停服期间统计 `lost_items` | — | 6 行 → 6 行，**未写入** |
| 5 | 重启 `user-service` 后再发 | 200 | `发布成功，失物ID：7`，行数 6 → 7 |

补充：`lost-service` 容器重建后健康接口约 10.6 秒 `UP`；`user-service` 重启后约 9.8 秒恢复到可调用。**健康检查通过不等于已在注册中心可被发现**。

### 1.7 关键结论

- 404 是业务答案（400，不写库），实例为空/超时是基础设施故障（503，不写库），两者必须分开；
- 依赖不可用时应拒绝发布，而不是跳过校验写库；
- 服务发现的价值是调用时实时选实例，写死 `http://user-service:8081` 只是用了 Compose 的 DNS。

---

## 2. 使用 OpenFeign

### 2.1 依赖

`services/lost-service/pom.xml`，两个依赖，版本全部由 BOM 管理（不写 version）：

```xml
<dependency>
  <groupId>org.springframework.cloud</groupId>
  <artifactId>spring-cloud-starter-openfeign</artifactId>
</dependency>
<dependency>
  <groupId>org.springframework.cloud</groupId>
  <artifactId>spring-cloud-starter-loadbalancer</artifactId>
</dependency>
```

启动类加注解：`@EnableFeignClients`。

### 2.2 客户端：只用服务名

`services/lost-service/src/main/java/com/zjgsu/zcl/lost/client/UserServiceClient.java`

```java
@FeignClient(name = UserClient.USER_SERVICE_NAME, path = UserServiceClient.BASE_PATH)
public interface UserServiceClient {

    String BASE_PATH = "/api/users";

    @GetMapping("/{id}")
    Map<String, Object> findById(@PathVariable("id") Long id);
}
```

- `name = "user-service"` 是**服务名**，不是容器 IP / 端口；
- `path` + `@GetMapping("/{id}")` 拼成 `GET /api/users/{id}`，与 user-service 原有接口一致；
- 实际目标地址由 `spring-cloud-starter-loadbalancer` 从 Nacos 实例列表中选，本次只有一个实例。

### 2.3 调用逻辑与失败处理

`services/lost-service/src/main/java/com/zjgsu/zcl/lost/client/UserClient.java`

```java
public boolean checkUserExists(Long userId) {
    try {
        Map<String, Object> user = userServiceClient.findById(userId);
        return user != null && user.get("id") != null;
    } catch (FeignException.NotFound e) {
        return false;                       // 用户不存在：业务答案
    } catch (FeignException e) {
        throw new UserServiceUnavailableException("调用 user-service 失败："
                + (e.status() > 0
                ? "对方返回 HTTP " + e.status()
                : "无法连接到 user-service（注册中心无可用实例、连接失败或超时）"), e);
    } catch (RuntimeException e) {          // LoadBalancer 无实例、超时等
        throw new UserServiceUnavailableException("调用 user-service 失败：" + e.getMessage(), e);
    }
}
```

三种结果对应控制器三种响应：

| 情况 | 异常 | 响应 |
|---|---|---|
| 用户存在 | 无 | 200 `发布成功，失物ID：n` |
| 用户不存在 | `FeignException.NotFound` | 400 `发布失败：用户 x 不存在` |
| 依赖不可用 | `UserServiceUnavailableException` | 503 `依赖服务不可用：调用 user-service 失败：…` |

超时配置（`application.yml`）：

```yaml
spring:
  cloud:
    openfeign:
      client:
        config:
          default:
            connect-timeout: 3000
            read-timeout: 3000
```

### 2.4 与第七周的差别

| 项 | 第七周（手工寻址） | 第八周（OpenFeign） |
|---|---|---|
| 地址来源 | `DiscoveryClient.getInstances()` 手写，自己拼 URI | `@FeignClient(name = "user-service")`，LoadBalancer 自动选实例 |
| 代码里的地址 | 代码中出现 URI 拼接 | 只有服务名与路径注解 |
| 超时 | `RestTemplate` 工厂设置 | `spring.cloud.openfeign` 配置 |
| 失败处理 | `catch RestClientException` / `HttpClientErrorException` | `catch FeignException` / `RuntimeException` |
| 已删除 | — | `RestTemplateConfig`、`DiscoveryClient` 手工寻址代码 |

### 2.5 验证方法

```bash
# 1) 单元测试：4 个场景（存在 / 404 / 对方 5xx / 无实例），不连真实 Nacos
cd services/lost-service && ./mvnw test

# 2) 正常调用
curl -s -w "\nHTTP %{http_code}\n" -H "Content-Type: application/json" \
  -d '{"itemName":"黑色折叠伞","feature":"伞柄贴有蓝色标签","userId":1}' \
  http://localhost:8082/api/lost-items

# 3) 失败处理：停掉被调用方，应返回 503 与可读错误
docker compose stop user-service && sleep 4
curl -s -w "\nHTTP %{http_code}\n" -H "Content-Type: application/json" \
  -d '{"itemName":"黑色折叠伞","feature":"伞柄贴有蓝色标签","userId":1}' \
  http://localhost:8082/api/lost-items

# 4) 恢复
docker compose start user-service
```

### 2.6 结果

- `./mvnw test`：**Tests run: 5, Failures: 0, Errors: 0**（`UserClientTest` 4 个 + 上下文 1 个）；
- 正常调用：**200**，`发布成功，失物ID：6`；
- 停掉 `user-service`：**503**，`依赖服务不可用：调用 user-service 失败：无法连接到 user-service（注册中心无可用实例、连接失败或超时）`；
- 停服期间 `lost_items` 行数不变（6 → 6），未写库；
- 重启后：**200**，`发布成功，失物ID：7`，行数 6 → 7；
- 冒烟脚本 `scripts/smoke-test.sh`：4 项全部 PASS。

截图见 [`screenshots/`](screenshots/)。

---

## 3. 本周未完成（大作业实践，另行提交）

| 项 | 状态 |
|---|---|
| 多实例部署（同一服务 ≥2 副本） | 未做 |
| 负载均衡验证（多次请求落到不同实例） | 未做 |
| `scripts/e2e-test.sh`（可重复验证脚本） | 未做 |
