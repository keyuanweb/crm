# 调研：前端按钮级权限收口（086）

**Created**: 2026-09-13

本文件是三份取证记录，直接决定 plan.md 的取舍：①为什么**不需要**补授权迁移；②哪些按钮**收不了口**；
③两处我曾判断错、以代码为准的订正。

---

## 1. 授权影响盘点：结论是「零迁移」

**决策 2 原定"严格隐藏 + 盘点后补授权迁移"。盘点做完了，结论是零迁移。**

方法：逐条重放 V46 → V87 的授权迁移，对每个本轮新增挂载的码，列出其**非 ADMIN 持有者**，
再判断挂码后前端会不会藏掉一个后端本会放行的能力。

### 判据（为什么可以推出"零迁移"）

```
hasPerm(code, user) = user.role === 'ADMIN' ? true : (user.permissions ?? []).includes(code)
```

**对非 ADMIN，它严格等价于 `permissions.includes(code)`**，而后端 `@RequirePermission` 切面判的是
**同一个集合**（`PermissionAspect` 对 ADMIN 同样直通）。两边同源 ⇒ 挂码后的按钮可见性 ≡ 后端放行集合。

由此，**唯一**能造成真切断的形态是：**端点无码，而我们硬挂一个码**——
此时后端"所有人都放行"，前端却按码收窄。这类端点已全部挑出，放进下面的「不可收口清单」，**一个都不挂**。

因此：**不存在「后端放行、挂码后前端会藏掉」的角色。**

### 表 1 里删除类按钮对若干角色"消失"——那是删死按钮，不是切断

| 码 | 今天点了必然 403 的角色 | 证据 |
|---|---|---|
| `announcement:manage` | SALES_MANAGER / MARKETING_MANAGER / FINANCE_MANAGER（持有「公告管理」菜单却无此码） | `V75:30,76,97` |
| `retention:*`、`export:scheduled`、`mail_account:manage`、`playbook:manage`、`open_platform:manage`、`integration:manage`、`field_permission:manage`、`tag:manage`、`stage:manage`、`retention:delete/execute` | **所有人**（非 ADMIN 持有者为零） | `V81:19-21`、`V87:41-42,53-56` |

其余各码的持有者清单一律对得上（`contact:delete` → SALES/SUPPORT/SALES_MANAGER/SALES_REP `V80:95-101`；
`ticket:assign` → SUPPORT `V46:128`、SUPPORT_MANAGER `V75:171`；`user:manage` → 四个 *_MANAGER `V75:144,177,204,229`；
依此类推）。

### 两处附带确认

- **`ticket:*` 对 SALES 的影响**：SALES 持有「工单」菜单（`V46:58`）但**无任何 `ticket:*` 写码** →
  工单详情页 4 个写按钮对 SALES 消失。后端今天同样 403，属**删死按钮**。
- **`contract_template:manage` 的行为等价性**：该码**零持有者**（`V87:41-42,53-56`），
  故 `ContractTemplateListPage` 从 `isAdmin` 硬编码改挂本码后，对非 ADMIN 的效果**逐字一致**，只是从此可勾选授予。

---

## 2. 不可收口清单（= 后端待加码工单）

**这份清单本身是 086 的可交付物之一。** 每一条都是"想收口就得先改后端建模"的实证。

| 页面 / 按钮 | 端点 | 为什么收不了口 | 要收口需要什么 |
|---|---|---|---|
| **`approval/ApprovalCenterPage` 通过 / 驳回 / 转交** | `ApprovalController.java:98-124` | **三个端点零注解、类上也无 `@PreAuthorize`**。判权在 `ApprovalEngineService.java:131` 的 `checkApprover(task)`——按**任务分配人**放行，与角色码无关。挂任何码都会与后端判据不一致（合法审批人可能没那个码 → 按钮消失） | 把审批权从「任务分配」改成「角色码」是一次**建模变更**，不是接线。字典里现成的 `approval:approve` 是死码，加了注解才可用 |
| `exports/ExportCenterPage` 下载（`:71`） | `ExportController.java:67-69` | 无注解。硬挂 `export:create` 会把"任何登录用户都能下载"变成"仅 SALES/SUPPORT/ADMIN 可见"，而后端仍放行 → **真切断** | 后端先给 `:67` 加码 |
| `custom-object/CustomObjectRecordPage` 删除记录（`:89`） | `CustomObjectController.java:135-136` | 只有 `@PreAuthorize("hasAnyRole('ADMIN','SALES')")`，5 个记录端点全无码，字典无 `custom_object_record:*` 族 | 建码族后单独立项。挂 `custom_object:delete` 会**双向错**：ANALYST 有码却被角色门挡住（露出 403 按钮）、SALES 被门放行却无码（按钮消失 = 真收窄） |
| `components/CommentSection` 删除评论（`:110`，判据 `:71`） | `CommentController.java:63` | 类级 `@PreAuthorize("hasAnyRole('ADMIN','SALES','SUPPORT')")`（`:36`），字典无 `comment:*` | 建 `comment:*` 码族 + 授予名单；类注释 `:23-33` 已书面裁决，单独立项 |
| `stats/DashboardPage` 设置目标（`:467`） | `StatsController.java:89-105` | 无注解，闸门是方法体内联的"`userId` 为空 ⇒ 仅管理员"。`kpi:view` 被 ANALYST 持有（`V83:14-15`）→ 挂它反而给 ANALYST 露出必然 403 的按钮 | 后端先加码（`quota:*` 管的是销售配额不是销售目标，不可复用） |
| `components/FollowUpTimeline` 编辑（`:129`，判据 `:98`） | `FollowUpController.java:70-71` → `follow_up:update` | 有码，但该判据下只有"编辑"，属**排除类**（编辑不收口）。`follow_up:delete` 是死码，本组件也没有删除按钮 | 不收口 |

### 纯本地 state / 只读，无可收口对象（不挂码）

`ApprovalFlowPage` 表单内节点删除（`:236`）与只读 `Switch`（`:277`）、`OnlineFormPage` 字段必填开关与删行（`:305/309`）、
`SegmentListPage` 筛选项删除（`:313`）、`QuoteListPage` 明细行删除与新建（`:155/237`）、
`SlaCalendarPage` 表单 `Switch` / 删时间段 / 保存（`:80/99/111`，全在同一个「保存」写入里）、
`ExportCenterPage` 刷新、`ScheduledExportExecutionHistoryPage` 刷新、
`DataRetentionPolicyListPage` 合规导出（只 `navigate()`，真正要收的是目标页的 `export:compliance`）、
`TicketListPage` 详情链接、`ContractListPage`（只有新建）、`ContractRenewalPage`（客户端分组）、
`SalesOpportunityListPage` 删除（**后端无 DELETE 端点、前端也无按钮**，不是漏接线）。

### 权限码陷阱

- `SegmentListPage` 的删除端点挂的是 **`tag:manage`**（`SegmentController.java:56-57`），
  **不是** `segment:manage`——后者零端点校验，是死码。挂它会让按钮对所有人消失，且
  `FrontendPermissionCodeAlignmentTest` 断言 2 会直接判红。
- **同一个码覆盖多个动作**，故**不得新增码**（FR-B03）：例 `email:manage` 同时管邮件模板删除、
  活动测试发送、退订恢复三个页面；`export:scheduled` 同时管暂停/恢复/删除/立即执行。

---

## 3. 订正：两处我此前判断错的地方

写规格的过程中有两处判断被代码推翻，记在这里以免后人重蹈。

### 3.1 "`CustomerDetailPage` 的 `canShare` 是缺陷" —— **错**

我此前断言 `CustomerDetailPage.tsx:179` 的 `canShare = isAdmin || data.ownerId === user.id`
是缺陷，理由是后端 `CustomerShareController` 要求 `customer:update` 而该码"仅 ADMIN 持有"，
故 SALES 作为 owner 会看到按钮却 403。

**错误在前提。** `customer:update` 的持有者是 SALES / SUPPORT（`V46:114,127`）
加 SALES_MANAGER / SALES_REP（`V75:137,154`）——`CustomerShareController.java:36-37` 的类注释
**逐字写了这份名单**，并解释了选它而非 `customer:transfer` 的原因：

> 用它会把 SALES_REP（一线最需要把客户共享给同事与主管的人）挡在门外

所以该页**不是漏接线**，而是判据写法要换：`isAdmin` → `hasPerm(PERMS.customerUpdate, user)`，
`ownerId === user.id` **保留**（业务归属规则，无码可替）。

**教训**：把"我推测的授予范围"当成事实来判定缺陷，是最容易出错的一步。

### 3.2 "『导出』类按钮一律收口" —— **错**

`QuoteDetailPage` 的「导出报价单 PDF」挂的是**读码** `quote:read`（`QuoteController.java:128-131`）；
合同附件「下载」挂 `contract:read`（`ContractAttachmentController.java:31`）。
能渲染出详情页的人**必然已持有该读码**，挂上去是**恒真的空动作**。

**正确口径**：「导出」类要看后端给该端点挂的是**读码还是写码**——
读码 ⇒ 不收口；写码（`export:create` / `export:scheduled` / `export:compliance`）⇒ 收口。

**教训**：按按钮**文案**归类（"这类叫导出"）会出错，必须回到**端点上的注解**。
