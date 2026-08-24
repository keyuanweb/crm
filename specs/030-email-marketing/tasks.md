# Tasks: 邮件营销触达

**Input**: Design documents from `/specs/030-email-marketing/`

**Prerequisites**: plan.md (required), spec.md (required), research.md, data-model.md, contracts/email-marketing.md

**Tests**: 章程原则四要求测试先于实现（红→绿），本功能含后端单元/集成 + 前端测试。

## Phase 1: 基础设施

- [x] T001 [P] [US1] 后端：`db/migration/V48__email_marketing.sql`——email_template/email_campaign/email_send_log/email_track 四表。
- [x] T002 [P] [US1] 后端：实体 4 个 + Mapper 4 个；`MailConfig`（SMTP 环境变量，无 SMTP 注入 null）；pom 加 spring-boot-starter-mail。
- [x] T003 [P] [US1] 后端：RoleConstants 权限字典加 `email:manage`。

## Phase 2: 后端测试先行（TDD 红）

- [x] T004 [P] [US1] 后端：`EmailTemplateServiceTest`——模板 CRUD、变量渲染（{name}/{company}/{phone}）。红阶段。
- [x] T005 [P] [US2] 后端：`EmailCampaignServiceTest`——创建群发（SEGMENT/CUSTOMER_IDS 收件人）、邮箱空跳过、状态统计。红阶段。
- [x] T006 [P] [US1] 后端：`integration/EmailIT.java`——模板 CRUD→建群发→发送记录→打开/点击追踪端点。红阶段。

## Phase 3: 后端实现

- [x] T007 [US1] 后端：DTO（EmailTemplateRequest/Response、CampaignRequest/Response）。
- [x] T008 [US1] 后端：`EmailTemplateService`——模板 CRUD + 变量渲染 + 预览。（依赖 T002）
- [x] T009 [US2] 后端：`EmailCampaignService`——创建群发（收件人解析：SEGMENT 复用 031 / CUSTOMER_IDS）+ @Async 异步发送队列（无 SMTP dev 模拟 SENT）+ 测试发送 + 统计 + 打开/点击记录。（依赖 T002/T008 + 031 SegmentService）
- [x] T010 [US1] 后端：`EmailController`（模板 + 群发端点）+ `EmailTrackController`（公开 open/click 追踪）。（依赖 T008/T009）

## Phase 4: 前端

- [x] T011 [P] [US1] 前端：`types/email.ts` + `emailService.ts`。
- [x] T012 [US1] 前端：`EmailTemplatePage`（模板管理：CRUD + 变量提示 + 预览）。（依赖 T011）
- [x] T013 [US2] 前端：`EmailCampaignPage`（群发：选模板 + 收件人细分/客户 + 测试发送 + 发送记录 + 打开/点击统计）；App.tsx 营销分组注册路由。（依赖 T012）

## Phase 5: 验证与收尾

- [x] T014 后端：`mvn test` 全量通过；H2 schema 同步新表。
- [x] T015 前端：`pnpm run typecheck` + `lint` + `test` 全量通过。
- [x] T016 [P] 手动冒烟：建模板→建群发（细分收件人）→发送记录→打开/点击追踪。

## Dependencies & Execution Order

- T001/T002/T003 可并行。
- T004/T005/T006 可并行，均红阶段；依赖 T001-T003。
- T007 依赖 T002；T008 依赖 T007；T009 依赖 T008 + 031；T010 依赖 T008/T009。
- T011 无依赖；T012 依赖 T011；T013 依赖 T012。
- Phase 5 完成后收尾。

## Notes

- 无 SMTP dev：发送标记 SENT（日志模拟），不阻塞。
- 收件人 SEGMENT 复用 031 细分成员；CUSTOMER_IDS 查客户邮箱。
- 追踪端点公开（防爬频控）；打开/点击防重复。
- 变量 {name}/{company}/{phone}。
