# Tasks: 自定义报表
**Input**: Design documents from `/specs/021-custom-reports/`



**Prerequisites**: plan.md (required), spec.md (required), research.md, data-model.md, contracts/custom-reports.md



**Tests**: 单元测试覆盖 ReportService 各维度聚合逻辑；集成测试覆盖查询/导出/权限；前端测试覆盖报表页渲染与交互

## Phase 1: 基础设施搭建



- [x] T001 [P] 自定义报表 Flyway `backend/src/main/resources/db/migration/V45__report_template.sql` 创建 `report_template` 表（P3，可选）- [x] T002 [P] 自定义报表 DTO `dto/report/ReportQuery.java` 定义 dimension/metric/granularity/startDate/endDate/stageFilter 字段；`dto/report/ReportRow.java` + `dto/report/ReportResult.java` 定义

## Phase 2: 自定义报表后端测试（P1 优先级）

- [x] T003 [P] [US1] 自定义报表测试 `backend/src/test/java/com/crm/service/ReportServiceTest.java` 验证各维度聚合正确性：销售维度按 created_by 分组聚合 quote_item.amount；产品维度按 quote_item.product_id 分组聚合订单金额；线索来源维度按 lead.source 分组聚合线索数量；阶段维度按 opportunity.stage 聚合商机金额；时间维度按创建日期分组聚合。ReportService 需支持数据权限过滤（SALES 仅自身）- [x] T004 [P] [US1] 自定义报表集成测试 `backend/src/test/java/com/crm/integration/CustomReportsIT.java` 验证查询/导出/权限：POST /reports/query 返回聚合结果，非法参数返回 422；GET /reports/export 生成 xlsx 文件，内容与页面一致

## Phase 3: 自定义报表后端实现（P1 优先级）

- [x] T005 [US1] 自定义报表服务 `service/ReportService.java` 实现聚合查询逻辑：支持 SALES/PRODUCT/SOURCE/STAGE/TIME 维度分组，使用 LinkedHashMap 内存分组，返回 rows 列表。需遵循数据权限：SALES 用户仅查询自身数据（created_by=当前用户 ID）- [x] T006 [US1] 自定义报表控制器 `controller/ReportController.java` 实现 REST API：POST /reports/query 返回聚合结果；GET /reports/export 使用 POI 生成 xlsx 文件

## Phase 4: 自定义报表后端实现（P2 / P3 可选）

- [x] T007 [P] [US2] 自定义报表导出 ReportController 实现导出功能：使用 POI 生成 xlsx 文件，包含表头与数据行。支持空报表导出（带表头或提示无数据）- [x] T008 [P] [US3] 自定义报表模板 Flyway V45 表 + `entity/ReportTemplate.java` + `ReportTemplateMapper` + `ReportTemplateService` 实现模板保存/加载/列表/删除。ReportController 提供 CRUD API，仅 ADMIN 可操作

## Phase 5: 前端实现

- [x] T009 [P] [US1] 自定义报表前端 API `types/report.ts` + `services/reportService.ts` 定义 queryReport/exportReport 接口- [x] T010 [US1] 自定义报表前端页面 `pages/reports/ReportCenterPage.tsx` 实现报表页：维度/指标/时间选择器 + 结果表格 + 导出按钮。支持空状态提示、加载状态（Progress 进度条）。导出功能调用后端 API。路由注册到 `App.tsx` 数据分析分组

## Phase 6: 质量检查

- [x] T011 自定义报表后端测试 `mvn test` 验证所有单元测试：ReportServiceTest + CustomReportsIT 通过，覆盖率 ≥ 80%- [x] T012 自定义报表前端测试 `pnpm run typecheck` + `lint` + `test` 验证无类型错误、无 lint 警告、前端测试通过- [x] T013 [P] 人工审查代码质量：关注聚合逻辑正确性、数据权限过滤、异常处理、POI 导出兼容性

## Dependencies & Execution Order



- T001/T002 依赖基础设施搭建，可并行- T003/T004 依赖 T002 DTO 定义，可并行- T005 依赖 T002 DTO，实现聚合服务- T006 依赖 T005 服务，实现控制器- T007 依赖 T006 控制器，实现导出- T008 依赖 T001 Flyway 表，实现模板 CRUD- T009 依赖后端 API 契约，实现前端服务- T010 依赖 T009 前端服务，实现报表页- Phase 6 依赖所有 Phase 完成，执行质量检查

## Notes



- 聚合通过现有 Mapper + 内存分组实现（数据量万级以内），不引入 OLAP- 导出复用 016 的 POI 能力- 模板保存（P3）本期仅实现基础 CRUD，高级功能后续迭代- 数据权限：SALES 用户仅查询自身数据，ADMIN 全量查询
