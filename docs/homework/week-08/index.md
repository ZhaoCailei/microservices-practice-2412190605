# 第八周作业：服务间通信与负载均衡

> 本周提交四项：① **选择一个跨服务业务流程**；② **使用 OpenFeign**；③ **负载均衡验证**；④ **测试**。
> 其余大作业实践（跨服务写补偿等）另行提交。

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
[lost_db · lost_items] ──▶ [客户端] 200 {"id":n,"message":"发布成功"}
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
docker compose stop user-service user-service-b && sleep 4
curl -s -w "\nHTTP %{http_code}\n" -H "Content-Type: application/json" \
  -d '{"itemName":"黑色折叠伞","feature":"伞柄贴有蓝色标签","userId":1}' \
  http://localhost:8082/api/lost-items
```

> PowerShell 下内联 JSON 的引号会被吞掉，先写文件再用 `--data-binary "@body.json"`。

### 1.6 运行结果（2026-11-15 实测）

| # | 场景 | 状态码 | 返回 |
|---|---|---|---|
| 1 | 正常发布（`userId=1`） | 200 | `{"id":6,"message":"发布成功"}` |
| 2 | 用户不存在（`userId=9999`） | 400 | `{"error":"发布失败：用户 9999 不存在，无法发布失物"}` |
| 3 | 停掉 `user-service` 后再发 | 503 | `{"error":"依赖服务不可用：调用 user-service 失败：无法连接到 user-service（注册中心无可用实例、连接失败或超时）"}` |
| 4 | 停服期间统计 `lost_items` | — | 6 行 → 6 行，**未写入** |
| 5 | 重启 `user-service` 后再发 | 200 | `{"id":7,"message":"发布成功"}`，行数 6 → 7 |

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

三种结果对应控制器三种响应（**统一 JSON**，`LostItemController` 用 `LinkedHashMap` 序列化，字段顺序固定）：

| 情况 | 异常 | 状态码 | 响应体 |
|---|---|---|---|
| 用户存在 | 无 | 200 | `{"id": n, "message": "发布成功"}` |
| 用户不存在 | `FeignException.NotFound` | 400 | `{"error": "发布失败：用户 x 不存在，无法发布失物"}` |
| 依赖不可用 | `UserServiceUnavailableException` | 503 | `{"error": "依赖服务不可用：调用 user-service 失败：…"}` |

字段约定：成功用 `id` + `message`；失败统一用 `error`，便于调用方与自动化脚本按字段断言，而不是解析纯文本。

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

> 下表只是**与第七周做对比**，说明“哪些旧代码已经删掉”；当前实现以 2.1–2.3 的 Feign 代码为准，仓库里不再有 `DiscoveryClient` 手工寻址代码。

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

# 3) 失败处理：停掉被调用方全部实例，应返回 503 与可读错误
docker compose stop user-service user-service-b && sleep 4
curl -s -w "\nHTTP %{http_code}\n" -H "Content-Type: application/json" \
  -d '{"itemName":"黑色折叠伞","feature":"伞柄贴有蓝色标签","userId":1}' \
  http://localhost:8082/api/lost-items

# 4) 恢复
docker compose start user-service user-service-b
```

### 2.6 结果

- `./mvnw test`：**Tests run: 5, Failures: 0, Errors: 0**（`UserClientTest` 4 个 + 上下文 1 个）；
- 正常调用：**200**，`{"id":6,"message":"发布成功"}`；
- 停掉 `user-service`：**503**，`{"error":"依赖服务不可用：调用 user-service 失败：无法连接到 user-service（注册中心无可用实例、连接失败或超时）"}`；
- 停服期间 `lost_items` 行数不变（6 → 6），未写库；
- 重启后：**200**，`{"id":7,"message":"发布成功"}`，行数 6 → 7；
- 冒烟脚本 `scripts/smoke-test.sh`：4 项全部 PASS。

截图见 [`screenshots/`](screenshots/)。

### 2.7 响应格式（JSON 契约）

三种分支统一返回 JSON（`Content-Type: application/json`），由 `LostItemController` 用 `LinkedHashMap` 序列化，字段顺序固定：

| 场景 | 状态码 | 响应体 |
|---|---|---|
| 发布成功 | 200 | `{"id":51,"message":"发布成功"}` |
| 用户不存在 | 400 | `{"error":"发布失败：用户 9999 不存在，无法发布失物"}` |
| 依赖不可用 | 503 | `{"error":"依赖服务不可用：调用 user-service 失败：…"}` |

- 成功用 `id`（新失物主键）+ `message`；失败统一用 `error`；
- HTTP 状态码与语义一致：业务拒绝 400、依赖故障 503；
- 端到端脚本按 `"id"` / `"error"` 字段断言，不再比对整段纯文本。

实测（2026-11-20，`lost-service` 重建后）：

```text
$ curl -s -w "\nHTTP %{http_code}\n" -H "Content-Type: application/json" \
    -d '{"itemName":"e2e-item","feature":"e2e-feature","userId":1}' \
    http://localhost:8082/api/lost-items
{"id":51,"message":"发布成功"}
HTTP 200

$ curl -s -w "\nHTTP %{http_code}\n" -H "Content-Type: application/json" \
    -d '{"itemName":"e2e-item","feature":"e2e-feature","userId":9999}' \
    http://localhost:8082/api/lost-items
{"error":"发布失败：用户 9999 不存在，无法发布失物"}
HTTP 400
```

响应格式归一后，前端/调用方可以统一按 JSON 解析；失败时读 `error` 字段展示，成功时读 `id` 继续后续流程。

---

## 3. 负载均衡验证

### 3.1 目标

把被调用方 `user-service` 启动 **2 个实例**，验证 `lost-service` 经 Feign + LoadBalancer 的请求**真的分发到了两个实例**，而不是只在注册列表里看到两行。

### 3.2 改动

1. **`compose.yaml` 增加实例 B**：`user-service-b` 与 `user-service` 同一镜像、同一服务名，宿主端口用 `18081 → 8081`，实例 A 仍是 `8081 → 8081`，**两个实例不抢同一个宿主机端口**。
2. **`user-service` 打实例标识**：`UserController` 每次查询都记日志 `instance=<INSTANCE_ID> 本实例第 <n> 次查询，userId=<id>`；`INSTANCE_ID` 由 compose 注入（`user-service-1` / `user-service-b-1`）。
3. **调用方零改动**：`@FeignClient(name = "user-service")` 只认服务名，服务名下出现两个实例后，`RoundRobinLoadBalancer` 自动轮流分发。

### 3.3 运行命令

```bash
# 启动两个实例（首次会构建镜像）
docker compose up -d --build user-service user-service-b
# 等约 20 秒让两个实例都注册到 Nacos，然后连发 6 次，全部经过 lost-service 的 Feign 调用
for i in $(seq 1 6); do
  curl -s -o /dev/null -w "$i: %{http_code}\n" \
    -H "Content-Type: application/json" \
    -d '{"itemName":"黑色折叠伞","feature":"伞柄贴有蓝色标签","userId":1}' \
    http://localhost:8082/api/lost-items
done
# 查看两个实例的日志（用起始时间过滤）
docker compose logs --since "2026-11-15T16:54:00" --no-color user-service user-service-b | grep "instance="
```

> PowerShell 里 `for` 循环改 `1..6 | ForEach-Object { ... }`，JSON 建议写文件后 `--data-binary "@body.json"`。

### 3.4 结果（2026-11-15 实测）

6 次请求全部 **200**，两个实例各处理 **3 次**，交替出现：

| 次序 | 实际实例 |
|---|---|
| 1 | `user-service-b-1` |
| 2 | `user-service-1` |
| 3 | `user-service-b-1` |
| 4 | `user-service-1` |
| 5 | `user-service-b-1` |
| 6 | `user-service-1` |

日志原文（节选）：

```text
user-service-b-1 | instance=user-service-b-1 本实例第 1 次查询，userId=1
user-service-1   | instance=user-service-1   本实例第 1 次查询，userId=1
user-service-b-1 | instance=user-service-b-1 本实例第 2 次查询，userId=1
user-service-1   | instance=user-service-1   本实例第 2 次查询，userId=1
user-service-b-1 | instance=user-service-b-1 本实例第 3 次查询，userId=1
user-service-1   | instance=user-service-1   本实例第 3 次查询，userId=1
```

补充验证：`docker compose stop user-service-b` 后再发 3 次，**全部 200**，日志只出现在 `user-service-1`（第 4、5、6 次），说明单实例下线后调用方自动切到存活实例，无需改代码、无需重启。

### 3.5 结论

- 负载均衡确实生效：同一服务名下两个实例，请求 3/3 交替；不是“注册列表里有两行”这种伪验证；
- 实例标识可直接核对：宿主机 `8081` 对应实例 A、`18081` 对应实例 B，配合日志里的 `instance=` 双向印证；
- 水平扩容对调用方透明：调用方只写服务名，加实例只要在 `compose.yaml` 里加一份配置；
- 两个实例共用同一个 `user_db`，属于无状态读服务，扩副本不需要改数据层。

---

## 4. 测试

### 4.1 测试分层

| 层次 | 内容 | 位置 |
|---|---|---|
| Feign 客户端单元测试 | 用户存在、404、对方 5xx、无可用实例 四种情况 | `services/lost-service/.../client/UserClientTest.java` |
| Service 层单元测试 | 用户查询/校验、审核状态机与重复审核 | `services/user-service/.../UserServiceTest.java`、`services/admin-service/.../AuditServiceTest.java` |
| 跨服务端到端 | 真实容器、真实 Nacos、真实两库，验证 200 / 400 / 503 与“是否落库” | `scripts/e2e-test.sh` |

单元测试全部用 Mockito，不连 Nacos、不连 MySQL；端到端脚本只用 `curl` + `docker compose`，不依赖语言环境。

### 4.2 `scripts/e2e-test.sh` 做了什么

| 步骤 | 操作 | 断言 |
|---|---|---|
| 1 | 检查 Nacos 控制台 + 三服务 `/actuator/health` | 全部可达 |
| 2 | `POST /api/lost-items`（合法 `userId`） | **200**，响应含 `id` 与 `message` 字段，`lost_items` 行数 **+1** |
| 3 | `POST /api/lost-items`（`userId=9999`） | **400**，响应含 `error` 字段且信息含“不存在”，行数 **不变** |
| 4 | 停掉 `user-service` 与 `user-service-b`，再发同一条 | **503**，响应含 `error` 字段且信息含“依赖服务不可用”，行数 **不变** |
| 5 | 重新启动两个实例，轮询等待 | 自动恢复 **200**，响应含 `id`，行数再 **+1** |

要点：

- 任一断言不满足，该步 `FAIL` 计 1，脚本最后 **以非 0 退出**（修复了“失败也返回 0”的问题）；
- `trap ... EXIT` 保证脚本异常中断时也会把被停掉的服务拉起来，不留残废环境；
- 模拟依赖不可用必须**停掉该服务的全部实例**——只停一个时另一个仍能应答，请求不会 503（这正是第三项验证出来的差别）。

### 4.3 运行命令与结果

```bash
bash scripts/e2e-test.sh          # Git Bash / macOS / Linux
echo $?                            # 0 = 全部通过；非 0 = 有断言失败
```

实测输出（2026-11-20，响应改为 JSON 后重跑）：

```text
============================================================
 跨服务业务流程端到端验证：发布失物前校验发布者
============================================================
1. 环境检查（Nacos 控制台 + 三个服务健康接口）
  [PASS] Nacos 控制台可访问 (http://localhost:18080)
  [PASS] user-service 健康接口正常 (http://localhost:8081)
  [PASS] lost-service 健康接口正常 (http://localhost:8082)
  [PASS] admin-service 健康接口正常 (http://localhost:8083)
2. 正常发布：应 200，且 lost_items 行数 +1
  发布前 lost_items 行数: 50
  [PASS] 发布成功 (HTTP 200): {"id":51,"message":"发布成功"}
  [PASS] 200 响应为 JSON，含 id 与 message 字段
  [PASS] 行数 +1（50 → 51）
3. 用户不存在：应 400，且不落库
  [PASS] 业务拒绝 (HTTP 400): {"error":"发布失败：用户 9999 不存在，无法发布失物"}
  [PASS] 400 响应为 JSON，含 error 字段
  [PASS] 未写库（行数保持 51）
4. 模拟依赖不可用：停掉 user-service 全部实例，应 503 且不落库
  停止 user-service / user-service-b ...
  [PASS] 依赖不可用 (HTTP 503): {"error":"依赖服务不可用：调用 user-service 失败：无法连接到 user-service（注册中心无可用实例、连接失败或超时）"}
  [PASS] 错误信息可读，未把调用失败当成业务成功
  [PASS] 503 响应为 JSON，含 error 字段
  [PASS] 失败期间未写库（行数保持 51）
5. 恢复依赖后：应自动恢复 200，且行数再 +1
  已启动 user-service / user-service-b，等待可调用（最多 60 秒）...
  [PASS] 恢复成功 (HTTP 200): {"id":52,"message":"发布成功"}
  [PASS] 恢复后行数 +1（51 → 52）
============================================================
 结果：PASS=16  FAIL=0
============================================================
端到端验证全部通过
```

断言从 13 项增加到 **16 项**：新增的 3 项分别检查 200/400/503 响应是否为 JSON 且包含约定字段。

（脚本首次编写时确实失败过：Git Bash 把中文命令行参数转成 GBK，服务端 JSON 解析报 `Invalid UTF-8`，7 项断言 FAIL、退出码 1。这也验证了“失败会返回非 0”。修复办法：请求体保持纯 ASCII，注释写在脚本里。）

### 4.4 模拟依赖不可用：为什么不会把失败当成成功

- 校验是**读操作**：`user-service` 不可用时，`lost-service` 抛出 `UserServiceUnavailableException`，控制器返回 **503**，事务未提交，`lost_items` 行数不变；
- 断言里同时检查**状态码**、**响应体 JSON 字段**（`id` / `error`）、**数据库行数**三个证据，而不是只看 HTTP 200/非 200；
- 若只看“请求没报错”就认为发布成功，就会写出“失物挂在不存在用户下”的脏数据——这正是本项要排除的情况。

### 4.5 写操作超时后，结果可能未知

上面的 404/503 都发生在**校验阶段（读）**，结论是确定的：失败即未写库。但换成**跨服务的写操作**，语义会变：

- 调用方发出写请求后**超时**，可能对方已经提交成功，只是响应没回来；
- 此时既不能当成功（可能实际没写），也不能简单当失败重试（可能写两次）；
- 正确做法是幂等键（每次业务请求带唯一 requestId）+ 对方唯一约束/去重表 + 超时后查询确认或对账补偿；
- 本项目当前**没有跨服务写**：发布失物的写库发生在 `lost-service` 本地事务内，跨服务只做读校验，所以超时只会导致“拒绝发布”，不会产生未知状态。这条约束需要在后续做跨服务写（如认领、扣减积分）时显式设计。

---

## 5. 本周未完成（另行提交）

| 项 | 状态 |
|---|---|
| 跨服务写操作的幂等键与补偿（4.5 讨论的落地实现） | 未做，当前无跨服务写场景 |
| 多实例启动的自动化（`--scale` 或 `deploy.replicas` 编排） | 未做，当前用 `user-service` + `user-service-b` 两条显式实例配置 |
