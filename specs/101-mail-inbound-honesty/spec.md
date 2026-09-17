# 101-mail-inbound-honesty：邮件同步收信侧诚实化（配置门）

**Feature Branch**: `101-mail-inbound-honesty`

**Created**: 2026-09-17

**Status**: 立项（Draft）

**Input**: 用户原话（2026-09-17，两句裁决）：① 收信侧方向 = **「诚实化（推荐）」**（不接真实 IMAP、也不从功能清单移除）；② 尺度 = **「配置门（推荐）」**——「默认（`crm.mail.inbound.*` 为空…）：同步端点明确回「未接入收信源」且**不写任何记录**；只有显式打开 demo 开关时才生成示例记录，且状态标为 `SIMULATED`（不再显示「已同步」）。保留 086 已收口的三个控件与权限用例结构，也保留一个真实可演示的入口。」

---

## 1 由来

### 1.1 上游那一行（原文逐字）

`CRM_FEATURE_COMPARISON.md:371`（第 6 节 P0 清单第 6 条）：

> | 6 | **邮件同步为模拟实现** | P0 | **部分闭合** | **发信侧**假成功已修（`MailStatus`/`SKIPPED`/`MailNotConfiguredException`）；**收信侧 `simulateSync` 仍在** → 仍属对外承诺不实，接 IMAP 或从功能清单移除 |

同一份文档的模块表 `:223` 与小结 `:228`：

> | 邮件同步 | Gmail/Outlook 插件 | `mail_account` + 同步记录 | ❌ **（收信侧）** | **`MailSyncRecordService.simulateSync` 为模拟实现**，不连任何服务器。⚠️ 一期只修了**发信侧**的假成功，收信侧仍是模拟——**不可对客户宣称已具备邮件同步** |

> **小结**：判定**全部未变**。这一域的最大缺口与 v2.0 相同——**邮件同步的收信侧仍是 `simulateSync`**，它是"诚信缺口"里唯一没被一期关掉的一条（因为一期明确只做诚实化与配置化，不接 IMAP）。

### 1.2 实测复核：这一行准确，且它描述的行为比文字更糟

立项勘察读了实现（不是「跑一遍看没红」）：

- `backend/src/main/java/com/crm/service/MailSyncRecordService.java` 的 `simulateSync` **无条件**插入一条记录：`direction=INBOUND`、`subject=「模拟同步邮件」`、`fromAddress=customer@example.com`、`toAddress=该账户邮箱`、**`syncStatus=SYNCED`**、`externalId=mock-<nanoTime>`。
- 唯一的生产调用点是 `controller/MailAccountController.java` 的 `POST /api/v1/mail-accounts/{id}/sync`（挂 `@RequirePermission("mail_sync:manage")`）。
- 前端 `frontend/src/pages/mail/MailSyncPage.tsx` 把 `syncStatus` 渲染成**绿色「已同步」**。

⇒ **每一调用一次，库里就多一条「已同步」的收件记录**，而发件人是硬编码的 `customer@example.com`。界面上**没有任何东西**能区分「真的从服务器收到了一封邮件」与「有人点了一下按钮」。这不是「功能尚未实现」，而是**已经实现了一条会生产假数据的通路**——与一期修掉的发信侧假成功（未发出的邮件被标成 `SENT`）是同一种病，只是长在收信侧。

### 1.3 一期做了什么、没做什么（这条边界必须说准，否则会误读本项）

| | 发信侧（030，一期诚信修复） | 收信侧（062，本项之前） |
|---|---|---|
| 假成功的形态 | SMTP 未配置时**静默跳过发送**，却把记录标成 `SENT` | **无条件**生成一条 `SYNCED` 的收件记录 |
| 一期的处置 | `MailStatus`（唯一判据）+ `EmailSendLog.STATUS_SKIPPED` + `MailNotConfiguredException` + 界面橙色「未发送」 | **一字未动** |
| 与外部系统的关系 | 不接 SMTP 时功能降级但**诚实** | 不接 IMAP，却**声称收到了** |

一期的范围声明（`specs/062-email-sync/spec.md:86`）是「v1 不接真实 IMAP/OAuth（模拟同步验证链路…）」——那句话对**产品**是如实的（它确实没接），但对**界面**不是：界面照着实现渲染，而实现写的是 `SYNCED`。

### 1.4 四条决定形状的实测事实

1. **062 的契约要求的就是那条不实行为**。`specs/062-email-sync/contracts/email-sync.md:32-40` 逐字写着「模拟同步（生成一条 INBOUND 记录，验证链路）。**Response 200**」并给出 `"syncStatus": "SYNCED"`；`:59-66` 的错误码表只有 4 行（`MAIL_EMAIL_INVALID` / `MAIL_EMAIL_DUPLICATE` / `MAIL_ACCOUNT_NOT_FOUND` / `MAIL_RECORD_NOT_FOUND`）。⇒ **本项是偏离契约，方向与 085 的判例相反**（085 是「契约对、实现错」；这里是**契约与诚信要求冲突**，取诚信并登记偏差）。详见 `contracts/mail-inbound.md`。
2. **不需要迁移，且本仓有先例**。`mail_sync_record.sync_status` 是 `VARCHAR(20)`：`V69__mail_sync.sql` 的 `COMMENT` 只是说明文字、**无 CHECK 约束**，测试用 `schema-h2.sql` 同样无约束。先例是 030 的 `SKIPPED`——`V48` 的 `COMMENT` 至今**只列 `'SENT/FAILED'`**、从不追认 `SKIPPED` ⇒ 本仓既定做法是**状态值域由实体常量维护、迁移 COMMENT 不追**。
3. **发信侧那套的每个零件都有现成落点**（本项逐件照形，不发明新范式）：判据类 `config/MailStatus.java`（`@Component` + `@Value("${crm.mail.host:}")` + `NOT_CONFIGURED_MESSAGE` 常量 + javadoc 自称「唯一判据，不要在别处重复判断」）；注入方式是**构造函数字段**（`EmailCampaignService`）；状态常量放在**实体上**（`EmailSendLog.STATUS_SENT`/`STATUS_SKIPPED`）；异常放 `com.crm.common`。
4. **前端状态列是二向渲染**（`MailSyncPage.tsx:207`：`SYNCED ? 绿「已同步」: 红「失败」`）⇒ 只把后端状态改成 `SIMULATED` 而不动前端，该记录会被渲染成**红色「失败」**——同样不是事实。**诚实化必须贯通到这一格**，且 `types/mail.ts:21` 的字面量联合类型 `'SYNCED' | 'FAILED'` 会替我们挡住「只改后端」。

## 2 用户故事

### US1 默认部署下，点「同步收件」不会产生任何假记录（P1）

作为**运维/部署方**，我在**没接 IMAP** 的默认部署里，希望收信同步端点**明确告诉我它没接**并且**什么都不写**，而不是每调一次就往库里塞一条「已同步」的收件记录。

**Why this priority**：这是本项的全部理由。今天这条通路会**生产假数据**且**冒充真实收信**——它比「功能没做」严重，因为它污染了数据、且对外可被读成能力。

**Independent Test**：默认配置下 `POST /api/v1/mail-accounts/{id}/sync` ⇒ **409 + `error.code == MAIL_INBOUND_NOT_CONFIGURED`**，且随后 `GET /api/v1/mail-accounts/{id}/records` 的 `total` **仍为 0**。

**Acceptance Scenarios**：

1. **Given** 默认配置（未设置 `crm.mail.inbound.demo-enabled`），**When** 调同步端点，**Then** 409 + `MAIL_INBOUND_NOT_CONFIGURED`，且**零插入**。
2. **Given** 同上，**When** 连调 5 次，**Then** 5 次都是 409，记录数**始终为 0**（连点不累积）。
3. **Given** 同上，**When** 传入不存在的账户 id，**Then** 仍是 **404 `MAIL_ACCOUNT_NOT_FOUND`**（既有语义不变；门在账户存在性之后）。

### US2 演示能力仍在，且它生成的记录**不会冒充**真实收信（P2）

作为**售前/演示方**，我需要一个能真实点出结果的入口（而不是「功能点不开」），但同时**不能让演示数据看起来像真的**。

**Why this priority**：用户裁决明确要求「保留一个真实可演示的入口」。演示是这个模块存在的现实价值（062 的 US 与 SC-E02 都围绕它），直接删会把这批从「诚实化」变成「删功能」。

**Independent Test**：显式打开 `crm.mail.inbound.demo-enabled=true` 后调同步端点 ⇒ 200，`syncStatus == SIMULATED`，界面把该行渲染成**橙色「模拟」**而非绿色「已同步」；记录数与主题都带演示标记。

**Acceptance Scenarios**：

1. **Given** 演示开关已打开，**When** 调同步端点，**Then** 200，`syncStatus == "SIMULATED"`，记录列表 `total` 为 1。
2. **Given** 该条演示记录，**When** 看它的 `subject` 与 `externalId`，**Then** 两者都明确带演示/非真实的标记（不是「模拟同步邮件」这种可被读成真实主题的措辞）。
3. **Given** 界面渲染该条记录，**When** 看状态列，**Then** 是「模拟」且**不是**「已同步」。

### US3 契约偏差有处可查，不是静默改写（P1）

作为**后来者**，我要能从 101 自己的契约里读到「062 原来承诺什么、本项为什么偏离、新契约是什么」，而不是自己发现 062 与实现对不上。

**Why this priority**：本仓的**原则一（契约不得被静默修改）**在「实现偏离契约」这个方向同样适用。本项目已有一次「实现偏离冻结契约而无人登记」的先例（`036` 的表单提交 429→400）：

**Independent Test**：`contracts/mail-inbound.md` 里能**逐字 grep 到** 062 原契约那两段原文（`Response 200` 与 `"syncStatus": "SYNCED"`），且偏差表逐行列出了「原约定 / 本项实现 / 为什么」。

**Acceptance Scenarios**：

1. **Given** 101 的 `contracts/mail-inbound.md`，**When** grep `Response 200`，**Then** 命中（旧值逐字保留）。
2. **Given** 同上，**When** grep `MAIL_INBOUND_NOT_CONFIGURED`，**Then** 命中新行为的完整声明。
3. **Given** `specs/062-email-sync/` 下的任何文件，**When** `git diff 101-立项前..交付后`，**Then** **零改动**（冻结决策件一字不动）。

## 3 功能需求

### 3.1 配置门（唯一判据）

- **FR-001** 新增 `com.crm.config.MailInboundStatus`（`@Component`），构造参数为 `@Value("${crm.mail.inbound.demo-enabled:false}") boolean demoEnabled`，暴露 `isDemoEnabled()`；常量 `NOT_CONFIGURED_MESSAGE = "未接入收信源（IMAP），同步未执行"`。
  形制逐件照 `config/MailStatus.java`（`@Component` + `@Value` 缺省值 + 消息常量 + 构造函数字段注入）。
- **FR-002** 该类的 javadoc 必须写明：**它是「收信侧能否生成记录」的唯一判据**；真接 IMAP 时改的是这里；**不要在别处重复判断配置**（照 `MailStatus` 的 javadoc 体例）。
- **FR-003** 默认值**两处同向**且都为 `false`：`application.yml` 的 `crm.mail.inbound.demo-enabled: ${CRM_MAIL_INBOUND_DEMO_ENABLED:false}` 与 Java 侧 `@Value` 的 `:false`。yml 的注释必须写明「**默认值的唯一真源是本行的 `false`；Java 侧的 `:false` 只是缺失兜底**」——本仓有过「代码默认 true、yml false」的陷阱先例（captcha），行形照 `crm.mail.host` / `captcha.enabled`。
- **FR-004** 配置键同时登记到 `.env.example`（邮件段）与 `docker-compose.yml`（`CRM_MAIL_*` 那组旁），注释照 030 那两处的体例：**解释「不设置会怎样」**，而不是只列变量名。

### 3.2 默认路径（拒绝且零副作用）

- **FR-005** `MailSyncRecordService` 的 `simulateSync` **改名 `triggerSync`**，方法体顺序固定为：① `accountService.require(accountId)`（账户不存在 ⇒ **404 `MAIL_ACCOUNT_NOT_FOUND`**，既有语义不变）→ ② 判门 → ③ 分支。
- **FR-006** 判门失败时抛 `com.crm.common.MailInboundNotConfiguredException`（新增，`extends BusinessException`，构造 `super(ErrorCode.MAIL_INBOUND_NOT_CONFIGURED, MailInboundStatus.NOT_CONFIGURED_MESSAGE)`）。
  ⚠️ **刻意与发信侧的裸 `RuntimeException` 不同**：发信侧那处由**调用方**决定降级（记 `SKIPPED`）还是失败，本处只有一条出路——回一个**受控状态码 + 稳定 code**。继承 `BusinessException` 让它落进既有的 `handleBusiness`，即使日后有人漏改处理器也不会掉进 `Exception` catch-all 变 500（100 的 `RateLimitExceededException` 已是这个形）。
- **FR-007** 新增 `ErrorCode.MAIL_INBOUND_NOT_CONFIGURED(409, "MAIL_INBOUND_NOT_CONFIGURED", "未接入收信源（IMAP），同步未执行")`，紧邻 mail 块（`MAIL_RECORD_NOT_FOUND` 之后）放置。
  **为什么是 409**（备选与否决理由留档）：501 的语义最贴（「服务端不支持该功能」）但**全仓 0 先例**（实测状态码分布为 400/401/403/404/409/422/429/500/503），引入一个客户端与八道门禁都没见过的状态类，收益全在语义纯度、成本落在生态；503 已被 `MFA_STORE_UNAVAILABLE` 占为**「依赖暂时不可用、可重试」**，本情形**不可重试**。409 在本仓有 30 处先例，语义族正是**「服务端当前状态不允许该操作」**，且日后真接上 IMAP 时「账户未启用 / 凭证缺失」仍会落回 409 ⇒ **这个码在真实实现里也活着**。
- **FR-008** 默认路径**零插入**：`insert` 不得被调用。**判据是正面断言**（单测 `verify(mapper, never()).insert(any())` + IT 里 `total == 0`），**不得**用「反正 `@Transactional` 会回滚」代替。

### 3.3 演示路径（显式开关）

- **FR-009** `demo-enabled=true` 时插入 **1 条**记录：`direction=INBOUND`、`syncStatus=MailSyncRecord.STATUS_SIMULATED`、`subject` 与 `externalId` **都带演示标记**（主题不得使用可被读成真实邮件的措辞；`externalId` 前缀由 `mock-` 改为 `demo-`），HTTP **200**（与 062 的状态码一致）。
- **FR-010** `entity/MailSyncRecord` 增加三个常量 `STATUS_SYNCED` / `STATUS_FAILED` / `STATUS_SIMULATED`（形制照 `EmailSendLog.STATUS_SENT`/`STATUS_SKIPPED`），并把类里 `/** SYNCED / FAILED。 */` 的注释订正为三值；服务里改用常量、不写字面量。
- **FR-011** **零迁移**：不新增 Flyway 脚本，`mail_sync_record` 的 DDL 与 COMMENT 一律不动（依据事实 1.4-2 与 030 的 `SKIPPED` 先例）。

### 3.4 控制器与可观测形态

- **FR-012** `MailAccountController` 的 `POST /{id}/sync`：`@Operation(summary = "触发收信同步（未接入收信源时拒绝）")`，javadoc 写明默认/演示两种结果；`@RequirePermission("mail_sync:manage")` **一字不动**（086 收口的两个码不合并）。
- **FR-013** 拒绝时**沿用全仓统一的 `ApiResponse` 错误信封**（走 `GlobalExceptionHandler`），`error.code` = `MAIL_INBOUND_NOT_CONFIGURED`、`error.message` = 那条中文说明。**不新增响应头**、不改任何全局错误处理。

### 3.5 前端（诚实化的界面落点）

- **FR-014** `frontend/src/types/mail.ts` 的 `syncStatus` 联合类型由 `'SYNCED' | 'FAILED'` 扩为三值（含 `'SIMULATED'`）。
- **FR-015** `frontend/src/services/mailService.ts` 的 `simulateSync` **改名 `triggerSync`**（路径与方法不变）。
- **FR-016** `MailSyncPage.tsx` 的状态列由二向改为**三向**：`SYNCED` → 绿 `tagSynced`；`SIMULATED` → **橙** `tagSimulated`；**其余（含未知值）→ 红 `tagFailed`**（保留「未知值兜底为失败」，不把未知状态外泄给用户）。
- **FR-017** **Alert 与按钮保留**（086 收口的三个控件与权限结构不动），只改文案与调用：`demoDataNoticeTitle` / `demoDataNoticeDesc` 的**键不动、值改写**（说清「默认会被拒绝、不产生记录」与「演示开关下生成的是标注为『模拟』的示例记录」两件事）；按钮改调 `triggerSync`。
- **FR-018** i18n：**删** `pages.mail.btnSimulateSync`（改名）、**增** `pages.mail.btnSyncInbox` 与 `pages.mail.tagSimulated`、**改值**（键保留）`pages.mail.msgSyncTriggered` / `demoDataNoticeTitle` / `demoDataNoticeDesc`。⇒ 键数 **2962 → 2963**、行数 **3452/3425 → 3453/3426**（差仍 27）。删除的键必须全仓 grep 零命中（孤儿键分支会兜底）。
- **FR-019** `MailSyncPage.perm.test.tsx`：mock 与按键键名同步改名（**四条既有用例的结构与断言对象不变**——这是「保留 086 结构」的落地形态），并**新增**「`SIMULATED` 渲染成『模拟』且不是『已同步』」与「未知状态仍渲染成『失败』」两条断言。

### 3.6 登记、契约与订正

- **FR-020** 产出 `contracts/mail-inbound.md`：给出**新的对外行为**（默认 409 / 演示 200），并**逐字收存** 062 的原约定作为偏差台账（`Response 200` 与 `"syncStatus": "SYNCED"` 必须可 grep 到）。**062 的任何文件一字不动**。
- **FR-021** 对比文档按「**订正不静默**」改写 7 处（`:223` / `:228` / `:318` / `:347` / `:371` / `:448` / `:477`）：原文逐字保留 + 带日期 ⚠️ 块，**粒度到每一列**，自查判据是「**旧值仍能被 grep 到**」；**判定列与分值一字不动**。
- **FR-022** ⚠️ **P0 第 6 条的状态列刻意不翻**（保持 `**部分闭合**`）：该行动作列给的两条出路——「**接 IMAP**」与「**从功能清单移除**」——本项**一条都没做**；本项关掉的是「**对外承诺不实**」这一半，能力缺口仍在。这与 100 对第 5 条的处置**不同**（那里翻了，因为动作列的**意图**已被落地的机制满足）——判断依据是**动作列的字面要求是否被满足**，逐条看，不套模板。
- **FR-023** `README.md` 的功能行（「邮件同步」那条）与 `PROJECT_FEATURES.md` 的模块表行改写为如实的收信侧说明；**同批**处理 `CRM_FEATURE_COMPARISON.md:448`（它把 README 那一行的**现文逐字**当证据引用——不改它，那句引文当场变成假话）。
- **FR-024** 数字落点一并收口（**一个数字住在好几个地方**）：i18n 键数与行数的 3 处（`PROJECT_FEATURES.md:19` / `:77` / `:111`）、Spec 模块数的 2 处（`PROJECT_FEATURES.md:20` / `:107`）与两份登记（`specs/README.md` 模块表与编号说明段、`specs/roadmap.md` 的 `## 当前进度` 与整体覆盖度）。旧值必须仍可 grep 到。
- **FR-025** 迁移数（89，`V1~V90`）的 4 个落点**一个都不用动**——本项零迁移（FR-011）。

## 4 非目标（明确不做，且各有理由）

1. **不接真实 IMAP**（用户裁决）。理由不只是工作量：真接 IMAP 需要凭证保管、增量拉取、失败重试与**映射到线索/联系人**的产品决策（哪封邮件算客户来信），那是另一个规格。
2. **不从功能清单移除**（用户裁决）。模块、菜单、路由、权限码全部保留。
3. **不改 062 的任何工件**（冻结决策件）。本项的偏差登记放在 101 自己的 `contracts/` 与 `research.md`。
4. **不新增 Flyway 迁移**。状态值域由实体常量维护（事实 1.4-2），因此迁移数的 4 个落点不动。
5. **不给前端加「演示模式」探针**。没有端点暴露该开关；为此新增端点会把本项从**收口**变成**开面**。
6. **不动 `.i18n-keys/**`**（gitignored 的遗留临时件，其 `.gitignore` 注释自称「合并后即弃」）与 `frontend/coverage/**` 生成物。
7. **不修 `apiClient.ts` 的 `isVersionConflict`**（它只按状态码 409 判定）。邮件页**没有**任何调用点会误读本项的 409（实测只用 `extractErrorMessage`）⇒ 只登记、不改共享工具，避免为一个不存在的调用点改全局语义。
8. **不动历史论述**：`V87` 的 SQL 注释、`RoleConstants` 与 `MailAccountController` 类 javadoc 里「要么丢掉模拟同步（打成 403）」那几句——权限拆分的理由仍然成立（同步面仍在、仍受 `mail_sync:manage` 管），且已应用的迁移永不编辑。

## 5 成功判据

### 可度量结果

- **SC-001** 默认部署下同步端点**零插入**：连调 5 次，`GET /records` 的 `total` 始终为 0，5 次全部为 409 + `MAIL_INBOUND_NOT_CONFIGURED`（实测）。
- **SC-002** 演示档下该端点返回 200、`syncStatus == SIMULATED`、`total == 1`（实测，隔离实例）。
- **SC-003** 界面上**不存在**任何会把 `SIMULATED` 读成「已同步」的通路：三向渲染用例断言「是『模拟』且不是『已同步』」（实测）。
- **SC-004** 062 的工件**零改动**（`git diff --stat` 对着该目录为空）。
- **SC-005** 八道门禁全绿（后端 `mvn -B verify` 的失败集合 ⊆ 4 例已批准偏差 + 本项新增全绿；前端 i18n/lint/typecheck/ui/zh + 定向 vitest）+ `i18n:check` 报 **2963/2963**、路由 58 / manifest 56 不变。
- **SC-006** 定向破坏 D1–D7 逐条**被观测到转红**（逐条 `cp` 还原，判据是 `git hash-object` 与破坏前相等）。

## 6 未验证边界（如实声明）

- **默认拒绝是行为变更，且它让那个按钮在默认部署下变成必然报错**。这是诚实的代价（用户已裁决保留入口），但它确实是一处**用户体验的退步**；本项用 Alert 与三处配置注释来让它可被理解，**不声称它「不影响用户」**。
- **`SIMULATED` 记录仍会出现在同步记录列表里**（演示档下）。它被标注、被染成橙色，但它**仍然是库里的一行**；本项不提供「一键清除演示数据」。若演示环境与生产共用库，需由部署方自行清理（本项不新增该能力）。
- **「未接入收信源」这一判断只看一个配置键**。它不探测 IMAP 是否真的可达——**键为 true 时插件不存在也照样生成演示记录**（键的语义是「允许生成演示记录」，不是「收信源可用」）；这个口径写在 javadoc 与 yml 注释里，**不得**被读成「连通性探测」。
- **本条与「企业级邮件同步」的距离不变**：无凭证保管、无增量拉取、无 OAuth、无附件解析。本项只是**把假数据通路关掉并把演示标注清楚**。
- **不验证真实 IMAP 场景**（因为没有实现）——本项没有任何一条用例能证明「接上 IMAP 后同步是对的」。
