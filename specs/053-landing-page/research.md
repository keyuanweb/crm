# Research: 托管落地页 + UTM 跟踪模块

**Branch**: `053-landing-page` | **Date**: 2026-08-25

## 1. 落地页模型

**Decision**: `landing_page` 表（title/subtitle/description/theme_color/form_id/enabled）。公开访问 `/api/v1/public/lp/{id}` 返回落地页+表单元数据，前端渲染；表单提交复用 036 公开提交端点（扩展捕获 UTM）。

**Rationale**: 落地页是表单的展示壳（营销内容 + 转化入口），复用 036 提交链路最小侵入。

**Alternatives considered**: 独立提交端点——重复 036 逻辑；拖拽编辑器——复杂度高，v1 不做。

## 2. UTM 捕获

**Decision**: FormSubmission 加 5 个 UTM 列（utm_source/medium/campaign/term/content 可空）。公开表单提交端点从查询字符串解析 `utm_*` 参数存入。无参数则空（不报错）。

**Rationale**: 查询字符串解析零前端改动（表单提交 URL 带 ?utm_*=... 即可）；列存储便于统计聚合。

## 3. UTM 统计

**Decision**: `GET /api/v1/landing-pages/{id}/stats?from&to` 按 utm_source 与 utm_campaign 聚合提交数（GROUP BY）。返回维度列表。

**Rationale**: 提交数归因满足 v1（转化率/收入归因留待报表模块）。
