# 数据模型：邮件营销

## email_template

| 字段 | 类型 | 说明 |
|---|---|---|
| id | bigint | 主键 |
| name | varchar(50) | 模板名（唯一） |
| subject | varchar(200) | 邮件主题（可含变量） |
| content | text | HTML 正文（含变量占位） |
| category | varchar(30) | 分类：WELCOME/PROMOTION/FOLLOW_UP/NOTICE |
| created_by / deleted / version / created_at / updated_at | | 审计 |

## email_campaign

| 字段 | 类型 | 说明 |
|---|---|---|
| id | bigint | 主键 |
| template_id | bigint | 模板 |
| name | varchar(100) | 活动名 |
| source_type | varchar(20) | SEGMENT / CUSTOMER_IDS |
| source_ref | varchar(255) | 细分 id 或客户 id 列表（JSON） |
| total_count / sent_count / failed_count | int | 统计 |
| status | varchar(20) | PENDING/RUNNING/DONE/FAILED |
| created_by / created_at / updated_at | | 审计 |

## email_send_log

| 字段 | 类型 | 说明 |
|---|---|---|
| id | bigint | 主键 |
| campaign_id | bigint | 批次 |
| customer_id | bigint? | 收件客户 |
| email | varchar(100) | 收件邮箱 |
| subject / content | varchar/text | 实际发送内容（渲染后） |
| status | varchar(20) | SENT/FAILED |
| error_message | varchar(255)? | 失败原因 |
| created_at | | 时间 |

## email_track

| 字段 | 类型 | 说明 |
|---|---|---|
| id | bigint | 主键 |
| send_log_id | bigint | 发送记录 |
| track_type | varchar(10) | OPEN / CLICK |
| click_url | varchar(500)? | 点击目标 |
| created_at | | 时间 |

## 约束

- 模板名唯一；正文变量 {name}/{company}/{phone}。
- 打开/点击防重复：同 send_log + type 仅记首次。
