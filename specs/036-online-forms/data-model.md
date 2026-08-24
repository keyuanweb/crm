# 数据模型：在线表单

## form

| 字段 | 类型 | 说明 |
|---|---|---|
| id | bigint | 主键 |
| name | varchar(50) | 表单名 |
| fields | text | JSON 字段配置 |
| success_message | varchar(200)? | 提交成功提示 |
| source | varchar(30) | 线索来源（默认 WEBSITE） |
| status | varchar(20) | ENABLED/DISABLED |
| submission_count | int | 提交数（冗余统计） |
| created_by / deleted / version / created_at / updated_at | | 审计 |

## form_submission

| 字段 | 类型 | 说明 |
|---|---|---|
| id | bigint | 主键 |
| form_id | bigint | 表单 |
| payload | text | 提交字段快照 JSON |
| client_ip | varchar(45)? | 提交 IP |
| lead_id | bigint? | 生成的线索 |
| created_at | | 时间 |

## 约束

- 字段映射 lead：name/company/phone/email；其余存 payload。
- 防重复：email/phone 已有未删除 lead 拦截。
- 频控：同 IP 1 分钟 ≤3 次。
