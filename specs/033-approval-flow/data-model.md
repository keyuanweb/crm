# 数据模型：审批流

## approval_flow

| 字段 | 类型 | 说明 |
|---|---|---|
| id | bigint | 主键 |
| name | varchar(50) | 流程名 |
| business_type | varchar(20) | CONTRACT / QUOTE |
| nodes | text | JSON 节点序列 |
| condition_json | text? | 条件分支（金额阈值 + 追加节点） |
| enabled | tinyint | 启用 |
| deleted/version/created_at/updated_at | | 审计 |

## approval_instance

| 字段 | 类型 | 说明 |
|---|---|---|
| id | bigint | 主键 |
| flow_id | bigint | 流程 |
| business_type | varchar(20) | 业务类型 |
| business_id | bigint | 业务 id（合同/报价） |
| title | varchar(200) | 审批标题 |
| status | varchar(20) | PENDING/APPROVED/REJECTED/CANCELED |
| current_task_id | bigint? | 当前任务 |
| initiator | bigint | 发起人 |
| created_at/updated_at | | 时间 |

## approval_task

| 字段 | 类型 | 说明 |
|---|---|---|
| id | bigint | 主键 |
| instance_id | bigint | 实例 |
| node_name | varchar(50) | 节点名 |
| approver_type | varchar(20) | ROLE/USER/MANAGER |
| approver_value | varchar(100) | 角色码或 userId |
| approver_id | bigint? | 实际审批人 |
| status | varchar(20) | PENDING/APPROVED/REJECTED/TRANSFERRED |
| comment | varchar(500)? | 意见 |
| created_at/updated_at | | 时间 |

## approval_log

| 字段 | 类型 | 说明 |
|---|---|---|
| id | bigint | 主键 |
| instance_id | bigint | 实例 |
| task_id | bigint? | 任务 |
| action | varchar(20) | SUBMIT/APPROVE/REJECT/TRANSFER/RENEW |
| operator | bigint | 操作人 |
| comment | varchar(500)? | 意见 |
| created_at | | 时间 |

## 约束

- 任务并发：UPDATE WHERE status=PENDING 乐观校验。
- 驳回后重提：新 instance，旧标 CANCELED。
