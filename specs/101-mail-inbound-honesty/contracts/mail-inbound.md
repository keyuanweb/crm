# 契约：邮件收信同步端点（101）

**Type**: 端点行为契约（请求结构不变，**响应状态码与状态值域变更**）

**Base**: `/api/v1/mail-accounts`

**端点**: `POST /mail-accounts/{id}/sync`（`mail_sync:manage`）

**由本文件取代的约定**: `specs/062-email-sync/contracts/email-sync.md` 的 `POST /mail-accounts/{id}/sync` 一节（**该文件一字未改**，其原文逐字收存于本文 §2）

---

## 1. 为什么这份契约存在

062 的契约是**冻结**的，而 101 的实现**偏离**了它。按章程**原则一（契约优先的 API 设计，不可协商）**——「契约不得被静默修改，任何偏差必须明确说明理由并登记」——偏离同样需要一个登记位置。

⚠️ **本项的偏离方向与 085 的判例相反，不得混用**：

| | 085（`GlobalExceptionHandler` 的注释即判例） | 101（本项） |
|---|---|---|
| 情形 | `FormService` 在限流时抛 400，而 `036` 的契约承诺 **429** | 062 的契约承诺「模拟同步、**Response 200**、`syncStatus: SYNCED`」，而这条行为**本身就是不实的** |
| 谁对 | **契约对**，实现错 | **契约写下了不实的行为**（它描述的正是那条会把假数据标成「已同步」的通路） |
| 处置 | 契约一个字符不改，**改实现** | 062 **仍然一个字符不改**（冻结决策件），但变更登记**写在 101 自己的契约里**，且 101 的规格内说明「此处取诚信、登记偏差」 |

**为什么不能沿用「改实现、契约不动」**：那样只能让实现变成契约要求的样子——即继续生产 `SYNCED` 的假记录。⇒ 本项的选择是：**保留旧契约作为历史留痕**（它如实地记录了 062 当时决定做什么），**由 101 的契约给出新的对外行为**，并把两者之间的差异逐条写在 §3。

## 2. 被取代的原文（**逐字保留，可 grep**）

### 2.1 062 的端点约定（`specs/062-email-sync/contracts/email-sync.md:32-40`）

> ### POST /mail-accounts/{id}/sync
>
> 模拟同步（生成一条 INBOUND 记录，验证链路）。**Response 200**
>
> ```json
> { "id": 10, "accountId": 1, "direction": "INBOUND", "subject": "模拟同步邮件",
>   "fromAddress": "customer@ext.com", "toAddress": "sales@corp.com",
>   "syncStatus": "SYNCED", "syncTime": "..." }
> ```

### 2.2 062 的错误码表（同文件 `:59-66`，**逐字**）

> | code | status | 含义 |
> |---|---|---|
> | MAIL_EMAIL_INVALID | 422 | 邮箱格式不合法 |
> | MAIL_EMAIL_DUPLICATE | 409 | 邮箱已存在 |
> | MAIL_ACCOUNT_NOT_FOUND | 404 | 账户不存在 |
> | MAIL_RECORD_NOT_FOUND | 404 | 同步记录不存在 |

### 2.3 062 的范围声明（`specs/062-email-sync/spec.md:86`，**逐字**）

> - v1 不接真实 IMAP/OAuth（模拟同步验证链路；数据模型含外部邮件 id 占位，真实对接时填充）。

### 2.4 062 的功能需求（`specs/062-email-sync/spec.md:62-64`，**逐字**）

> - **FR-E05**: 系统必须提供模拟同步端点（生成同步记录）。

## 3. 变更对照

| 维度 | 062 原约定（§2） | 101 实现 | 不变的部分 |
|---|---|---|---|
| 默认（未配置 `crm.mail.inbound.demo-enabled`） | 200 + 一条 `SYNCED` 记录 | **409 + `error.code = MAIL_INBOUND_NOT_CONFIGURED`**，message「未接入收信源（IMAP），同步未执行」，**零插入** | 端点路径、方法、权限码（`mail_sync:manage`）、错误信封（`ApiResponse`） |
| 演示（显式 `demo-enabled=true`） | 同左（062 里**没有**开关概念，只有一种行为） | 200 + 一条 `SIMULATED` 记录 | 200、响应结构（`MailSyncRecordResponse` 字段不变） |
| `syncStatus` 值域 | `SYNCED` / `FAILED`（`V69` 的 COMMENT 如此，**无 CHECK 约束**） | 增 **`SIMULATED`**（`MailSyncRecord.STATUS_SIMULATED`） | 值域的**维护方式**：由实体常量维护，迁移 COMMENT 不追（030 的 `SKIPPED` 是同一处置） |
| 记录的主题 / 外部 id | `模拟同步邮件` / `mock-<nanoTime>` | 带**演示标记**的主题 / `demo-<nanoTime>` | 字段名与类型 |
| 账户不存在 | 404 `MAIL_ACCOUNT_NOT_FOUND` | **不变**（门在账户存在性**之后**） | 全部 |
| 错误码表（§2.2） | 4 行 | 增第 5 行（下表） | 原 4 行不变 |

### 3.1 新增错误码

| code | status | 含义 | 何时 |
|---|---|---|---|
| `MAIL_INBOUND_NOT_CONFIGURED` | **409** | 未接入收信源（IMAP），同步未执行 | 默认配置下调用该端点 |

**为什么是 409**（备选否掉的理由，与 `spec.md` FR-007 同一口径）：501 语义最贴但**全仓 0 先例**（实测状态码分布 400/401/403/404/409/422/429/500/503），引入客户端与八道门禁都没见过的状态类，收益在语义纯度、成本在生态；503 已被 `MFA_STORE_UNAVAILABLE` 占为**「依赖暂时不可用、可重试」**，本情形**不可重试**。409 在本仓有 30 处先例，语义族是**「服务端当前状态不允许该操作」**，且日后真接上 IMAP 时「账户未启用 / 凭证缺失」仍会落回 409 ⇒ 这个码在真实实现里也活着，不是一次性占位。

⚠️ **一条已知口径**：`frontend/src/services/apiClient.ts` 的 `isVersionConflict` **只按状态码 409 判定**（不按 `error.code`），其注释自称「409 = 乐观锁冲突」。本项的 409 **不会**被误读——邮件页只用 `extractErrorMessage`（实测无任何调用点接 `isVersionConflict`）。**但**日后若有调用点把该工具接到邮件页，就会把这条 409 读成「数据已被他人修改」。此口径登记在 `research.md`，本项**不改** `apiClient`（不为一个不存在的调用点改全局语义）。

## 4. 生效后的可观测行为（可验证的预期）

| 场景 | 请求 | 预期 |
|---|---|---|
| 默认 + 账户存在 | `POST /api/v1/mail-accounts/1/sync` | **409**，`error.code = MAIL_INBOUND_NOT_CONFIGURED`；随后 `GET /records` 的 `total` **不变**（仍为原有条数） |
| 默认 + 账户不存在 | `POST /api/v1/mail-accounts/999999/sync` | **404**，`error.code = MAIL_ACCOUNT_NOT_FOUND`（与 062 一致） |
| 默认 + 无 `mail_sync:manage` | 同上（SALES 除外的主体） | **403**，`error.code = PERMISSION_DENIED`（权限先于业务，086 的两码不合并） |
| 演示 + 账户存在 | 同上 | **200**，`data.syncStatus == "SIMULATED"`，`data.subject` 带演示标记；`GET /records` 的 `total` **+1** |

**授权的强制点仍在服务端**：`@RequirePermission("mail_sync:manage")` 由切面在方法调用前判定，前端不参与（章程原则三）。本项**不动**该注解。

## 5. 与 062 的关系（边界声明）

- 062 的 `spec.md` / `plan.md` / `contracts/` / `tasks.md` / `quickstart.md` **全部一字不动**——它们是 062 当时决策的如实记录，**不是**待修订的现行文档。
- 本文件是**现行**的对外约定；两者冲突时以本文件为准（冲突点限于 §3 表格所列的四个维度）。
- 本项**不重新审议**「062 该不该做成模拟」——那是 062 当时的既定决策（`spec.md:86` 的假设已写明 v1 不接真实 IMAP）。本项只处理**它产出的假数据与不实展示**。
- 反过来，**本项也不宣称 062 决策「有错」**：062 交付了一套可演示的链路，那在演示语境下有真实价值；缺陷是它在**默认部署**下也照样生产被标成「已同步」的记录。

## 6. 验证方式

- **单测**：`service/MailSyncRecordServiceTest`——默认路径抛 `MailInboundNotConfiguredException` 且 `verify(mapper, never()).insert(any())`；演示路径返回 `SYNCED`→`SIMULATED` 的记录。
- **集成**：`integration/EmailSyncIT`——默认路径断言 409 + `error.code`，并**正面断言** `GET /records` 的 `total == 0`；`integration/MailInboundDemoIT`（`@TestPropertySource(properties = "crm.mail.inbound.demo-enabled=true")`）断言 200 + `SIMULATED` + `total == 1`。
- **授权面**：`integration/PermissionEnforcementIT` 里 SALES 调该端点的探针由 `isOk()` 改为 `isConflict()`（它断言的是「**不是 403**」，即 `mail_sync:manage` 确实被授予）。
- **界面**：`pages/mail/MailSyncPage.perm.test.tsx` 断言 `SIMULATED` 渲染成「模拟」且**不是**「已同步」。
- **手工**：`quickstart.md` 的隔离实例配方——默认档连调两次（409 + 零累积），演示档重启一次（200 + 一条 `SIMULATED`）。
