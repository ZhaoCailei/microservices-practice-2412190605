# 配置清单（Nacos Data ID / Group / 用途）

> 第十周「多环境配置」的配置台账：每条 Data ID 记录 Group、所属环境、用途与关键值。
> 实现细节与验收结果见 [`docs/homework/week-10/index.md`](homework/week-10/index.md)。

## 1. 环境与命名规则

| 环境 | Nacos namespace | 说明 |
|---|---|---|
| `dev` | `zcl-dev` | 本地开发，方便调试、少噪音 |
| `test` | `zcl-test` | 自动测试 / 答辩演示，日志更详细、有效期更短 |

- **Data ID 命名规则**：`<服务名>.yaml`，与注册到 Nacos 的服务名一致；
- **Group**：统一 `ZCL_GROUP`（与注册中心 group 同名，但配置中心靠 namespace 区分环境）；
- **格式**：`yaml`；
- 每个环境各一套，共 4 个服务 × 2 环境 = **8 份配置**；
- 只放非敏感配置：数据库密码、`JWT_SECRET`、`GATEWAY_SHARED_SECRET` 仍由 `.env` 注入，不入库、不放 Nacos。

## 2. Data ID 清单

| Data ID | Group | 环境（namespace） | 用途 | dev 值 | test 值 |
|---|---|---|---|---|---|
| `user-service.yaml` | `ZCL_GROUP` | dev=`zcl-dev` / test=`zcl-test` | 认证服务：JWT 有效期、演示连接池大小、日志级别 | jwt=`3600`，pool=`5`，log=`INFO` | jwt=`1800`，pool=`8`，log=`DEBUG` |
| `lost-service.yaml` | `ZCL_GROUP` | 同上 | 失物服务：Feign 连接/读取超时、演示连接池、日志级别 | connect/read=`3000/3000`，pool=`3`，log=`INFO` | connect/read=`2000/2000`，pool=`6`，log=`DEBUG` |
| `admin-service.yaml` | `ZCL_GROUP` | 同上 | 审核服务：审核列表业务参数、日志级别 | audit-page-size=`20`，log=`INFO` | audit-page-size=`10`，log=`DEBUG` |
| `gateway-service.yaml` | `ZCL_GROUP` | 同上 | 网关：日志级别 | log=`INFO` | log=`DEBUG` |

## 3. 各配置项的取值理由

| 配置项 | dev | test | 理由 |
|---|---|---|---|
| `app.jwt.expiration-seconds` | 3600（1 小时） | 1800（30 分钟） | 开发时少登录几次；测试要覆盖“token 过期 → 401”分支，短一点更易触发，泄露风险窗口也更小 |
| `app.demo.pool-size` | 5 / 3 | 8 / 6 | 测试并发压力更大，连接池给多一点；开发本机资源有限 |
| `spring.cloud.openfeign.client.config.default.connect-timeout/read-timeout` | 3000/3000 | 2000/2000 | 测试环境希望更快暴露“依赖慢/不可用”，便于验证 503 |
| `app.demo.audit-page-size` | 20 | 10 | 测试用小分页更容易验证翻页边界 |
| `logging.level.com.zjgsu.zcl.*` | INFO | DEBUG | 开发看 INFO 少刷屏；测试/联调需要 DEBUG 排查请求、SQL、Feign 细节 |

> 生产环境通常再保守一档（日志 WARN、超时更短、JWT 有效期更短、密钥独立），本项目按 dev/test 两个环境演示。

## 4. 配置项与应用的对应关系

| 配置项 | 生效位置 | 是否支持热更新 |
|---|---|---|
| `app.jwt.expiration-seconds` | `JwtTokenService`（签发 Token 时读取） | 是（`@RefreshScope`） |
| `app.demo.pool-size` | `ConfigDemoController`（验证端点） | 是（`@RefreshScope`） |
| `logging.level.com.zjgsu.zcl.*` | Spring Boot 日志系统 | 是（`LoggingRebinder`） |
| `spring.cloud.openfeign.client.*.timeout` | Feign 客户端 | 否（启动期配置，需重启/重建客户端） |
| `app.demo.audit-page-size` | 审核服务业务参数 | 按使用方式（建议 `@ConfigurationProperties` + 刷新） |

## 5. 如何切换环境

```bash
# 开发环境（compose 默认，.env 里 NACOS_CONFIG_NAMESPACE=zcl-dev）
docker compose up -d

# 测试环境：只换 namespace，不换代码、不换镜像
NACOS_CONFIG_NAMESPACE=zcl-test docker compose up -d

# 切回开发
docker compose up -d
```

验证：

```bash
curl -s http://localhost:8081/api/users/config
# dev  → {"jwtExpirationSeconds":3600,"poolSize":5,"logLevel":"INFO",...}
# test → {"jwtExpirationSeconds":1800,"poolSize":8,"logLevel":"DEBUG",...}
```

`scripts/config-test.sh` 末尾会自动执行一次“切到 test 再切回 dev”的验证，共 16 项断言。
