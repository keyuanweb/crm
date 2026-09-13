---
description: "任务清单：前端按钮级权限收口（086）"
---

# 任务清单：前端按钮级权限收口（086）

**Input**: Design documents from `/specs/086-frontend-button-gating/`

**Prerequisites**: [plan.md](./plan.md)（必需）、[spec.md](./spec.md)（必需，用户故事来源）、[research.md](./research.md)（必需，取证与不可收口清单）

**Tests**: **必需**。FR-B10 明文要求每个被收口的码至少一条双向渲染用例；FR-B09 要求护栏被实测验证。

**Organization**: 按用户故事分组。阶段 0（码表）与阶段 1（护栏）是**所有故事的共同前置**，单列为「准备」。

## 格式：`[ID] [P?] [Story] 说明`

- **[P]**：可并行（不同文件、无未完成依赖）
- **[Story]**：所属用户故事（US1–US4）
- 每项任务均含**确切文件路径**

> **编号消歧（重要）**：本文档的 `T0xx` 编号**属于本规格**，与 `specs/083-engineering-consolidation/tasks.md`、
> `specs/085-verify-green/tasks.md` 的同名编号**无关**。引用其他规格时**一律加前缀**。
> 本文档中 `FrontendPermissionCodeAlignmentTest` 的断言编号（断言 1/2）指的是该测试类内部的两条断言。

## 路径约定

沿用仓库既有的 backend / frontend 双模块布局（plan.md 的 Structure Decision）：

- **前端**：`frontend/src/`、`frontend/scripts/`
- **后端**：`backend/src/test/java/com/crm/security/`（**只读**——本规格零后端改动，后端只提供护栏）

---

## 准备（所有用户故事的共同前置）

- [x] **T001** 在 `frontend/src/constants/permissions.ts` 登记 45 个新码（17 → 62）。格式须匹配
      `^\s+[A-Za-z0-9_]+:\s*'([^']+)',`（`FrontendPermissionCodeAlignmentTest` 的解析正则，行尾不锚定），
      插在 `export const PERMS = {` 与 `} as const` 之间。**先跑后端护栏再接页面**——见 plan.md 阶段 0。
- [x] **T002** 运行 `backend/src/test/java/com/crm/security/FrontendPermissionCodeAlignmentTest`
      验证 T001：断言 1（registered ⊆ `PERMISSION_DEFS`）与断言 2（registered ⊆ 被 `@RequirePermission` 校验的集合）。
      **实测记录（2026-09-13）**：`Tests run: 2, Failures: 0, Errors: 0, Time elapsed: 0.765 s`。
      本地佐证：`grep -cE "^\s+[A-Za-z0-9_]+:\s*'[^']+',"` = 62，值集合 `uniq -d` 为空（无重复）。
- [x] **T002b** 补登 `contractUpdate: 'contract:update'`（T025 发现的 T001 遗漏）后**重跑** T002 护栏。
      **实测记录（2026-09-13）**：`Tests run: 2, Failures: 0, Errors: 0`；`grep -cE` = 63，无重复值。
- [x] **T002c** 全部页面接线完成后**第三次**复跑 T002 护栏（这是收尾复验，不是重复劳动：
      期间 `permissions.ts` 未再改动，但接线批次动了 47 个页面，须确认登记表仍与后端字典一致）。
      **实测记录（2026-09-13 16:42）**：`Tests run: 2, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.726 s`，
      报告文件 `backend/target/surefire-reports/com.crm.security.FrontendPermissionCodeAlignmentTest.txt`
      （mtime `16:42:51`，与本次运行同分钟，非陈旧报告）。
      **这道证据的口径边界（如实写明，别让后人以为它证明了更多）**：该次运行**跳过了编译**——
      `target/classes`（07:56）与 `target/test-classes`（08:24）都早于 `pom.xml` 的改动（15:38），
      且后端源码本规格一字未动 ⇒ maven-compiler-plugin 判定"所有类都是最新的"，直接复用既有 class 跑 surefire。
      **这不影响本结论**，因为该测试是**运行时读文件**的：它用 `Files.readString` 读
      `frontend/src/constants/permissions.ts`（`FrontendPermissionCodeAlignmentTest.java:44-45,115-118`），
      故 16:42 那次判定针对的是**当时的注册表内容**，正是我们要证的东西。
      但它**没有**证明"后端源码能编译"——那由 T101 覆盖，且当前受阻于 JDK 25/17 的跨工作流问题。
- [x] **T003** 确认 `permissions.ts` 中无死码：45 个新码无一属于 `PERMISSION_DEFS` 里「有码无校验」的那 22 个。
      由 T002 的断言 2 机械保证。
- [x] **T004** 创建 `specs/086-frontend-button-gating/{spec.md,plan.md,tasks.md,research.md}`。
      **research.md 的「不可收口清单」是本规格的可交付物之一**，不是过程记录。

> **阶段 0 的验收点**：T002 转绿之前**不得改动任何页面**。45 个码里任何一个不合格都在此暴露，
> 而不是等 49 个页面改完之后。

---

## US4：护栏脚本（P1，**必须在 US1–US3 之前完成**）

> 理由（plan.md 阶段 1）：145 个判定点写完就没有第二次机会逐一复核。护栏先就位，后续每个页面改动即时受检。

- [x] **T010** [US4] 新建 `frontend/scripts/check-perms.mjs`，实现四项检查：
      ①`role === 'ADMIN'` 硬判断（**跳过注释行**，`App.tsx:488` 是注释）；②`PERMS` 值不重复；
      ③无未定义引用（`PERMS.(\w+)` 的每个键须存在）；④白名单。
      与既有 `scripts/check-i18n.mjs` / `check-menu.mjs` 同形（同一目录、同样的非零退出约定）。
      **实现要点**：注释剥离是**引号感知**的逐字符扫描（否则 `'https://…'` 里的 `//` 会截掉真代码）；
      正则用 `={2,3}` 而非 `==?`（后者会把 `me.role = 'ADMIN'` 这类**赋值**当成比较）；
      ADMIN 扫描**排除测试文件**（测试里断言"管理员能看见全部"是对直通语义的正当验证，不是 gating）。
- [x] **T011** [US4] 在 `check-perms.mjs` 中内建**带理由**的白名单。**按「文件 + 预期命中数」登记，不按行号**——
      行号会随无关改动漂移，会让护栏无故转红，而会无故转红的护栏很快被绕过。命中数**双向**校验：
      多了 = 新增了未批准的判断；少了 = 白名单陈旧（有人修好却没删条目）。共 8 条：
      - 合法短路：`hooks/usePermission.ts`(2)、`components/PermissionGuard.tsx`(1)、`constants/menuVisibility.ts`(1)
      - 非权限判断：`pages/users/UserManagementPage.tsx`(1)（角色 → Tag 颜色）
      - 不可收口（后端无码）：`components/CommentSection.tsx`(1)、`components/FollowUpTimeline.tsx`(1)、`pages/stats/DashboardPage.tsx`(1)
      - 数据归属规则的 ADMIN 例外：`pages/customers/CustomerDetailPage.tsx`(1)（镜像后端 `!isAdmin && !ownerId.equals(...)`，
        `hasPerm` 无法表达"是 ADMIN 但不是 owner"这一支）
- [x] **T012** [US4] `frontend/package.json` 加 `"perms:check": "node scripts/check-perms.mjs"`。
- [x] **T013** [US4] `.github/workflows/ci.yml` 的 frontend job 在 `menu:check` 之后加一步 `pnpm perms:check`
      （与 `i18n:check` / `menu:check` 同形，附理由注释）。
- [x] **T014** [US4] **实测护栏会转红**（spec.md US4 场景 5，FR-B09）。
      **实测记录（2026-09-13）**，三次注入各转红一次，还原后转绿，探针文件已清理：

      | 注入 | 手段 | 结果 |
      |---|---|---|
      | A：ADMIN 硬判断 | 探针文件 `src/__permcheck_probe.tsx` 写 `u.role === 'ADMIN'` | `exit=1`，报 `【ADMIN 硬判断】1 处 src/__permcheck_probe.tsx` |
      | B：码值重复 | 备份 `permissions.ts` 后将 `contractUpdate` 的值改为 `'customer:update'` | `exit=1`，报 `码 'customer:update' 被 2 个键登记：customerUpdate, contractUpdate`，并指出第 134 行 |
      | C：未定义引用 | 探针文件写 `PERMS.customerDelte` | `exit=1`，报 `第 2 行：PERMS.customerDelte` |

      还原校验：`grep -c "contractUpdate: 'contract:update',"` = 1；护栏 `exit=0`。
      **附带确认**：三次都是**独立**跑脚本（无 `tsc`、无构建产物）即判红——检查 ③ 不依赖 `tsc`，
      与 `menu:check` 一样可独立执行。
- [x] **T015** [P] [US4] 反向验证白名单：实测 `UserManagementPage.tsx`（角色 → Tag 颜色）等 8 条白名单项
      **均未**让脚本转红（T014 还原后 `exit=0`，输出 `8 个文件含已登记的 ADMIN 判断，共 9 处`）。
      反向亦有校验：白名单命中数少于登记数会报「白名单陈旧」——故条目不会悄悄失效。

---

## US1：无权者看不到破坏性操作（P1）

> 约 30 个页面。改法一律照抄 `CustomerListPage.tsx`（工具栏 `usePerms` + 条件展开；
> 行内动作用三元 `null` / `cond && <a/>`），**不发明新写法**（FR-B05）。
> 每个页面两条用例：有码 ⇒ 按钮在；无码 ⇒ 按钮不在（FR-B10，负向用例**必须用非 ADMIN**）。
>
> **实施方式与证据（2026-09-13）**：本区 32 项按域分 4 批并行实施，**每批各自回读
> `src/services/*.ts` 与后端 Controller，逐条核对"按钮 → 端点 → 码"三者一致**后才动手；
> 4 批均报告"无一处端点挂的不是清单里给的码"。批次级证据见文末「执行记录」。

### 客户 / 联系人 / 线索

- [x] **T020** [P] [US1] `pages/customers/DuplicateMergePage.tsx` → 扫描（`:82`）/ 合并（`:142` 的 Popconfirm，**不可逆**）
      挂 `PERMS.customerMerge`。两个端点同码（`CustomerMergeController`），故一个判据管两个动作。
- [x] **T021** [P] [US1] `pages/contacts/ContactListPage.tsx` → 行内删除（`:182`）挂 `PERMS.contactDelete`
- [x] **T022** [P] [US1] `pages/leads/LeadListPage.tsx` → 删除线索（`:274`）挂 `PERMS.leadDelete`
      （**已接线页面的漏网**，见 plan.md）。既有 `canAssign` 判据未动；权限与业务状态判据写成
      `canEdit(row) && canDelete &&`，两者并存。
- [x] **T023** [P] [US1] `pages/calls/CallRecordPage.tsx` → 行内删除（`:95`）挂 `PERMS.callRecordDelete`

### 合同 / 报价 / 订单 / 发票

- [x] **T024** [US1] `pages/contract-templates/ContractTemplateListPage.tsx` → 删除挂 `PERMS.contractTemplateManage`，
      **替代 `user.role === 'ADMIN'` 硬编码**。该码零持有者 ⇒ 与现状逐字等价。
      **实施说明（记为偏差 D1）**：原判据 `isAdmin` 同时管住行内「编辑+删除」（`:116`）与工具栏「新建」（`:152`），
      而后端 `ContractTemplateController` 的 create/update/delete **挂的是同一个码**，故三处共用
      `canManage = hasPerm(PERMS.contractTemplateManage, user)`——一个码管一个写面，不拆判据。
      顺带订正了原注释「无需改动」的结论。
- [x] **T025** [P] [US1] `pages/contracts/ContractDetailPage.tsx` → 终止合同 + 删除附件挂 `PERMS.contractUpdate`。
      **发现**：`contract:update` 在 T001 之前**未登记**（原 17 个码里合同只有 `contractApprove`），
      属 T001 的遗漏——已补登为第 46 个新码（17 → 63），并由 T002b 复验。
      审批按钮的 `contractApprove` 判据不受影响——两码独立，**不可互替**（FR-B07）。
      实施：新增 `canUpdate = hasPerm(PERMS.contractUpdate, user)`，「终止合同」写成 `canFinish && canUpdate`
      （业务状态 ∧ 权限，未互相替代），「删除附件」用 `canUpdate ? <a/> : null`。
- [x] **T026** [P] [US1] `pages/invoices/InvoiceListPage.tsx` → 作废（`:158`）挂 `PERMS.invoiceManage`，
      写成 `row.status === 'ISSUED' && can[PERMS.invoiceManage]`。
- [x] **T027** [P] [US1] `pages/quotes/QuoteDetailPage.tsx` → 导出 PDF **不收口**（读码，research.md §3.2），
      仅加一条代码注释说明为何不挂码。**已按此实施**（引 `QuoteController.java:128-131`）。

### 商机 / 任务 / 拜访 / 回收站

- [x] **T028** [P] [US1] `pages/opportunities/OpportunityListPage.tsx` → 行内删除（`:139`）挂 `PERMS.opportunityDelete`
- [x] **T029** [P] [US1] `pages/tasks/TaskListPage.tsx` → 删除（`:227`）挂 `PERMS.taskDelete`
      （「完成/重开」属 US2 的 `taskUpdate`，同页不同码，未混用）
- [x] **T030** [P] [US1] `pages/visits/VisitListPage.tsx` → 取消（`:215`）挂 `PERMS.visitManage`
      （「签到」（`:209`）同码，一并收）
- [x] **T031** [P] [US1] `pages/recycle/RecycleBinPage.tsx` → 彻底删除（`:120`）挂 `PERMS.recyclePurge`（不可逆）；
      恢复（`:111`）挂 `PERMS.recycleRestore`（**同页不同码，未一刀切**，spec.md US2 场景 4）。
      工具栏数组用条件展开 `...(can.x ? [<Popconfirm/>] : [])`。

### 营销 / 邮件 / 表单 / 落地页

- [x] **T032** [P] [US1] `pages/marketing/EmailTemplatePage.tsx` → 行内删除挂 `PERMS.emailManage`
- [x] **T033** [P] [US1] `pages/marketing/OnlineFormPage.tsx` → 删除挂 `PERMS.formManage`（启停 `Switch` 属 US2，同批收）
- [x] **T034** [P] [US1] `pages/landing/LandingPageListPage.tsx` → 行内删除挂 `PERMS.marketingManage`
- [x] **T035** [P] [US1] `pages/email/EmailUnsubscribePage.tsx` → 恢复挂 `PERMS.emailManage`
- [x] **T036** [P] [US1] `pages/mail/MailSyncPage.tsx` → 删除邮箱账号挂 `PERMS.mailAccountManage`；
      模拟同步 + 删除同步记录挂 `PERMS.mailSyncManage`（两个码未混用）。
      `SyncRecordList` 是独立组件，单独调用了一次 `usePerms`。

### 知识库 / 公告 / 审批流 / 工作流

- [x] **T037** [P] [US1] `pages/knowledge/KnowledgeArticleListPage.tsx` → 删除挂 `PERMS.knowledgeDelete`
      （发布/下架同页不同码，属下方 US2 部分）
- [x] **T038** [P] [US1] `pages/announcements/AnnouncementPage.tsx` → 删除挂 `PERMS.announcementManage`
- [x] **T039** [P] [US1] `pages/approval/ApprovalFlowPage.tsx` → 删除审批流挂 `PERMS.workflowManage`（`ApprovalController:66-67`）。
      表单内的节点删除/只读 `Switch` 是纯本地 state，**刻意不收**。
- [x] **T040** [P] [US1] `pages/workflows/WorkflowRuleListPage.tsx` → 删除挂 `PERMS.workflowDelete`（启停属 US2，同批收）

### 系统配置类页面

- [x] **T041** [P] [US1] `pages/custom-object/CustomObjectListPage.tsx` → 删除挂 `PERMS.customObjectDelete`
- [x] **T042** [P] [US1] `pages/departments/DepartmentListPage.tsx` → 删除挂 `PERMS.departmentManage`。
      **记为偏差 D2**：本页取数的 `GET /departments/tree`（`DepartmentController:48-50`）挂的是**同一个**码，
      故这道判据对"能加载出本页的人"恒真、**不会真的隐藏任何按钮**。仍挂的理由与证据见「执行记录 · D2」。
- [x] **T043** [P] [US1] `pages/settings/CurrencyRatePage.tsx` → 行内删除挂 `PERMS.currencyManage`
- [x] **T044** [P] [US1] `pages/settings/CustomFieldListPage.tsx` → 行内删除挂 `PERMS.customFieldDelete`
- [x] **T045** [P] [US1] `pages/settings/FieldPermissionPage.tsx` → 行内删除挂 `PERMS.fieldPermissionManage`
- [x] **T046** [P] [US1] `pages/settings/OpportunityStagePage.tsx` → 删除挂 `PERMS.stageManage`
      （启停属 US2，同批收；删除列保留了原有的业务状态三元，权限只加在**可执行那一支**上）
- [x] **T047** [P] [US1] `pages/sla/SlaPolicyListPage.tsx` → 行内删除挂 `PERMS.slaManage`
- [x] **T048** [P] [US1] `pages/playbook/StageActionTemplatePage.tsx` → 行内删除挂 `PERMS.playbookManage`
- [x] **T049** [P] [US1] `pages/tags/SegmentListPage.tsx` → 行内删除挂 `PERMS.tagManage`
      ⚠️ **不是 `segment:manage`**（死码，`SegmentController.java:56-57` 挂的是 `tag:manage`）。
      **实施时已二次确认**，且 T090 为该页写了一条以 `tagManage` 为"有码 ⇒ 可见"支的断言，
      使后人若"修正"成死码会立刻转红。
- [x] **T050** [P] [US1] `pages/tags/TagListPage.tsx` → 行内删除挂 `PERMS.tagManage`
- [x] **T051** [P] [US1] `pages/data-retention/DataRetentionPolicyListPage.tsx` → 删除挂 `PERMS.retentionDelete`。
      「合规导出」按钮**刻意不收**（只 `navigate()`，不发请求；已加注释）。

---

## US2：高权操作与不可逆终态（P1）

- [x] **T060** [P] [US2] `pages/users/UserManagementPage.tsx` → 数据权限 / 重置密码 / 启用停用
      挂 `PERMS.userManage`（`usePerms` 只调一次，三处共用）。
      **该页 `:36` 的角色→Tag 颜色是白名单项，未动**（T011）。
- [x] **T061** [P] [US2] `pages/tickets/TicketDetailPage.tsx` → 删除 `PERMS.ticketDelete`（并与业务状态
      `status !== 'CLOSED'` 并存）/ 分配 `PERMS.ticketAssign` /
      **状态流转三按钮整组挂 `PERMS.ticketUpdate`** / 发送回复 `PERMS.ticketReply`。
      **实施说明**：「发送回复」收的是**整个回复块**（含 TextArea），不是只包按钮——
      否则无码者会看到一个必然 403 的死输入框。这也使 T090 的验收场景（持 `ticketReply` 而无
      `ticketAssign` ⇒ 只有回复可见）成立。
- [x] **T062** [P] [US2] `pages/sales-opportunities/SalesOpportunityListPage.tsx` → 赢单/输单挂
      `PERMS.opportunityUpdate`。**看板拖拽刻意不收**（决策 7）——已在 `OpportunityBoard.tsx` 加注释说明。
      `isTerminal(row.stage)` 仍在最外层：终态行照旧显示「已关闭」，非终态但无码则**不渲染任何动作**
      （不会误显「已关闭」）。
- [x] **T063** [P] [US2] `pages/recycle/RecycleBinPage.tsx` → 恢复挂 `PERMS.recycleRestore`
      （与 T031 同文件，已合并为一次改动）
- [x] **T064** [P] [US2] `pages/data-retention/DataRetentionExecutionHistoryPage.tsx` → 立即执行挂 `PERMS.retentionExecute`
- [x] **T065** [P] [US2] `pages/workflows/WorkflowRuleListPage.tsx` → 启停挂 `PERMS.workflowUpdate`
      （与 T040 同文件，已合并改动）
- [x] **T066** [P] [US2] `pages/marketing/OnlineFormPage.tsx` → 启停 `Switch` 挂 `PERMS.formManage`
      （与 T033 同文件，已合并改动）
- [x] **T067** [P] [US2] `pages/custom-object/CustomObjectListPage.tsx` → 启用/停用挂 `PERMS.customObjectUpdate`
      （与 T041 同文件，已合并改动；**与 `customObjectDelete` 是两个码，未合并判据**）
- [x] **T068** [P] [US2] `pages/settings/OpportunityStagePage.tsx` → 停用/启用挂 `PERMS.stageManage`
      （与 T046 同文件，已合并改动。启用/停用那对分支无状态占位，写成两条 `can && state &&` 条目）
- [x] **T069** [P] [US2] `pages/settings/IntegrationHubPage.tsx` → 启用/停用 + 删除挂 `PERMS.integrationManage`
      （两端点同码：`IntegrationChannelController:67,74`。原任务文本里 `pages/integrations/…` 是笔误，
      实际路径即此。）
- [x] **T070** [P] [US2] 「角色与权限配置」类页面复核：`RoleListPage.tsx:58` 的
      `canManage = hasPerm(PERMS.roleManage, user)` 同时管住行内删除（`:199` 的 Popconfirm，`:205-211`）
      与工具栏（`:263`），权限勾选 `Switch`（`:346`）位于被同一判据守护的弹窗内——
      **该类无遗漏，无需新增改动**。`permissions.ts` 中 `roleManage` 是原有 17 个码之一。

---

## US3：已接线页面的漏网与死代码补齐（P2）

- [x] **T080** [US3] `pages/leads/LeadListPage.tsx` → 删除线索（见 T022，本任务负责**双向测试**）——
      **测试由 T090 覆盖**（`LeadListPage.perm.test.tsx`）。
- [x] **T081** [US3] `pages/contracts/ContractDetailPage.tsx` → 终止合同 + 删除附件（见 T025）。
      **缺口已补（2026-09-13，本会话直接实施）**：该页的双向测试未被 T090 的四个批次分到，
      已补写 `src/pages/contracts/ContractDetailPage.perm.test.tsx`（4 例）。
      它的核心用例是第 4 例：**同一用户、同一码**（持 `contract:update`）看 DRAFT 合同——
      「删除附件」必须**可见**（该判据不看状态），「终止合同」必须**不可见**（该判据嵌在 `canFinish` 之内）。
      一对相反结论同时成立，才排除"权限判据把状态判据一起吞掉"与"判据恒真"两种失效。
      **变异自验**：`const canUpdate` → `true` 后**恰好 1 例转红**（第 2 例，输出
      `expected document not to contain element, found <button … pages.contract.detail.terminate`），
      另 3 例保持通过；按唯一探针串还原后 4/4 转绿，`git diff` 复核改动**只**落在判据声明与两处挂载点。
- [x] **T082** [US3] `pages/customers/CustomerDetailPage.tsx` → `canShare` 补上**缺失的码闸门**：
      `!!data && hasPerm(PERMS.customerUpdate, user) && (isAdmin || data.ownerId === user?.id)`。
      **对 research.md §3.1 的订正**：§3.1 原文说"`isAdmin` 换成 `hasPerm`，`ownerId` 保留"——**那样是错的**。
      后端是 `码 ∧（归属者 ∨ 管理员）` **两道**闸门（`CustomerShareController:52,60` + `CustomerShareService.share` 的
      `!isAdmin && !ownerId.equals(currentUserId)`），而 `hasPerm` 对 ADMIN 直通、无法表达"是 ADMIN 但不是 owner"，
      故换成 `hasPerm && isOwner` 会让**管理员失去共享任何客户的能力**（后端本会放行）= 一次真回退。
      正确写法是保留 `isAdmin ||` 这一支，**另外加上**码闸门；`isAdmin` 因此留在白名单里（见 T011）。
      **修掉的真缺陷**：改造前只判归属不判码 ⇒ 一个**拥有客户但无 `customer:update`** 的角色（如 VIEWER）
      会看到按钮却必然 403——正是本规格要消除的"界面宣称可用、实际不可用"。
      **测试证据**：`CustomerDetailPage.perm.test.tsx` 用四格矩阵覆盖两道闸门（含"非 owner 的管理员仍可见"，
      即上段那个回退的回归锁）。
- [x] **T083** [US3] `components/PermissionGuard.tsx` 补齐到**至少一个非测试引用**（spec.md US3 场景 3）。
      **已完成（2026-09-13）**：落点是 `pages/tickets/TicketDetailPage.tsx` 的**整块回复区**
      （`{canOperate && <PermissionGuard permission={PERMS.ticketReply}> …回复框+发送按钮… </PermissionGuard>}`）。
      选它的理由：该组件正是为"包裹一整块"设计的场景，而给单个按钮加判据用 `usePerms`/`&&` 更直接——
      这正是接线批次 47 个页面统一用后者的原因，也就是该组件此前零引用的成因。
      **语义保持不变**：业务状态判据 `canOperate`（`status !== 'CLOSED'`）留在**外层**，权限判据交给守卫，
      两者仍是 ∧；守卫内部对 ADMIN 直通（`PermissionGuard.tsx:36`）与 `hasPerm` 同义（该处已在护栏白名单）。
      **测试证据**：`TicketDetailPage.perm.test.tsx` 仍 8/8 通过（行为无变化）；
      另**单独变异验证了这条新路径**（批次 B 当时变异的是 `ticketAssign`，**没有**覆盖回复闸门）——
      把 `permission={PERMS.ticketReply}` 摘掉后**恰好 2 例转红**（第 2 例"零权限全不可见"与
      第 5 例"互不串码反向"），输出均定位到 `pages.ticket.detail.sendReply`，证明守卫的 `permission` 属性
      真的在起作用而不是被静默忽略；还原后 8/8 复绿。
      **非测试引用核验**：`grep -rn "PermissionGuard" src` → `TicketDetailPage.tsx` 的 import 与 `:287`/`:304` 一对标签。
- [x] **T084** [US3] 复核 `hooks/usePerms.ts` 的引用面：**实测**
      `grep -rnE "hasPerm\(\s*'|usePerms\(\s*\[?\s*'" src/pages src/components` → **零命中**，
      即无任何页面残留 `hasPerm('硬编码字符串', user)` 的散落字面量；所有调用点都走 `PERMS` 常量
      （`PERMS.xxx`），`tsc` 与护栏检查 ③ 同时对键名兜底。
      使用原语的分工：列表页工具栏/行内动作用 `usePerms`，详情页单条件与"需与业务状态做 ∧"的场合用 `hasPerm`。

---

## 测试（贯穿 US1–US3）

- [x] **T090** 为每个被收口的码至少写一条**双向**渲染用例（FR-B10）。负向用例**必须用非 ADMIN 用户**
      （`role: 'SALES'`, `permissions: []`）——ADMIN 在 `hasPerm` 里直通，永远拿不到"看不见"的结论。
      沿用 `src/test/renderWithProviders.tsx`（ConfigProvider(zhCN) + antd App + QueryClient + MemoryRouter）。
      **完成（2026-09-13）**：**39 个 `*.perm.test.tsx`，实测 146 个用例**（占全仓 246 例的 59%；计数口径为
      `npx vitest run <39 个文件>` 的汇总，非按 `it(` 估算），
      覆盖 086 自己收口的**全部 46 个码**。清单、覆盖面口径与**范围边界**见「执行记录 · 测试覆盖面」。
      每个文件都做了**变异自验**：把该页判据临时改成恒真，确认"无码 ⇒ 不可见"那条**真的转红**，再还原；
      自验一律用**带唯一标记串的整行替换**（`/* MUTATION-PROBE-086-<页面> */`）还原，
      完成后 `grep -rn "MUTATION-PROBE\|new Proxy({}, { get: () => true })" src/` **无输出**。
- [x] **T091** **回归确认**：`CustomerListPage.test.tsx` 依赖 ADMIN 直通语义找「新增客户」按钮，
      **T001/T084 之后该测试必须仍然通过**。若它转红，说明 ADMIN 直通语义被改坏了（违反 FR-B06）。
      **实测记录（2026-09-13，终态）**：全量 `pnpm test:coverage` → `Test Files 63 passed (63)`、
      `Tests 246 passed (246)`、`exit=0`、`% Stmts 63.66`。该文件在其中且通过。
      （这是**收尾复跑**，含 T090 全部 39 个权限用例；接线中途曾有一次基线 `26/107`，非终态数值。）
- [x] **T092** [P] 为 `check-perms.mjs` 白名单的两条**非权限**角色字面量各写一条断言——
      **以 T015 的实测记录代替独立断言**：白名单的**双向命中数校验**（多了报"新增未批准判断"、
      少了报"白名单陈旧"、文件消失报"请删除该条"）已使"误删白名单项"必然转红，
      比单独的断言更强（它同时防"白名单被悄悄扩大"）。此为对 FR-B09 的等价满足，理由记录于此。

---

## 收尾

- [x] **T100** 跑通前端全部门禁：
      `pnpm typecheck && pnpm lint && pnpm i18n:check && pnpm menu:check && pnpm perms:check && pnpm test:coverage`
      **六道全部实测通过（2026-09-13，终态复跑，全部 agent 已停止改动页面）**：

      | 门禁 | 退出码 | 证据输出 |
      |---|---|---|
      | `typecheck` | **0** | `tsc --noEmit` 无输出 |
      | `lint` | **0** | `eslint .` 无输出 |
      | `i18n:check` | **0** | `✓ 语言资源一致：zh-CN 2884 键 / en 2884 键；菜单路由与清单双向对齐（路由 58 条 / 清单 56 项，粗粒度别名 3 条）` |
      | `menu:check` | **0** | `✓ 菜单清单是最新的（56 个菜单项，来源：RoleConstants.MENU_TREE）` |
      | `perms:check` | **0** | `✓ 权限判定接线校验通过（63 个权限码；8 个文件含已登记的 ADMIN 判断，共 9 处）`，**"尚无页面引用"提示已归零**——63 个码全部至少有一处页面引用 |
      | `test:coverage` | **0** | `Test Files 63 passed (63)`、`Tests 246 passed (246)`、`% Stmts 63.66` |

      **退出码是逐条单独取的**（不是管道尾部的退出码）——中途有一次因把 `tsc` 的退出码读成了 `head` 的，
      一度把带错的状态读成绿的，故此处明确口径：每个门禁 `> log 2>&1` 后立即取 `$?`。
      **另**：接线/变异进行中时 `tsc` 会报**瞬时**的 `TS6133 'usePerms' is declared but never read`
      （文件正被改成"已 import、判据尚未落笔"的中间态）。2026-09-13 有两次这样的报错被误当成既有缺陷
      （`OnlineFormPage` / `DataRetentionPolicyListPage`），经 mtime + 复核内容确认均为**在飞状态**，
      两文件随后各自用上了该 import。**看到 TS6133 先看 mtime，别急着改。**
- [x] **T101** 跑通后端门禁：`mvn -B verify`（`FrontendPermissionCodeAlignmentTest` 会在 backend job 独立复验登记）。
      **2026-09-13 实测：全量 `verify` 已跑通一次（BUILD SUCCESS），但带两处口径折让，且复跑被并行会话污染。**

      **一、通过的那一次**（17:11:22，1 分 55 秒）——命令：
      `mvn -B verify -Dspring-boot.repackage.skip=true > log 2>&1; echo $?` → **exit=0，BUILD SUCCESS**。
      verify 相位插件顺序与结果（逐项取自日志）：

      | 相位 | 结果 |
      |---|---|
      | `compiler:compile` / `testCompile` | `Nothing to compile - all classes are up to date`（**整个跳过**） |
      | `surefire:test` | `Tests run: 555, Failures: 0, Errors: 0, Skipped: 0` |
      | `failsafe:integration-test` | `Tests run: 282, Failures: 0, Errors: 0, Skipped: 0`（72 个 IT 类） |
      | `spotless:check` | 通过（无违规输出） |
      | `jacoco:check` | `All coverage checks have been met.`（`minimum 0.73`） |
      | `FrontendPermissionCodeAlignmentTest` | `Tests run: 2, Failures: 0, Errors: 0` ← **唯一约束 086 的那道** |

      合计 555 + 282 = **837 个用例全绿**。

      **二、两处口径折让，必须写明，不得当成"干净地跑通了"**：

      1. **`repackage` 被跳过**（`-Dspring-boot.repackage.skip=true`）。原因不是代码：裸跑时它以
         `Unable to rename '…crm-backend-0.1.0-SNAPSHOT.jar' to '….jar.original'` 失败于 `package` 相位
         ——**fat jar 被占用**。占用者是**两个**跑着 `target/crm-backend-0.1.0-SNAPSHOT.jar` 的 java 进程
         （PID 3848 与 7680，见第三节），不是本会话起的。跳过的只是打胖 jar，**不影响任何门禁的判定**。
      2. **编译整个被跳过** ⇒ 本次运行**不构成"后端源码可编译"的证据**（与 T002c 同一口径边界）。
         跳过是因为 Maven 按"每个源文件比它自己那个 .class"判定无过期——**不是**比全局最新 class。
         实践中注意：`find src -newer target/classes/<最新class>` 会给出误导性结果
         （实测有 4 个测试源 mtime 15:19–15:30 晚于最新 class 15:17:11，但 `testCompile` 仍报 up to date）。
         故 JDK 25/17 那道坎在"源码不动"的前提下确实不咬人，**但一旦真改了后端源码就会咬**。

      **三、复跑失败，且已坐实为并行会话所致，不是回归。** 同一条命令复跑 → **exit=1**，
      `surefire` 报 `Tests run: 555, Errors: 30`，**根本没跑到 IT 相位**。30 个错误全部集中在
      `DashboardStatsServiceTest` / `FieldPermissionServiceTest` / `OpportunityStageServiceTest` /
      `SalesOpportunityStateTest` 四个类，形态一律是
      `NoClassDefFound com/crm/support/StageDictionaryTestSupport`（及 `FieldPermissionServiceTest$1/$2` 这类匿名内部类）
      ——**编译产物在运行当中被抽走了**。证据链：

      - `ListAgents` 显示对等会话 `engineering-consolidation-ci-gates [07f653]` 状态 **busy**（已 9 小时）；
      - `target/test-classes/…/StageDictionaryTestSupport.class` 的 mtime 是 **17:16:41**，正落在本次 surefire 运行**当中**，
        而该目录 mtime 仍是 08:24（说明没有 `clean`，只是 class 被逐个重写）；
      - 同会话新增了未跟踪文件 `backend/src/test/java/com/crm/integration/WebhookSweepScheduleIT.java`。

      两个构建共用同一个 `target/`，谁都无法得到可信结论——**该次的红与上一次的绿，都属"有其他写入者时门禁不作数"**。
      **④ 但 086 那道门禁在两次运行里都过了**：`FrontendPermissionCodeAlignmentTest` 在通过的那次与失败的那次
      均为 `Tests run: 2, Failures: 0`（第 122–123 行，两次输出逐字相同）。且该测试是**运行期**读
      `frontend/src/constants/permissions.ts`（`Files.readString`，`:44-45,115-118`），
      **不依赖后端编译产物**，故它的绿不受上面两条折让影响。

      **四、遗留**：要拿到"干净的一次"（不跳 repackage、且有其他写入者时的对照），需待 `[07f653]` 空闲后重跑。
      在此之前，**不得把本次结果表述为"后端源码可编译"**，也不得表述为"CI 会拦住"（见 T105）。
      另：本机 MySQL 与 Redis 均已确认可用（`crm_db` 可连、`redis-cli ping` → `PONG`），
      它们**不再是**本项的障碍——先前"本工作区跑不起来"的顾虑仅在 JDK 25 那一项上成立。
- [x] **T102** 验证 FR-B11（零后端改动）：`git diff --stat -- backend/` 应为空
      （`backend/src/test/` 亦无改动——护栏测试是既有的，本规格只调用它）。
      **实测记录（2026-09-13）**：`git diff --stat -- backend/` 输出 **`backend/pom.xml | 4 +++-`**，
      **非空，但与本规格无关**：该改动是 `java.version` 17→25 与新增 `<lombok.version>1.18.42</lombok.version>`
      （JDK 25 兼容），属**并行工作流**（分支名 `appmod/java-upgrade-20260912235322` 即 Java 升级），
      在本次会话开始前就已存在于工作区（会话起始 `git status` 即列出 ` M backend/pom.xml`）。
      **本规格自身对后端的改动为零**：`backend/src/` 与 `backend/src/test/` 无任何改动，
      FR-B11 成立。此处如实区分"diff 非空"与"本规格改了后端"两件事，不以后者掩盖前者。
- [x] **T103** 盘点核对（spec.md 成功标准 2）：对每个被收口的码，逐个确认**持有该码的角色仍能看到按钮**。
      证据来源为 research.md §1 的持有者清单 + T090 的正向用例。
      **机械核对（2026-09-13）**：用脚本把 `permissions.ts` 的码表与页面源码里的 `PERMS.*` 引用做交叉，
      再在**全部测试文件**里搜该码的字面量，得到：
      **63 个码被页面引用；其中 39 个在测试里出现过该码的字面量，24 个没有。**
      这 24 个的构成是**关键的、必须区分清楚**的两类：

      | 类别 | 个数 | 说明 |
      |---|---|---|
      | 086 登记并接线的新码 | **8** | `announcement:manage` / `workflow:manage` / `retention:execute` / `marketing:manage` / `currency:manage` / `custom_field:delete` / `field_permission:manage` / `visit:manage`。**本会话已派三批补写** |
      | 原有 17 个码里、1.5 批 1~3 就已接线的 | **16** | `customer:{create,delete,import,claim,pool_manage}` / `contract:approve` / `quote:approve` / `lead:assign` / `order:delete` / `product:{create,update,delete}` / `campaign:{create,update,delete}` / `role:manage`。**086 未改动其判据**，属既成接线 |

      原有 17 个码里唯一的例外是 `customer:update`——它有字面量用例（本会话写的 `CustomerDetailPage.perm.test.tsx`）。

      **⚠️ 如实记录一处范围边界，不掩盖**：FR-B10 的字面表述是「**每个**被收口的码至少有一条双向渲染测试」。
      按字面，上表第二类的 16 个码**也算缺口**；但它们**不在 086 的交付范围内**——086 的 Input
      （spec.md:9）明确把范围定为「45 个页面未接线 + 8 个已接线页面的漏网」，这 16 个码的判据在 086 之前就已存在。
      因此本项按「086 自己收口的 46 个码（17 → 63 的增量）」判定：**这 46 个码全部有双向渲染用例**
      （38 个由 T090 的四个批次产出，8 个由本会话的两批补写）。
      **是否要为那 16 个既成接线的码补测，是一个需要裁决的范围问题，不是疏漏**——补测对回归有真实价值
      （它们至今只被 ADMIN 直通路径覆盖，即 `CustomerListPage.test.tsx` 找「新增客户」按钮那种断言），
      但那属于 1.5 批 1~3 的补课，建议单独立项而不是塞进 086。
- [x] **T104** 更新 `specs/README.md` 的模块表，加入 086。
      **已完成（2026-09-13）**：三处改动——① 模块表新增 086 行（标为**收口类**，
      与 085 的「加固类」形制并列）；② 文件头的版本行补上 086 的「无迁移、无后端改动、无契约变更」；
      ③ 编号说明段补 086 的定性，并写明**下方迁移对照表没有 086 行**的理由，以及 086 的两项可交付物
      **不是代码而是口径记录**（`research.md` 的「不可收口清单」＝给后端的待办工单；
      `tasks.md` 记录的三处偏差）。
- [x] **T105** 记录本规格的**门禁口径**：哪些门禁是本地跑的、哪些在 CI 跑。
      **实测记录（2026-09-13，本会话独立复核，非转述 083 的结论）**：
      `git remote -v` **输出为空**、`command -v gh` → **gh NOT installed**。
      ⇒ `.github/workflows/ci.yml` 中本规格新增的那一步 `pnpm perms:check`（T013）**在当前仓库状态下一次都不会触发**。
      **因此 086 的门禁口径正式为「以本地命令为准」**，与 083 T069（`spec.md` FR-G10 / `quickstart.md` 验证 8）
      与 085 的同类声明一致。T013 的那一步是**为将来配好远端预留**的，不是当下生效的保障——
      本规格真正当下的保障是 T002c 的后端护栏与本节 T100 的本地六道命令。
      **不得把「已写进 ci.yml」表述为「CI 会拦住」**：配置远端后应改回以 CI 为准。

---

## 执行记录（2026-09-13）

### 接线批次（US1 + US2 共 47 个页面）

按域分 4 批并行实施（营销/邮件/表单、系统配置、业务实体、流程/工单/导出），另 2 页
（`DepartmentListPage`、`OpenPlatformPage`）由本会话直接实施——**这两页是批次划分时的遗漏**，
由护栏的"尚无页面引用"提示发现（该提示一度只剩下 `departmentManage, openPlatformManage` 两个码）。

每批动手前都回读 `src/services/*.ts` 与后端 Controller 核对"按钮 → 端点 → 码"，4 批均报告无偏差。

接线完成后的门禁实测：

```
node scripts/check-perms.mjs  → exit 0
  ✓ 权限判定接线校验通过（63 个权限码；8 个文件含已登记的 ADMIN 判断，共 9 处）
  （无"尚无页面引用"提示 —— 63 个码全部至少有一处引用）
pnpm typecheck                → exit 0
pnpm lint                     → exit 0
pnpm test:coverage            → exit 0；Test Files 26 passed (26)；Tests 107 passed (107)
```

后端登记护栏（T002c）：`Tests run: 2, Failures: 0, Errors: 0, Skipped: 0`。

### 偏差 D1：`ContractTemplateListPage` 的「新建/编辑」被一并收口

086 决策 1 规定"新建/编辑保持现状不收口"，本页是**唯一的例外**，理由是被后端结构强制的：
`ContractTemplateController` 的 create / update / delete **挂的是同一个 `contract_template:manage`**
（`:58-60` / `:67-68` / `:75-76`）。若把前端拆成"删除收口、新建编辑不收"，就等于宣称
"可以只授删除不授新建"——而后端做不到这件事。**行为无变化**：该码非 ADMIN 持有者为零，
且原判据是 `role === 'ADMIN'`，故对非管理员的效果逐字相同；换成码之后该权限从此可被真正授予。
测试证据：`ContractTemplateListPage.perm.test.tsx` 第三条用例（"持有该码的 SALES 可见"）
正是旧写法**不可能通过**的那条——它锁住了"这个权限现在可授予"。

### 偏差 D2：四处判据当前是冗余的（页面读端点挂的就是同一个码）

这些页面的删除/吊销判据**如实记为冗余**，不假装它们隐藏了什么。
**初稿只记了前两行，后两行由 T090 的补测批次在核对后端 Controller 时发现并补入**——
同一形态比原先记录的更常见，"只有两页"这个印象本身是需要订正的：

| 页面 | 收口的动作 | 为什么冗余 |
|---|---|---|
| `DepartmentListPage` | 删除部门 | 页面取数的 `GET /departments/tree`（`DepartmentController:48-50`）挂的**就是** `department:manage`，故"能渲染出这棵树"已蕴含"持有该码" |
| `OpenPlatformPage` | 吊销 API Key / 启停 Webhook / 删除 Webhook | 两个列表读端点（`OpenPlatformController:77,104`）挂的**就是** `open_platform:manage` |
| `ApprovalFlowPage` | 删除审批流 | 列表读端点 `ApprovalController:43-44` 挂的**就是** `workflow:manage` |
| `LandingPageListPage` | 删除落地页 | 列表读端点 `LandingPageController:43-44` 挂的**就是** `marketing:manage` |

仍然挂上的理由（四页都写进了代码注释）：**码与端点逐字对应**，且后端一旦把读与写拆成两个码，
这里的判据立刻变成有效的收窄——不挂则会在那时静默漏出。这与
「读码挂在读端点上 ⇒ 不收口」**不是**同一种形态（那是 `QuoteDetailPage` 的导出 PDF），故不豁免。
四页的测试文件都在注释里写明了这一点，避免后人把"当前恒真"误读成"这道判据没用、可以删"。

**与之相对照的正例（同一批补测发现，值得记住）**：`AnnouncementPage` 的**列表读端点
`GET /announcements`（`AnnouncementController:33`）不挂任何码**，所以非持有者**真的能**打开这张表、
看见全部公告内容，只是没有「删除」。因此该页的负向用例是**货真价实的权限收窄**，
而不是上述四页那种"判据正确但当前恒真"。
**这四个"冗余"与一个"真收窄"的差别，全部来自后端读端点挂不挂码——接线时唯一的判据来源就是后端 Controller。**

> ⚠️ 这两页（审批流 / 落地页）的测试必须**替身化列表 service** 才能在"无码"下渲染出数据行：
> 生产里非持有者连列表都拉不出来（403），真实数据行根本不会出现。
> 故这两页的第 2 例锁的是**判据挂得对不对**（码的身份），**不是**生产里可观测的差异——
> 与 `OpenPlatformPage.perm.test.tsx` 里已有的诚实说明同一口径，不得把这层区别抹掉。

### 测试覆盖面（T090 终态）

**39 个 `*.perm.test.tsx` / 实测 146 个用例**，全部通过且**逐个做过变异自验**
（把判据临时改成恒真 ⇒ "无码不可见"那条必须转红 ⇒ 按唯一标记串整行还原）。

| 产出方 | 文件数 | 用例数 | 备注 |
|---|---|---|---|
| 本会话直接写的 5 个 | 5 | **18** | 含 `ContractTemplateListPage`（"可授予"回归锁）、`CustomerDetailPage`（两闸门四格矩阵）、`DepartmentListPage`、`OpenPlatformPage`、`ContractDetailPage`（T081 缺口） |
| US1/US2 接线批次的四个补测批次 | 26 | **94** | 覆盖全部被收口的列表页与详情页 |
| T103 补测的三个批次（086 登记但无渲染用例的 8 个码） | 8 | **34** | `AnnouncementPage` / `ApprovalFlowPage` / `LandingPageListPage` / `CurrencyRatePage` / `CustomFieldListPage` / `FieldPermissionPage` / `VisitListPage` / `DataRetentionExecutionHistoryPage` |

变异自验的还原一律用**带唯一标记串的整行替换**（`/* MUTATION-PROBE-086-<页面> */`），
**全程未使用 `Edit` 的 `replace_all`**——2026-09-13 有一次 `replace_all: true` 命中文件里每一处 `true`、
改坏 6 处无关代码（`success: true` / `setModalOpen(true)` / `required: true`）而 `tsc` 与 `eslint` 全程沉默，
故此后把"短常见字面量不得用 replace_all"作为硬约束下发给每个批次，收尾时
`grep -rn "MUTATION-PROBE\|new Proxy({}, { get: () => true })" src/` **无输出**。

#### 覆盖面口径与一处**范围边界**（必须如实区分，不得合并成一个数字）

机械核对（脚本交叉 `permissions.ts` 的码表 × 页面源码的 `PERMS.*` 引用 × 测试文件里的码字面量）：
**63 个码被页面引用，其中 48 个的码字面量出现在测试里，15 个没有。**

| 类别 | 个数 | 结论 |
|---|---|---|
| 086 登记并接线的码（17 → 63 的增量） | **46** | **全部有双向渲染用例**（T090 的 39 个文件覆盖） |
| 原有 17 个码里、1.5 批 1~3 就已接线的 | **15** | `customer:{create,delete,import,claim,pool_manage}` / `contract:approve` / `quote:approve` / `lead:assign` / `order:delete` / `product:{create,update,delete}` / `campaign:{create,update}` / `role:manage`。**086 未改动其判据**，无渲染用例 |

原有 17 个码里唯一的例外是 `customer:update`——本会话补的 `CustomerDetailPage.perm.test.tsx` 覆盖了它。

⚠️ **两个必须说清的口径问题**：

1. **"码字面量出现在测试里" ≠ "有正向用例"**。上表 48 这个数里至少有一例是**负向夹具**：
   `campaign:delete` 只出现在 `LandingPageListPage.perm.test.tsx` 的"持别的码不放行"断言里，
   并**没有**任何测试断言"持 `campaign:delete` 者能看见什么"。故 48 是**码字面量覆盖数**，
   不是"有正向用例的码数"，两者不可互相替代。按 FR-B10 的字面（"**每个**被收口的码至少一条双向渲染用例"），
   上表第二类的 15 个码**也算缺口**；但它们**不在 086 的交付范围内**——086 的 Input（`spec.md:9`）
   把范围定为"45 个页面未接线 + 8 个已接线页面的漏网"，这 15 个码的判据在 086 之前就已存在。
2. **是否为这 15 个既成接线的码补测，是一个需要裁决的范围问题，不是本次的疏漏。**
   补测有真实回归价值（它们至今只被 ADMIN 直通路径覆盖，即 `CustomerListPage.test.tsx` 找「新增客户」按钮
   那种断言——**而 ADMIN 直通恰恰是最抓不到判据写错的那条路径**），
   但那属于 1.5 批 1~3 的补课，建议单独立项，不要塞进 086 的收尾。

#### 附带发现（**未修**，如实记录，避免静默扩大范围）

- **`pages/leads/LeadListPage.render.test.tsx:15` 的 mock 是失效的**：它在
  `vi.mock('../../services/leadService')` 的工厂里定义了 `fetchCampaigns`，
  而 `LeadListPage.tsx:30` 是从 **`marketingService`** import 该函数的 ⇒ 这个 mock **静默不生效**，
  真实调用落到 `setup.ts` 的 XHR no-op 上。与 084 已记录过的 mock 路径 bug 属**同一类**。
  **刻意不动**：修好 mock 路径后该测试可能从绿转红（它现在的"通过"部分建立在真实调用被吞掉之上），
  这需要它归属方的判断，不该由 086 顺手改掉——**改一个 mock 路径不等于修一个测试**。
- **在飞变异会造成瞬时 TS6133**（详见 T100 的说明）：接线的四个批次与补测的三个批次并行期间，
  三次出现 `'usePerms' is declared but its value is never read`。它们是"已 import、判据尚未落笔"的中间态，
  **不是缺陷**。其中两次被批次 agent 报告为"另一并行会话的在飞工作"——**归因错了**：
  那是**本次会话自己的兄弟批次**，只是在同一分钟改同一目录。
  教训：多 agent 并行时，agent 无法区分"兄弟 agent"与"外部会话"，主会话必须自己核对 mtime 后再采信归因。
