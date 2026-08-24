# 数据模型：工单满意度调查（CSAT/NPS）模块

**Branch**: `051-csat-nps` | **Date**: 2026-08-25

## 1. ticket_survey（工单满意度，Flyway V59）

| 列 | 类型 | 约束 | 说明 |
|---|---|---|---|
| id | BIGINT | PK, AUTO | |
| ticket_id | BIGINT | NOT NULL, FK→ticket | 工单（唯一） |
| rating | INT | NOT NULL | 评分 1-5 |
| comment | VARCHAR(500) | NULL | 评语 |
| created_by | BIGINT | NOT NULL | 提交人 |
| created_at | DATETIME | NOT NULL | 提交时间 |

唯一约束：`uk_survey_ticket`（ticket_id）——一张工单一次评分。

## 2. 业务规则

- 仅 CLOSED 工单可提交评分（OPEN/IN_PROGRESS/RESOLVED → 422 SURVEY_STATE_INVALID）。
- 评分 1-5 整数（422 SURVEY_RATING_INVALID）；评语 ≤500 字。
- 重复评分（409 SURVEY_ALREADY_SUBMITTED）。
- NPS 分档：1-3 贬损 / 4 中立 / 5 推荐；NPS = 推荐% - 贬损%。
- CSAT 均值 = rating 平均分（1-5）。
