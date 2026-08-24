# 数据模型：客户查重合并

## DuplicateGroupResponse（派生，无新表）

| 字段 | 类型 | 说明 |
|---|---|---|
| id | Long | 组 id（用主客户 id） |
| primaryId | Long | 建议主记录 |
| primaryName | String | 主记录名 |
| duplicates | DuplicateItem[] | 疑似重复列表 |

## DuplicateItem

| 字段 | 类型 | 说明 |
|---|---|---|
| customerId | Long | 重复客户 |
| name | String | 名称 |
| company | String? | 公司 |
| similarity | int | 相似度 0-100 |
| relatedCount | long | 关联数据数（订单+商机+联系人+跟进+工单） |

## MergeRequest

| 字段 | 类型 | 说明 |
|---|---|---|
| primaryId | Long | 主记录 |
| duplicateId | Long | 从记录 |

## 约束

- 查重临时计算（无表）。
- 合并单事务：转移 → 冲突合并 → 回收站 → 审计。
- 权限：customer:merge（或复用 customer:manage）。
