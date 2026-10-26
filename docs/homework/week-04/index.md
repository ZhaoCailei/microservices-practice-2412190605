# 第四周作业：单体应用与数据库持久化

## 1. 业务建模
抽取 `User` 和 `LostItem` 两个实体，设计了完整的 REST API。业务规则：在 Service 层校验发布失物时 `feature`（物品特征）不能为空。
运行结果：见下方测试截图。

## 2. 单体应用实现
完成结果：实现了 4 个 REST 接口（增删查），严格遵循 Controller -> Service -> Repository 的分层结构。
验证方法：使用 Postman 发送 POST 请求，特征为空时成功返回 400 及错误提示。

## 3. 数据库持久化
完成结果：使用 Spring Data JPA 连接 MySQL，配置了 `ddl-auto: update` 自动建表。
验证方法：通过 GET 接口成功查询到 MySQL 中持久化的数据。

## 4. 测试与验证
运行 `./mvnw test` 可重复通过，测试覆盖了 Repository 读写、Service 业务规则拦截。
验证截图

![API-test](G:\IDEA Project\microservices-practice-2412190605\docs\homework\week-04\API-test.png)