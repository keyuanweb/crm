# 数据模型：客户标签与细分

## tag

| 字段 | 类型 | 说明 |
|---|---|---|
| id | bigint | 主键 |
| name | varchar(50) | 标签名（唯一） |
| color | varchar(20) | 颜色（antd Tag 色板） |
| entity_type | varchar(20) | 适用实体：CUSTOMER/LEAD/CONTACT |
| created_by | bigint | 创建人 |

## customer_tag

| 字段 | 类型 | 说明 |
|---|---|---|
| id | bigint | 主键 |
| customer_id | bigint | 客户 |
| tag_id | bigint | 标签 |

## segment

| 字段 | 类型 | 说明 |
|---|---|---|
| id | bigint | 主键 |
| name | varchar(50) | 细分名（唯一） |
| description | varchar(255) | 描述 |
| conditions | text | JSON 条件（logic + filters） |
| created_by / deleted / version / created_at / updated_at | | 审计 |

## 约束

- 标签名唯一（按实体类型）；删除标签级联清关联。
- 细分条件 JSON 校验（字段/操作符白名单）。
