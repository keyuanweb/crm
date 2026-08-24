# Tasks: 公告与内部协作

**Input**: Design documents from `/specs/037-announcements/`

**Prerequisites**: plan.md (required), spec.md (required), research.md, data-model.md, contracts/announcements.md

**Tests**: 章程原则四要求测试先于实现（红→绿），本功能含后端单元/集成 + 前端测试。

## Phase 1: 基础设施

- [x] T001 [P] [US1] 后端：`db/migration/V52__announcements.sql`——announcement/announcement_read/comment 三表。
- [x] T002 [P] [US1] 后端：实体 3 个 + Mapper 3 个。
- [x] T003 [P] [US1] 后端：RoleConstants 权限字典加 `announcement:manage`。

## Phase 2: 后端测试先行（TDD 红）

- [x] T004 [P] [US1] 后端：`AnnouncementServiceTest`——公告 CRUD、置顶排序、过期过滤、已读幂等、未读计数。红阶段。
- [x] T005 [P] [US2] 后端：`CommentServiceTest`——评论 CRUD、@提及解析通知、非作者删除拒绝。红阶段。
- [x] T006 [P] [US1] 后端：`integration/AnnouncementIT.java`——发公告→列表含已读状态→标记已读→未读计数→评论 @提及。红阶段。

## Phase 3: 后端实现

- [x] T007 [US1] 后端：DTO（AnnouncementRequest/Response、CommentRequest/Response）。
- [x] T008 [US1] 后端：`AnnouncementService`——CRUD + 置顶/过期过滤 + 已读（幂等）+ 未读计数。（依赖 T002）
- [x] T009 [US2] 后端：`CommentService`——评论 CRUD（多实体通用）+ @提及正则解析 → 026 通知（跳转实体详情）+ 删除权限。（依赖 T002 + UserMapper + NotificationService）
- [x] T010 [US1] 后端：`AnnouncementController` + `CommentController`。（依赖 T008/T009）

## Phase 4: 前端

- [x] T011 [P] [US1] 前端：`types/announcement.ts` + `announcementService.ts` + `commentService.ts`。
- [x] T012 [US1] 前端：`CommentSection`（通用评论区）+ `AnnouncementCard`（首页公告卡 + 未读角标）。（依赖 T011）
- [x] T013 [US1] 前端：`AnnouncementPage`（公告管理：CRUD + 置顶 + 过期时间）+ 详情页嵌入评论区（客户/线索/商机/工单）+ App.tsx 路由。（依赖 T012）

## Phase 5: 验证与收尾

- [x] T014 后端：`mvn test` 全量通过；H2 schema 同步新表。
- [x] T015 前端：`pnpm run typecheck` + `lint` + `test` 全量通过。
- [x] T016 [P] 手动冒烟：发公告→首页卡未读→已读→详情评论 @提及→通知。

## Dependencies & Execution Order

- T001/T002/T003 可并行。
- T004/T005/T006 可并行，均红阶段；依赖 T001/T002。
- T007 依赖 T002；T008 依赖 T007；T009 依赖 T007；T010 依赖 T008/T009。
- T011 无依赖；T012 依赖 T011；T013 依赖 T012。
- Phase 5 完成后收尾。

## Notes

- 公告过期自动隐藏；已读幂等（唯一键）。
- @提及：正则 `@用户名` → 026 通知。
- 评论实体：CUSTOMER/LEAD/OPPORTUNITY/TICKET；删除仅作者/管理员。
- 权限 announcement:manage 入 028 字典。
