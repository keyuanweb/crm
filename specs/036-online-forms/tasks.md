# Tasks: 在线表单线索收集

**Input**: Design documents from `/specs/036-online-forms/`

**Prerequisites**: plan.md (required), spec.md (required), research.md, data-model.md, contracts/online-forms.md

**Tests**: 章程原则四要求测试先于实现（红→绿），本功能含后端单元/集成 + 前端测试。

## Phase 1: 基础设施

- [x] T001 [P] [US1] 后端：`db/migration/V51__online_forms.sql`——form + form_submission 表。
- [x] T002 [P] [US1] 后端：实体 Form/FormSubmission + 2 Mapper。
- [x] T003 [P] [US1] 后端：RoleConstants 权限字典加 `form:manage`。

## Phase 2: 后端测试先行（TDD 红）

- [x] T004 [P] [US1] 后端：`FormServiceTest`——表单 CRUD、提交建线索（字段映射/来源）、必填校验、防重复（email/phone 已有线索）、频控超限。红阶段。
- [x] T005 [P] [US1] 后端：`integration/FormIT.java`——建表单→公开提交→线索生成→重复提交拦截。红阶段。

## Phase 3: 后端实现

- [x] T006 [US1] 后端：DTO（FormRequest/FormResponse/SubmitRequest/FormSubmissionResponse）。
- [x] T007 [US1] 后端：`FormService`——表单 CRUD + toggle + 公开提交（字段校验/长度/防重复/内存频控/建 lead（source=表单）/submission 快照 + IP）。（依赖 T002/T006 + LeadMapper）
- [x] T008 [US1] 后端：`FormController`——/api/v1/forms（管理 CRUD/toggle/submissions）+ /api/v1/public/forms/{id}/submit（公开，禁 JWT）。（依赖 T007）

## Phase 4: 前端

- [x] T009 [P] [US1] 前端：`types/form.ts` + `formService.ts`。
- [x] T010 [US1] 前端：`OnlineFormPage`（表单管理：列表 + 字段配置器（field/label/type/required 行编辑）+ 外链复制 + 提交记录 Drawer）。（依赖 T009）
- [x] T011 [US1] 前端：公开提交页 `/f/:id`（动态渲染字段 + 提交 + 成功提示）；App.tsx 路由。（依赖 T010）

## Phase 5: 验证与收尾

- [x] T012 后端：`mvn test` 全量通过；H2 schema 同步新表。
- [x] T013 前端：`pnpm run typecheck` + `lint` + `test` 全量通过。
- [x] T014 [P] 手动冒烟：建表单→公开提交→线索池（来源）→重复提交拦截→提交记录。

## Dependencies & Execution Order

- T001/T002/T003 可并行。
- T004/T005 可并行，均红阶段；依赖 T001/T002。
- T006 依赖 T002；T007 依赖 T006；T008 依赖 T007。
- T009 无依赖；T010 依赖 T009；T011 依赖 T010。
- Phase 5 完成后收尾。

## Notes

- 公开端点匿名可提交（白名单/长度/频控/防注入）。
- 防重复：email/phone 已有未删除 lead 拦截 409。
- 频控：内存 IP 窗口 1 分钟 ≤3 次（429）。
- 权限 form:manage 入 028 字典。
