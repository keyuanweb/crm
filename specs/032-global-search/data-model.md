# 数据模型：全局搜索

## SearchResponse（派生，无新表）

| 字段 | 类型 | 说明 |
|---|---|---|
| keyword | string | 关键字 |
| groups | SearchGroup[] | 分组（按实体） |

## SearchGroup

| 字段 | 类型 | 说明 |
|---|---|---|
| type | string | CUSTOMER/LEAD/CONTACT/OPPORTUNITY/TICKET/PRODUCT |
| label | string | 中文名 |
| items | SearchItem[] | 结果（Top 5） |

## SearchItem

| 字段 | 类型 | 说明 |
|---|---|---|
| id | Long | 实体 id |
| title | string | 主显示（名称/标题） |
| subtitle | string | 副显示（公司/编码/客户名） |
| path | string | 跳转路径（如 /customers/{id}） |

## 约束

- 复用现有表 LIKE 查询，无新表。
- 数据权限：ADMIN 全量/其他按创建人或归属人。
