

# 校园失物招领系统

```
课程：微服务实践课程仓库
姓名：赵才磊
学号：2412190605
仓库用途：用于存放微服务实践课程每周作业、代码与文档。
```

## 第二周

### 1.项目名称：

校园失物招领系统

### 2.业务背景：

 校园内师生经常发生物品丢失或捡拾物品的情况，目前依靠班级群、朋友圈发布信息，信息分散、容易过期，难以核验物品归属，存在冒领风险。本系统搭建统一的校园失物招领线上平台，集中管理失物、招领信息，帮助师生高效匹配物品，降低找回成本。

### 3.目标群体：

在校学生 / 教职工：发布丢失物品信息；发布捡到物品的招领信息；按物品类型、地点检索信息；提交认领申请；查看自己发布的全部记录。

校园管理员：审核发布的失物 / 招领帖子，删除违规、虚假信息；核验物品认领，防止冒领；统计校园物品丢失高发地点与物品类型。

系统管理员：管理账号、角色权限，维护物品分类字典。

### 4.选择项目原因： 

业务流程完整，包含信息发布、检索、申请、审核、状态流转，业务边界清晰。可以先开发单体版本，后续能够很自然拆分成多个微服务。业务复杂度适中，刚好满足课程从单体逐步迭代到微服务的全部作业要求。

### 5.功能模块划分

**用户模块**：账号注册登录、个人资料管理、权限控制

**物品发布模块** 

失物发布：填写丢失时间、地点、物品名称、物品类别、物品描述、传图片

招领发布：填写捡拾时间、地点、物品信息、图片

**物品检索模块**：按关键词、物品分类、丢失 / 捡拾地点、时间筛选查询，分页展示帖子

**认领申请模块**：失主针对招领信息提交认领申请，填写物品特征描述用于核验

**审核管理模块** 

帖子审核：管理员审核用户发布的失物 / 招领信息，审核通过才对外展示

认领审核：管理员比对申请人描述，核验通过后完成认领，标记帖子为已完结

**数据统计模块**：统计每月丢失物品数量、高频丢失地点、物品类型分布

| 模块名称     | 模块名称                                   |
| ------------ | ------------------------------------------ |
| 用户模块     | 账号注册登录、个人资料维护、角色权限控制   |
| 物品发布模块 | 失物信息发布、招领信息发布，支持上传图片   |
| 物品检索模块 | 关键词、分类、地点、时间筛选，分页展示帖子 |
| 认领申请模块 | 提交认领申请，填写物品特征用于核验         |
| 审核管理模块 | 帖子内容审核、认领申请核验，标记帖子状态   |
| 数据统计模块 | 统计丢失数量、高频丢失地点、物品类型分布   |



### 6.核心业务流程：

学生登录系统，发布【失物启事】，填写物品信息，提交等待管理员审核。

拾获物品的人登录，发布【招领启事】，提交后等待管理员审核。

管理员审核通过，帖子对外展示，所有人可以检索查看。

失主浏览招领列表，找到匹配物品，提交认领申请，填写物品独有特征。

管理员比对申请描述与招领物品信息，核验确认无误，审核通过，标记该招帖子为**已认领**。

双方线下对接领取物品，流程结束；认领失败则申请驳回，帖子继续展示等待其他匹配。

### **7.当前项目范围和暂不实现内容**：

当前实现：用户登录、失物 / 招领发布、帖子审核、物品检索、认领申请、认领审核、基础数据统计。

暂不实现：短信通知、物品定位、人脸识别身份核验、线上沟通聊天、物品邮寄。

### **8.后续可扩展演进方向**：

认证模块独立：拆为统一用户认证微服务，统一登录和权限鉴权

服务拆分：拆分为用户服务、物品发布检索服务、认领审核服务、统计服务

异步消息：消息队列处理帖子审核通知、认领结果通知

搜索优化：独立检索服务，实现全文关键词搜索

运维监控：服务健康监控、接口日志采集

容器部署：DockerCompose 编排所有服务，一键启动整套系统

## 第三周

### 1.环境要求

- Java：25
- Maven：3.8+（建议直接用仓库自带的 `./mvnw`，版本锁定为 3.9.16）

### 2.启动命令：

mvn spring-boot:run

### 3,测试命令

```
./mvnw test
```

### 4.接口地址

问候接口：http://127.0.0.1:8080/api/hello

健康检查接口：<http://127.0.0.1:8080/actuator/health>



### 5.当前尚未实现的业务能力

物品图片上传、认领申请、管理员审核、用户登录鉴权、数据库持久化存储。

## 第四周

### 1. 数据库配置
### 本地 MySQL 配置说明
第四周版本的单体应用使用本地 MySQL 8.0+（容器编排从第五周起引入）。
1. 确保本地 MySQL 服务已启动。
2. 真实密码通过环境变量 `SPRING_DATASOURCE_PASSWORD` 注入，不要写进仓库；也可在 `monolith/src/main/resources/application.yaml` 中临时填写本地值（切勿提交）。
3. 项目配置了 `createDatabaseIfNotExist=true`，启动时会自动创建 `lostfound_db` 数据库并自动建表，无需手动执行建表 SQL。
4. `src/main/resources/data.sql` 准备了初始用户数据，通过 `spring.sql.init.mode: always` 在每次启动时执行（`INSERT IGNORE`，可重复执行）。

### 2. cURL 测试示例

**① 创建失物（正常，返回 200）：**

```bash
curl -i -X POST http://localhost:8080/api/lost-items \
-H "Content-Type: application/json" \
-d '{"userId": 1, "itemName": "水杯", "feature": "底部有贴纸"}'
```

**② 查询失物列表（返回 200 与 JSON 数组）：**

```bash
curl -i http://localhost:8080/api/lost-items
```

**③ 特征为纯空格（返回 400，用于验证业务规则拦截）：**

```bash
curl -i -X POST http://localhost:8080/api/lost-items \
-H "Content-Type: application/json" \
-d '{"userId": 1, "itemName": "校园卡", "feature": "   "}'
```

#    第五周：Docker 部署说明

### 1. 环境变量准备
复制根目录下的环境变量模板并修改密码：

```bash
cp .env.example .env
```

2.健康接口检查

```
bash
curl http://localhost:8080/actuator/health
```

# 第六周：微服务测试命令说明

由于项目已拆分为多个独立的微服务模块，运行测试需要分别进入对应的服务目录执行。

### 1. 三个服务的测试命令

```bash
cd services/user-service  && ./mvnw test    # 6 个用例
cd services/lost-service  && ./mvnw test    # 5 个用例
cd services/admin-service && ./mvnw test    # 7 个用例
```

测试通过 `@SpringBootTest(properties=...)` 关闭 Nacos 注册并切换 H2 内存库，不依赖真实 MySQL 与注册中心。

### 2. 独立数据库与独立账号

| 服务 | 数据库 | 业务账号（compose 创建，只授权本库） | 环境变量 |
|---|---|---|---|
| user-service | `user_db` | `user_app` | `USER_DB_USER` / `USER_DB_PASSWORD` |
| lost-service | `lost_db` | `lost_app` | `LOST_DB_USER` / `LOST_DB_PASSWORD` |
| admin-service | `admin_db` | `admin_app` | `ADMIN_DB_USER` / `ADMIN_DB_PASSWORD` |

* 服务通过 `DB_USER` 与 `SPRING_DATASOURCE_PASSWORD` 注入账号密码，`root` 只用于容器初始化与运维排障。
* 本地不用 Docker 时，`DB_USER` 未设置会退回 `root`，方便单机调试。
* 各服务只读写自己的库，跨服务仅保存对方 ID，不建外键、不直连对方的表。

# 第七周：Nacos 部署与服务注册

### 1. 部署组件

*   镜像：`nacos/nacos-server:v3.2.4`，单机模式（`MODE=standalone`），编排在根目录 `compose.yaml` 的 `nacos` 服务中。
*   鉴权：已开启，Token 密钥与 server identity 由本地 `.env` 注入（`NACOS_AUTH_TOKEN`、`NACOS_AUTH_IDENTITY_KEY`、`NACOS_AUTH_IDENTITY_VALUE`）；仓库只提交不含真实凭据的 `.env.example`。
*   持久化：`nacos-data`（挂载 `/home/nacos/data`）与 `nacos-logs`（挂载 `/home/nacos/logs`）两个命名数据卷。
*   三个业务服务通过 `spring-cloud-starter-alibaba-nacos-discovery` 注册，服务名即各自的 `spring.application.name`。

### 2. 端口说明（控制台端口 / 服务端 / SDK 三者分离）

| 用途 | 容器端口 | 宿主机端口 | 说明 |
| --- | --- | --- | --- |
| 控制台 UI | 8080 | **18080** | Nacos 3.x 控制台独立端口，访问 <http://localhost:18080> |
| 服务端 HTTP API | 8848 | 8848 | 服务注册、发现与 OpenAPI |
| 客户端 gRPC | 9848 | 9848 | SDK 长连接端口（8848 + 1000） |

三个端口均只绑定 `127.0.0.1`，不对局域网或公网暴露。

### 3. 启动命令（必须先启动 Nacos，再启动业务服务）

```bash
# 0) 首次使用：准备本地环境变量（真实密码/Token 只写进 .env，该文件已被 .gitignore 忽略）
cp .env.example .env

# 1) 先单独启动 Nacos
docker compose up -d nacos

# 2) 首次使用：浏览器打开 http://localhost:18080 初始化管理员密码
#    用户名固定为 nacos，密码自行设置，并同步写进本机 .env 的 NACOS_PASSWORD

# 3) 再启动三个业务服务（构建镜像，启动后自动注册到 Nacos）
docker compose up -d --build user-service lost-service admin-service
```

### 4. 控制台地址与账号

*   控制台：<http://localhost:18080>
*   用户名：`nacos`
*   密码：见本机 `.env` 中的 `NACOS_PASSWORD`

### 5. 服务注册验证

```bash
docker compose ps
docker compose logs user-service lost-service admin-service | grep "register finished"
```

**查看服务列表的方法**：

1. 浏览器打开控制台 <http://localhost:18080>（映射的是容器 8080，不是旧版 `8848/nacos` 地址）；
2. 用 `nacos` 账号登录（密码见本机 `.env` 的 `NACOS_PASSWORD`）；
3. 左侧「服务管理 → 服务列表」，命名空间选 `public`，即可看到 3 个服务、分组 `ZCL_GROUP`；`user-service` 下有 **2 个实例**（`user-service` 与 `user-service-b`），其余服务各 1 个；
4. 点开某个服务详情，可看到实例 IP、端口、健康状态与是否临时实例。

| 服务名 | 分组 | 实例 IP:端口 | 健康状态 |
| --- | --- | --- | --- |
| user-service | ZCL_GROUP | 172.19.0.5:8081（实例 A，宿主 8081） | 健康 |
| user-service | ZCL_GROUP | 另一容器 IP:8081（实例 B，宿主 18081） | 健康 |
| lost-service | ZCL_GROUP | 172.19.0.7:8082 | 健康 |
| admin-service | ZCL_GROUP | 172.19.0.6:8083 | 健康 |

> 实例 IP 是容器在 `micro-net` 网络中的地址；`user-service` 的两个实例容器内端口都是 8081，只有宿主机映射端口不同（8081 / 18081）。

### 6. 注册相关配置要点

*   三个服务的 `application.yml` 均已配置：

```yaml
spring:
  cloud:
    nacos:
      discovery:
        server-addr: ${NACOS_SERVER_ADDR:nacos:8848}
        username: ${NACOS_USERNAME:nacos}
        password: "${NACOS_PASSWORD:}"
        group: ${NACOS_GROUP:ZCL_GROUP}
        register-enabled: true
```

*   命名空间使用默认的 `public`，三个服务一致；分组统一为 `ZCL_GROUP`（由 `.env` 中的 `NACOS_GROUP` 注入），不与课堂示例的默认分组混用。

*   容器内访问 Nacos 必须用服务名 `nacos:8848`，不能写 `localhost`；本机直接运行服务时可用 `NACOS_SERVER_ADDR=localhost:8848` 覆盖。
*   版本由 BOM 统一管理：Spring Cloud `2025.1.4` + Spring Cloud Alibaba `2025.1.0.0`（与 Spring Boot 4.1.2 匹配）。各服务只声明 `spring-cloud-starter-alibaba-nacos-discovery`，**不单独覆盖 nacos-client 版本**；本周**未引入** Config Starter。

### 7. 服务发现与业务调用（第七周：DiscoveryClient 手工寻址）

第七周的实现是：每次请求都用 `DiscoveryClient.getInstances("user-service")` 取实例列表，自己拼出 `http://<实例IP>:8081/api/users/{id}`，再用 `RestTemplate` 调用；实例列表为空或连接失败时返回 503。

**第八周已整体替换为 OpenFeign**：调用方代码里只写服务名，寻址与实例选择交给 LoadBalancer；失败处理、JSON 响应契约与负载均衡验证见文末 **《第八周：OpenFeign 跨服务调用与负载均衡验证》**。

> 本章余下内容（§8 测试命令、§9 冒烟脚本、§10 健康与依赖下线验证）仍然适用于当前代码。

### 8. 各服务测试命令

进入对应服务目录执行（无需先启动 MySQL / Nacos）：

```bash
cd services/user-service && ./mvnw test
cd services/lost-service && ./mvnw test
cd services/admin-service && ./mvnw test
```

> Windows PowerShell 里不能写 `./mvnw`（PowerShell 要求带扩展名，且 `scripts/` 目录下没有 wrapper），请先进到服务目录再执行 `.\mvnw.cmd test`；Git Bash 里则用 `bash mvnw test`。

> 测试**不依赖真实 MySQL 与 Nacos**：测试类中关闭了 Nacos 发现与自动注册（`spring.cloud.nacos.discovery.enabled=false`、`register-enabled=false`、`service-registry.auto-registration.enabled=false`），数据源切换为 H2 内存库（`com.h2database:h2`，test 作用域），下游 HTTP 用 Mockito 模拟 Feign 客户端（不再使用 `MockRestServiceServer`）。`lost-service` 另有 4 个 Feign 客户端用例（用户存在、404、对方 5xx、无可用实例）。

### 9. 冒烟脚本（在仓库根目录执行）

```bash
# 从仓库根目录运行
bash scripts/smoke-test.sh
```

脚本共检查 **4 项**，**任一失败即以非零状态退出**（不会忽略错误后仍报成功）：

| # | 检查项 | 说明 |
| --- | --- | --- |
| 1 | Nacos 控制台页面可访问 | 使用实际映射地址 `http://localhost:18080`（容器 8080 → 宿主 18080），**不是旧版 `8848/nacos`**；脚本会跟随 3.x 控制台的 `/` → `/next/` 跳转，并校验返回的是控制台 HTML 页面 |
| 2 | `user-service` (8081) `/actuator/health` | 要求响应含 `"status":"UP"` |
| 3 | `lost-service` (8082) `/actuator/health` | 同上 |
| 4 | `admin-service` (8083) `/actuator/health` | 同上 |

退出码：全部通过返回 `0`，任一失败返回 `1`。脚本**不输出任何密码或 Token**。

需要指向其他控制台地址时（默认已是 18080）：

```bash
NACOS_CONSOLE_URL=http://localhost:18080 bash scripts/smoke-test.sh
```

> 注意：控制台能打开 ≠ 业务调用成功。业务请求（200 / 400 / 503）的结果验证见上面第 7、10 节。

### 10. 健康接口与依赖下线/恢复验证

三个服务都保留 Actuator，并配置了健康明细：

```yaml
management:
  endpoints:
    web:
      exposure:
        include: health,info
  endpoint:
    health:
      show-details: always
```

```bash
curl http://localhost:8081/actuator/health   # user-service
curl http://localhost:8082/actuator/health   # lost-service
curl http://localhost:8083/actuator/health   # admin-service
```

验证依赖下线与恢复（`lost-service` 调用 `user-service` 校验用户）：

```bash
# ① 正常：成功并落库，返回 JSON
curl -i -X POST http://localhost:8082/api/lost-items -H 'Content-Type: application/json' \
  -d '{"itemName":"umbrella","feature":"black","userId":1}'

# ② 下线被调用方全部实例（两个实例都要停，只停一个另一个仍会应答）
docker compose stop user-service user-service-b

# ③ 用新的有效参数请求：返回 503 JSON，且数据库不新增记录
curl -i -X POST http://localhost:8082/api/lost-items -H 'Content-Type: application/json' \
  -d '{"itemName":"umbrella-while-down","feature":"black","userId":1}'

# ④ 恢复：等待重新注册后可再次成功
docker compose start user-service user-service-b
```

⚠️ 注意：Actuator 的 `UP/DOWN` 是应用自身健康（`db`、`diskSpace`、`liveness/readiness`），**不会**自动影响 Nacos 注册状态；实例是否被摘除由 SDK 心跳/连接决定。

---

# 第八周：OpenFeign 跨服务调用与负载均衡验证

本周把第七周的“手工寻址 + RestTemplate”整体换成 **OpenFeign 声明式客户端**，用两个 `user-service` 实例验证负载均衡，并把接口响应统一为 JSON、补了一层端到端自动化验证。

## 1. 依赖与启动类

`services/lost-service/pom.xml`（版本全部由 BOM 管理，不写 `version`）：

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

`LostServiceApplication` 上加 `@EnableFeignClients`，扫描 `com.zjgsu.zcl.lost` 包下的 `@FeignClient` 接口。

## 2. Feign 客户端：只写服务名

`services/lost-service/src/main/java/com/zjgsu/zcl/lost/client/UserServiceClient.java`：

```java
@FeignClient(name = UserClient.USER_SERVICE_NAME, path = UserServiceClient.BASE_PATH)
public interface UserServiceClient {
    String BASE_PATH = "/api/users";

    @GetMapping("/{id}")
    Map<String, Object> findById(@PathVariable("id") Long id);
}
```

*   `name = "user-service"` 是注册中心里的**服务名**，不是 IP、端口或容器名；
*   `path` + `@GetMapping("/{id}")` 拼成 `GET /api/users/{id}`，与 `user-service` 原有接口一致；
*   实际目标地址由 LoadBalancer 在**每次调用时**从 Nacos 实例列表中选一个；
*   调用方代码里不出现任何 IP / 端口 / 容器地址。

超时配置（`services/lost-service/src/main/resources/application.yml`）：

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

## 3. 失败处理：三分支 + JSON 响应

`UserClient#checkUserExists` 把 Feign 异常翻译成业务语义，不把 `FeignException` 泄漏给 Service 层：

```java
try {
    Map<String, Object> user = userServiceClient.findById(userId);
    return user != null && user.get("id") != null;
} catch (FeignException.NotFound e) {          // 404：对方明确的业务答案
    return false;
} catch (FeignException e) {                   // 对方 5xx / 响应解码失败
    throw new UserServiceUnavailableException("调用 user-service 失败：" + ...);
} catch (RuntimeException e) {                 // LoadBalancer 无实例、连接/读取超时
    throw new UserServiceUnavailableException("调用 user-service 失败：" + e.getMessage(), e);
}
```

`LostItemController` 三种结果统一返回 JSON（`Content-Type: application/json`）：

| 场景 | 状态码 | 响应体 |
| --- | --- | --- |
| 发布成功 | `200` | `{"id": 51, "message": "发布成功"}` |
| 用户不存在 | `400` | `{"error": "发布失败：用户 9999 不存在，无法发布失物"}` |
| 实例为空 / 超时 / 5xx | `503` | `{"error": "依赖服务不可用：..."}` |

*   成功带 `id`（新失物主键）与 `message`，字段顺序固定（`LinkedHashMap`）；
*   失败统一用 `error` 字段，HTTP 状态码与语义对应：业务拒绝 400、依赖故障 503；
*   三种情况都**不写库**：校验在 `entityManager.persist()` 之前，异常触发事务回滚。

## 4. 负载均衡：两个实例

`compose.yaml` 把 `user-service` 跑成两个实例，**同一镜像、同一服务名**：

```yaml
user-service:
  ports:
    - "8081:8081"        # 实例 A：宿主机 8081 → 容器 8081
  environment:
    INSTANCE_ID: user-service-1

user-service-b:
  # 同一镜像；spring.application.name 仍是 user-service
  ports:
    - "18081:8081"       # 实例 B：只错开宿主机端口，容器内仍是 8081
  environment:
    INSTANCE_ID: user-service-b-1
```

两个实例容器内端口都是 `8081`（代码没改），只有**宿主机端口**必须错开，否则第二个容器会报 `port is already allocated`；这样也满足“不绑定同一宿主机端口”的要求。

`user-service` 的 `UserController` 每次查询打印实例标识与计数，用于核对请求落在哪个实例：

```text
instance=user-service-1   本实例第 1 次查询，userId=1
instance=user-service-b-1 本实例第 1 次查询，userId=1
```

验证方法：等两个实例都注册后连发 6 次 `POST /api/lost-items`，应全部 200；日志中 `instance=user-service-1` 与 `instance=user-service-b-1` 交替出现、各 3 次，证明请求真的分发到两个实例，而不是只在注册列表里看到两行。

```bash
for i in $(seq 1 6); do
  curl -s -o /dev/null -w "$i: %{http_code}\n" \
    -H "Content-Type: application/json" \
    -d '{"itemName":"lb-test","feature":"lb","userId":1}' \
    http://localhost:8082/api/lost-items
done

docker compose logs --tail=60 user-service user-service-b | grep "instance="
```

实例下线验证：`docker compose stop user-service-b` 后再发 3 次，仍全部 200，日志只出现在 `user-service-1`，说明实例摘除后自动切到存活实例，调用方无需重启、无需改代码。若要测 503，必须把两个实例都停掉。

## 5. 测试

| 层次 | 内容 | 位置 |
| --- | --- | --- |
| Feign 客户端单测 | 用户存在、404、对方 5xx、无可用实例 | `services/lost-service/.../client/UserClientTest.java` |
| Service 层单测 | 用户查询/校验、审核状态机与重复审核 | `services/user-service/.../UserServiceTest.java`、`services/admin-service/.../AuditServiceTest.java` |
| 端到端 | 真实容器 + 真实 Nacos + 两个库：断言 200/400/503、JSON 字段、行数变化 | `scripts/e2e-test.sh` |

`scripts/e2e-test.sh` 共 **16 项断言**（原 13 项 + 3 项 JSON 字段断言），任一失败即以非 0 退出；脚本用 `trap` 保证异常中断时也会把停掉的实例拉起来，不留残废环境。

## 6. 运行命令

```bash
# 1) 启动整套环境（含两个 user-service 实例）
docker compose up -d --build

# 2) 正常发布（应 200 JSON）
curl -s -w "\nHTTP %{http_code}\n" -H "Content-Type: application/json" \
  -d '{"itemName":"校园卡","feature":"蓝色卡套","userId":1}' \
  http://localhost:8082/api/lost-items

# 3) 用户不存在（应 400 JSON，且不写库）
curl -s -w "\nHTTP %{http_code}\n" -H "Content-Type: application/json" \
  -d '{"itemName":"测试","feature":"x","userId":9999}' \
  http://localhost:8082/api/lost-items

# 4) 依赖不可用：停掉全部实例（应 503 JSON），测完恢复
docker compose stop user-service user-service-b
curl -s -w "\nHTTP %{http_code}\n" -H "Content-Type: application/json" \
  -d '{"itemName":"down-test","feature":"x","userId":1}' \
  http://localhost:8082/api/lost-items
docker compose start user-service user-service-b

# 5) 端到端脚本（Git Bash / macOS / Linux）
bash scripts/e2e-test.sh
echo $?     # 0 = 全部通过
```

## 7. 本周交付物

| 内容 | 位置 |
| --- | --- |
| 需求与实现说明、实测结果 | [`docs/homework/week-08/index.md`](docs/homework/week-08/index.md) |
| 跨服务调用过程说明 | [`docs/service-communication.md`](docs/service-communication.md) |
| 端到端验证脚本 | [`scripts/e2e-test.sh`](scripts/e2e-test.sh) |
| 截图 | [`docs/homework/week-08/screenshots/`](docs/homework/week-08/screenshots/) |

四个提交项（① 跨服务业务流程、② OpenFeign、③ 负载均衡验证、④ 测试）的实现思路、运行命令与实测结果，见上面两份文档。

---

# 第九周：API 网关与认证服务

大作业实践三项：① 新增 `services/gateway-service` 作为统一入口；② 在 `user-service` 中实现登录、JWT 签发/验签、角色与密码哈希；③ 在网关实现鉴权（401/403、角色校验、伪造身份头处理）。第三项详见 §6 与 [`docs/security.md`](docs/security.md)。

## 1. 网关工程与依赖

新增 `services/gateway-service/`（Spring Boot 4.1.2 + Java 25，与其它服务同一套 BOM）：

*   `spring-cloud-starter-gateway-server-webflux`（Spring Cloud Gateway `5.0.4`）；
*   `spring-cloud-starter-alibaba-nacos-discovery` + `spring-cloud-starter-loadbalancer`（路由目标用服务名）；
*   `spring-boot-starter-actuator`（健康检查）。

## 2. 路由配置

`services/gateway-service/src/main/resources/application.yml`（Gateway 5.x 的配置前缀是 `spring.cloud.gateway.server.webflux`）：

```yaml
server:
  port: 8080
spring:
  application:
    name: gateway-service
  cloud:
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

| 路由 | 匹配 | 目标 | 后端接口 |
| --- | --- | --- | --- |
| `auth-service-route` | `/api/auth/**` | `lb://user-service` | `POST /api/auth/login`、`GET /api/auth/me` |
| `user-service-route` | `/api/users/**` | `lb://user-service` | `GET /api/users/{id}` |
| `lost-service-route` | `/api/lost-items/**` | `lb://lost-service` | `POST /api/lost-items` |
| `admin-service-route` | `/api/admin/**` | `lb://admin-service` | `GET/POST /api/admin/audits` |

`lb://` 表示按服务名转发：网关从 Nacos 取实例列表，再由 LoadBalancer 选一个实例，配置里不出现任何实例 IP/端口；没有加 `StripPrefix`，因为后端路径就是这个前缀。

## 3. 健康检查入口

*   `GET http://localhost:8080/api/health` → `{"status":"UP","service":"gateway-service","port":8080}`（网关自建 Controller）；
*   `GET http://localhost:8080/actuator/health` → `{"status":"UP",...}`（Actuator）。

## 4. 运行与验证

```bash
# 启动（含网关；首次构建 4 个服务镜像）
docker compose up -d --build

# 先登录拿 Token（鉴权开启后，业务路由必须带 Token）
TOKEN=$(curl -s -H "Content-Type: application/json" \
  -d '{"username":"student_zhang","password":"Student@123"}' \
  http://localhost:8080/api/auth/login | grep -o '"token":"[^"]*"' | sed 's/"token":"//;s/"$//')

# 三条业务路由都从统一入口 8080 访问，并带上 Token
curl -s -w "\nHTTP %{http_code}\n" -H "Authorization: Bearer $TOKEN" \
  http://localhost:8080/api/users/1
curl -s -w "\nHTTP %{http_code}\n" -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"itemName":"gateway-test","feature":"gateway","userId":1}' \
  http://localhost:8080/api/lost-items
curl -s -w "\nHTTP %{http_code}\n" -H "Authorization: Bearer $TOKEN" \
  http://localhost:8080/api/admin/audits

# 业务拒绝经网关仍保留 400
curl -s -w "\nHTTP %{http_code}\n" -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"itemName":"gateway-test","feature":"gateway","userId":9999}' \
  http://localhost:8080/api/lost-items
```

实测（2026-11-21）：三条业务路由经 8080 分别返回 200 / 200 / 200，响应体与直连后端一致；`userId=9999` 经网关返回 400 与原始业务错误；网关启动日志可见 `nacos registry, ZCL_GROUP gateway-service 172.19.0.10:8080 register finished`，说明网关本身也已注册到 Nacos。

## 5. 认证服务（user-service）

### 5.1 测试账号与角色

| 用户名 | 演示密码 | 角色 | 说明 |
| --- | --- | --- | --- |
| `student_zhang` | `Student@123` | `USER` | 普通用户 |
| `admin_li` | `Admin@123` | `ADMIN` | 管理员 |

数据库只存 BCrypt 哈希（`password_hash`，`$2a$` 开头、长度 60），没有明文；演示密码只用于课堂验证。

### 5.2 JWT 密钥

密钥只从环境变量注入，仓库不保存。首次使用需要在本地 `.env` 里加一行（不会入库，`.env` 已被忽略）：

```bash
# 生成 32 字节随机密钥并写入 .env
echo "JWT_SECRET=$(openssl rand -base64 32)" >> .env
```

`compose.yaml` 会把 `JWT_SECRET` 注入两个 `user-service` 实例；缺这个变量时服务启动失败并提示，不会用弱默认值。

### 5.3 登录与验签

```bash
# 登录（可直连 8081，也可经网关 8080）
curl -s -H "Content-Type: application/json" \
  -d '{"username":"student_zhang","password":"Student@123"}' \
  http://localhost:8080/api/auth/login

# 成功返回：{"token":"eyJ...","tokenType":"Bearer","expiresIn":3600,"user":{"id":1,"username":"student_zhang","role":"USER"}}

# 密码错误：401 {"error":"用户名或密码错误"}

# 验签：带 Token 访问 /me
curl -s -H "Authorization: Bearer <token>" http://localhost:8080/api/auth/me
# 200 {"id":1,"username":"student_zhang","role":"USER"}

# 无 Token / 篡改签名 / 过期：401
```

### 5.4 认证脚本

```bash
bash scripts/auth-test.sh     # 18 项断言：未登录/登录/授权/越权，全 PASS 退出码为 0
```

覆盖：登录成功与角色、错误密码 401、不存在用户 401、`/me` 验签、缺 Token 401、篡改签名 401、管理员登录与 `/me`、数据库哈希格式、经网关登录、未登录访问受保护接口 401、登录后 200、USER 越权 403、ADMIN 授权 200、伪造身份头 401。

### 5.5 实现位置

| 文件 | 作用 |
| --- | --- |
| `services/user-service/.../controller/AuthController.java` | `POST /api/auth/login`、`GET /api/auth/me` |
| `services/user-service/.../service/AuthService.java` | 登录校验、签发、验签 |
| `services/user-service/.../security/JwtTokenService.java` | JWT 签发与验签（claims：subject/username/role/iat/exp） |
| `services/user-service/.../security/JwtConfig.java` | BCrypt、JwtEncoder、JwtDecoder Bean |
| `services/user-service/src/main/resources/data.sql` | 两个账号的 BCrypt 哈希种子 |

## 6. 网关鉴权

### 6.1 规则

| 路径 | 规则 | 结果 |
| --- | --- | --- |
| `/api/health`、`/actuator/health`、`POST /api/auth/login` | 公开 | 直接放行 |
| `/api/auth/me`、`/api/users/**`、`/api/lost-items/**` | 需要合法 Bearer Token | 无/无效 → 401；有效 → 200 |
| `/api/admin/**` | 需要 Token 且 `role=ADMIN` | 普通用户 → 403；管理员 → 200 |

实现：`services/gateway-service/.../filter/AuthGlobalFilter.java`（全局过滤器，`getOrder() = -100`）。

- 验签使用与 user-service 相同的 `JWT_SECRET`（网关只验签、不签发）；
- 验签通过后先删除客户端可能伪造的 `X-User-Id / X-Username / X-User-Role / X-Gateway-Signature`，再写入真实身份；
- 下游 `GET /api/users/current` 用 `GATEWAY_SHARED_SECRET` 校验 `X-Gateway-Signature`（HMAC-SHA256），直接访问 8081 或伪造身份头都会 401。

### 6.2 验证

```bash
# 未登录 → 401
curl -s -w "\nHTTP %{http_code}\n" http://localhost:8080/api/users/current

# 普通用户访问管理员接口 → 403
TOKEN=$(curl -s -H "Content-Type: application/json" \
  -d '{"username":"student_zhang","password":"Student@123"}' \
  http://localhost:8080/api/auth/login | grep -o '"token":"[^"]*"' | sed 's/"token":"//;s/"$//')
curl -s -w "\nHTTP %{http_code}\n" -H "Authorization: Bearer $TOKEN" \
  http://localhost:8080/api/admin/audits

# 管理员访问同一接口 → 200
ADMIN_TOKEN=$(curl -s -H "Content-Type: application/json" \
  -d '{"username":"admin_li","password":"Admin@123"}' \
  http://localhost:8080/api/auth/login | grep -o '"token":"[^"]*"' | sed 's/"token":"//;s/"$//')
curl -s -w "\nHTTP %{http_code}\n" -H "Authorization: Bearer $ADMIN_TOKEN" \
  http://localhost:8080/api/admin/audits

# 直连后端 + 伪造身份头 → 401
curl -s -w "\nHTTP %{http_code}\n" \
  -H "X-User-Id: 2" -H "X-User-Role: ADMIN" -H "X-Gateway-Signature: forged" \
  http://localhost:8081/api/users/current

# 一键验证（10 项断言）
bash scripts/authz-test.sh
```

### 6.3 密钥

| 密钥 | 用途 | 存放 |
| --- | --- | --- |
| `JWT_SECRET` | JWT 签发/验签，user-service 与 gateway 共用 | `.env`（已忽略） |
| `GATEWAY_SHARED_SECRET` | 网关身份头 HMAC 签名，下游校验 | `.env`（已忽略） |

```bash
# 首次使用：生成并写入 .env
echo "JWT_SECRET=$(openssl rand -base64 32)" >> .env
echo "GATEWAY_SHARED_SECRET=$(openssl rand -base64 32)" >> .env
```

安全设计的完整说明见 [`docs/security.md`](docs/security.md)。

## 7. 文档与截图

实现细节、验收点对照、截图清单见 [`docs/homework/week-09/index.md`](docs/homework/week-09/index.md)，截图保存到 [`docs/homework/week-09/screenshots/`](docs/homework/week-09/screenshots/)；网关鉴权与认证流程的安全设计见 [`docs/security.md`](docs/security.md)。

---

# 第十周：配置中心（Nacos Config）

把 4 个服务的非敏感配置迁到 Nacos，用 namespace 区分 dev / test，并验证“改配置不重启即生效”。

## 1. Nacos 中的结构

```text
Nacos 3.2.4
├── namespace zcl-dev（开发）
│   ├── user-service.yaml
│   ├── lost-service.yaml
│   ├── admin-service.yaml
│   └── gateway-service.yaml
└── namespace zcl-test（测试）
    └── 同名 4 份，值与 dev 不同
```

Group 统一 `ZCL_GROUP`；环境用 namespace 隔离（与注册中心共用 Nacos 实例）。

## 2. 迁移了哪些配置

| Data ID | 服务 | 配置项 | dev | test |
| --- | --- | --- | --- | --- |
| `user-service.yaml` | user-service | JWT 有效期 / pool-size / 日志级别 | 3600 / 5 / INFO | 1800 / 8 / DEBUG |
| `lost-service.yaml` | lost-service | Feign 超时 / pool-size / 日志级别 | 3000/3000 / 3 / INFO | 2000/2000 / 6 / DEBUG |
| `admin-service.yaml` | admin-service | audit-page-size / 日志级别 | 20 / INFO | 10 / DEBUG |
| `gateway-service.yaml` | gateway-service | 日志级别 | INFO | DEBUG |

只迁非敏感项；数据库密码、`JWT_SECRET`、`GATEWAY_SHARED_SECRET` 仍在 `.env`，不入库。

## 3. 接入方式

```xml
<dependency>
  <groupId>com.alibaba.cloud</groupId>
  <artifactId>spring-cloud-starter-alibaba-nacos-config</artifactId>
</dependency>
```

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
        namespace: ${NACOS_CONFIG_NAMESPACE:}   # compose 默认 zcl-dev
```

`optional:` 保证配置中心暂不可用时服务仍能启动（代码里保留默认值）。

## 4. 多环境切换

```bash
# 开发环境（默认）
docker compose up -d

# 测试环境：只换 namespace
NACOS_CONFIG_NAMESPACE=zcl-test docker compose up -d
```

## 5. 动态刷新

- `ConfigDemoController`（user-service / lost-service）加 `@RefreshScope`；
- 验证端点：`GET http://localhost:8081/api/users/config`、`GET http://localhost:8082/api/lost-items/config`；
- 实测：改 Nacos 后 1 秒内生效，容器 `StartedAt` 不变（不是重启）。

## 6. 运行与验证

```bash
bash scripts/config-setup.sh     # 创建 namespace + 发布 8 份配置
docker compose up -d --build     # 重建服务
bash scripts/config-test.sh      # 14 项断言
```

实测（2026-11-23）：`PASS=14 FAIL=0`，覆盖 dev/test 配置存在且值不同、服务读到配置、动态刷新（1s、未重启）、日志级别刷新、环境切换（切 test 9s 生效、切回 dev 9s 恢复）、`.env` 未入库、配置内容无敏感项。

## 7. 文档与截图

配置中心说明见 [`docs/config-center.md`](docs/config-center.md)，配置台账（Data ID / Group / 用途）见 [`docs/configuration.md`](docs/configuration.md)，验收对照与截图清单见 [`docs/homework/week-10/index.md`](docs/homework/week-10/index.md)，截图保存到 [`docs/homework/week-10/screenshots/`](docs/homework/week-10/screenshots/)。


