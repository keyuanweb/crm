# 数据模型：外勤拜访

## field_visit

| 字段 | 类型 | 说明 |
|---|---|---|
| id | bigint | 主键 |
| customer_id | bigint | 客户 |
| theme | varchar(100) | 拜访主题 |
| visit_time | datetime | 计划拜访时间 |
| duration_minutes | int? | 预计时长（分） |
| status | varchar(20) | PLANNED/DONE/CANCELED |
| latitude | decimal(10,7)? | 签到纬度 |
| longitude | decimal(10,7)? | 签到经度 |
| location_text | varchar(255)? | 签到地址/坐标文本 |
| check_in_time | datetime? | 签到时间 |
| summary | varchar(1000)? | 拜访小结（签到后填） |
| late_flag | tinyint? | 补签标记 |
| created_by / deleted / version / created_at / updated_at | | 审计 |

## 约束

- 签到防重复：仅 PLANNED 可签到（乐观 UPDATE）。
- 小结自动写 follow_up（type=VISIT）。
