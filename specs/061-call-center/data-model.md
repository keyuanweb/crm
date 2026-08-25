# 数据模型：通话记录管理模块

**Branch**: `061-call-center` | **Date**: 2026-08-25

## 1. call_record（通话记录，Flyway V68）

| 列 | 类型 | 约束 | 说明 |
|---|---|---|---|
| id | BIGINT | PK, AUTO | |
| customer_id | BIGINT | NULL | 客户（可空） |
| contact_id | BIGINT | NULL | 联系人（可空） |
| direction | VARCHAR(20) | NOT NULL | INBOUND/OUTBOUND |
| duration_seconds | INT | NOT NULL DEFAULT 0 | 时长（秒） |
| result | VARCHAR(20) | NOT NULL | CONNECTED/NO_ANSWER/BUSY/FAILED |
| remark | VARCHAR(500) | NULL | 备注（录音链接占位） |
| recorded_by | BIGINT | NOT NULL | 记录人 |
| recorded_at | DATETIME | NOT NULL | 记录时间 |

索引：`idx_call_customer`（customer_id）、`idx_call_recorded_at`（recorded_at）。

## 2. 业务规则

- 方向 INBOUND/OUTBOUND；结果 CONNECTED/NO_ANSWER/BUSY/FAILED。
- 联系人提供时 customerId 须匹配所选客户（422 CALL_CONTACT_MISMATCH）。
- 统计：次数/总时长/平均时长（按方向/时间筛选）。
