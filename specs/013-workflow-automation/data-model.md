# Data Model: 工作流自动化模块

**Branch**: `013-workflow-automation` | **Date**: 2026-08-22

## 新增实体

### WorkflowRule（工作流规则）

| 字段 | 类型 | 约束 | 说明 |
|---|---|---|---|
| id | BIGINT | PK, AUTO_INCREMENT | 主键 |
| name | VARCHAR(100) | NOT NULL | 规则名称 |
| event_type | VARCHAR(40) | NOT NULL | LEAD_CREATED/OPPORTUNITY_STAGE_CHANGED/FOLLOW_UP_CREATED/PAYMENT_RECORDED |
| condition_json | TEXT | NULL | 条件 JSON（field/value 单一等值） |
| action_type | VARCHAR(30) | NOT NULL | CREATE_TASK/ASSIGN/NOTIFY |
| action_json | TEXT | NOT NULL | 动作 JSON（目标用户/天数/标题模板等） |
| enabled | TINYINT(1) | NOT NULL DEFAULT 1 | 启用状态 |
| deleted | TINYINT(1) | NOT NULL DEFAULT 0 | 逻辑删除 |
| version | INT | NOT NULL DEFAULT 0 | 乐观锁 |
| created_by | BIGINT | NULL | 创建人 |
| created_at | DATETIME | NOT NULL DEFAULT CURRENT_TIMESTAMP | 创建时间 |
| updated_at | DATETIME | NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP | 更新时间 |

**索引**: `idx_rule_event_enabled(event_type, enabled)`。

### WorkflowExecutionLog（执行日志）

| 字段 | 类型 | 约束 | 说明 |
|---|---|---|---|
| id | BIGINT | PK, AUTO_INCREMENT | 主键 |
| rule_id | BIGINT | NOT NULL | 规则 |
| event_type | VARCHAR(40) | NOT NULL | 触发事件 |
| entity_type | VARCHAR(30) | NULL | 业务实体类型（LEAD/CUSTOMER/OPPORTUNITY/SALES_OPPORTUNITY/FOLLOW_UP/ORDER） |
| entity_id | BIGINT | NULL | 业务实体 id |
| matched | TINYINT(1) | NOT NULL DEFAULT 1 | 是否匹配 |
| action_result | VARCHAR(500) | NULL | 执行结果描述 |
| success | TINYINT(1) | NOT NULL DEFAULT 1 | 是否成功 |
| error_message | VARCHAR(1000) | NULL | 失败原因 |
| created_at | DATETIME | NOT NULL DEFAULT CURRENT_TIMESTAMP | 执行时间 |

**索引**: `idx_log_rule(rule_id)`、`idx_log_event(event_type)`。

### WorkflowNotification（站内通知）

| 字段 | 类型 | 约束 | 说明 |
|---|---|---|---|
| id | BIGINT | PK, AUTO_INCREMENT | 主键 |
| user_id | BIGINT | NOT NULL | 接收人 |
| message | VARCHAR(500) | NOT NULL | 通知内容 |
| read | TINYINT(1) | NOT NULL DEFAULT 0 | 已读 |
| created_at | DATETIME | NOT NULL DEFAULT CURRENT_TIMESTAMP | 通知时间 |

**索引**: `idx_notif_user(user_id, read)`。

## 事件与动作

- 事件枚举：LEAD_CREATED / OPPORTUNITY_STAGE_CHANGED / FOLLOW_UP_CREATED / PAYMENT_RECORDED。
- 动作枚举：CREATE_TASK（创建任务，title 模板 + dueDays）、ASSIGN（自动分配，targetUserId）、NOTIFY（站内通知，message）。
- 匹配：condition_json 的 field/value 与事件 context 等值比较（单一条件）。
