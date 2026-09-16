# 任务：086「不可收口清单」收尾（096）

**Created**: 2026-09-16
**图例**：`[ ]` 未完成 / `[x]` 已完成。**立项阶段一律不预勾。**
**⚠️ 本项的实际编写顺序是「先实现、后补用例」**（沿用 087/088/092/095 的既有做法）——
**不得**据此声称走过 spec-first；§D 的定向破坏留痕证明的是**护栏有牙齿**，不是「红先出现」。

---

## 阶段 A 工件与登记（提交 1）

- [ ] T001 写 `spec.md` / `plan.md` / `research.md` / `quickstart.md` / `falsification-evidence.md` / `tasks.md` / `checklists/requirements.md`
- [ ] T002 `specs/README.md` 模块表加 096 行（**状态列如实写「⏳ 进行中」**）
- [ ] T003 `specs/roadmap.md` 的 `## 当前进度` 加 096 行，**勾选框留空、不预勾**（照 095 先例）

## 阶段 B 后端（提交 2–4）

- [ ] T004 `RoleConstants.PERMISSION_DEFS` 新增 `comment:read` / `comment:create` / `comment:delete`
      （新开「评论协作」组——评论在 `MENU_TREE` 里**没有**菜单）
- [ ] T005 `RoleConstants.PERMISSION_DEFS` 在既有「自定义对象」组新增
      `custom_object_record:read` / `:create` / `:update` / `:delete`
- [ ] T006 订正 T005 所在组的注释（「不设码——靠菜单 + 数据范围」**两半都不成立**），
      **原文逐字保留 + 带日期 ⚠️**
- [ ] T007 `CommentController`：撤类级 `@PreAuthorize`，3 个方法各挂 `@RequirePermission`
- [ ] T008 `CommentController` 类 javadoc：改写「刻意保留」的书面裁决为「已建码族并明确授予名单」，
      **原文逐字保留 + 带日期 ⚠️**
- [ ] T009 `CustomObjectController`：5 个记录端点改挂 `@RequirePermission`（定义面 6 个端点一行不动）
- [ ] T010 `CustomObjectController` 类 javadoc 订正「且靠数据范围过滤」（原文留痕 + 带日期 ⚠️）
- [ ] T011 新增 `V90__comment_and_custom_object_record_codes.sql`（纯授权 INSERT，判据①，两族各一组）
- [ ] T012 `backend/src/test/resources/schema-h2.sql` 落 `-- ---------- V90__… ----------` 段头
- [ ] T013 `SchemaParityIT.MIRRORED_MIGRATIONS` 加 `"90"`
- [ ] T014 `specs/README.md` 迁移对照表加 V90 行，并订正该表标题的迁移计数与上界
- [ ] T015 `PermissionEnforcementIT` 追加两族分角色断言（有码 ⇒ 非 403；无码 ⇒ `PERMISSION_DENIED`；
      **两层 403 分离**；**负向必须用非 ADMIN 令牌**）

## 阶段 C 前端（提交 5–6）

- [ ] T016 `constants/permissions.ts` 登记本项**真要 gating** 的码（每条必须被端点真实校验）
- [ ] T017 `CommentSection.tsx`：删除判据改为 `hasPerm(comment:delete) && (ADMIN || 作者)`；
      发布区按 `comment:create` 渲染
- [ ] T018 `CustomObjectRecordPage.tsx`：`usePerms` 收口新建/编辑/删除
- [ ] T019 若新增文案：`i18n/{zh-CN,en}.ts` **双语同一次提交**
- [ ] T020 `ApprovalCenterPage.tsx`：三个操作链接加 `&& row.approverId === user?.id`
- [ ] T021 `scripts/check-perms.mjs` 白名单**理由**更新（`CommentSection.tsx` 与 `DashboardPage.tsx`
      两条，**count 均不变**）

## 阶段 D 测试与留痕（提交 7）

- [ ] T022 新建 `CustomObjectRecordPage.perm.test.tsx`（负向必须用非 ADMIN、先 `findByText` 证明页面已渲染）
- [ ] T023 扩 `CustomerDetailPage.perm.test.tsx` 覆盖评论删除码
- [ ] T024 `ApprovalCenterPage` 归属渲染用例（**不是 perm 用例**）
- [ ] T025 **9 条定向破坏**逐条做、逐条逐字节还原（清单见 `quickstart.md` §2），留痕进 `falsification-evidence.md`
- [ ] T026 订正 `specs/086-frontend-button-gating/research.md` §2 清单第 2、5 行（**原文逐列逐字保留 +
      带日期 ⚠️**），并做「旧值仍可 grep 到」自查
- [ ] T027 跑全部门禁，实测读数写进 `falsification-evidence.md` §0 与 `tasks.md` 交付块
- [ ] T028 `roadmap.md` 勾选 096 行 + 加【交付后记】；`README.md` 状态列改 ✅
- [ ] T029 登记 `V87` 头注释那处不实表述（**已应用迁移不可改**，只登记）

---

## 交付块（交付时填写实测读数，**不引用任何历史数字**）

> 待填：`mvn -B verify` 退出码 / surefire / failsafe / JaCoCo INSTRUCTION；
> `pnpm test:coverage` 文件数·用例数·四项覆盖率；`i18n:check` / `menu:check` / `perms:check` / `ui:check`
> （含冻结台账读数）；定向破坏条数与还原证据。
