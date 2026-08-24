# 数据模型：团队销售目标与排行

## sales_target 表扩展（Flyway V44）

| 字段 | 变更 | 说明 |
|---|---|---|
| user_id | 新增 | BIGINT 可空；NULL=全局目标，非 NULL=个人目标 |
| active_key | 修改 | 生成列改为按 (user_id, target_month) 唯一（user_id 空时按 target_month） |

**索引**: `idx_sales_target_user_month (user_id, target_month)`。

**兼容**: 既有全局目标记录（user_id=NULL）不受影响；006 查询逻辑适配。

## LeaderboardItem（派生，无新表）

| 字段 | 类型 | 说明 |
|---|---|---|
| userId | Long | 销售 id |
| displayName | String | 显示名 |
| targetAmount | Long? | 个人目标（未设置 null） |
| wonAmount | Long | 当月赢单金额（分） |
| achievementRate | Double? | 达成率（未设目标 null） |

## 约束

- 赢单金额按 sales_opportunity.created_by 归属。
- 排行实时聚合，无缓存无持久化。
