

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

- Java：17
- Maven：3.8+

### 2.启动命令：

mvn spring-boot:run

### 3,测试命令

```
./mvnw test
```

### 4.接口地址

问候接口：http://127.0.0.1:8080/hello

健康检查接口：<http://127.0.0.1:8080/actuator/health>



### 5.当前尚未实现的业务能力

物品图片上传、认领申请、管理员审核、用户登录鉴权、数据库持久化存储。

## 第四周

### 1. 数据库配置
### 本地 MySQL 配置说明
本项目未使用 Docker Compose，需本地安装 MySQL 8.0+ 版本。
1. 确保本地 MySQL 服务已启动。
2. 在 `application.yml` 中配置正确的用户名和密码。
3. 项目配置了 `createDatabaseIfNotExist=true`，启动时会自动创建 `lostfound_db` 数据库并自动建表，无需手动执行建表 SQL。

### 2. cURL 测试示例
**创建失物（正常）：**

```bash
curl -X POST http://localhost:8080/api/lost-items \
-H "Content-Type: application/json" \
-d '{"userId": 1, "itemName": "水杯", "feature": "底部有贴纸"}'
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

### 1. 用户服务 (user-service) 测试
进入 `services/user-service` 目录后执行：
```bash
cd services/user-service
./mvnw test
```

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

控制台「服务管理 → 服务列表」可见 3 个服务，实例数均为 1；点开服务详情可看到实例 IP 与端口：

| 服务名 | 分组 | 实例 IP:端口 | 健康状态 |
| --- | --- | --- | --- |
| user-service | ZCL_GROUP | 172.19.0.6:8081 | 健康 |
| lost-service | ZCL_GROUP | 172.19.0.7:8082 | 健康 |
| admin-service | ZCL_GROUP | 172.19.0.5:8083 | 健康 |

> 实例 IP 是容器在 `micro-net` 网络中的地址，与宿主机发布的 8081 / 8082 / 8083 一一对应。

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

### 7. 服务发现与业务调用（发布失物前校验用户）

`lost-service` 发布失物（`POST /api/lost-items`）时，需要先调用 `user-service` 的 `GET /api/users/{id}` 校验用户是否存在。该调用**不再使用固定地址**，而是每次请求都通过 `DiscoveryClient` 实时解析实例：

```java
List<ServiceInstance> instances = discoveryClient.getInstances("user-service");
if (instances.isEmpty()) {
    throw new UserServiceUnavailableException(...);   // 控制器 -> 503
}
URI uri = instance.getUri() + "/api/users/" + userId; // 用发现的地址 + 原业务路径
```

行为约定：

| 情况 | 返回 |
| --- | --- |
| 用户存在 | `200`，发布成功 |
| 用户不存在（user-service 返回 404） | `400`，保留原有业务响应，不写库 |
| Nacos 中 `user-service` 实例列表为空 / 连接失败 / 超时 | `503`，依赖服务不可用，不写库 |

试用：

```bash
curl -i -X POST http://localhost:8082/api/lost-items \
  -H 'Content-Type: application/json' \
  -d '{"itemName":"校园卡","feature":"蓝色卡套","userId":1}'
```

验证依赖下线/恢复：`docker compose stop user-service` 后再次请求会得到 503，`docker compose start user-service` 后（约十几秒重新注册）自动恢复为 200。

### 8. 各服务测试命令

```bash
cd services/user-service && ./mvnw test
cd services/lost-service && ./mvnw test
cd services/admin-service && ./mvnw test
```

> 测试**不依赖真实 MySQL 与 Nacos**：测试类中已关闭 Nacos 注册/发现，并把数据源切换为 H2 内存库（`com.h2database:h2`，test 作用域），无需先启动任何中间件即可执行。`lost-service` 另有 4 个服务发现用例（空实例列表、按发现地址调用、404 保留业务响应、每次调用重新寻址）。

### 9. 冒烟测试

```bash
bash scripts/smoke-test.sh
```

脚本依次检查 user-service(8081) / lost-service(8082) / admin-service(8083) 的 `/actuator/health`；**任一服务不可用时以非零状态退出**，便于自动化检查。


