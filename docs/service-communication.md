# 跨服务业务流程：发布失物前校验发布者

> 仓库：<https://github.com/ZhaoCailei/microservices-practice-2412190605>
> 流程归属：`lost-service`（调用方）→ `user-service`（被调用方），经 Nacos 注册中心寻址。

## 1. 选了哪条流程，为什么是它

选 **“发布失物”** 这条链路：`lost-service` 在写库之前，必须确认 `userId` 对应的用户真实存在。

理由：

- **天然跨服务**：用户数据归 `user-service`、失物数据归 `lost-service`，两者各自一个私有库，`lost-service` 无法用 SQL 直接查用户；
- **有真实业务后果**：不校验就会出现“幽灵发布”——失物挂在不存在的人名下，审核与认领全部失效；
- **失败分支可观测**：用户不存在、用户服务不可用是两种不同性质的结果，正好能验证错误语义是否区分得开。

## 2. 调用链路图

### 2.1 架构链路（纯文本版，任何编辑器都能看）

```text
[客户端] curl / Postman
   │  POST /api/lost-items
   │  {"itemName":"黑色折叠伞","feature":"伞柄贴有蓝色标签","userId":1}
   ▼
[lost-service :8082]  LostItemController → LostItemService（@Transactional）
   │
   │  ① 寻址：discoveryClient.getInstances("user-service")
   ├───────────────▶ [Nacos 3.2.4 · 命名空间 public / 分组 ZCL_GROUP]
   │                 ◀──── 当前实例列表：[172.x.x.x:8081]
   │
   │  ② 调用：GET http://172.x.x.x:8081/api/users/1
   │     （实例 URI + 原业务路径；连接 3s、读取 3s 超时）
   ├───────────────▶ [user-service :8081] ──▶ [user_db · users]
   │                 ◀──── 200 用户 JSON → 校验通过，继续
   │                 ◀──── 404            → 用户不存在，返回 400，不写库
   │                 ◀──── 超时 / 5xx      → 返回 503，不写库
   │
   │  ③ 写库：只有校验通过才 entityManager.persist(item)
   ▼
[lost_db · lost_items]   只存 user_id，不建跨库外键
   │
   ▼
[客户端] ◀── 200 发布成功，失物ID：n
```

### 2.2 一次调用的四步与三类结果

```text
第 1 步  客户端 → lost-service     POST /api/lost-items
第 2 步  lost-service → Nacos     getInstances("user-service")  → 实例列表
第 3 步  lost-service → user-service   GET /api/users/{id}（用实例 URI 拼出来）
第 4 步  按返回值分支：
          200 → INSERT lost_items → 返回 200 发布成功，失物ID：n
          404 → 返回 400 校验未通过（事务回滚，不写库）
          实例为空 / 超时 / 5xx → 返回 503 依赖服务不可用（不写库）
```

| 分支 | 触发条件 | 本服务响应 | 是否写库 |
|---|---|---|---|
| 成功 | 远端 200，用户存在 | 200 `发布成功，失物ID：n` | 是 |
| 业务拒绝 | 远端 404，用户不存在 | 400 `校验未通过` | 否 |
| 依赖故障 | 实例列表为空、超时、5xx | 503 `依赖服务不可用` | 否 |

## 3. 调用方、被调用方与接口路径

| 角色 | 服务 | 端口 | 接口路径 | 说明 |
|---|---|---|---|---|
| 调用方 | `lost-service` | 8082 | `POST /api/lost-items` | 对外入口，请求体含 `itemName`、`feature`、`userId` |
| 调用方内部 | `UserClient#checkUserExists` | — | — | 用 `DiscoveryClient` 取实例，再拼出下面这条 URL |
| 被调用方 | `user-service` | 8081 | `GET /api/users/{id}` | 存在返回 200 + 用户 JSON，不存在返回 404 |
| 注册中心 | Nacos | 8848（控制台 18080） | — | 服务名 `user-service`，分组 `ZCL_GROUP`，命名空间 `public` |

调用方代码位置：`services/lost-service/src/main/java/com/zjgsu/zcl/lost/client/UserClient.java`
被调用方代码位置：`services/user-service/src/main/java/com/zjgsu/zcl/user/controller/UserController.java`

## 4. 设计要点

1. **寻址方式**：不写固定 IP，也不在 `RestTemplate` 里写服务名靠 DNS；每次请求都 `discoveryClient.getInstances("user-service")`，取 `instance.getUri()` 再拼 `/api/users/{id}`。
2. **超时**：`RestTemplateConfig` 里连接 3 秒、读取 3 秒，避免被下游拖死。
3. **错误语义三分**：
   - 远端 `404` → 业务结果，调用方返回 **400**，不写库；
   - 实例列表为空 / 连接超时 / 5xx → **503**，不写库；
   - 远端 `200` → 返回 `true`，继续发布。
4. **事务边界**：校验发生在 `entityManager.persist()` 之前，整个方法在 `@Transactional` 内，失败不会产生半条记录。
5. **数据边界**：`lost_items` 只存 `user_id`，不建跨库外键，也不直连 `user_db`。

## 5. 完成情况

| 项目 | 状态 | 说明 |
|---|---|---|
| 跨服务发现与调用 | 已完成 | `DiscoveryClient` + `RestTemplate`，地址来自注册中心 |
| 用户不存在时的业务拒绝 | 已完成 | 远端 404 → 本服务 400，不落库 |
| 依赖不可用时的降级 | 已完成 | 实例列表为空 → 503，不落库 |
| 每次请求重新寻址 | 已完成 | 不在启动时缓存实例地址，服务上下线可感知 |
| 单元测试 | 已完成 | `lost-service` 5 个用例，含“实例列表为空”用 Mockito 模拟 |
| 轮询 / 权重 / 重试 / 熔断 | 未做 | 本周范围外，只取第 1 个实例 |

## 6. 实测结果

环境：`docker compose up -d` 后 7 个容器在运行，三服务均注册在 Nacos `public` 命名空间、`ZCL_GROUP` 分组。

| # | 场景 | 请求 | 状态码 | 响应体 |
|---|---|---|---|---|
| 1 | 被调用方可用 | `GET /api/users/1` | 200 | 用户 JSON |
| 2 | 正常发布 | `POST /api/lost-items`，`userId=1` | 200 | `发布成功，失物ID：3` |
| 3 | 用户不存在 | 同上，`userId=9999` | 400 | `发布失败：用户不存在或用户服务不可用（校验未通过）` |
| 4 | 依赖不可用 | 停掉 `user-service` 3 秒后再发 | 503 | `依赖服务不可用：no available instance of service 'user-service'` |
| 5 | 失败是否写库 | 停服期间查 `lost_items` | — | 3 行 → 3 行，**未写入** |
| 6 | 恢复后发布 | 重启 `user-service` 后再发 | 200 | `发布成功，失物ID：4`，行数 3 → 4 |

恢复过程还有一个值得记的细节：`user-service` 容器启动后约 **9.3 秒**健康接口才 `UP`，但此时 Nacos 里的实例还没注册完，紧接着的一次调用仍返回 **503**；再下一次请求（约 0.2 秒后）才转为 200。也就是说，**健康检查通过 ≠ 已可被调用方发现**。

## 7. 关键结论

1. **“服务名 + 注册中心”不能替代寻址**：只有在调用前查到实例 URI，才能在服务迁移、换 IP、扩副本时自动跟上；写死 `http://user-service:8081` 只是用了 Compose 的 DNS，不是服务发现。
2. **错误要分层**：远端 404 是业务答案（用户不存在），实例列表为空是基础设施故障（503）。两者都不写库，但对调用方含义完全不同。
3. **降级是“拒绝服务”而不是“绕过校验”**：依赖不可用时宁可不发布，也不能跳过校验把数据写进去，否则脏数据会一直留在库里。
4. **注册是有延迟的**：容器健康、端口可连，不代表注册中心已更新。重启窗口内的 503 属于预期行为。
5. **单实例调用的边界**：现在只取 `get(0)`，没有轮询与重试；多实例与负载均衡属于后续内容。

## 8. 可复现的运行命令

在仓库根目录执行（Git Bash / macOS / Linux；PowerShell 注意 JSON 引号会被吞掉，建议先写文件再 `--data-binary`）：

```bash
# 0) 启动整套环境
docker compose up -d

# 1) 被调用方可用性
curl -s -o /dev/null -w "user-service: %{http_code}\n" http://localhost:8081/api/users/1

# 2) 正常发布（应为 200，返回失物 ID）
curl -s -w "\nHTTP %{http_code}\n" -H "Content-Type: application/json" \
  -d '{"itemName":"黑色折叠伞","feature":"伞柄贴有蓝色标签","userId":1}' \
  http://localhost:8082/api/lost-items

# 3) 用户不存在（应为 400，且不写库）
curl -s -w "\nHTTP %{http_code}\n" -H "Content-Type: application/json" \
  -d '{"itemName":"测试失物","feature":"不存在用户","userId":9999}' \
  http://localhost:8082/api/lost-items

# 4) 依赖不可用（应为 503）
docker compose stop user-service
sleep 3
curl -s -w "\nHTTP %{http_code}\n" -H "Content-Type: application/json" \
  -d '{"itemName":"黑色折叠伞","feature":"伞柄贴有蓝色标签","userId":1}' \
  http://localhost:8082/api/lost-items

# 5) 失败期间确认没有写库
docker compose exec -T lost-db mysql -u"$MYSQL_USER" -p"$MYSQL_PASSWORD" -N lost_db \
  -e "SELECT COUNT(*) FROM lost_items;"

# 6) 恢复后再次发布（应为 200）
docker compose start user-service
sleep 12
curl -s -w "\nHTTP %{http_code}\n" -H "Content-Type: application/json" \
  -d '{"itemName":"黑色折叠伞","feature":"伞柄贴有蓝色标签","userId":1}' \
  http://localhost:8082/api/lost-items
```

