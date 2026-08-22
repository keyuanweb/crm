# Research: CRM 初始版本技术决策

**Branch**: `001-crm-core` | **Date**: 2026-08-21 | **Spec**: [spec.md](./spec.md)

> Phase 0 输出：解决 Technical Context 中全部未知项与依赖项，为 Phase 1 设计提供依据。
> 格式：Decision / Rationale / Alternatives considered

---

## R1. 认证与授权方案

- **Decision**: Spring Security 6.2 提供 JWT 无状态认证（access token 短期 30 分钟）+ refresh token（长期 7 天，存 Redis，支持登出黑名单）；RBAC 角色模型（ADMIN、SALES、SUPPORT），方法级 `@PreAuthorize` 授权。
- **Rationale**: 章程原则三要求服务端强制授权；前后端分离架构下 JWT 无状态认证避免 session 集群问题；默认账号 admin/admin123 由初始化脚本创建（bcrypt 哈希）。
- **Alternatives considered**:
  - Session Cookie 认证：需维护服务端会话与跨域 Cookie 配置，违背无状态 API 方向，弃用。
  - OAuth2 全流程授权码：单租户内部系统引入 IdP 成本过高，不符合 YAGNI（章程原则五）；JWT 自签足够，预留升级路径。
- **权限矩阵**（基于规格假设）：客户 CRUD 全员；商机/销售机会管理 SALES + ADMIN；商机统计 ADMIN + SALES；导入/导出 ADMIN；跟进记录全员（本角色记录可被本人编辑，管理员可改）。

## R2. 逻辑删除与并发控制

- **Decision**: 客户/商机/销售机会使用 MyBatis-Plus `@TableLogic` 逻辑删除（`deleted` 字段，0 正常 / 1 已删），全局逻辑删除配置；并发控制采用乐观锁（`version` 字段，更新时 `SET version = version+1 WHERE version = ?`，冲突返回 409）。
- **Rationale**: 规格 FR-005 明确逻辑删除且数据可恢复；乐观锁满足规格边界情况"并发修改防静默覆盖"；MyBatis-Plus 原生支持，无额外依赖。
- **Alternatives considered**:
  - 物理删除 + 审计表：恢复成本高，与 FR-005 冲突。
  - 悲观锁（SELECT FOR UPDATE）：高频读场景下锁开销大，仅列表操作无需，弃用。

## R3. 缓存策略（Redis 7）

- **Decision**: Redis 缓存两类数据：(a) 客户详情与商机统计结果（TTL 5 分钟，写操作后主动失效对应 key）；(b) refresh token（key=`refresh:<userId>`，登出时删除实现黑名单）。列表查询不缓存（保证分页/搜索数据实时一致，规避失效风暴）。
- **Rationale**: 规格 SC-002 要求 10 万数据规模下查询 ≤2 秒——列表走 MySQL 索引即可满足，缓存仅用于高频详情与统计聚合；章程原则五要求简洁，避免过度缓存。
- **Alternatives considered**:
  - 列表全量缓存：数据一致性维护复杂（组合筛选键爆炸），弃用。
  - 仅依赖数据库：统计聚合在数据量大时可能超时，保留统计缓存。

## R4. 分页与搜索

- **Decision**: MyBatis-Plus 分页插件（`PaginationInnerInterceptor`），列表统一返回分页信封 `{items, total, page, pageSize}`；关键字搜索对客户名称/公司/联系人做 `LIKE '%kw%'`（配合复合索引 `(deleted, company)`、`(deleted, name)`），搜索词转义。
- **Rationale**: 规格 FR-001 要求分页+搜索+筛选；前缀匹配用索引，中缀匹配在 10 万量级可接受；无 N+1（章程原则五），列表关联字段一次 join 或批量查询。
- **Alternatives considered**:
  - Elasticsearch 全文检索：10 万量级引入重型依赖，违反 YAGNI，弃用。
  - 仅 DB 端分页 offset：数据量增长后深分页变慢，初始版本可接受（记录为后续优化项）。

## R5. 商机/销售机会父子模型落库

- **Decision**: 两张表，`opportunity`（父）与 `sales_opportunity`（子，含 `opportunity_id` FK）。阶段与关闭结果仅存在于 `sales_opportunity`；商机统计按子表阶段聚合。
- **Rationale**: 规格 Q1 已确认选项 C（一个商机可产生多个销售机会）；父表承载聚合信息（预期金额范围、备注），子表承载具体销售尝试（金额、阶段、预计成交、关闭结果），符合规格 FR-007~014 与关键实体定义。
- **Alternatives considered**:
  - 单表自关联：阶段语义混淆，弃用。
  - 三表（中间表）：当前无多对多需求，弃用。

## R6. Excel 导入导出

- **Decision**: 后端 Apache POI 处理 .xlsx（客户导入校验必填字段与格式，逐行报告成功/失败明细；导出按当前筛选条件生成）；提供 Excel 模板下载接口（GET /api/v1/customers/import-template）；导入采用事务分批（每 500 行一批），单条失败不阻塞整批。
- **Rationale**: 规格 FR-006 要求 Excel 导入导出并报告错误明细；POI 是 Java 生态标准；逐行校验满足边界情况要求。
- **Alternatives considered**:
  - 前端 SheetJS 处理：大文件导入在浏览器内存受限、校验逻辑难复用后端规则，弃用（前端仅做模板下载入口）。
  - CSV：用户体验与中文兼容性差于 .xlsx，弃用。

## R7. 契约优先落地（章程原则一）

- **Decision**: 采用 springdoc-openapi（Swagger 3.0）以注解定义并暴露 OpenAPI 契约（`/v3/api-docs`、`/swagger-ui`）；前端 `types/` 与 `services/` 按契约手写对齐（v1 由人工同步，后续可引入 openapi-generator 生成）；Phase 1 的 `contracts/` 目录为契约的权威文档源。
- **Rationale**: 章程原则一（不可协商）要求契约先于实现、前后端同源；当前团队规模下单次功能开发手写对齐成本可控，生成器留待契约稳定后引入（YAGNI）。
- **Alternatives considered**:
  - openapi-generator 全自动生成前后端类型：v1 阶段契约仍在演化，生成物维护成本高，延后。
  - 手工文档不落 OpenAPI：无法满足"契约可测试"，弃用。

## R8. 数据库迁移

- **Decision**: Flyway 版本化迁移（`src/main/resources/db/migration/V1__init.sql` 等），启动时自动执行；schema 变更必须新增迁移脚本，禁止修改已发布脚本。
- **Rationale**: 章程技术与架构约束明确要求"版本化 schema 迁移（Flyway/Liquibase），schema 变更附带迁移计划"；与 MyBatis-Plus 兼容良好。
- **Alternatives considered**:
  - Liquibase：功能等价，Flyway 对纯 SQL 迁移更简洁，选用 Flyway。
  - 手动 SQL 脚本：无版本控制与可重复执行保障，弃用。

## R9. 测试策略

- **Decision**: 后端——Service 层 JUnit 5 单元测试（Mockito）、Repository/API 层 `@SpringBootTest` 集成测试（H2 或 Testcontainers MySQL）、契约测试（校验 OpenAPI 契约与实现一致，如 spring-restdocs/自动契约断言）；前端——Vitest + React Testing Library 组件/逻辑测试；端到端——Playwright 覆盖 quickstart 验证场景；合并门禁：`mvn test` + `pnpm run test` + lint + 类型检查 + 覆盖率门槛。
- **Rationale**: 章程原则四（不可协商）要求测试先于实现、合并门禁；测试金字塔：大量单测、少量集成、极少量 e2e。
- **Alternatives considered**:
  - 仅集成测试：速度慢、定位难，违反金字塔原则，弃用。
  - 无契约测试：违反章程原则一"每次契约变更附带测试"，弃用。

## R10. 商机统计报表

- **Decision**: `GET /api/v1/stats/opportunity-pipeline` 按 sales_opportunity.stage 分组聚合数量与金额合计（`SUM(amount)`、`COUNT(*)`），结果缓存 Redis（TTL 5 分钟）；关闭（WON/LOST）计入对应终态阶段；统计接口与列表接口共享同一数据源保证一致性（规格 SC-006）。
- **Rationale**: 规格 FR-011/用户故事 6 要求按阶段汇总；聚合查询走索引（stage + deleted），10 万量级内秒级；缓存缓解热点读。
- **Alternatives considered**:
  - 预聚合表：初始量级过度设计，弃用。
  - 实时全表扫描：量级增长后不可控，统计缓存已覆盖。

---

## 结论

全部未知项已解决，无遗留 NEEDS CLARIFICATION；Phase 0 完成，进入 Phase 1（data-model.md、contracts/、quickstart.md）。
