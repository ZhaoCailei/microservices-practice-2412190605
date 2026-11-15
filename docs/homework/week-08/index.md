# 第八周作业：服务间通信与负载均衡

> 本周作业分两部分：
> ① **选择一个跨服务业务流程**（已提交，见下）；
> ② 大作业实践：OpenFeign + 多实例负载均衡 + `scripts/e2e-test.sh`（**本周暂不提交**）。

## 1. 选择一个跨服务业务流程

**流程：发布失物前校验发布者是否存在。**

一个用户发失物帖时，`lost-service` 必须先确认这个 `userId` 真的存在，而用户数据归 `user-service`、失物数据归 `lost-service`，两个服务各有一个私有库（`user_db` / `lost_db`），无法用 SQL 直接关联，所以这条链路**必须跨服务完成**。

```text
[客户端]
   │  POST /api/lost-items   {"itemName","feature","userId":1}
   ▼
[lost-service :8082]
   │ ① getInstances("user-service")  ──▶ [Nacos · public / ZCL_GROUP] ──▶ 实例列表 [172.x.x.x:8081]
   │ ② GET /api/users/1（实例 URI + 原业务路径，超时 3s） ──▶ [user-service :8081] ──▶ [user_db.users]
   │      200 → 校验通过     404 → 返回 400，不写库     实例为空/超时 → 返回 503，不写库
   │ ③ 校验通过才 INSERT
   ▼
[lost_db.lost_items] ──▶ [客户端] 200 发布成功，失物ID：n
```

## 2. 实现思路

1. **寻址**：`UserClient` 每次调用先 `discoveryClient.getInstances("user-service")`，再 `UriComponentsBuilder.fromUri(instance.getUri()).path("/api/users/{id}")`，拼出真实地址后才发 HTTP。
2. **不缓存地址**：类里没有实例缓存字段，服务上下线、换 IP 都能立即感知。
3. **超时**：`RestTemplateConfig` 设置连接 3 秒、读取 3 秒。
4. **错误语义**：
   - 远端 `404` → 用户不存在 → 本服务返回 **400**，不写库；
   - 实例列表为空 / 超时 / 5xx → **503**，不写库；
   - 远端 `200` → 返回 `true`，继续发布。
5. **事务边界**：校验在 `entityManager.persist()` 之前，方法整体在 `@Transactional` 内，失败不会留下半条数据。
6. **数据边界**：`lost_items` 只保存 `user_id`，不建跨库外键，不直连 `user_db`。

涉及文件：

| 文件 | 作用 |
|---|---|
| `services/lost-service/.../client/UserClient.java` | 发现实例并调用 `GET /api/users/{id}` |
| `services/lost-service/.../client/UserServiceUnavailableException.java` | 依赖不可用异常，映射 503 |
| `services/lost-service/.../config/RestTemplateConfig.java` | `RestTemplate` 与 3 秒超时 |
| `services/lost-service/.../service/LostItemService.java` | 先校验后 `persist` |
| `services/lost-service/.../controller/LostItemController.java` | 200 / 400 / 503 三态映射 |
| `services/user-service/.../controller/UserController.java` | 被调用方，存在 200、不存在 404 |

## 3. 运行命令

```bash
# 启动 7 个容器（3 库 + 3 服务 + Nacos）
docker compose up -d
docker compose ps

# ① 被调用方可用
curl -s -o /dev/null -w "user-service: %{http_code}\n" http://localhost:8081/api/users/1

# ② 正常发布
curl -s -w "\nHTTP %{http_code}\n" -H "Content-Type: application/json" \
  -d '{"itemName":"黑色折叠伞","feature":"伞柄贴有蓝色标签","userId":1}' \
  http://localhost:8082/api/lost-items

# ③ 用户不存在
curl -s -w "\nHTTP %{http_code}\n" -H "Content-Type: application/json" \
  -d '{"itemName":"测试失物","feature":"不存在用户","userId":9999}' \
  http://localhost:8082/api/lost-items

# ④ 依赖不可用：先停掉 user-service，等 3 秒再发
docker compose stop user-service && sleep 3
curl -s -w "\nHTTP %{http_code}\n" -H "Content-Type: application/json" \
  -d '{"itemName":"黑色折叠伞","feature":"伞柄贴有蓝色标签","userId":1}' \
  http://localhost:8082/api/lost-items

# ⑤ 确认失败期间没有写库
docker compose exec -T lost-db mysql -u"$MYSQL_USER" -p"$MYSQL_PASSWORD" -N lost_db \
  -e "SELECT COUNT(*) FROM lost_items;"

# ⑥ 恢复后再次发布
docker compose start user-service && sleep 12
curl -s -w "\nHTTP %{http_code}\n" -H "Content-Type: application/json" \
  -d '{"itemName":"黑色折叠伞","feature":"伞柄贴有蓝色标签","userId":1}' \
  http://localhost:8082/api/lost-items
```

> PowerShell 下 `-d '{...}'` 的单引号会被吞掉，导致服务端收到空 JSON、返回笼统的 400；把请求体写进文件再用 `--data-binary "@body.json"` 即可。

## 4. 运行结果（2026-11-15 实测）

| 步骤 | 请求 | 状态码 | 返回 |
|---|---|---|---|
| ① | `GET /api/users/1` | 200 | 用户 JSON |
| ② | `POST /api/lost-items`（`userId=1`） | 200 | `发布成功，失物ID：3` |
| ③ | 同上（`userId=9999`） | 400 | `发布失败：用户不存在或用户服务不可用（校验未通过）` |
| ④ | 停服 3 秒后再发 | 503 | `依赖服务不可用：no available instance of service 'user-service'` |
| ⑤ | 停服期间统计行数 | — | 3 → 3，**未写入** |
| ⑥ | 重启后发布 | 200 | `发布成功，失物ID：4`，行数 3 → 4 |

补充观测：`user-service` 容器启动后约 **9.3 秒**健康接口才 `UP`，但此时实例尚未在 Nacos 注册完成，紧接着一次请求仍是 **503**，下一次请求（约 0.2 秒后）才变成 200 —— **健康检查通过不等于已可被发现**。

对应截图（第七周实测，同一链路）：`docs/homework/week-07/screenshots/flow-success.png`、`flow-503.png`、`flow-recovered.png`。

## 5. 关键结论

1. 只在调用前查注册中心拿实例 URI，才算真正的服务发现；写死 `http://user-service:8081` 只是用了 Compose 的 DNS。
2. `404` 与“实例列表为空”必须区分：前者是业务答案（400），后者是基础设施故障（503）。
3. 依赖不可用时正确做法是拒绝发布（503），而不是跳过校验写库，否则脏数据会长期留存。
4. 注册存在延迟，重启窗口内出现 503 属预期。
5. 本周只取第 1 个实例，未做轮询、权重、重试、熔断。

## 
