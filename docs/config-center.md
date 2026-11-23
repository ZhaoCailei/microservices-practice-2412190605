# 配置中心（Nacos Config）

> 第十周「配置中心」的实现说明。详细验收对照、截图与实测见 [`docs/homework/week-10/index.md`](homework/week-10/index.md)。

## 1. 为什么用配置中心

原来每个服务的 `application.yml` 各写一份配置，改超时、改有效期要重新打包发版；开发/测试配置靠改文件切换，容易混。接入 Nacos Config 后：

| 问题 | 解决 |
|---|---|
| 配置分散、重复 | 4 个服务的非敏感配置集中到 Nacos，按服务一份 Data ID |
| 改配置要重新部署 | `@RefreshScope` + 日志级别刷新，改完秒级生效、不重启 |
| 多环境切换麻烦 | dev / test 用两个 namespace，切换只改 `NACOS_CONFIG_NAMESPACE` |
| 改了没记录/不能回滚 | Nacos 控制台有配置历史，可回滚 |
| 密钥泄露风险 | 密钥仍走 `.env`，配置中心只放非敏感项 |

## 2. 结构

```text
Nacos 3.2.4
├── namespace zcl-dev（开发）
│   ├── user-service.yaml
│   ├── lost-service.yaml
│   ├── admin-service.yaml
│   └── gateway-service.yaml
└── namespace zcl-test（测试）
    ├── user-service.yaml   （值不同：更短过期、DEBUG 日志）
    ├── lost-service.yaml
    ├── admin-service.yaml
    └── gateway-service.yaml
```

Group 统一为 `ZCL_GROUP`；环境用 namespace 隔离。注册中心（服务发现）与配置中心共用同一个 Nacos 实例，但用途独立。

## 3. Data ID 列表

| Data ID | 服务 | 迁过去的配置 |
|---|---|---|
| `user-service.yaml` | user-service | JWT 有效期、pool-size、日志级别 |
| `lost-service.yaml` | lost-service | Feign 连接/读取超时、pool-size、日志级别 |
| `admin-service.yaml` | admin-service | audit-page-size、日志级别 |
| `gateway-service.yaml` | gateway-service | 日志级别 |

## 4. 接入方式

依赖：

```xml
<dependency>
  <groupId>com.alibaba.cloud</groupId>
  <artifactId>spring-cloud-starter-alibaba-nacos-config</artifactId>
</dependency>
```

导入（每个服务）：

```yaml
spring:
  config:
    import: "optional:nacos:user-service.yaml?group=${NACOS_GROUP:ZCL_GROUP}&refreshEnabled=true"
  cloud:
    nacos:
      config:
        server-addr: ${NACOS_SERVER_ADDR:nacos:8848}
        username: ${NACOS_USERNAME:nacos}
        password: "${NACOS_PASSWORD:}"
        namespace: ${NACOS_CONFIG_NAMESPACE:}
```

`optional:` 保证配置中心不可用时服务仍能启动，用代码里的默认值兜底。

## 5. 动态刷新

- `ConfigDemoController`（user-service / lost-service）加 `@RefreshScope`，通过 `@Value` 读取 Nacos 配置；
- 配置变更 → Nacos 推送 → 刷新事件 → Bean 重建 → 新值生效；
- `logging.level.*` 由 Spring Cloud 的 `LoggingRebinder` 刷新；
- 验证端点：`GET /api/users/config`、`GET /api/lost-items/config`。

## 6. 安全

- Nacos 只放非敏感配置：有效期、池大小、超时、日志级别；
- 数据库密码、`JWT_SECRET`、`GATEWAY_SHARED_SECRET` 仍由 `.env` → compose 注入；
- `.env` 被 `.gitignore` 忽略，`scripts/config-test.sh` 会校验 `.env` 未入库、配置内容无私钥/密码。

## 7. 命令

```bash
bash scripts/config-setup.sh     # 创建 namespace + 发布 8 份配置
docker compose up -d --build     # 重建服务
bash scripts/config-test.sh      # 12 项断言：读取/多环境/动态刷新/安全
```

Nacos 控制台：`http://localhost:18080`，命名空间选 `zcl-dev` 或 `zcl-test` 查看配置。
