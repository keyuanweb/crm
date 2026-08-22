# Tasks: 市场营销模块

**Input**: Design documents from `/specs/014-marketing/`

**Prerequisites**: plan.md (required), spec.md (required), research.md, data-model.md, contracts/

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel
- **[Story]**: US1/US2/US3

---

## Phase 1: 数据模型与基础

- [x] T001 [P] 创建 Flyway 迁移 V32 in `backend/src/main/resources/db/migration/V32__marketing_campaign.sql`（marketing_campaign 表 + 索引）
- [x] T002 [P] 创建 Flyway 迁移 V33 in `backend/src/main/resources/db/migration/V33__attribution_columns.sql`（lead/customer 表 campaign_id 列 + 索引）
- [x] T003 [P] 创建 MarketingCampaign 实体/Mapper in `backend/src/main/java/com/crm/entity/MarketingCampaign.java` + `backend/src/main/java/com/crm/repository/MarketingCampaignMapper.java`
- [x] T004 [P] Lead/Customer 实体新增 campaignId 字段 in `backend/src/main/java/com/crm/entity/Lead.java` + `backend/src/main/java/com/crm/entity/Customer.java`
- [x] T005 [P] H2 测试 schema 同步（campaign 表 + lead/customer campaign_id）in `backend/src/test/resources/schema-h2.sql`
- [x] T006 [P] ErrorCode 新增 CAMPAIGN_NOT_FOUND/CAMPAIGN_INVALID_STATE/CAMPAIGN_HAS_ATTRIBUTION in `backend/src/main/java/com/crm/common/ErrorCode.java`

**Checkpoint**: 数据模型就绪

---

## Phase 2: 用户故事 1 - 营销活动管理 (P0) 🎯 MVP

**Goal**: 活动 CRUD + 状态流转 + 分页筛选。

**Independent Test**: 建活动→列表→编辑→开始→结束。

### 实现

- [x] T007 [P] [US1] 创建 CampaignRequest/CampaignResponse DTO in `backend/src/main/java/com/crm/dto/marketing/`
- [x] T008 [US1] 创建 MarketingCampaignService in `backend/src/main/java/com/crm/service/MarketingCampaignService.java`（CRUD/状态流转/日期校验/审计）
- [x] T009 [US1] 创建 MarketingController in `backend/src/main/java/com/crm/controller/MarketingController.java`（GET 列表/POST/PUT/DELETE、start/end；写 ADMIN+SALES）
- [x] T010 [US1] 创建 MarketingCampaignServiceTest 单元测试 in `backend/src/test/java/com/crm/service/MarketingCampaignServiceTest.java`（CRUD/状态流转非法 409/日期校验）
- [x] T011 [US1] 创建 MarketingIT 集成测试（活动部分）in `backend/src/test/java/com/crm/integration/MarketingIT.java`
- [x] T012 [US1] 前端类型/服务 in `frontend/src/types/marketing.ts` + `frontend/src/services/marketingService.ts`
- [x] T013 [US1] 活动管理页 in `frontend/src/pages/marketing/CampaignListPage.tsx`（ProTable + 新增/编辑/开始/结束/删除）

**Checkpoint**: US1 可用——活动管理

---

## Phase 3: 用户故事 2 - 营销归因 (P0)

**Goal**: 线索/客户创建可关联活动；活动详情展示归因计数。

**Independent Test**: 建活动→建线索选活动→活动 leadCount=1。

### 实现

- [x] T014 [US2] LeadRequest/CustomerRequest 新增 campaignId（可选）in `backend/src/main/java/com/crm/dto/lead/LeadRequest.java` + `backend/src/main/java/com/crm/dto/customer/CustomerRequest.java`
- [x] T015 [US2] LeadService.create/CustomerService.create 应用 campaignId（校验活动存在）in `backend/src/main/java/com/crm/service/LeadService.java` + `backend/src/main/java/com/crm/service/CustomerService.java`
- [x] T016 [US2] LeadService.convert 转化时 campaign_id 带入客户 in `backend/src/main/java/com/crm/service/LeadService.java`
- [x] T017 [US2] MarketingCampaignService 增加归因计数（leadCount/customerCount）in `backend/src/main/java/com/crm/service/MarketingCampaignService.java`
- [x] T018 [US2] MarketingCampaignService 删除防护（有归因 409）in `backend/src/main/java/com/crm/service/MarketingCampaignService.java`
- [x] T019 [US2] MarketingCampaignServiceTest 增加归因/删除防护用例 in `backend/src/test/java/com/crm/service/MarketingCampaignServiceTest.java`
- [x] T020 [US2] MarketingIT 增加归因用例（建线索选活动→计数→转化带入→删除 409）in `backend/src/test/java/com/crm/integration/MarketingIT.java`
- [x] T021 [US2] 前端线索/客户创建弹窗增加"营销活动"选择 in `frontend/src/pages/leads/LeadListPage.tsx` + `frontend/src/pages/customers/CustomerListPage.tsx`

**Checkpoint**: US2 可用——营销归因

---

## Phase 4: 用户故事 3 - 渠道 ROI 统计 (P1)

**Goal**: 按渠道聚合 ROI。

**Independent Test**: 多渠道数据→ROI 正确→除零保护。

### 实现

- [x] T022 [P] [US3] 创建 ChannelRoiResponse DTO in `backend/src/main/java/com/crm/dto/marketing/`
- [x] T023 [US3] 创建 MarketingRoiService in `backend/src/main/java/com/crm/service/MarketingRoiService.java`（按渠道聚合活动/成本/归因线索/客户/转化率/收益/ROI，除零保护）
- [x] T024 [US3] MarketingController 增加 GET /campaigns/channel-roi in `backend/src/main/java/com/crm/controller/MarketingController.java`
- [x] T025 [US3] 创建 MarketingRoiServiceTest 单元测试 in `backend/src/test/java/com/crm/service/MarketingRoiServiceTest.java`（聚合/ROI 计算/除零）
- [x] T026 [US3] MarketingIT 增加 ROI 用例 in `backend/src/test/java/com/crm/integration/MarketingIT.java`
- [x] T027 [US3] 前端渠道 ROI 页 in `frontend/src/pages/marketing/ChannelRoiPage.tsx`（表格：渠道/活动数/成本/线索/客户/转化率/收益/ROI）

**Checkpoint**: US3 可用——渠道 ROI

---

## Phase 5: 收尾与验证

- [x] T028 前端路由与菜单 in `frontend/src/App.tsx`（营销菜单 + 活动/ROI 路由）
- [x] T029 Backend `mvn verify` 通过（含新增测试、spotless、JaCoCo）
- [x] T030 单独运行 `mvn test "-Dtest=MarketingIT"` 通过
- [x] T031 Frontend typecheck / lint / test / build 通过
- [x] T032 线上端点验证（活动 CRUD→状态流转→归因→计数→ROI→删除防护→权限 403）
- [x] T033 更新契约文档（按实现校正）与 roadmap 014 标记 `[x]`

**Checkpoint**: 模块完整可用
