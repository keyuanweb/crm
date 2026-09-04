# Data Model: 回收站与批量恢复

## RecycleItem（派生，无新表）

| 字段 | 类型 | 说明 |
|---|---|---|
| type | String | CUSTOMER / LEAD / CONTACT / OPPORTUNITY |
| id | Long | 实体 id |
| name | String | 显示名（客户名/线索名/联系人名/商机名） |
| deletedAt | LocalDateTime | 删除时间（updated_at） |
| deletedBy | Long | 删除人（created_by 近似） |

## 操作

- **恢复**: `UPDATE <table> SET deleted=0 WHERE id=#{id}`（自定义注解 SQL）
- **彻底删除**: `DELETE FROM <table> WHERE id=#{id}`（物理删）

## 约束

- 覆盖实体：customer / lead / contact / opportunity（各表均有 deleted 字段）。
- 恢复不动关联数据；SALES 仅本人删除记录。
