# Data Model: 任务与提醒模块

**Branch**: `010-task-reminder` | **Date**: 2026-08-22

## 新增实体

### TaskItem（任务）

| 字段 | 类型 | 约束 | 说明 |
|---|---|---|---|
| id | BIGINT | PK, AUTO_INCREMENT | 主键 |
| title | VARCHAR(200) | NOT NULL | 任务标题 |
| due_at | DATETIME | NULL | 截止时间（可空） |
| priority | VARCHAR(20) | NOT NULL DEFAULT 'MEDIUM' | HIGH/MEDIUM/LOW |
| status | VARCHAR(20) | NOT NULL DEFAULT 'TODO' | TODO/DONE |
| linked_type | VARCHAR(20) | NULL | CUSTOMER/LEAD/CONTRACT/ORDER |
| linked_id | BIGINT | NULL | 关联对象 id |
| remark | VARCHAR(500) | NULL | 备注 |
| owner_id | BIGINT | NOT NULL | 归属用户（数据隔离） |
| deleted | TINYINT(1) | NOT NULL DEFAULT 0 | 逻辑删除 |
| version | INT | NOT NULL DEFAULT 0 | 乐观锁 |
| created_by | BIGINT | NULL | 创建人 |
| created_at | DATETIME | NOT NULL DEFAULT CURRENT_TIMESTAMP | 创建时间 |
| updated_at | DATETIME | NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP | 更新时间 |

**索引**: `idx_task_owner_status(owner_id, status)`、`idx_task_owner_due(owner_id, due_at)`。

## 状态与标识（动态计算，不落库）

- 状态：TODO（待办）/ DONE（完成）；完成/重开切换（update status）。
- 提醒标识（响应计算）：
  - DONE → "DONE"；
  - TODO 且 due_at < now → "OVERDUE"（逾期，含 overdueDays）；
  - TODO 且 due_at ∈ [今天, 明天) → "TODAY"（今日到期）；
  - 其余 → "NORMAL"。
- 逾期天数 = 天数差（ceil，至少 1）。

## 跟进自动建任务规则

- POST /follow-ups 请求新增 `createTask`（可选布尔）与既有 `nextFollowUpAt`：
  - createTask=true 且 nextFollowUpAt 非空 → 创建 TaskItem：
    - title = "跟进：{客户名或线索名}"；
    - due_at = nextFollowUpAt；priority=MEDIUM；status=TODO；
    - linked_type = CUSTOMER（有 customerId）或 LEAD（有 leadId）；linked_id 对应；
    - owner_id = 当前用户。
  - 否则不创建（无副作用）。
