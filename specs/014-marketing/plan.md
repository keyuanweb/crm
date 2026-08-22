# Implementation Plan: 市场营销模块

**Branch**: `014-marketing` | **Date**: 2026-08-22 | **Spec**: [spec.md](./spec.md)

## Summary

新增营销活动（MarketingCampaign）实体：活动 CRUD/状态流转（PLANNING→RUNNING→ENDED）/分页筛选；线索与客户新增 campaign_id（营销归因，创建时可选）；渠道 ROI 统计（按渠道聚合活动数/成本/归因线索数/客户数/线索转化率/ROI=预估收益/成本，收益=归因客户商机 max 金额合计）。前端新增活动管理页与渠道 ROI 报表页。

## Technical Context

**Language/Version**: Java 17 / TypeScript 5（沿用既有技术栈）

**Primary Dependencies**: MyBatis-Plus、Spring Validation、AuditService

**Storage**: MySQL 新增 `marketing_campaign` 表；`lead`/`customer` 表新增 campaign_id 列（Flyway V32~V33）

**Testing**: JUnit 5 + Spring Boot Test（CampaignServiceTest 单元、MarketingIT 集成）

**Target Platform**: Web（活动管理页 + 渠道 ROI 报表页）

**Project Type**: 现有 Web 应用新增模块

**Performance Goals**: 活动列表/创建 ≤1s（SC-M01）

**Constraints**: 渠道枚举固定；状态流转单向；成本/预算 ≥0；结束日期 ≥ 开始日期；有归因活动不可删（409）；归因计数/ROI 口径明确

**Scale/Scope**: 活动 ≤ 数百；归因数据 ≤ 数千

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| 章程条款 | 门禁要求 | 状态 |
|---|---|---|
| 原则二：分层架构与关注点分离 | Controller→Service→持久层 | ✅ 满足（复用既有分层） |
| 原则三：数据完整性、安全与校验 | DTO 校验、服务端授权 | ✅ 满足（Bean Validation + @PreAuthorize） |
| 原则四：测试优先与质量门禁 | 测试先于实现、合并门禁 | ✅ 满足（单元/集成测试 + 既有门禁） |
| 原则五：简洁、可维护与可观测 | 无 N+1、审计 | ✅ 满足（批量装配 + AuditService） |

**结论**: 无门禁违规。

## Project Structure

```text
specs/014-marketing/
├── spec.md / plan.md / tasks.md / research.md / data-model.md / quickstart.md
├── contracts/marketing.md

backend/src/main/java/com/crm/
├── entity/MarketingCampaign.java + repository/MarketingCampaignMapper.java
├── entity/Lead.java（新增 campaignId）+ entity/Customer.java（新增 campaignId）
├── dto/marketing/（CampaignRequest/CampaignResponse/ChannelRoiResponse）
├── service/MarketingCampaignService.java（CRUD/状态流转/归因计数/删除防护）
├── service/MarketingRoiService.java（渠道 ROI 聚合）
├── controller/MarketingController.java
├── common/ErrorCode.java（新增 CAMPAIGN_* 错误码）
└── resources/db/migration/V32__marketing_campaign.sql + V33__attribution_columns.sql

backend/src/test/java/com/crm/
├── service/MarketingCampaignServiceTest.java + MarketingRoiServiceTest.java
├── integration/MarketingIT.java

frontend/src/
├── types/marketing.ts + services/marketingService.ts
├── pages/marketing/CampaignListPage.tsx（活动管理，含新增/编辑/状态）
├── pages/marketing/ChannelRoiPage.tsx（渠道 ROI 报表）
├── 线索/客户创建弹窗增加"营销活动"选择
└── App.tsx（营销菜单 + 路由）
```

**Structure Decision**: 沿用既有前后端目录结构（Option 2 Web 应用）。归因通过 Lead/Customer 新增 campaign_id 实现（创建请求扩展）；ROI 聚合独立 `MarketingRoiService`。

## Complexity Tracking

无违规，本表留空。
