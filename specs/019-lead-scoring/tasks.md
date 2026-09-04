# Tasks: 智能线索评分与销售预测校准
**Input**: Design documents from `/specs/019-lead-scoring/`



**Prerequisites**: plan.md (required), spec.md (required), research.md, data-model.md, contracts/lead-scoring.md



**Tests**: 线索评分单元测试、预测校准单元测试、前后端集成测试、评分渲染与排序验证

## Phase 1: 基础设施搭建



- [x] T001 [P] 编写 Flyway `backend/src/main/resources/db/migration/V43__lead_score_config.sql` 创建 `lead_score_config` 表 + 默认评分规则种子（SOURCE 渠道 REFERRAL 30 / INFO 30 / FOLLOWUP 25 / FRESHNESS 15）
- [x] T002 [P] 编写配置实体 `entity/LeadScoreConfig.java`（添加 Lombok @TableName）+ `repository/LeadScoreConfigMapper.java`

## Phase 2: 后端测试

- [x] T003 [P] [US1] 编写单元测试 `backend/src/test/java/com/crm/service/LeadScoreServiceTest.java` 验证评分维度（来源渠道/信息完整度/跟进活跃度/互动时效），测试 REFERRAL+信息完整得高分、COLD_CALL+信息不全得低分（<40，红色），跟进后评分上升
- [x] T004 [P] [US2] 编写单元测试 `backend/src/test/java/com/crm/service/StageConversionServiceTest.java` 验证转化率计算（历史样本充足用历史值、不足回退默认），验证 CLOSED_WON=1.0 / CLOSED_LOST=0.0 固定不校准

## Phase 3: 后端聚合服务实现 (P1)

- [x] T005 [US1] 编写实现 `service/LeadScoreService.java` 实现评分引擎（SOURCE/INFO/FOLLOWUP/FRESHNESS 维度加权），总分 0-100，自动写回 lead.score（通过 scoreAndUpdate(Lead)）依赖 T001/T002
- [x] T006 [US1] 修改 LeadService.create()` 和 `update()` 调用 `LeadScoreService.scoreAndUpdate`；修改 FollowUpService.create()` 后根据 leadId 触发线索重算依赖 T005
- [x] T007 [US1] 修改 LeadService.page()` 支持评分排序（`sortBy=score&order=desc`），默认按评分降序

## Phase 4: 后端预测校准实现 (P2)

- [x] T008 [US2] 编写实现 `service/StageConversionService.java` 实现转化率统计（从 sales_opportunity 表聚合各阶段数据），样本<10 回退默认概率，CLOSED_WON=1.0/CLOSED_LOST=0.0 固定不校准，Redis 短缓存 5 分钟
- [x] T009 [US2] 修改 DashboardStatsService.computeForecast()` 使用 StageConversionService 校准概率，ForecastItem 增加 `probabilitySource` 字段（HISTORICAL/DEFAULT/FIXED）依赖 T008

## Phase 5: 前端实现

- [x] T010 [P] [US1] 修改 `types/lead.ts` 添加评分相关类型（score 字段）+ `pages/leads/LeadListPage.tsx` 支持评分排序（`sortBy=score&order=desc`），红/黄/绿 Tag 标识（<40/70 阈值）
- [x] T011 [P] [US2] 修改 `types/stats.ts` ForecastItem 增加 `probabilitySource` 字段 + DashboardPage` 显示预测校准来源（历史计算/默认回退）

## Phase 6: 质量检查

- [x] T012 运行 `mvn test` 验证后端测试（LeadScoreServiceTest + StageConversionServiceTest + LeadScoringIT）覆盖率>80%
- [x] T013 运行 `pnpm run typecheck` + `lint` + `test` 验证前端
- [x] T014 [P] 人工审查评分规则与 spec.md 一致性，验证预测校准逻辑正确性

## Dependencies & Execution Order


- T001/T002 依赖基础设施（无前置）
- T003/T004 依赖后端测试（T001/T002）
- T005 依赖 T001/T002
- T006 依赖 T005
- T007 依赖 T006
- T008 依赖后端测试
- T009 依赖 T008
- T010/T011 依赖前端
- Phase 6 依赖所有 Phase 完成

## Notes


- 评分采用规则引擎（非 ML），`lead_score_config` 表存储可配置规则，评分实时计算写回 lead.score
- 预测校准使用历史转化率统计 + Redis 5 分钟短缓存，CLOSED_WON/LOST 固定 1.0/0.0 不参与校准
- 前端排序复用 Lead.score 字段（004 已定义），评分规则变更立即生效
