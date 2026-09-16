# 097 调研：096 遗留清单的逐项复核

**日期**：2026-09-16 · **方法**：全部读数来自**当前工作区代码与 git 对象库的命令输出**，不沿用 096 的任何历史数字。
**本文件是本项的核心交付物**——它回答的不是「怎么做」，而是「**每一项到底是什么**」。

---

## 0 一句话结论

096 登记的五笔账里，**三笔的射程与登记时完全不同**：
① 从 1 个死码变成 **22 个未接线码**（两种性质各 11）；② 从「差 1」变成**根因已定位的注释漂移**；
⑤ 从「V72 空缺」连带查出 **`PROJECT_FEATURES.md` §一 有 4 行已被 096 作废**。
③ 与 ④ 与登记一致，但**各有一个 096 当时不知道的事实**（③ 的删除不可逆；④ 的 081 实际是 13 个角色 / 另一套模型）。
⇒ 本项**不能照着清单字面动手**，处置全部改写（见 `spec.md` §3）。

---

## 1 ① 未接线权限码：从 1 个到 22 个

### 1.1 怎么量出来的

```bash
# 字典里的码（perm("x" 形式）
grep -oE 'perm\("[^"]+"' backend/src/main/java/com/crm/common/RoleConstants.java | sed 's/perm("//;s/"//' | sort -u
# 被注解引用的码（@RequirePermission("x")，含 4 处服务层全限定写法）
grep -rhoE '@RequirePermission\(([^)]*)\)' backend/src/main/java --include=*.java | grep -oE '"[^"]+"' | tr -d '"' | sort -u
# 差集
comm -23 /tmp/dict_codes.txt /tmp/used_codes.txt
```

**读数**：字典 **144** 码 / 被注解引用 **122** 码 / **差集 22**。
反向（注解用了字典没有的码）**为空**——那是 `RequirePermissionCatalogTest` 守的方向，本项不动它。

### 1.2 先排除「另有机制在校验」

在把 22 个码判成「不生效」之前，必须先证明**没有第二种码校验路径**（否则「暂无对应操作」是假话）：

```bash
grep -rn "hasAuthority\|hasPermission\|permissionsOf\|permissionCodes" backend/src/main/java --include=*.java
```

**读数**：只有两处——`PermissionAspect:40` 与 `TokenService:108`，两者都是 `roleService.permissionsOf(...)`：
前者是 `@RequirePermission` 的切面本体，后者是**把权限列表下发给前端**。
⇒ **码校验的唯一入口就是 `@RequirePermission`**（`SecurityUtil` 没有 `hasPermission`）。
再逐码扫 `src/main/java`，**22 个码除 `RoleConstants` 外零命中** ⇒ 判定成立。

### 1.3 两条既有裁决（决定处置，不能绕开）

| 出处 | 原文要点 |
|---|---|
| `RequirePermissionCatalogTest` 类注释 | 「反向（字典里有、注解没用）**刻意不断言**：字典同时承载菜单权限点与『将来要用』的码，**未使用是常态**」 |
| `specs/096-.../spec.md` §4 非目标 4 | 「**只建被端点真引用的码**」 |

**射程不同、不矛盾**：前者管**存量**（不因未用而删），后者管**新增**（不凭空造码）。
⇒ ① 的处置**只能是**：存量**登记成可核事实**、只对**新增**设护栏（`spec.md` §1.2）。
**删码这条路走不通**，除了违背上面的裁决，还有一条硬后果：22 个码里 **20 个已被 `V46`/`V75` 授给真实角色**，
删字典会直接撞 `PermissionMatrixIT`（已授予码 ⊆ 字典），除非再写一条**删除授权**的迁移去动已发布的权限语义。

### 1.4 22 条逐码复核（本项要冻结的就是这张表）

**A 类（11）——有对应操作，服务端由相邻的码放行**

| 码 | 对应端点（存在） | 现由谁放行 |
|---|---|---|
| `email:create` | `POST /email-templates`、`POST /email-campaigns` | `email:manage` |
| `email:update` | `PUT /email-templates/{id}` | `email:manage` |
| `email:delete` | `DELETE /email-templates/{id}`、`DELETE /email/unsubscribes/{id}` | `email:manage` |
| `email:send` | `POST /email-campaigns`（`summary = "创建并发送邮件群发"`）、`POST /email-campaigns/{id}/test` | `email:manage` |
| `invoice:create` | `POST /invoices` | `invoice:manage` |
| `invoice:update` | `POST /invoices/{id}/void`（状态变更 = 写） | `invoice:manage` |
| `segment:manage` | `POST` / `PUT /{id}` / `DELETE /{id}` of `/segments` | `tag:manage`（**未查见书面裁决**——该模块是 031-customer-tags） |
| `approval:create` | `POST /approval-flows` | `workflow:manage` |
| `approval:update` | `PUT /approval-flows/{id}` | `workflow:manage` |
| `approval:delete` | `DELETE /approval-flows/{id}` | `workflow:manage` |
| `approval:approve` | `POST /approvals/{id}/tasks/{taskId}/approve` | **故意不挂码**：`ApprovalEngineService.checkApprover` 是逐任务归属判定，096 §2 US3 已论证「挂码 = 从『分给你才能审』退到『有码就能审任何任务』」 |

**B 类（11）——全仓没有对应操作**（每条都注明了**最接近而不同**的那个端点，免得将来有人误判成 A）

| 码 | 最接近的端点（**不算对应**） | 为什么不算 |
|---|---|---|
| `invoice:delete` | 无（`InvoiceController` 只有 GET / POST / POST `{id}/void` / GET stats） | 没有删除动作，`void` 是作废（状态变更） |
| `follow_up:delete` | 无（`FollowUpController` 只有 GET / POST / PUT `{id}`） | 跟进记录没有删除端点 |
| `quote:delete` | 无（`QuoteController` 无 DELETE） | 报价单没有删除端点 |
| `ticket:approve` | `POST /tickets/{id}/transition` | 状态流转挂 `ticket:update`；工单没有「审批」动作（`TicketController` 类 javadoc 已明写） |
| `quota:delete` | 无（`SalesQuotaController` 无 DELETE） | `V87` 头注释已明写「本类没有删除动作」 |
| `contract:delete` | `POST /contracts/{id}/terminate` | **终止 ≠ 删除**；terminate 挂 `contract:update` |
| `customer:transfer` | `CustomerMergeController` 的 `POST /customers/merge` | **合并 ≠ 转移**；且它挂 `customer:merge` |
| `export:delete` | `ScheduledExportController` 的 `DELETE /{id}` | 那是**订阅**删除，挂 `export:scheduled`；一次性导出没有删除端点 |
| `opportunity:export` | `ExportController` 的 `POST /exports`（通用导出） | 通用导出挂 `export:create`，没有按业务对象分的导出端点 |
| `campaign:export` | 同上 | 同上 |
| `system:manage` | 无 | **全仓没有 `SystemController`**；`V46` 曾把它授给 ADMIN |

### 1.5 今天这件事写成什么样了

三处**有**书面理由（互不相见、无索引）：`RoleConstants:330-333`（`follow_up:delete`，并提到另两个「同一形态」）、
`QuoteController:50-54`（`quote:delete`）、`TicketController:41`（`ticket:approve`）。
**其余 19 个码没有任何地方说过话**——`grep` 只能证明它们存在，证明不了它们是**故意留的**还是**忘了接的**。
本项要消灭的正是这个状态（`spec.md` US1）。

---

## 2 ② R6 注释：11/9 vs 10/8，根因已定位

### 2.1 三个读数各自是什么

| 位置 | 内容 | 数 |
|---|---|---|
| `R6_ALLOWED` 数组 | **条目** 8 个（`App.tsx`、`LoginPage`、`MailSyncPage`、`ProductListPage`、`ContactListPage`、`ScheduledExportCreatePage`、`OnlineFormPage`、`OpportunityStagePage`） | 8 |
| 同上，各条 `count` 之和 | 1+1+**3**+1+1+1+1+1 | **10** |
| `CANDIDATE_READINGS.R6` | 注释写着「**裸写法**的 placeholder / aria-label（走 t() 的不计入）」 | **10** |
| `MIN_CANDIDATES.R6` | 反假绿下限（「至少见过一个裸写法」） | 1 |
| **块头注释** | 「实测 **11** 处，其中 **9** 处不该翻译」+「真正是缺陷的只有 **2** 处」（9+2=11） | **11 / 9** |

`R6_ALLOWED` 里 **2 条**是「**真缺陷但本轮不动**」（`App.tsx:623` 的 `aria-label="折叠/展开菜单"`、
`LoginPage` 验证码图的 `aria-label="验证码图片"`）+ **6 条**是「不是缺陷」（格式/单位/字段名示例）= 8 条、10 处。
⇒ **代码侧自洽是 10/8，注释侧是 11/9**。

### 2.2 根因

`ProductListPage` 那一条自己写着：

> 「① `Currency` **已修**：它是**字段提示词**……改用新键 `pages.product.list.priceCurrencyPlaceholder`」

⇒ 某次把 `Currency` 改走 `t()` 之后，**命中从 11 掉到 10、不该翻译的从 9 掉到 8**，
**该条目改了，块头的两个聚合数没跟着改**。这就是「差 1」的全部内容——**不是错，是漂移**。

### 2.3 为什么块头注释没有护栏

机器守的是 `R6_ALLOWED` 的 **count**（`ui:check` 会把实际命中与 count 核对，D3 破坏证明它有牙齿），
**块头注释里那两个数是给人读的**，没有任何断言看着它 ⇒ 本项对它**只能订正 + 留下复算命令**，
并**如实声明「无断言可观测转红」**（`falsification-evidence.md` 里写明，不沉默、不假称有护栏）。

---

## 3 ③ `.bak`：删除不可逆，但已核实没有独有信息

| 事实 | 读数 | 命令 |
|---|---|---|
| 未被跟踪 | `git ls-files --error-unmatch` 报 pathspec 不匹配 | `git ls-files --error-unmatch frontend/src/App.tsx.bak` |
| 被忽略 | `.gitignore:45` 的 `*.bak` | `git check-ignore -v frontend/src/App.tsx.bak` |
| 与现行文件不同代 | **498** 行 vs 现行 **890** 行；mtime 2026-08-23 23:55 | `wc -l` |
| **内容不在版本库里** | `git log --all --find-object=<blob>` **空**；全历史 `App.tsx` **无 498 行版本** | 见下 |
| 独有信息 = 无 | 路由集 54 条，差异**只有 `/board`** —— 那是「数据大屏」改名前的路径 | `comm -23` |

```bash
tr -d '\r' < frontend/src/App.tsx.bak > /tmp/bak.norm
git hash-object -w --stdin < /tmp/bak.norm          # 取 blob 后查可达性
git log --all --oneline --find-object=f3e8c6013f169b2f44ace54276a8eea06a957d43   # 空
for c in $(git log --format=%h --all -- frontend/src/App.tsx); do \
  [ "$(git show $c:frontend/src/App.tsx | wc -l)" = 498 ] && echo "命中 $c"; done          # 无命中
```

⚠️ **一处方法学注意**（写给自己与下一个读的人）：`git hash-object -w` **会把对象写进对象库**，
之后 `git cat-file -e` 必然成功——**那不是「历史里有」的证据**。判可达性必须用 `--find-object`（它查的是**可达性**）。
本项执行时踩过这一步，故在此写明。

**`/board` 的去向**：`68a14d5`（2026-09-04）的提交消息里有「i18n 修复: `/data-vision` 映射」，
页面从 `pages/board/KpiBoardPage.tsx` 变为 `pages/dataVision/DataVisionPage.tsx`，
现行 `App.tsx:435` 是 `{ path: '/data-vision', name: '酷炫大屏' }`。
⇒ **没有丢失任何路由或页面**，`.bak` 只是改名前的快照。

---

## 4 ④ 081：实际交付的模型与 `tasks.md` 写的不是一套

### 4.1 实际交付（按迁移与代码清点）

| 项 | 实际 |
|---|---|
| 角色 | **13 个**：`V46` 三个（`ADMIN`/`SALES`/`SUPPORT`）+ `V75` 十个（`SALES_MANAGER`/`SALES_REP`/`SUPPORT_MANAGER`/`SUPPORT_AGENT`/`MARKETING_MANAGER`/`MARKETING_SPECIALIST`/`FINANCE_MANAGER`/`FINANCE_ACCOUNTANT`/`ANALYST`/`VIEWER`） |
| 表 | 三张：`role`（含 `data_scope` ALL/DEPT/SELF、`built_in`、`enabled`、乐观锁 `version`）、`role_menu`、`role_permission` |
| 权限模型 | **码表**：`role_permission(role_id, permission_code)` + `RoleConstants.PERMISSION_DEFS`（分组 → `children[{code,label}]`），**无 `action` 列** |
| 菜单模型 | `MENU_TREE`（分组 → `children[{key,title}]`）+ `role_menu(role_id, menu_key)`，**无 `path` 列** |
| 初始化 | **Flyway 迁移**（`V46`/`V75`）**+** Java 常量字典，**无** `RolePermissionInitializerService` |
| 数据范围 | `role.data_scope` 列 + 服务层的 `DataPermissionService`/`EntityAccessService`（**手动调用**，无全局拦截——097 的 ① 与 086 都不动它） |
| 管理界面 | `RoleListPage.tsx`（`GET /roles/permission-defs` 供勾选、`GET /roles/menu-tree`） |
| 护栏（081 之后才立） | `PermissionMatrixIT`、`RequirePermissionCatalogTest`、`FrontendPermissionCodeAlignmentTest` |

### 4.2 偏差表（`tasks.md` / `design.md` 写了什么 vs 实际是什么）

| # | 计划（081 的工件里写的） | 实际 | 证据 |
|---|---|---|---|
| 1 | T001「**11 个**预置角色」 | 库存 **13** 个 | `design.md` §1.1 列的 11 个**含 ADMIN**，与既有的 `SALES`/`SUPPORT`（`V46` 已有）求并集 = 13 |
| 2 | T002/T003「40+ 权限模块 × 11 × 40+ 关联」 | **码表**模型，不是「模块 × 操作」矩阵；`V87` 时码数已到 100+ | `V46`/`V75` 的 `role_permission` 插入全是 `permission_code` 字面量 |
| 3 | T005「`RolePermission` 实体加 `action` 字段」 | **无** `action` 列，也未落实体 | `V46` 的 `role_permission` DDL |
| 4 | T006「`RoleMenu` 加 `path` 字段」 | **无** `path` 列，菜单用 `menu_key` 关联 `MENU_TREE` | 同上 |
| 5 | T007「`RolePermissionInitializerService` 启动时初始化、幂等」 | **Flyway 迁移**承担初始化；字典是 Java 常量（**不落库**） | `backend/src/main/resources/db/migration/V46,V75`；全仓无该类 |
| 6 | T009「数据范围控制 ALL/DEPT/SELF，查询时自动过滤」 | 有 `data_scope` 列与判定类，但**是手动调用、无全局拦截**（086 的清单第 11 项仍记着这条） | 见 `CRM_FEATURE_COMPARISON.md` P1 #11 |
| 7 | T016「E2E 测试」 | 本项**未见** 081 名下的 e2e | `frontend/e2e/` 7 个 spec 无 081 对应物 |
| 8 | §4.3/§5 的「单元测试覆盖率 80%+」 | 无该口径的留痕（覆盖率门槛是后来才立的） | — |

⚠️ **不臆断**：上表只写**可核的差异**。哪些是「计划改过」、哪些是「实现走偏」，**081 的工件里没有记录**，
本项**不替它下结论**——回填件照实写「未查见当时的裁决记录」。

---

## 5 ⑤ 编号空缺与规模数字

### 5.1 V72 与 069：全历史从未存在

```bash
git log --all --oneline --diff-filter=A -- '*V72*'      # 空
git log --all --oneline --diff-filter=A -- 'specs/069*'  # 空
ls backend/src/main/resources/db/migration/ | grep -E "V7[0-9]__"   # V71 … V73（无 V72）
```

⇒ 是**纯编号空缺**，不是「被删过」或「被改过名」。**两条处置都要否决**：
- **不补号**：补一个空的 `V72__noop.sql` 是**造一个假工件**，且它会进入迁移计数；
- **不改名**：把 `V73`+ 整体前移会**改变已应用迁移的版本号** ⇒ **破坏 Flyway checksum**，生产库会拒绝启动。
- `specs/069` 同理（`specs/README.md:137` 的编号说明**已登记**过这件事）。

### 5.2 `PROJECT_FEATURES.md` §一 的 4 行已被 096 作废

该表头自称「规模数字**于 2026-09-16** 按『依据』列口径重新实测」，但它列的批次**不含 096**。
今晚按**它自己的依据口径**逐行重测：

| 行 | 文中 | 实测 | 是否一致 |
|---|---|---|---|
| 后端 REST Controller | 66 | 66 | ✔ |
| 数据库表 | 86 | 86（`V90` 纯授权、不建表） | ✔ |
| **Flyway 迁移** | **88（V1–V89）** | **89（V1–V90）** | ✘ |
| 后端测试类 | 181（`src/test` 187） | 181 / 187 | ✔ |
| 前端页面组件 | 101（含测试共 **174**） | 101 / **176** | ✘（括注） |
| 前端路由定义 | 88（`<Route` 89 处 − `<Routes>` 1 处） | `<Route` **89** ⇒ 同口径 88 | ✔（口径含 `<Routes>`，别按 89 判错） |
| 前端 service | 56（`services/` 非测试 `.ts` 共 60） | 60 | ✔ |
| **前端单测 / E2E** | **89** / 7 | **91** / 7 | ✘ |
| i18n 资源 | zh 3393 行 / en 3395 行 | 3393 / 3395 | ✔ |
| **Spec 模块** | **94（001–095，缺 069）** | **95（001–096，缺 069）** | ✘ |

⇒ 4 行要改（迁移 / spec 模块 / 前端单测 / 页面组件括注），其余 6 行**保留原值**并把实测命令留痕（上表即留痕）。

### 5.3 迁移计数是「一个数字住在 6 个地方」

096 改过 **5 处**（`specs/README.md` 标题与版本行、`INSTALL.md` ×2、根 `README.md` 目录树），
**第 6 处就是本文件**（`PROJECT_FEATURES.md` §一）——096 当时漏了。
本项补上后**六处一致**（`specs/README.md` 与 `INSTALL.md` 的现值今晚核对均为 89，无需再动）。

---

## 6 给实施阶段的三条纪律（从上面的读数直接推出）

1. **护栏断言集合、不断言空集**——否则等于推翻 `RequirePermissionCatalogTest` 的书面裁决。
2. **A 类不得被写成「暂无对应操作」**——它有操作，只是由相邻码放行。两类措辞必须不同，
   本项因此**不动任何 label**（要动就得设计两套措辞，另立）。
3. **删除类动作先取两份读数再动手**（是否在版本库里 / 有没有独有信息），读数进留痕——
   因为删除**不可逆**，留痕是唯一的凭据。
