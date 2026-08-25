# 数据模型：托管落地页 + UTM 跟踪模块

**Branch**: `053-landing-page` | **Date**: 2026-08-25

## 1. landing_page（落地页，Flyway V62）

| 列 | 类型 | 约束 | 说明 |
|---|---|---|---|
| id | BIGINT | PK, AUTO | |
| title | VARCHAR(200) | NOT NULL | 标题 |
| subtitle | VARCHAR(500) | NULL | 副标题 |
| description | TEXT | NULL | 描述 |
| theme_color | VARCHAR(20) | NULL | 品牌色（#RRGGBB） |
| form_id | BIGINT | NOT NULL | 关联在线表单 |
| enabled | TINYINT | NOT NULL DEFAULT 1 | 启用 |
| created_by | BIGINT | NULL | |
| created_at / updated_at | DATETIME | NOT NULL | |

## 2. form_submission（扩展，V62）

| 列 | 变更 |
|---|---|
| utm_source | VARCHAR(100) NULL |
| utm_medium | VARCHAR(100) NULL |
| utm_campaign | VARCHAR(100) NULL |
| utm_term | VARCHAR(100) NULL |
| utm_content | VARCHAR(100) NULL |

## 3. 业务规则

- 落地页关联表单须 ENABLED 才可公开展示；落地页/表单任一停用 → 提示不可用。
- UTM 从公开提交 URL 查询字符串解析；无参数则字段为空。
- 统计按 utm_source / utm_campaign 聚合提交数。
