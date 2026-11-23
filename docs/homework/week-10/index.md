# 第十周作业：配置中心（Nacos Config）

## 1. 配置中心

### 1.1 目标与要求对照

使用 Nacos Config 管理个人微服务系统的**非敏感配置**，区分开发（dev）与测试（test）环境，并完成“改配置 → 不重启服务即生效”的验证。

| 评分项 | 分值 | 本项目的实现 | 证据 |
|---|---|---|---|
| 配置中心接入 | 30% | 4 个服务都接入 Nacos Config，配置集中在 `zcl-dev` / `zcl-test` 两个 namespace | §1.3、§1.4 |
| 多环境设计 | 20% | dev/test 用**不同 namespace**隔离，4 个 Data ID 各一份，值不同 | §1.7、§1.10 |
| 动态配置验证 | 20% | `@RefreshScope` + `LoggingRebinder`；脚本改配置后 30s 内服务生效，容器启动时间不变 | §1.6、§1.10 |
| 安全配置 | 15% | 只迁非敏感项；`JWT_SECRET` / 数据库密码仍走 `.env`；脚本自动检查配置中无密钥 | §1.8、§1.10 |
| 文档与截图 | 15% | 本文 + `README` + 截图 | §1.11 |

通用提交要求：

- `.env` 不提交到 Git（`.gitignore` 已忽略，脚本会校验）；
- `scripts/config-test.sh` 能证明配置值被服务读取（§1.9、§1.10）；
- Nacos Data ID 命名在 §1.3 列出。

### 1.2 结构

```text
Nacos 3.2.4（独立部署，与注册中心同一个实例）
├── 命名空间 zcl-dev     ← 开发环境
│   ├── user-service.yaml
│   ├── lost-service.yaml
│   ├── admin-service.yaml
│   └── gateway-service.yaml
└── 命名空间 zcl-test    ← 测试环境
    ├── user-service.yaml   （值与 dev 不同）
    ├── lost-service.yaml
    ├── admin-service.yaml
    └── gateway-service.yaml

服务端：每个服务 application.yml 里用 spring.config.import 拉取自己的 Data ID，
        通过 NACOS_CONFIG_NAMESPACE 选择加载哪个 namespace。
```

注册中心（服务发现）仍用 group `ZCL_GROUP`；**配置中心的 group 也用 `ZCL_GROUP`**，环境靠 namespace 区分，两者不混用。

### 1.3 Data ID 命名清单（必须列出的部分）

| Data ID | 对应服务 | 迁移到 Nacos 的配置项 | dev 值 | test 值 |
|---|---|---|---|---|
| `user-service.yaml` | user-service | `app.jwt.expiration-seconds`、`app.demo.pool-size`、`logging.level.com.zjgsu.zcl.user` | 3600 / 5 / INFO | 1800 / 8 / DEBUG |
| `lost-service.yaml` | lost-service | `spring.cloud.openfeign.client.config.default.connect-timeout/read-timeout`、`app.demo.pool-size`、`logging.level.com.zjgsu.zcl.lost` | 3000/3000 / 3 / INFO | 2000/2000 / 6 / DEBUG |
| `admin-service.yaml` | admin-service | `app.demo.audit-page-size`、`logging.level.com.zjgsu.zcl.admin` | 20 / INFO | 10 / DEBUG |
| `gateway-service.yaml` | gateway-service | `logging.level.com.zjgsu.zcl.gateway` | INFO | DEBUG |

Group：`ZCL_GROUP`；命名空间：`zcl-dev`（开发）、`zcl-test`（测试）；格式：`yaml`。

### 1.4 依赖与配置导入

四个服务的 `pom.xml` 都加上配置中心 starter（版本由 BOM 管理，不写版本号）：

```xml
<dependency>
  <groupId>com.alibaba.cloud</groupId>
  <artifactId>spring-cloud-starter-alibaba-nacos-config</artifactId>
</dependency>
```

每个服务的 `application.yml` 增加导入与 Nacos Config 连接信息：

```yaml
spring:
  config:
    # Data ID 直接写服务名；Group 与 namespace 由环境变量控制
    import: "optional:nacos:user-service.yaml?group=${NACOS_GROUP:ZCL_GROUP}&refreshEnabled=true"
  cloud:
    nacos:
      config:
        server-addr: ${NACOS_SERVER_ADDR:nacos:8848}
        username: ${NACOS_USERNAME:nacos}
        password: "${NACOS_PASSWORD:}"
        namespace: ${NACOS_CONFIG_NAMESPACE:}   # compose 里默认 zcl-dev
```

`optional:` 表示配置中心暂不可用时服务仍能启动（用代码里的默认值兜底），避免“配置中心挂了整个系统起不来”。

### 1.5 迁移了哪些配置（示例）

以 `user-service.yaml`（dev）为例：

```yaml
app:
  jwt:
    expiration-seconds: 3600      # 原来在 application.yml，现已迁到 Nacos
  demo:
    pool-size: 5                  # 演示“数据库连接池参数”这类业务参数
logging:
  level:
    com.zjgsu.zcl.user: INFO      # 日志级别
```

`lost-service.yaml`（dev）：

```yaml
spring:
  cloud:
    openfeign:
      client:
        config:
          default:
            connect-timeout: 3000
            read-timeout: 3000
app:
  demo:
    pool-size: 3
logging:
  level:
    com.zjgsu.zcl.lost: INFO
```

说明：`connect-timeout` 这类 Feign 参数属于“启动期配置”，改完后需要重新构建 Feign 客户端才彻底生效；真正能热更新的是 `@RefreshScope` 里的值和日志级别。本项目把 Feign 超时作为“集中管理”的示例，把 JWT 有效期 / pool-size / 日志级别作为“动态刷新”的示例。

### 1.6 动态刷新的实现

1. 控制器加 `@RefreshScope`，配置变更时 Bean 重建，重新注入 `@Value`：

```java
@RefreshScope
@RestController
@RequestMapping("/api/users")
public class ConfigDemoController {
    private final long jwtExpirationSeconds;
    private final int poolSize;
    public ConfigDemoController(
            @Value("${app.jwt.expiration-seconds:3600}") long jwtExpirationSeconds,
            @Value("${app.demo.pool-size:5}") int poolSize) { ... }

    @GetMapping("/config")
    public Map<String, Object> config() { ... }   // 供 config-test.sh 断言
}
```

2. `logging.level.*` 由 Spring Cloud 的 `LoggingRebinder` 在刷新事件里重新设置，验证端点会返回当前生效级别。

3. 观察证据：配置变更后容器**没有重启**（`docker inspect` 的 `StartedAt` 不变），但接口返回值已变化。

验证端点：

| 服务 | 端点 | 返回 |
|---|---|---|
| user-service | `GET http://localhost:8081/api/users/config` | `jwtExpirationSeconds`、`poolSize`、`logLevel` |
| lost-service | `GET http://localhost:8082/api/lost-items/config` | `feignConnectTimeout`、`feignReadTimeout`、`poolSize`、`logLevel` |

### 1.7 多环境设计（dev / test）

| | dev | test |
|---|---|---|
| namespace | `zcl-dev` | `zcl-test` |
| 配置值 | jwt 3600、pool 5、INFO | jwt 1800、pool 8、DEBUG |
| 启动方式 | `NACOS_CONFIG_NAMESPACE=zcl-dev`（compose 默认） | `NACOS_CONFIG_NAMESPACE=zcl-test` |
| 用途 | 本地开发、答辩演示 | 联调/测试，日志更详细、有效期更短 |

切换环境只改一个环境变量，不用改代码、不用改镜像：

```bash
# 开发环境（默认）
docker compose up -d

# 测试环境：只换 namespace
NACOS_CONFIG_NAMESPACE=zcl-test docker compose up -d
```

### 1.8 安全配置

| 内容 | 存放位置 | 是否进仓库 |
|---|---|---|
| 数据库密码 | `.env` → compose 注入 `SPRING_DATASOURCE_PASSWORD` | 否（`.gitignore`） |
| `JWT_SECRET` | `.env` → compose 注入 | 否 |
| `GATEWAY_SHARED_SECRET` | `.env` → compose 注入 | 否 |
| Nacos 账号密码 | `.env` → compose 注入 / 脚本读取 | 否 |
| 非敏感配置 | Nacos Config（jwt 有效期、pool-size、超时、日志级别） | 配置内容在脚本里，可提交 |

`scripts/config-test.sh` 会遍历 8 份配置，检查内容里没有 `password`、`JWT_SECRET`、`GATEWAY_SHARED_SECRET` 等敏感字样。

### 1.9 运行命令

```bash
# 1) 初始化：创建 dev/test namespace，发布 4 个服务的配置（可重复执行）
bash scripts/config-setup.sh

# 2) 启动/重建服务（首次会构建镜像）
docker compose up -d --build

# 3) 验证：配置被读取 + 动态刷新 + 环境隔离 + 无敏感项
bash scripts/config-test.sh

# 4) 手工看配置
curl -s http://localhost:8081/api/users/config
curl -s http://localhost:8082/api/lost-items/config
```

### 1.10 实测结果（2026-11-23）

```text
=== 配置中心验证：http://localhost:8848（dev=zcl-dev / test=zcl-test）===
[PASS] .env 未被 Git 跟踪（.gitignore 生效）
[PASS] dev/test 两个 namespace 已创建（zcl-dev / zcl-test）
[PASS] dev user-service.yaml 的值正确（expiration=3600, pool=5）
[PASS] test user-service.yaml 的值与 dev 不同（expiration=1800, pool=8）
[PASS] 4 个服务的 Data ID 在 dev/test 均存在（8 份配置）
[PASS] user-service 读到 dev 配置：jwt=3600, pool=5
[PASS] lost-service 读到 dev 配置：feignRead=3000, pool=3
--- 动态修改 dev 配置：jwt=7200, pool=6, log=DEBUG ---
[PASS] 配置变更后 1s 内服务生效（jwt=7200, pool=6），未重启
[PASS] 容器启动时间未变（2026-11-23T07:44:16.476450494Z），确认是热刷新而非重启
[PASS] 日志级别随配置刷新为 DEBUG
--- 还原 dev 配置 ---
[PASS] 还原配置后 1s 内回到 jwt=3600, pool=5
--- 切换到 test 环境（NACOS_CONFIG_NAMESPACE=zcl-test）---
[PASS] 切到 test namespace 后 9s 内生效（jwt=1800, pool=8）
--- 切回 dev 环境 ---
[PASS] 切回 dev namespace 后 9s 内恢复（jwt=3600, pool=5）
[PASS] Nacos 配置里没有密码/签名密钥等敏感项

结果：PASS=14  FAIL=0
```

要点：

- 改配置后 **1 秒内**接口就返回新值，`docker inspect` 的 `StartedAt` 没变，说明不是重启；
- dev / test 两份配置值不同（jwt 3600 vs 1800、pool 5 vs 8），证明环境隔离；
- 配置内容中没有任何密码/签名密钥。

服务实际读到的值：

```text
GET http://localhost:8081/api/users/config
{"jwtExpirationSeconds":3600,"poolSize":5,"logLevel":"INFO","source":"nacos:user-service.yaml"}

GET http://localhost:8082/api/lost-items/config
{"feignConnectTimeout":3000,"feignReadTimeout":3000,"poolSize":3,"logLevel":"INFO","source":"nacos:lost-service.yaml"}
```

### 1.11 截图清单（提交 1–5 张）

| # | 文件名建议 | 截图内容 |
|---|---|---|
| 1 | `config-nacos-list.png` | Nacos 控制台配置列表：namespace 选 `zcl-dev`，能看到 4 个 Data ID |
| 2 | `config-user-content.png` | 点开 `user-service.yaml`，显示 jwt 有效期、pool-size、日志级别 |
| 3 | `config-test-namespace.png` | 切到 `zcl-test`，同一 Data ID 的值不同（证明环境隔离） |
| 4 | `config-test-output.png` | `bash scripts/config-test.sh` 输出，多个 `[PASS]` 与最终结果 |
| 5 | `config-dynamic-refresh.png` | 改配置后 `curl /api/users/config` 返回新值（可配合 Nacos 控制台改配置的画面） |

截图保存到 `docs/homework/week-10/screenshots/`。

### 1.12 提交清单

| 项 | 位置 |
|---|---|
| Nacos Config 依赖 | 4 个服务的 `pom.xml` |
| 配置导入 | 4 个服务的 `application.yml`（`spring.config.import` + `spring.cloud.nacos.config`） |
| 非敏感配置示例 | `scripts/config-setup.sh`（dev/test 各 4 份） |
| 动态刷新 | `ConfigDemoController`（user-service、lost-service） |
| 验证脚本 | `scripts/config-test.sh` |
| 文档 | 本文件 + `README` 第十周章节 |
| 密钥 | 只在 `.env`，不入库 |

## 2. 多环境配置

### 2.1 设计思路

要求“至少两个环境：`dev` 本地开发、`test` 自动测试或演示”。本项目用 **Nacos namespace 隔离**，而不是复制多套代码或镜像：

```text
同一份代码 / 同一份镜像
        │
        ├── NACOS_CONFIG_NAMESPACE=zcl-dev  → dev 配置（本地开发，少噪音）
        └── NACOS_CONFIG_NAMESPACE=zcl-test → test 配置（自动测试/演示，DEBUG、更短有效期）
```

设计要点：

1. **配置键完全相同，值不同**：4 个 Data ID 在 dev/test 各一份，服务代码不区分环境；
2. **切换只改一个环境变量**：`NACOS_CONFIG_NAMESPACE`，不重建镜像、不改代码；
3. **环境边界可观察**：dev 的 `jwt=3600/pool=5/INFO`，test 的 `jwt=1800/pool=8/DEBUG`，脚本能断言两边值不同；
4. **配置台账**：每个 Data ID、Group、用途与两套值记录在 [`docs/configuration.md`](../../configuration.md)。

### 2.2 运行命令

```bash
# 开发环境（compose 默认，.env 中 NACOS_CONFIG_NAMESPACE=zcl-dev）
docker compose up -d

# 测试环境：只换 namespace
NACOS_CONFIG_NAMESPACE=zcl-test docker compose up -d

# 切回开发
docker compose up -d

# 一键验证（含环境切换）
bash scripts/config-test.sh
```

### 2.3 实测结果（2026-11-23）

环境切换实测：切到 `zcl-test` 后 9 秒内服务读到 test 值，切回 `zcl-dev` 后 9 秒内恢复：

```text
--- 切换到 test 环境（NACOS_CONFIG_NAMESPACE=zcl-test）---
[PASS] 切到 test namespace 后 9s 内生效（jwt=1800, pool=8）
--- 切回 dev 环境 ---
[PASS] 切回 dev namespace 后 9s 内恢复（jwt=3600, pool=5）
```

两套值对照：

| 配置 | dev | test |
|---|---|---|
| `app.jwt.expiration-seconds` | 3600 | 1800 |
| `app.demo.pool-size` | 5 | 8 |
| `logging.level.com.zjgsu.zcl.user` | INFO | DEBUG |
| Feign connect/read timeout | 3000/3000 | 2000/2000 |

接口实测：

```text
# dev 环境
GET http://localhost:8081/api/users/config
{"jwtExpirationSeconds":3600,"poolSize":5,"logLevel":"INFO","source":"nacos:user-service.yaml"}

# 切到 test 环境后
GET http://localhost:8081/api/users/config
{"jwtExpirationSeconds":1800,"poolSize":8,"logLevel":"DEBUG","source":"nacos:user-service.yaml"}
```

整体脚本 14 项断言全部通过（`PASS=14 FAIL=0`），其中 2 项就是环境切换。

### 2.4 截图清单（提交 1–3 张）

| # | 文件名建议 | 截图内容 |
|---|---|---|
| 1 | `env-dev-config.png` | Nacos 控制台，namespace=`zcl-dev`，`user-service.yaml` 内容（3600/5/INFO） |
| 2 | `env-test-config.png` | Nacos 控制台，namespace=`zcl-test`，同一 Data ID 内容（1800/8/DEBUG） |
| 3 | `env-switch.png` | 终端：切到 test 后 `curl /api/users/config` 返回 1800/8/DEBUG，再切回 dev 返回 3600/5/INFO |

保存到 `docs/homework/week-10/screenshots/`。

### 2.5 提交清单

| 项 | 位置 |
|---|---|
| 配置台账 | [`docs/configuration.md`](../../configuration.md)：Data ID、Group、用途、dev/test 值 |
| 多环境实现 | 4 个服务的 `spring.config.import` + `NACOS_CONFIG_NAMESPACE` 切换 |
| 验证脚本 | `scripts/config-test.sh`（14 项断言，含切换） |
| 文档 | 本文件 §2（设计、命令、结果） |
| 截图 | §2.4 |
