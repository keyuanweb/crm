# 实施计划：前端按钮级权限收口（086）

**Created**: 2026-09-13

**Spec**: [spec.md](./spec.md)　**Evidence**: [research.md](./research.md)　**Tasks**: [tasks.md](./tasks.md)

## 摘要

把 45 个未接线页面 + 8 个已接线页面里漏网的范围内按钮，按**其后端端点上的同一个码**收口；
并新增 `check-perms.mjs` 护栏，使"挂错码"从此可被机器抓到。**零后端改动、零迁移、零新增权限码。**

## 技术上下文

| 项 | 值 |
|---|---|
| 语言 / 框架 | TypeScript 5 + React 18 + antd v5 + ProComponents + React Query |
| 判定原语 | `hasPerm`（`hooks/usePermission.ts`）、`usePerms`（`hooks/usePerms.ts`）、`PermissionGuard`（`components/PermissionGuard.tsx`） |
| 权限码表 | `frontend/src/constants/permissions.ts`（17 → 62 个码） |
| 测试 | vitest + RTL，`renderWithProviders`（`src/test/renderWithProviders.tsx`） |
| 门禁 | `typecheck` / `lint` / `i18n:check` / `menu:check` / **`perms:check`（新增）** / `test:coverage`；后端 `mvn -B verify` 的 `FrontendPermissionCodeAlignmentTest` |
| 存储 / 契约 | **无改动** |

## 章程检查

| 原则 | 本规格 |
|---|---|
| 一期范围（速赢与止损） | ✅ 属 1.5 权限接线的未交付半场 |
| 诚信缺口零容忍 | ✅ 直接消除"界面宣称可用、实际 403" |
| 不可协商：门禁必须被实际判定 | ✅ 新增护栏，且**要求实测它会转红**（US4 场景 5） |
| 零后端改动 | ✅ FR-B11 |

## 调研结论（详见 research.md）

1. **不需要任何补授权迁移。** `hasPerm` 对非 ADMIN 严格等价于 `permissions.includes(code)`，
   后端切面判同一个集合 ⇒ 挂码后可见性 ≡ 后端放行集合。唯一能真切断的形态是「端点无码却硬挂号」，
   这类端点已全部进入不可收口清单，**一个都不挂**。
2. **不可收口清单** 6 条（审批中心三按钮、导出下载、自定义对象记录、评论删除、销售目标设置、
   `FollowUpTimeline` 编辑）—— 是后端待加码工单，写进 spec 交付。
3. **两处订正**：`CustomerDetailPage` 的 `canShare` 不是缺陷（换判据写法即可）；
   "导出"类要按**读码/写码**分，不按文案归类。

---

## 实现策略

### 阶段 0：先合并码表（唯一的前置）

`permissions.ts` 登记 45 个新码（17 → 62），**立刻跑后端护栏**
`FrontendPermissionCodeAlignmentTest` 验证。这一步是**失败快**的：45 个码里任何一个
不在字典里或未被真实校验，都会在这一步被拒，而不是等到 45 个页面都改完之后。

已完成并验证：`Tests run: 2, Failures: 0, Errors: 0`。

格式是硬约束：每码一行，形态 `^\s+[A-Za-z0-9_]+:\s*'([^']+)',`（该测试的解析正则，行尾不锚定），
插在 `export const PERMS = {` 与 `} as const` 之间。

### 阶段 1：护栏脚本（在接线之前）

**先写护栏再接页面**，理由：145 个判定点写完就没有第二次机会逐一复核。
护栏先就位，后续每个页面的改动都即时受检。

`frontend/scripts/check-perms.mjs` 四项检查：

| # | 检查 | 实现要点 |
|---|---|---|
| 1 | `role === 'ADMIN'` 硬判断 | 扫 `src/**/*.{ts,tsx}`，**跳过注释行**（`App.tsx:488` 是注释），命中即非零退出并打印 文件:行号 |
| 2 | `PERMS` 值不重复 | 解析 `permissions.ts`，值集合去重后长度须等于总条数 |
| 3 | 无未定义引用 | 扫 `PERMS.(\w+)`，每个引用键须存在于 `PERMS` |
| 4 | 白名单 | 见下，**每条附理由**——白名单没有理由注释就会变成藏污纳垢处 |

**白名单（两类）**：

| 类别 | 文件:行 | 理由 |
|---|---|---|
| 合法短路 | `components/PermissionGuard.tsx:36` | 组件本身的 ADMIN 直通语义 |
| 合法短路 | `constants/menuVisibility.ts:42` | 菜单可见性的 ADMIN 兜底 |
| 合法短路 | `hooks/usePermission.ts:10,34` | `hasPerm` / `useHasMenu` 的原语实现 |
| **非权限判断** | `pages/users/UserManagementPage.tsx:36` | 角色 → Tag **颜色**，与权限无关 |
| **非权限判断** | `pages/stats/DashboardPage.tsx:272` | 管理员 → 选一条**文案**，未做权限收窄 |

后两条是**朴素扫描的必然误报**。白名单必须能挡住它们，否则开发者会学会忽略这个脚本——
那比没有脚本更糟。

`package.json` 加 `"perms:check": "node scripts/check-perms.mjs"`；
`.github/workflows/ci.yml` 的 frontend job 在 `menu:check` 之后加一步（与既有两步同形）。

### 阶段 2：逐页接线

**照抄已接线的 8 个页面**（`CustomerListPage.tsx` 是范本），不发明新写法：

| 场景 | 写法 | 范本 |
|---|---|---|
| 列表工具栏 | `const can = usePerms([...])` + `toolBarRender={() => [...(can.x ? [<Button/>] : [])]}` | `CustomerListPage.tsx:83,351-396` |
| 表格行内动作 | 三元 `cond ? <a/> : null` 或 `cond && <a/>`（数组元素位置写 `null`） | `CustomerListPage.tsx:267-290`、`ProductListPage.tsx:190-203` |
| 详情页 / 整块条件渲染 | `<PermissionGuard permission={...}>` | `components/PermissionGuard.tsx`（本规格首次投入使用） |
| 条件与业务状态并存 | `hasPerm(...) && status === 'X'` | `ContractDetailPage.tsx:119` |

**约束**：`usePerms` 必须在顶层无条件调用（Hooks 规则），且返回**新对象**、不可进 `useMemo`/`useEffect` 依赖。

### 阶段 3：测试

每个被收口的码**至少一条双向渲染用例**。负向用例**必须用非 ADMIN 用户**
（`role: 'SALES'`, `permissions: []`）——ADMIN 在 `hasPerm` 里直通，永远拿不到"看不见"的结论。
`CustomerListPage.test.tsx` 依赖该直通语义找「新增客户」按钮，**不可改**。

`src/test/setup.ts:95-123` 的 `react-i18next` mock 在缺键时**抛错**（import 了真实 `zh-CN.ts`），
故可以断言按钮的**文案**（i18n key 已被解析）而不只是断言 DOM 结构。

---

## 收口点映射（按码归组）

> 页码为改造前（HEAD `b4baf59`）的行号，供定位用，不作为改动契约。

| 码 | 页面 → 按钮 |
|---|---|
| `announcement:manage` | `announcements/AnnouncementPage` → 删除（`:150`） |
| `workflow:manage` | `approval/ApprovalFlowPage` → 删除审批流（`:287`） |
| `workflow:update` / `workflow:delete` | `workflows/WorkflowRuleListPage` → 启停（`:189`）/ 删除（`:195`） |
| `call_record:delete` | `calls/CallRecordPage` → 删除（`:91`） |
| `contact:delete` | `contacts/ContactListPage` → 删除（`:178`） |
| `contract_template:manage` | `contract-templates/ContractTemplateListPage` → 删除（`:36` 判据、`:77` 入口、`:152` 消费）——**替代 `isAdmin` 硬编码** |
| `customer:merge` | `customers/DuplicateMergePage` → 扫描（`:77`）/ 合并（`:135`，不可逆） |
| `custom_object:update` / `custom_object:delete` | `custom-object/CustomObjectListPage` → 启停（`:53`）/ 删除（`:56`） |
| `retention:delete` | `data-retention/DataRetentionPolicyListPage` → 删除（`:104`） |
| `retention:execute` | `data-retention/DataRetentionExecutionHistoryPage` → 立即执行（`:94`） |
| `department:manage` | `departments/DepartmentListPage` → 删除（`:189`） |
| `email:manage` | `email/EmailUnsubscribePage` → 恢复（`:37`）；`marketing/EmailTemplatePage` → 删除（`:126`）；`marketing/EmailCampaignPage` → 测试发送（`:181`） |
| `export:create` | `exports/ExportCenterPage` → 导出（`:94`） |
| `export:scheduled` | `exports/ScheduledExportListPage` → 暂停/恢复（`:122/126`）/ 删除（`:130`）；`exports/ScheduledExportExecutionHistoryPage` → 立即执行（`:98`） |
| `invoice:manage` | `invoices/InvoiceListPage` → 作废（`:155`） |
| `knowledge:update` / `knowledge:delete` | `knowledge/KnowledgeArticleListPage` → 发布/下架（`:145/149`）/ 删除（`:156`） |
| `lead:delete` | **`leads/LeadListPage` → 删除线索（漏网）** |
| `marketing:manage` | `landing/LandingPageListPage` → 删除（`:114`） |
| `mail_account:manage` / `mail_sync:manage` | `mail/MailSyncPage` → 删除邮箱账号（`:60`）/ 模拟同步（`:253`）/ 删除同步记录（`:210`） |
| `form:manage` | `marketing/OnlineFormPage` → 启停 `Switch`（`:188`）/ 删除（`:205`） |
| `open_platform:manage` | `open/OpenPlatformPage` → 撤销 API Key（`:67`）/ webhook 启停（`:200`）/ webhook 删除（`:203`） |
| `opportunity:delete` | `opportunities/OpportunityListPage` → 删除（`:135`） |
| `opportunity:update` | `sales-opportunities/SalesOpportunityListPage` → 赢单/输单（`:139/146`）**（看板拖拽刻意不收）** |
| `playbook:manage` | `playbook/StageActionTemplatePage` → 删除（`:157`） |
| `recycle:restore` / `recycle:purge` | `recycle/RecycleBinPage` → 恢复（`:106`）/ 彻底删除（`:111`） |
| `currency:manage` | `settings/CurrencyRatePage` → 删除（`:53`） |
| `custom_field:delete` | `settings/CustomFieldListPage` → 删除（`:150`） |
| `field_permission:manage` | `settings/FieldPermissionPage` → 删除（`:95`） |
| `integration:manage` | `settings/IntegrationHubPage` → 启停（`:57`）/ 删除（`:63`） |
| `stage:manage` | `settings/OpportunityStagePage` → 停用/启用（`:189/197`）/ 删除（`:207`） |
| `sla:manage` | `sla/SlaPolicyListPage` → 删除（`:142`） |
| `tag:manage` | `tags/TagListPage` → 删除（`:86`）；**`tags/SegmentListPage` → 删除（`:189`，挂 `tag:manage`！）** |
| `task:update` / `task:delete` | `tasks/TaskListPage` → 完成/重开（`:211/215`）/ 删除（`:222`） |
| `ticket:delete` / `ticket:assign` / `ticket:update` / `ticket:reply` | `tickets/TicketDetailPage` → 删除（`:148`）/ 分配（`:161`）/ 开始处理·已解决·关闭（`:163/168/173`，**整组收**）/ 发送回复（`:278`） |
| `user:manage` | `users/UserManagementPage` → 数据权限（`:234`）/ 重置密码（`:237`）/ 启停（`:240`） |
| `visit:manage` | `visits/VisitListPage` → 签到（`:205`）/ 取消（`:211`） |

### 已接线页面里的漏网（本规格一并收口）

| 页面 | 漏网按钮 | 码 | 依据 |
|---|---|---|---|
| `leads/LeadListPage.tsx` | 删除线索（已 import `deleteLead`，无判定） | `lead:delete` | `LeadController:127` |
| `contracts/ContractDetailPage.tsx` | 终止合同 | `contract:update` | `ContractController:138-144`（`:39-40` 注释：状态推进在语义上就是改这份合同） |
| `contracts/ContractDetailPage.tsx` | 删除附件 | `contract:update` | `ContractAttachmentController:82-86` |
| `customers/CustomerDetailPage.tsx` | 共享（`canShare`） | `customer:update` | `CustomerShareController:52,60`；**`isAdmin` 换成 `hasPerm`，`ownerId === user.id` 保留**（见 research.md §3.1） |
| `quotes/QuoteDetailPage.tsx` | 导出报价单 PDF | **不收口** | `QuoteController:128-131` 挂的是读码 `quote:read` → 恒真空动作（research.md §3.2） |

---

## 风险与缓解

| 风险 | 缓解 |
|---|---|
| **挂错码 → 按钮静默消失**（本项唯一的高频错法） | 45 条已逐条核过 `PERMISSION_DEFS` ∧ `@RequirePermission`（阶段 0 已实测通过）；`FrontendPermissionCodeAlignmentTest` + `perms:check` 两道机器校验兜底 |
| **把后端没码的端点硬挂上码 → 真切断** | 不可收口清单已穷举该类端点，**一个都不挂**；盘点结论：不存在其余形式的真切断 |
| **`ContractTemplateListPage` 改挂码后行为变化** | `contract_template:manage` 零持有者 ⇒ 对非 ADMIN 恒 `false`，与 `isAdmin` **逐字等价**，只是从此可勾选授予 |
| **改动面 ~49 个页面** | 每个码双向渲染用例；先合并码表 + 护栏，再逐页接线；分批跑门禁 |
| **护栏本身没被验证过** | US4 场景 5 要求**实测三种缺陷各转红一次**并记录在 tasks.md |

## 验证

```bash
cd frontend
pnpm typecheck && pnpm lint && pnpm i18n:check && pnpm menu:check && pnpm perms:check && pnpm test:coverage
```

```bash
cd backend && mvn -B verify
```

- **`perms:check` 要验它真会红**：分别注入「删掉一个 `PERMS` 键」「加一条 `role === 'ADMIN'` 硬判断」
  「注册一个重复值」，三次都确认非零退出后还原。**未验证过会红的门禁等于没有门禁**（本仓库 083 的核心教训）。
- **后端一起跑**：`FrontendPermissionCodeAlignmentTest` 的断言 2 是 `registered ⊆ enforced`，
  会在 CI 的 backend job 里独立于前端守卫这次登记。
- ⚠️ **e2e 只自起前端**（playwright 不打后端）。按钮可见性属纯前端渲染，e2e 可覆盖；
  但**不能用它证明后端权限行为**。
- ⚠️ 仓库常有并行会话共用工作区：**不得 `git add -A`**，只按显式路径暂存；提交需明确口令。

## 不覆盖的范围

- 后端权限建模改造（审批中心按任务分配判权 → 角色码）：见 research.md 不可收口清单，单独立项。
- 新建/编辑/导入按钮的收口（不在高权与破坏性操作之列）。
- 看板拖拽改阶段的收口（决策 7）。
