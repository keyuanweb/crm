# Tasks: AI 智能助手（规则型智能建议）
**Input**: Design documents from `/specs/022-ai-assistant/`



**Prerequisites**: plan.md (required), spec.md (required), research.md, data-model.md, contracts/smart-suggestions.md



**Tests**: 单元测试（SuggestionServiceTest）、集成测试（SmartSuggestionIT）、前端渲染测试（SuggestionCenterPage）

## Phase 1: 基础设施搭建



- [x] T001 [P] 创建 DTO 类：`dto/suggestion/SmartSuggestion.java` 定义 type/title/reason/priority/entityType/entityId/action 字段； `dto/suggestion/SuggestionSummary.java` 定义各类型计数（首页摘要）

## Phase 2: 后端测试

- [x] T002 [P] [US1] 创建单元测试：`backend/src/test/java/com/crm/service/SuggestionServiceTest.java` 测试规则聚合、去重、排序、忽略过滤逻辑。验证：流失预警 > 停滞商机 > 高分线索优先级；已跟进客户不再出现；忽略记录过滤生效；SuggestionService 聚合 CustomerService.atRiskCustomers、DashboardStatsService 停滞预警、LeadScoreService 高分线索。
- [x] T003 [P] [US1] 创建集成测试：`backend/src/test/java/com/crm/integration/SmartSuggestionIT.java` 验证 GET /suggestions 返回建议列表；POST /suggestions/{type}/{entityId}/ignore 忽略建议；GET /suggestions/summary 返回首页摘要计数。

## Phase 3: 后端聚合服务实现（P1 核心逻辑）

- [x] T004 [US1] 创建聚合服务：`service/SuggestionService.java` 实现四类规则聚合（客户流失预警复用 CustomerService.atRiskCustomers、商机停滞预警复用 DashboardStatsService、待跟进客户复用 FollowUpMapper、高分线索待处理复用 LeadMapper+LeadScoreService），去重（已跟进/已推进实体不再出现）、优先级排序（entityId 去重取最高优先级）、忽略过滤（Redis `ai:ignore:<userId>:<type>:<entityId>`），上限 20 条。
- [x] T005 [US1] 创建控制器：`controller/SuggestionController.java` 实现 GET /suggestions、GET /suggestions/summary、POST /suggestions/{type}/{entityId}/ignore` 接口；Redis 忽略记录 TTL 90 天。

## Phase 4: 后端聚合服务实现（P2/US3 接口）

- [x] T006 [P] [US2] 创建忽略记录服务：Redis SET + TTL 实现忽略记录；Redis 集合判断忽略状态；忽略记录 key 格式 `ai:ignore:<userId>:<type>:<entityId>`。
- [x] T007 [P] [US3] 创建摘要接口：`GET /suggestions/summary` 返回各类建议计数（流失预警数、停滞商机数、高分线索数）。

## Phase 5: 前端实现

- [x] T008 [P] [US1] 创建前端类型和服务：`types/suggestion.ts` + `services/suggestionService.ts` 定义 fetchSuggestions/ignoreSuggestion/fetchSummary 接口。
- [x] T009 [US1] 创建建议列表页：`pages/assistant/SuggestionCenterPage.tsx` 实现智能建议列表页，展示类型/原因/优先级，Tag 标签、跳转详情、忽略按钮。
- [x] T010 [US3] 修改 `DashboardPage` 添加 AI 智能建议摘要卡片：首页展示"X 个流失预警、Y 个停滞商机、Z 个高分线索"计数。
- [x] T009 [US1] 修改 `App.tsx` 注册建议路由（数据分析分组）。

## Phase 6: 质量检查

- [x] T011 运行后端测试：`mvn test` 验证单元测试通过率；SuggestionServiceTest + SmartSuggestionIT 全部通过。
- [x] T012 运行前端检查：`pnpm run typecheck` + `lint` + `test` 验证前端无错误。
- [x] T013 [P] 人工审查：验证建议列表排序正确、去重逻辑生效、忽略功能正常、数据权限隔离正确。

## Dependencies & Execution Order


- T001 是后续所有任务的基础
- T002/T003 依赖 T001 完成后并行执行
- T004 依赖 T001
- T005 依赖 T004
- T006 依赖 T005
- T007 依赖 T004
- T008/T009/T010 依赖后端接口完成后并行执行
- Phase 6 在所有 Phase 1-5 完成后执行

## Notes


- 智能建议为规则引擎（非 LLM），基于 018 健康度评分、019 线索评分、停滞预警、跟进时间生成
- 忽略记录存 Redis，key `ai:ignore:<userId>:<type>:<entityId>`，TTL 90 天
- 建议上限 20 条可配置
- SALES 用户仅见本人数据
