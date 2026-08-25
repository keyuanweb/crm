# 数据模型：邮件高级能力模块

**Branch**: `052-email-advanced` | **Date**: 2026-08-25

## 1. email_unsubscribe（邮件退订，Flyway V60）

| 列 | 类型 | 约束 | 说明 |
|---|---|---|---|
| id | BIGINT | PK, AUTO | |
| email | VARCHAR(255) | NOT NULL, UNIQUE | 退订邮箱 |
| campaign_id | BIGINT | NULL | 来源活动（可空） |
| unsubscribed_at | DATETIME | NOT NULL | 退订时间 |

## 2. email_campaign（扩展，V60）

| 列 | 变更 |
|---|---|
| variant | VARCHAR(10) NOT NULL DEFAULT 'NONE' | NONE / A / B |
| subject_b | VARCHAR(255) NULL | B 变体主题（variant=B 时必填） |
| winner | VARCHAR(10) NULL | A / B / NONE（测试后标记更优） |

## 3. email_send_log（扩展，V60）

| 列 | 变更 |
|---|---|
| variant | VARCHAR(10) NULL | 该封邮件所属变体（A/B） |

## 4. 业务规则

- 退订按邮箱唯一；群发与自动化发信排除退订邮箱。
- 统计：打开率 = openCount/sentCount；点击率 = clickCount/sentCount。
- A/B：variant=A/B 时收件人按 id 奇偶分组；统计按变体聚合打开率，标记更优者。
