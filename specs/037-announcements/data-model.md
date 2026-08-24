# 数据模型：公告与内部协作

## announcement

| 字段 | 类型 | 说明 |
|---|---|---|
| id | bigint | 主键 |
| title | varchar(100) | 标题 |
| content | text | 富文本正文 |
| pinned | tinyint | 置顶 |
| expires_at | datetime? | 过期时间（空 = 永久） |
| created_by / deleted / version / created_at / updated_at | | 审计 |

## announcement_read

| 字段 | 类型 | 说明 |
|---|---|---|
| id | bigint | 主键 |
| announcement_id | bigint | 公告 |
| user_id | bigint | 用户 |
| read_at | datetime | 时间 |

## comment

| 字段 | 类型 | 说明 |
|---|---|---|
| id | bigint | 主键 |
| entity_type | varchar(20) | CUSTOMER/LEAD/OPPORTUNITY/TICKET |
| entity_id | bigint | 业务 id |
| content | varchar(1000) | 评论内容（含 @提及） |
| author_id | bigint | 作者 |
| deleted / created_at / updated_at | | 审计 |

## 约束

- announcement_read 唯一（announcement_id, user_id）。
- 评论删除仅作者/管理员（逻辑删）。
