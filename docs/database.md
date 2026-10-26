# 数据库设计文档

## 1. 业务表结构

### users (用户表)
| 字段名   | 类型         | 约束               | 说明   |
| :------- | :----------- | :----------------- | :----- |
| id       | BIGINT       | PK, AUTO_INCREMENT | 用户ID |
| username | VARCHAR(255) | NOT NULL, UNIQUE   | 用户名 |

### lost_items (失物表)
| 字段名    | 类型         | 约束               | 说明           |
| :-------- | :----------- | :----------------- | :------------- |
| id        | BIGINT       | PK, AUTO_INCREMENT | 失物ID         |
| item_name | VARCHAR(255) | NOT NULL           | 物品名称       |
| feature   | VARCHAR(255) | NOT NULL           | 物品特征       |
| status    | VARCHAR(50)  | NOT NULL           | 状态           |
| user_id   | BIGINT       | FK, NOT NULL       | 关联发布用户ID |

## 2. 表关系说明
`lost_items.user_id` 外键关联 `users.id`，实现多对一关系（一个用户可以发布多条失物）。