# 安全设计：认证、鉴权与网关

> 本文记录第九周大作业实践的认证与鉴权方案：JWT 认证流程、角色权限、网关路由与拦截规则、伪造身份头的处理方式。

## 1. 认证流程

### 1.1 登录（签发 Token）

```text
客户端 ──POST /api/auth/login {username,password}──▶ 网关 8080（公开路径，直接放行）
                                                  └──▶ user-service（Nacos 负载均衡选实例）
                                                        1. 按 username 查用户
                                                        2. BCryptPasswordEncoder.matches(明文, 数据库哈希)
                                                        3. 通过 → 签发 JWT（HS256，exp 1 小时）
                                                        4. 返回 token + 角色
```

关键点：

- 数据库只存 BCrypt 哈希（`users.password_hash`），登录过程不还原明文；
- 用户不存在与密码错误返回同一句 `用户名或密码错误`，避免账号枚举；
- JWT 的 claims：`iss=user-service`、`sub=用户 id`、`username`、`role`、`iat`、`exp`。

### 1.2 访问受保护接口（验签）

```text
客户端 ──GET /api/xxx  Authorization: Bearer <JWT>──▶ 网关 8080
                                                        1. 提取 Bearer Token
                                                        2. 用共享 JWT_SECRET 验签（签名/格式/过期）
                                                        3. 按路径做角色校验
                                                        4. 删除客户端伪造的身份头
                                                        5. 写入 X-User-Id / X-Username / X-User-Role
                                                        6. 写入 X-Gateway-Signature（HMAC）
                                                     └──▶ 下游服务
                                                          校验 X-Gateway-Signature 后才信任身份头
```

## 2. 角色与权限

| 角色 | 说明 | 可访问 |
|---|---|---|
| `USER` | 普通用户 | `/api/auth/**`、`/api/users/**`、`/api/lost-items/**` |
| `ADMIN` | 管理员 | 上面全部 + `/api/admin/**`（审核记录查询/审核） |

角色写在 JWT 的 `role` claim 里，由 user-service 签发，网关只读不写。

## 3. 网关拦截规则

| 规则 | 行为 |
|---|---|
| 公开路径：`/api/health`、`/actuator/health`、`POST /api/auth/login` | 直接放行 |
| 其他路径无 `Authorization: Bearer` | `401` + `{"error":"未登录：缺少 Authorization: Bearer <token>"}` |
| Token 签名错误 / 格式错误 / 已过期 | `401` + `{"error":"Token 无效或已过期"}` |
| `/api/admin/**` 且角色不是 `ADMIN` | `403` + `{"error":"角色不足：该接口需要 ADMIN"}` |
| 验签通过 | 删除伪造身份头，注入可信身份头 + HMAC 签名后转发 |

实现位置：`services/gateway-service/src/main/java/com/zjgsu/zcl/gateway/filter/AuthGlobalFilter.java`。

## 4. 网关路由表

| 路由 id | 匹配路径 | 目标 | 保护级别 |
|---|---|---|---|
| `auth-service-route` | `/api/auth/**` | `lb://user-service` | 登录公开；`/api/auth/me` 需 Token |
| `user-service-route` | `/api/users/**` | `lb://user-service` | 需 Token |
| `lost-service-route` | `/api/lost-items/**` | `lb://lost-service` | 需 Token |
| `admin-service-route` | `/api/admin/**` | `lb://admin-service` | 需 Token 且角色为 `ADMIN` |

所有路由目标都使用服务名（`lb://`），由 Nacos + LoadBalancer 解析实例。

## 5. 伪造身份头与绕过网关

### 5.1 客户端伪造身份头会被覆盖

客户端可能自己往请求里加：

```http
X-User-Id: 2
X-User-Role: ADMIN
X-Username: admin_li
```

网关的 `AuthGlobalFilter` 在验签通过后**先 `remove` 这四个头**（连 `X-Gateway-Signature` 一起删），再用 Token 里的真实身份重新写入。因此伪造的 `X-User-Role: ADMIN` 不会生效，身份永远以 JWT 为准。

### 5.2 下游服务只认网关签名

下游接口 `GET /api/users/current`（`CurrentUserController`）不验 JWT，而是校验请求头里的 `X-Gateway-Signature`：

```text
signature = HMAC-SHA256(GATEWAY_SHARED_SECRET, "userId:role")
```

- 网关用共享密钥算出签名并写入；
- 下游用同一密钥重算并比较（`MessageDigest.isEqual`，常量时间比较）；
- 直接访问 8081 或自己伪造 `X-User-Role: ADMIN` 都算不出正确签名 → `401 {"error":"身份头签名无效..."}`。

### 5.3 如何避免绕过网关直接访问

| 措施 | 本项目的做法 |
|---|---|
| 端口暴露 | 生产只暴露 8080；8081/8082/8083 只用于本地调试，部署时应删除或用防火墙限制在内网 |
| 网络隔离 | backend 服务只加入 `micro-net` 桥接网络，不发布宿主端口即可完全内网化 |
| 应用层签名 | 下游只信任带 `X-Gateway-Signature` 的请求（见 5.2），即使端口可达也无法伪造 |
| 纵深防御 | 敏感接口（`/api/admin/**`）在网关做角色校验；如需更强，可在下游再验一次 JWT |

## 6. 密钥管理

| 密钥 | 用途 | 存放 |
|---|---|---|
| `JWT_SECRET` | 签发/验签 JWT（HS256），user-service 与 gateway 共用 | 本地 `.env`，`.gitignore` 忽略；仓库无密钥 |
| `GATEWAY_SHARED_SECRET` | 网关对身份头做 HMAC 签名，下游校验 | 同上 |
| `NACOS_PASSWORD` | Nacos 登录 | 同上 |

生成方式：

```bash
openssl rand -base64 32     # JWT_SECRET
openssl rand -base64 32     # GATEWAY_SHARED_SECRET
```

两个密钥都通过 `compose.yaml` 注入容器；缺失时服务启动失败并提示，不会静默使用弱默认值。

## 7. 验证方式

### 7.1 手工命令

```bash
# 未登录访问受保护接口 → 401
curl -s -w "\nHTTP %{http_code}\n" http://localhost:8080/api/users/current

# 登录拿 Token
curl -s -H "Content-Type: application/json" \
  -d '{"username":"student_zhang","password":"Student@123"}' \
  http://localhost:8080/api/auth/login

# 带 Token 访问 → 200
curl -s -w "\nHTTP %{http_code}\n" \
  -H "Authorization: Bearer <token>" http://localhost:8080/api/users/current

# 普通用户访问管理员接口 → 403
curl -s -w "\nHTTP %{http_code}\n" \
  -H "Authorization: Bearer <user-token>" http://localhost:8080/api/admin/audits

# 管理员访问 → 200
curl -s -w "\nHTTP %{http_code}\n" \
  -H "Authorization: Bearer <admin-token>" http://localhost:8080/api/admin/audits

# 绕过网关 + 伪造身份头 → 401
curl -s -w "\nHTTP %{http_code}\n" \
  -H "X-User-Id: 2" -H "X-User-Role: ADMIN" -H "X-Gateway-Signature: forged" \
  http://localhost:8081/api/users/current
```

### 7.2 自动化脚本

```bash
bash scripts/authz-test.sh     # 10 项断言：401/403/200/角色/伪造头
bash scripts/auth-test.sh      # 认证服务 13 项断言（登录、验签、密码哈希）
```
