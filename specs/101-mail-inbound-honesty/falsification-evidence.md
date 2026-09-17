# 定向破坏与订正留痕（101-mail-inbound-honesty）

**日期**：2026-09-17（立项批 · C1）· **配套**：[`quickstart.md`](./quickstart.md) · [`plan.md`](./plan.md) · [`tasks.md`](./tasks.md) · [`research.md`](./research.md)

> **本件在立项期写成骨架，但骨架里已经定稿的是「判据」，不是「读数」**：§A–§G 每一条破坏的
> 「**它该改变哪条可观察行为**」与「**该红的用例**」在开工前就写死（`tasks.md` T023 要求
> **逐条先写、再跑**），**实跑读数留到交付批（T034）填**。
> ⚠️ **这样做的理由**：破坏表若照结果补写，就变成「为已经绿的护栏编一张事后合理的清单」——
> 点不出名字的护栏（写完才发现「该红的那条用例不存在」）**必须在跑之前**就暴露出来。
> ⚠️ **`D1`–`D7` 的每一次破坏都满足两个前提**：① 跑完**核对那条行为确实变了**（没变 = **空操作**，
> 绿**不能**记成结论）；② 还原**一律用 `cp` 备份回写**，**禁用 `git checkout`**。
> 还原判据 = `git hash-object <file>` == 破坏前记录的哈希（**内容级相等**，不称「逐字节一致」——
> CRLF 会让 `sha1sum` 假不等）。
> ⚠️ **看到红先读是不是判据本身的红**（编译不过 / 无关的 TS6133 / 前置被污染）——**手段的红不算目的的红**。

---

## §0 本项的证据形态与三条硬规则

本项交付的证据是**四条行为**（`quickstart.md` §2 已列），**不是一个功能**：

1. 默认部署下，同步端点**拒绝且零副作用**；
2. 演示档下生成的记录**被明确标注**；
3. 界面上**不存在**把 `SIMULATED` 读成「已同步」的通路；
4. 配置缺省即 `false`。

⚠️ **本项最大的假绿陷阱**：断言「默认路径没写记录」时，最自然的写法是**从 `@Transactional`
推出来**——「异常在 `insert` 之前抛出，事务里没有插入语句，所以没写」。这个推理**今天是对的**，
但它**没有任何断言在看着它**：只要有人把 `insert` 挪到判门之前（比如先落一条 `PENDING` 再判门），
事务语义照样会把这一条回滚掉，而**推理的说法当场失效、却不会有任何用例变红**。

⇒ **「零副作用」必须写成正面断言，缺一即视为假绿**：
① 单测 `verify(mapper, never()).insert(any())`（直接钉住副作用，不依赖 Spring 的事务语义）；
② 集成 `GET /records` 的 `total == 0`（端到端可见的事实）。
**D3 是这两条断言的专属破坏**（在抛异常之前先 `insert`）。

⚠️ **第二条陷阱与它形态相同**：`SIMULATED` 这个值本身没有意义 —— 有意义的是一条**通路**：
后端写 `SIMULATED` ⇒ 响应带 `SIMULATED` ⇒ 类型允许 ⇒ **界面渲染成「模拟」而不是「已同步」**。
只改后端不改前端，演示记录会被渲染成**红色「失败」**——**同一个缺陷的第二次出现**。
**D4 是界面那一半的专属破坏**（把 `SIMULATED` 也渲染成绿色「已同步」）。

⚠️ **第三条：本项唯一能钉住「Java 侧缺省值」的判据是 T8**。IT 读的是 `application.yml` 的
`${CRM_MAIL_INBOUND_DEMO_ENABLED:false}` ⇒ 把 `MailInboundStatus` 的 `@Value` 默认值改成 `:true`
**不会让任何 IT 变红**。**D6 就是来证明这件事的**：它**只**让 T8 红，IT 全绿 —— 这个「不完全红」
**正是 T8 不可省的理由**。

---

## §A D1 —— 撤掉配置门（默认直接生成记录）· **本项的核心主张**

| 项 | 内容 |
|---|---|
| **破坏** | `service/MailSyncRecordService.java#triggerSync`：把「判门 ⇒ 抛异常」整段删掉（演示分支保留） |
| **它该改变哪条可观察行为** | 「默认部署下同步端点**不产生任何记录**」——本项存在的理由 |
| **该红的用例** | **T1**（单测：默认抛 `MailInboundNotConfiguredException`）· **T2**（IT：409 + `total == 0`） |
| **备份 / 日志** | `/tmp/destruct/101/D1-MailSyncRecordService.bak` · `/tmp/destruct/101/D1.log` |
| **破坏前哈希** | （破坏前记 `git hash-object`） |

**实跑读数**：（交付时填）

**还原**：`cp` 回写 → `git hash-object` = （交付时填）✅

---

## §B D2 —— 演示路径仍写 `SYNCED`

| 项 | 内容 |
|---|---|
| **破坏** | `service/MailSyncRecordService.java`：演示分支的 `setSyncStatus(MailSyncRecord.STATUS_SIMULATED)` 改回 `STATUS_SYNCED` |
| **它该改变哪条可观察行为** | 「演示记录在**数据层**就被标注为 `SIMULATED`」——证明 `SIMULATED` 不是只改了常量名/界面文案 |
| **该红的用例** | **T3**（IT：`data.syncStatus == "SIMULATED"`）· **T6**（前端：库里读到的值会让界面显示「已同步」——但 ⚠️ T6 打的是 mock，**T6 不会红**；本条**只**该由 T3 红。**若 T6 也红，说明测试打的是真后端而不是 mock，需要重新核对**） |
| **备份 / 日志** | `/tmp/destruct/101/D2-MailSyncRecordService.bak` · `/tmp/destruct/101/D2.log` |
| **破坏前哈希** | （破坏前记） |

**实跑读数**：（交付时填）

**还原**：`cp` 回写 → `git hash-object` = （交付时填）✅

---

## §C D3 —— 默认路径在抛异常**之前**先 `insert` · **专杀「靠事务会回滚」的推理**

| 项 | 内容 |
|---|---|
| **破坏** | `service/MailSyncRecordService.java#triggerSync`：在 `throw new MailInboundNotConfiguredException()` **之前**插一条 `PENDING` 记录（仍抛异常，故 HTTP 结果不变） |
| **它该改变哪条可观察行为** | 「**零副作用**」本身 —— 响应仍是 409（**状态码这一层看不出任何区别**），变的只有「库里有没有东西」 |
| **该红的用例** | **T1**（`verify(mapper, never()).insert(any())`）· **T2** 的 `total == 0` 正对照 |
| **备份 / 日志** | `/tmp/destruct/101/D3-MailSyncRecordService.bak` · `/tmp/destruct/101/D3.log` |
| **破坏前哈希** | （破坏前记） |

**实跑读数**：（交付时填）

⚠️ **这条破坏的形状要读懂**：它**不改变任何 HTTP 可观测的东西**（409 照旧）。⇒
「只断状态码」的用例集在这条破坏下**照样全绿**。这正是 §0 那条规则的来源。

⚠️ **若 D3 之下 T1/T2 仍绿**：说明 `never().insert` 压根没写、或写成了「调用过 insert」
⇒ **如实记为判据缺口并就地补强后重跑**（照 100 的 D3/D6 先例），**不得**记成绿。

**还原**：`cp` 回写 → `git hash-object` = （交付时填）✅

---

## §D D4 —— 前端把 `SIMULATED` 也渲染成绿色「已同步」· **界面层诚实化的唯一护栏**

| 项 | 内容 |
|---|---|
| **破坏** | `pages/mail/MailSyncPage.tsx`：状态列的三向渲染改回二向（`row.syncStatus === 'SYNCED' \|\| row.syncStatus === 'SIMULATED' ? 绿「已同步」: 红「失败」`） |
| **它该改变哪条可观察行为** | 「界面上不存在把演示记录读成『已同步』的通路」——本项在界面上最可见的那处 |
| **该红的用例** | **T6**（`MailSyncPage.perm.test.tsx`：`SIMULATED` 渲染成 `tagSimulated` 且**不是**「已同步」） |
| **备份 / 日志** | `/tmp/destruct/101/D4-MailSyncPage.bak` · `/tmp/destruct/101/D4.log` |
| **破坏前哈希** | （破坏前记） |

**实跑读数**：（交付时填）

⚠️ 前端破坏**只需跑定向 vitest**（`pnpm exec vitest run src/pages/mail/MailSyncPage.perm.test.tsx`），
**不跑全量**（本机默认 worker 池超订，会撞 `testTimeout` 制造与破坏无关的假红）。

**还原**：`cp` 回写 → `git hash-object` = （交付时填）✅

---

## §E D5 —— `ErrorCode` 的状态由 409 改成 500

| 项 | 内容 |
|---|---|
| **破坏** | `common/ErrorCode.java`：`MAIL_INBOUND_NOT_CONFIGURED(409, …)` 的 `409` 改成 `500` |
| **它该改变哪条可观察行为** | 「未接入收信源是一个**受控**的 4xx，不是服务端故障」 |
| **该红的用例** | **T2**（IT：`isConflict()`）· **T5**（`PermissionEnforcementIT` 的 SALES 探针：`isConflict()`） |
| **备份 / 日志** | `/tmp/destruct/101/D5-ErrorCode.bak` · `/tmp/destruct/101/D5.log` |
| **破坏前哈希** | （破坏前记） |

**实跑读数**：（交付时填）

⚠️ **这条与 D1 的红集合有交集（T2）但不重合**：D1 红在「记录被写出来了」，D5 红在「状态码不是 409」。
**同一条用例的两个成因**——「红了几条」不能替代「红了哪几条」。

**还原**：`cp` 回写 → `git hash-object` = （交付时填）✅

---

## §F D6 —— Java 侧 `@Value` 默认值改 `:true` · **证明 T8 不可省**

| 项 | 内容 |
|---|---|
| **破坏** | `config/MailInboundStatus.java`：`@Value("${crm.mail.inbound.demo-enabled:false}")` 的 `:false` 改成 `:true` |
| **它该改变哪条可观察行为** | 「**缺省即 false**」——即「没做任何配置的部署不会生产演示数据」 |
| **该红的用例** | **T8**（`MailInboundStatusTest` 直接构造该类，断两个分支） |
| ****本不该**红的用例** | **T1–T5 全部照绿** —— 因为 `application.yml` 里 `CRM_MAIL_INBOUND_DEMO_ENABLED` 的缺省是 `false`，yml **胜出** ⇒ 容器里注入的仍是 `false`。⚠️ **这个「不完全红」是预期的，且是本条留痕的全部价值**：它**实测**了「IT 抓不住 Java 侧默认值」这件事 |
| **备份 / 日志** | `/tmp/destruct/101/D6-MailInboundStatus.bak` · `/tmp/destruct/101/D6.log` |
| **破坏前哈希** | （破坏前记） |

**实跑读数**：（交付时填）

⚠️ **若 T8 没红**：T8 大概是**读容器/读 yml** 而不是「直接 `new MailInboundStatus(true/false)`」
⇒ 就地改成直接构造后重跑。**若改完仍不红，如实登记「Java 侧缺省值无护栏」**——
不得把它写成「两处默认值同向所以安全」（那正是 `captcha` 那次踩过的形状：代码默认 `true`、yml `false`，
yml 胜出 ⇒ 代码里的默认值**是死代码却会被读成真源**）。

**还原**：`cp` 回写 → `git hash-object` = （交付时填）✅

---

## §G D7 —— 删掉 `MailSyncRecordServiceTest` 里的 `never().insert` 断言

| 项 | 内容 |
|---|---|
| **破坏** | `test/service/MailSyncRecordServiceTest.java`：删掉默认路径那一条 `verify(recordMapper, never()).insert(any())` |
| **它该改变哪条可观察行为** | 无 —— 这是**反向**的一条：它删的是**判据自己**，用来验证「这条断言是唯一的副作用护栏」 |
| **该红的用例** | **无**（删判据不会让任何东西红）。⇒ **本条要与 D3 配对读**：D7 之下 D3 会**变成全绿**，那正是「删掉这条断言 = 副作用不再被看着」的证明 |
| **备份 / 日志** | `/tmp/destruct/101/D7-MailSyncRecordServiceTest.bak` · `/tmp/destruct/101/D7.log` |
| **破坏前哈希** | （破坏前记） |

**实跑读数**：（交付时填；**必做的是「D7+D3 组合」那一次**：应全绿 —— 若仍红，说明 T1 里还有别的断言看着副作用，**如实记下是哪一条**）

**还原**：`cp` 回写 → `git hash-object` = （交付时填）✅

---

## §L 门禁实跑读数（**交付批**，最终树）

**命令**：`cd backend && mvn -B spotless:apply && mvn -B verify` —— **未传 `-DargLine`**（传了会静默废掉 JaCoCo）；
取**那一次完整 `verify`** 的读数，**此后不再跑 Maven**（会重写 `target/jacoco.exec`，让本节的字节数变成假话）。

| 项 | 读数 |
|---|---|
| 退出码 / 结论 | （交付时填） |
| surefire | （交付时填） |
| failsafe | （交付时填） |
| 失败集合 | （交付时填）⇒ **判据 = ⊆ 4 例已批准偏差**（`IntegrationHubIT.integrationFlow`、`OpportunityIT.closeWithoutResultReturns422`、`UserIT.disableUserRevokesAccess`、`UserIT.userLifecycle`） |
| `jacoco:check` | （交付时填；**成功判据是打印「All coverage checks have been met.」**，不是「没搜到失败」） |
| `target/jacoco.exec` | （交付时填：字节数 + mtime） |
| JaCoCo 三项 | （交付时填；阈值 INSTRUCTION **0.73**、粒度 BUNDLE，**未改**） |
| 本项新增/改动的用例 | （交付时填） |
| 前端五道门禁 + 定向 vitest | （交付时填） |

⚠️ **4 例已批准偏差若本次一例也没红，如实写「比判据更严」**，不得据此改变判据本身。

---

## §M 隔离实例冒烟（两档，**不碰共享开发库**）

| 项 | 值 |
|---|---|
| 端口 / schema / Redis db | **8099** / `crm_ib101` / **db 5** |
| 默认档读数 | ① 同步端点**连调两次**：两次都应是 **409 + `error.code == MAIL_INBOUND_NOT_CONFIGURED`**、message 含「未接入收信源」；② `GET /records` 的 `total` 与调用前**逐字相同**（连点不累积） |
| 演示档读数 | 重启（`--crm.mail.inbound.demo-enabled=true`）后调一次：**200**、`data.syncStatus == "SIMULATED"`、`data.subject` 带演示标记；`GET /records` 的 `total` **+1** |
| 只读冒烟（5173） | `/mail-sync` 的 Alert 新文案与状态列三向渲染；⚠️ **不点同步按钮**（默认档下它必然 409、无害，但演示档若误开就会写库） |

**实跑读数**：（交付时填，逐条）

**收尾（三项都做，并核对共享库未动、8081 未重启）**：（交付时填）

⚠️ `GRANT` 的主机名**先查再写**：`SELECT user,host FROM mysql.user;` —— 本仓实测 `crm_user` 的 host 是
**`localhost`**（用 `%` 会 `ERROR 1410`）。`quickstart.md` §4 已按 `localhost` 写。

---

## §N 可核判据（`quickstart.md` §5）实跑

| # | 判据 | 期望 | 实跑读数 |
|---|---|---|---|
| ① | `simulateSync` / `btnSimulateSync` / `模拟同步邮件` 在 `backend/src` + `frontend/src` | **0 命中** | （交付时填） |
| ② | `STATUS_SIMULATED` 等三常量在实体上、`SIMULATED` 在 `types/mail.ts` | 都在 | （交付时填） |
| ③ | `demo-enabled` 两处默认值同向（都 `false`） | 两处都 `false` | （交付时填） |
| ④ | `git diff --stat 66bb71f..HEAD -- specs/062-email-sync/` | **空输出** | （交付时填） |
| ⑤ | 两个权限码仍在 `MailAccountController` | 都在 | （交付时填） |
| ⑥ | `git diff --stat 66bb71f..HEAD -- backend/src/main/resources/db/migration/` | **空输出**（零迁移） | （交付时填） |

---

## §O 订正不静默自查（`quickstart.md` §6）命中数

⚠️ **口径**：本节的判据要 grep 的正是**旧值**，而留痕规则要求旧值**仍留在 ⚠️ 块里**
⇒ **命中数 ≥1 才是「订正不静默」的正向证据**，**不是**「没改干净」。

| # | 要 grep 的旧值 | 期望 | 实跑命中 |
|---|---|---|---|
| ① | `收信侧 \`simulateSync\` 仍在` / `接 IMAP 或从功能清单移除`（`CRM_FEATURE_COMPARISON.md`） | 非零 | （交付时填） |
| ② | `不可对客户宣称已具备邮件同步` / `它是"诚信缺口"里唯一没被一期关掉的一条` | 非零 | （交付时填） |
| ③ | `邮件同步收信侧仍为模拟` | 非零 | （交付时填） |
| ④ | `邮件同步为 \`simulateSync\` 模拟实现` | 非零 | （交付时填） |
| ⑤ | `同步流程为模拟实现，尚未接入 IMAP`（`CRM_FEATURE_COMPARISON.md` 的引文）/ `邮件账户配置、同步记录框架`（`PROJECT_FEATURES.md`） | 非零 | （交付时填） |
| ⑥ | `2962` / `3452` / `99（001–100`（`PROJECT_FEATURES.md`） | 非零 | （交付时填） |

**新值到位的判据**（与上表**成对**读，缺一不可）：`i18n:check` 报 **2963/2963** ·
`grep -c "2963" PROJECT_FEATURES.md` ≥1 · `grep -c "100（001–101" PROJECT_FEATURES.md` ≥1。

⚠️ 若某条 **0 命中**：先分辨是「字面量不存在」还是「**被 spotless 的 javadoc 折行断成两行**」
（行式 grep 匹配不到跨行字面量）——本仓已有先例。后者处置是**改排版**（`<br>` 把原文固定在自己一行）
并复跑 `spotless:apply` 确认不被折回，**不是**登记成口径缺口。

---

## §P 如实登记的边界（**不得被读成已解决**）

1. **演示档记录的 `fromAddress` 仍是硬编码的 `customer@example.com`**。它现在与 `SIMULATED` 状态、
   带演示标记的主题**一起**出现，被标注清楚了；但**它本身不是真实地址**，且**没有任何用例或门禁
   能判断一个 `fromAddress` 是否真实**（本项不去猜）。⇒ 登记为「演示数据的固有边界」。
2. **`demo-enabled=true` 只表示「允许生成演示记录」，不表示「收信源可用」**。键为 `true` 而 IMAP
   完全不存在时，端点照样 200 并写出演示记录。**不得**被读成连通性探测（javadoc 与三处配置注释都写了这句）。
3. **默认拒绝会让默认部署下的按钮必然报错** —— 这是本项**有意**的代价（用户裁决），
   但**没有任何自动化判据能衡量「用户困惑」**；缓解只有 Alert 文案与三处配置注释。
4. **`SIMULATED` 记录不会被自动清理**（演示环境与生产共用库时需人工清理）；本项不新增该能力。
5. **本项没有任何一条用例能证明「接上 IMAP 后同步是对的」** —— 因为没有实现。
   本项的证据只覆盖「默认拒绝」「演示标注」「界面渲染」三件事。
6. **`apiClient.isVersionConflict` 只按状态码 409 判定**（不读 `error.code`），而本项的新码正是 409。
   实测邮件页**只用** `extractErrorMessage` ⇒ 今天不会误读；**日后**若有调用点把它接到邮件页，
   就会把这条 409 读成「数据已被他人修改」。本项**不改** `apiClient`（不为一个不存在的调用点改全局语义），
   此条登记在 `research.md` §12 与 `contracts/mail-inbound.md` §3.1。

---

## §R 各节由哪次实跑填（交付时补）

| 节 | 来源 |
|---|---|
| §0 · §P | 立项期勘察事实 + `quickstart.md` §2 的既定口径（**非**交付批新增读数） |
| §A–§G | `D1`–`D7` 逐条定向破坏（日志 `/tmp/destruct/101/`） |
| §L · §N | 交付批的完整 `mvn -B verify` 与 `quickstart.md` §5 逐条实跑 |
| §M | 隔离实例（8099 / `crm_ib101` / Redis db 5）两档冒烟 |
| §O | `quickstart.md` §6 六条 grep |
