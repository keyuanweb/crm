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
| **备份 / 日志** | `/tmp/destruct/101/MailSyncRecordService.java.bak`（⚠️ 实际落盘名见 §R 的口径订正；无 `D*.log`） |
| **破坏前哈希** | `b7b047ee2dcf3d12464ae8220789cfc943ff6276` |

**实跑读数**（12:47:37；`mvn -B -o verify -Dit.test='EmailSyncIT' -Dtest='MailSyncRecordServiceTest' -DfailIfNoTests=false -Dmaven.test.failure.ignore=true`，
输出经 `grep -E "^\[ERROR\].*(<<<|Tests run)|Tests run:.*Failures|BUILD"` 过滤 ⇒ **当次只留下类名/方法名与计数**）：

```
[ERROR] Tests run: 3, Failures: 1, Errors: 0, Skipped: 0 ... -- in com.crm.service.MailSyncRecordServiceTest
[ERROR] com.crm.service.MailSyncRecordServiceTest.defaultPathRejectsWithoutWritingAnything -- ... <<< FAILURE!
[ERROR] Tests run: 2, Failures: 1, Errors: 0, Skipped: 0 ... -- in com.crm.integration.EmailSyncIT
[ERROR] com.crm.integration.EmailSyncIT.emailSyncFlow -- ... <<< FAILURE!
[INFO] BUILD SUCCESS
```

⇒ **该红的两条都红了**（T1 + T2），且**没有别的红**：5 个用例里恰好失败 2 个，且就是点名的这两条。核心主张被两条独立通路（单测的副作用断言、IT 的端到端状态码+计数）同时钉住。

**还原**：`cp` 回写 → `git hash-object` = `b7b047ee2dcf3d12464ae8220789cfc943ff6276` ✅（12:48:04，与破坏前逐字相等）

---

## §B D2 —— 演示路径仍写 `SYNCED`

| 项 | 内容 |
|---|---|
| **破坏** | `service/MailSyncRecordService.java`：演示分支的 `setSyncStatus(MailSyncRecord.STATUS_SIMULATED)` 改回 `STATUS_SYNCED` |
| **它该改变哪条可观察行为** | 「演示记录在**数据层**就被标注为 `SIMULATED`」——证明 `SIMULATED` 不是只改了常量名/界面文案 |
| **该红的用例** | **T3**（IT：`data.syncStatus == "SIMULATED"`）· **T6**（前端：库里读到的值会让界面显示「已同步」——但 ⚠️ T6 打的是 mock，**T6 不会红**；本条**只**该由 T3 红。**若 T6 也红，说明测试打的是真后端而不是 mock，需要重新核对**） |
| **备份 / 日志** | `/tmp/destruct/101/MailSyncRecordService.java.bak`（同上；无 `D*.log`） |
| **破坏前哈希** | `b7b047ee2dcf3d12464ae8220789cfc943ff6276` |

**实跑读数**（12:48:08；`-Dit.test='MailInboundDemoIT,EmailSyncIT' -Dtest='MailSyncRecordServiceTest'`）：

```
[ERROR] com.crm.service.MailSyncRecordServiceTest.demoPathCreatesSimulatedRecord -- ... <<< FAILURE!   （类级 3/1F）
[INFO]  com.crm.integration.EmailSyncIT -- Tests run: 2, Failures: 0 ...                                （默认路照绿）
[ERROR] com.crm.integration.MailInboundDemoIT.demoModeWritesOneSimulatedRecord -- ... <<< FAILURE!     （类级 1/1F）
[INFO] BUILD SUCCESS
```

⚠️ **这条的读数必须精确到用例名才读得对**：`MailSyncRecordServiceTest` 的类级计数同样是「3 跑 1 红」，但红的是**演示路**那条
`demoPathCreatesSimulatedRecord`，**不是** §A 里红过的默认路那条 —— 只看类名的计数会把它误记成「默认路也红了」。
⚠️ §B 预写里说「本条**只**该由 T3 红、T6 不会红」：实测相符（T6 打的是 mock，后端写什么它都不知道）；
且 `EmailSyncIT` 照绿 —— 演示分支的颜色不影响默认路的 409 与 `total == 0`。

**还原**：`cp` 回写 → `git hash-object` = `b7b047ee2dcf3d12464ae8220789cfc943ff6276` ✅（12:48:36）

---

## §C D3 —— 默认路径在抛异常**之前**先 `insert` · **专杀「靠事务会回滚」的推理**

| 项 | 内容 |
|---|---|
| **破坏** | `service/MailSyncRecordService.java#triggerSync`：在 `throw new MailInboundNotConfiguredException()` **之前**插一条 `PENDING` 记录（仍抛异常，故 HTTP 结果不变） |
| **它该改变哪条可观察行为** | 「**零副作用**」本身 —— 响应仍是 409（**状态码这一层看不出任何区别**），变的只有「库里有没有东西」 |
| **该红的用例** | **T1**（`verify(mapper, never()).insert(any())`）· **T2** 的 `total == 0` 正对照 |
| **备份 / 日志** | `/tmp/destruct/101/MailSyncRecordService.java.bak`（同上；无 `D*.log`） |
| **破坏前哈希** | `b7b047ee2dcf3d12464ae8220789cfc943ff6276` |

**实跑读数**：⚠️ **这条实际拆成了两跑，因为计划里的单跑预期没有完全发生**（偏差已登记进 `tasks.md` 的「实做订正」）：

**D3a —— 只把 `insert` 挪到抛异常之前，`@Transactional` 保留**（12:48:44；`-Dit.test='EmailSyncIT' -Dtest='MailSyncRecordServiceTest'`）：

```
[ERROR] com.crm.service.MailSyncRecordServiceTest.defaultPathRejectsWithoutWritingAnything -- ... <<< FAILURE!   （类级 3/1F）
[INFO]  com.crm.integration.EmailSyncIT -- Tests run: 2, Failures: 0 ...                                        （2/0F ⇒ 绿）
[INFO] BUILD SUCCESS
```

⇒ **计划预期的「T2 的 `total=0` 会红」没有发生**：方法上的 `@Transactional` 把那条插入回滚了，库里依旧干净，
端到端**看不出任何区别**。这条破坏只被**单测的 `never().insert`** 抓住（它不依赖 Spring 的事务语义）。
⚠️ **这正是 §0 那条规则的实证**：若只写「IT 的 `total == 0`」而不写单测的副作用断言，D3a 这种形（先写后抛、事务兜底）
会**全绿通过**。

**D3b —— 在 D3a 之上再去掉 `@Transactional`**（12:49:12；同上命令，另取出 IT 的失败详情）：

```
[ERROR] com.crm.service.MailSyncRecordServiceTest.defaultPathRejectsWithoutWritingAnything -- ... <<< FAILURE!
[ERROR] com.crm.integration.EmailSyncIT.emailSyncFlow -- ... <<< FAILURE!
java.lang.AssertionError: JSON path "$.data.total" expected:<0> but was:<2>
	at org.springframework.test.util.AssertionErrors.fail(AssertionErrors.java:59)
```

⇒ `total == 0` 这条正对照**只有在事务兜底也撤掉时才红**：IT 连点两次同步端点，两次都真落了库 ⇒ `<2>`。
两跑合起来才把计划的断言「`total == 0` **真看着副作用**」证完：它看着的是**提交后的**副作用，而单测那条看着的是**调用本身**。

⚠️ **这条破坏的形状要读懂**：它**不改变任何 HTTP 可观测的东西**（409 照旧）。⇒
「只断状态码」的用例集在这条破坏下**照样全绿**。这正是 §0 那条规则的来源。

⚠️ **若 D3 之下 T1/T2 仍绿**：说明 `never().insert` 压根没写、或写成了「调用过 insert」
⇒ **如实记为判据缺口并就地补强后重跑**（照 100 的 D3/D6 先例），**不得**记成绿。

**还原**：`cp` 回写 → `git hash-object` = `b7b047ee2dcf3d12464ae8220789cfc943ff6276` ✅（12:49:36；两跑共用同一份备份）

---

## §D D4 —— 前端把 `SIMULATED` 也渲染成绿色「已同步」· **界面层诚实化的唯一护栏**

| 项 | 内容 |
|---|---|
| **破坏** | `pages/mail/MailSyncPage.tsx`：状态列的三向渲染改回二向（`row.syncStatus === 'SYNCED' \|\| row.syncStatus === 'SIMULATED' ? 绿「已同步」: 红「失败」`） |
| **它该改变哪条可观察行为** | 「界面上不存在把演示记录读成『已同步』的通路」——本项在界面上最可见的那处 |
| **该红的用例** | **T6**（`MailSyncPage.perm.test.tsx`：`SIMULATED` 渲染成 `tagSimulated` 且**不是**「已同步」） |
| **备份 / 日志** | `/tmp/destruct/101/MailSyncPage.tsx.bak`（同上；无 `D*.log`） |
| **破坏前哈希** | `39f012f16e5e62c42e147fcc2899b91c065658e1` |

**实跑读数**（12:50:53；`pnpm exec vitest run src/pages/mail/MailSyncPage.perm.test.tsx`，**只跑定向**）：

```
❯ src/pages/mail/MailSyncPage.perm.test.tsx  (6 tests | 1 failed)
❯ MailSyncPage 收信状态渲染（101） > SIMULATED 渲染成橙色「模拟」——既不是绿色「已同步」，也不是红色「失败」
  → expected [] to have a length of 1 but got +0
 FAIL  src/pages/mail/MailSyncPage.perm.test.tsx > ... > SIMULATED 渲染成橙色「模拟」——…
AssertionError: expected [] to have a length of 1 but got +0
 ❯ src/pages/mail/MailSyncPage.perm.test.tsx:189:62
 Test Files  1 failed (1)
      Tests  1 failed | 5 passed (6)
```

⇒ **恰好一条红，且正是专为这处新写的那条 T6**（`queryAllByText('pages.mail.tagSimulated')` 长度 0 ⇒ 破坏后它渲染成了绿色「已同步」）。
086 的四条结构用例与「未知值兜底为失败」那条**都不受影响** —— 破坏只动了 `SIMULATED` 这一个分支。

⚠️ 前端破坏**只需跑定向 vitest**（`pnpm exec vitest run src/pages/mail/MailSyncPage.perm.test.tsx`），
**不跑全量**（本机默认 worker 池超订，会撞 `testTimeout` 制造与破坏无关的假红）。

**还原**：`cp` 回写 → `git hash-object` = `39f012f16e5e62c42e147fcc2899b91c065658e1` ✅（12:51:04）

---

## §E D5 —— `ErrorCode` 的状态由 409 改成 500

| 项 | 内容 |
|---|---|
| **破坏** | `common/ErrorCode.java`：`MAIL_INBOUND_NOT_CONFIGURED(409, …)` 的 `409` 改成 `500` |
| **它该改变哪条可观察行为** | 「未接入收信源是一个**受控**的 4xx，不是服务端故障」 |
| **该红的用例** | **T2**（IT：`isConflict()`）· **T5**（`PermissionEnforcementIT` 的 SALES 探针：`isConflict()`） |
| **备份 / 日志** | `/tmp/destruct/101/ErrorCode.java.bak`（同上；无 `D*.log`） |
| **破坏前哈希** | `dcaae6f1fc7218a94e3c2cd6f209fcc91ad084e0` |

**实跑读数**（12:50:13；`-Dtest='MailSyncRecordServiceTest' -Dit.test='EmailSyncIT,PermissionEnforcementIT'`）：

```
[ERROR] com.crm.integration.EmailSyncIT.emailSyncFlow -- 期望 409、实得 500
[ERROR] com.crm.integration.PermissionEnforcementIT.permissionBatch3ModulesFollowTheMatrix -- ... <<< FAILURE!
[INFO] com.crm.integration.PermissionEnforcementIT -- Tests run: 14, Failures: 1
[INFO] com.crm.service.MailSyncRecordServiceTest -- Tests run: 3, Failures: 0   （3/0F ⇒ 绿）
```

⇒ **红的正是计划点名的两条**，且**只在 HTTP 状态码这一层**：
- `EmailSyncIT.emailSyncFlow` —— 端到端看到的是 `500` 而不是 `409`；
- `PermissionEnforcementIT` 里 086 那条探针 —— 它断的是「SALES 调同步端点是 **409 而不是 403**」，
  改 500 后两个码**都不成立**，探针红 ⇒ 正好证明该探针盯着的是这个码本身，不是「随便一个失败」。
- `MailSyncRecordServiceTest 3/0F` **照绿** —— 单测直接构造 service、只看异常类型，
  **不经过 `ErrorCode` → HTTP 的映射**。两个层次的用例在这里分工：单测管「抛哪种异常」，IT 管「映射成哪个码」。

⚠️ **这条与 D1 的红集合有交集（T2）但不重合**：D1 红在「记录被写出来了」，D5 红在「状态码不是 409」。
**同一条用例的两个成因**——「红了几条」不能替代「红了哪几条」。

⚠️ **这条与 D1 的红集合有交集（T2）但不重合**：D1 红在「记录被写出来了」，D5 红在「状态码不是 409」。
**同一条用例的两个成因**——「红了几条」不能替代「红了哪几条」。

**还原**：`cp` 回写 → `git hash-object` = `dcaae6f1fc7218a94e3c2cd6f209fcc91ad084e0` ✅（12:50:13）

---

## §F D6 —— Java 侧 `@Value` 默认值改 `:true` · **证明 T8 不可省**

| 项 | 内容 |
|---|---|
| **破坏** | `config/MailInboundStatus.java`：`@Value("${crm.mail.inbound.demo-enabled:false}")` 的 `:false` 改成 `:true` |
| **它该改变哪条可观察行为** | 「**缺省即 false**」——即「没做任何配置的部署不会生产演示数据」 |
| **该红的用例** | **T8**（`MailInboundStatusTest` 直接构造该类，断两个分支） |
| ****本不该**红的用例** | **T1–T5 全部照绿** —— 因为 `application.yml` 里 `CRM_MAIL_INBOUND_DEMO_ENABLED` 的缺省是 `false`，yml **胜出** ⇒ 容器里注入的仍是 `false`。⚠️ **这个「不完全红」是预期的，且是本条留痕的全部价值**：它**实测**了「IT 抓不住 Java 侧默认值」这件事 |
| **备份 / 日志** | `/tmp/destruct/101/MailInboundStatus.java.bak`（同上；无 `D*.log`） |
| **破坏前哈希** | `55bbe4d6e37de3364aadda54de043734823470c8` |

**实跑读数**（12:50:46；`-Dtest='MailInboundStatusTest,MailSyncRecordServiceTest' -Dit.test='EmailSyncIT,MailInboundDemoIT'`）：

```
[ERROR] com.crm.config.MailInboundStatusTest.demoDisabledByDefault -- ... <<< FAILURE!   （类级 3/1F）
[INFO]  com.crm.service.MailSyncRecordServiceTest        -- Tests run: 3, Failures: 0    （3/0F ⇒ 绿）
[INFO]  com.crm.integration.EmailSyncIT                  -- Tests run: 2, Failures: 0    （2/0F ⇒ 绿）
[INFO]  com.crm.integration.MailInboundDemoIT            -- Tests run: 1, Failures: 0    （1/0F ⇒ 绿）
```

⇒ **只有 T8 一条红，其余三个类全绿** —— 这就是本条的**全部价值**，且它把计划里的那句话从**推理**变成了**读数**：
「IT 走的是 `application.yml` 的 `${...:false}`，**yml 胜出**，所以改 Java 侧默认值 IT 抓不住」。
**若没写 T8，这条破坏会全绿通过**（缺省即 `true` ⇒ 未配置的部署会生产演示数据，而没有任何门禁看着它）。

⚠️ **手段的红另记一笔（不算目的的红）**：这一次 `mvn` 的构建结论是 `BUILD FAILURE`，
但**不是用例的红**，而是 `spotless:check` 拒绝了那次改写造成的 **CRLF → LF** 行尾变化
（当时的改写用手写的 `python` 原地重写，把整个文件的换行符换掉了）。
⇒ 判据是**测试层的读数**（上表），构建退出码在这里不可用作判据。
**教训已记入 `tasks.md` 的实做订正**：本仓的 Java 源是 CRLF，原地改**必须**用 Edit 工具，不得用脚本重写。
`file` 复核 `.bak` 确为 CRLF，`cp` 回写后 `spotless` 不再报错。

⚠️ **若 T8 没红**：T8 大概是**读容器/读 yml** 而不是「直接 `new MailInboundStatus(true/false)`」
⇒ 就地改成直接构造后重跑。**若改完仍不红，如实登记「Java 侧缺省值无护栏」**——
不得把它写成「两处默认值同向所以安全」（那正是 `captcha` 那次踩过的形状：代码默认 `true`、yml `false`，
yml 胜出 ⇒ 代码里的默认值**是死代码却会被读成真源**）。

**还原**：`cp` 回写 → `git hash-object` = `55bbe4d6e37de3364aadda54de043734823470c8` ✅（12:50:46）

---

## §G D7 —— 删掉 `MailSyncRecordServiceTest` 里的 `never().insert` 断言

| 项 | 内容 |
|---|---|
| **破坏** | `test/service/MailSyncRecordServiceTest.java`：删掉默认路径那一条 `verify(recordMapper, never()).insert(any())` |
| **它该改变哪条可观察行为** | 无 —— 这是**反向**的一条：它删的是**判据自己**，用来验证「这条断言是唯一的副作用护栏」 |
| **该红的用例** | **无**（删判据不会让任何东西红）。⇒ **本条要与 D3 配对读**：D7 之下 D3 会**变成全绿**，那正是「删掉这条断言 = 副作用不再被看着」的证明 |
| **备份 / 日志** | `/tmp/destruct/101/MailSyncRecordServiceTest.java.bak`（同上；无 `D*.log`） |
| **破坏前哈希** | `3e45361218b4a0ff58b71ed341c851663d300003` |

**实跑读数**（**做的是「D7 + D3a 组合」**，12:52:12；`mvn -B -o test -Dtest=MailSyncRecordServiceTest`）：

```
[INFO] Tests run: 3, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

⇒ **全绿**。这就是本条要证的东西：**把 `never().insert` 那一条断言删掉之后，
D3a 那种「先 `insert` 再抛异常（事务兜底）」的实现在 T1 里畅行无阻**
（T1 剩下的断言只看「抛了哪一种异常、message 里有没有『未接入收信源』」，
而 D3a **照样抛同一个异常、带同一句 message**）。
⇒ 该断言不是陪衬，是这一层**唯一**看着副作用的护栏；配上 D3b 可知：
`total == 0` 只在事务也撤掉时才红，**能覆盖「先写后抛」的最小判据就是这一条**。

⚠️ **这一次跑出现了「Nothing to compile - all classes are up to date」，一度让读数不可信**
（Maven 若真跳过编译，跑的就是**没被破坏**的旧 class，「全绿」会是假绿）。
**没有拿它当结论**，先证明破坏确实进了产物：

```
$ stat -c '%y %n' .../target/classes/com/crm/service/MailSyncRecordService.class   # 比源文件新 ~0.5s
$ javap -c -p .../target/classes/com/crm/service/MailSyncRecordService.class | grep -n -A2 'MailSyncRecordMapper.insert'
   49: invokeinterface #..  // InterfaceMethod com/crm/repository/MailSyncRecordMapper.insert
   ...
   55: new  #..  // class com/crm/common/MailInboundNotConfiguredException
```

⇒ 字节码里 `insert`（偏移 49）**先于**抛异常（偏移 55）⇒ **破坏确实被编译进了跑的那份 class**，
「全绿」是真读数而非假绿。（Maven 那次「跳过编译」是因为 IDE 的语言服务已把 `target/classes` 原地编过；
详见 §L 的读数漂移注。）

**还原**：`cp` 回写 **两个文件** → `git hash-object` = `b7b047ee2dcf3d12464ae8220789cfc943ff6276`（service）
与 `3e45361218b4a0ff58b71ed341c851663d300003`（测试）✅（12:52:12）
—— D7 只该还原测试文件，此处之所以两个一起还原：D7+D3a 是**同一次**组合跑，两者的还原都在这一刻做。
还原后 `git diff --stat -- backend/` 为空。

---

## §L 门禁实跑读数（**交付批**，最终树）

**命令**：`cd backend && mvn -B spotless:apply && mvn -B verify` —— **未传 `-DargLine`**（传了会静默废掉 JaCoCo）；
取**那一次完整 `verify`** 的读数，**此后不再跑 Maven**（会重写 `target/jacoco.exec`，让本节的字节数变成假话）。

| 项 | 读数 |
|---|---|
| 退出码 / 结论 | `BUILD SUCCESS`（日志 `/tmp/gate-final.log`，12:54 收尾） |
| surefire | `Tests run: 738, Failures: 0, Errors: 0, Skipped: 0` |
| failsafe | `Tests run: 334, Failures: 0, Errors: 0, Skipped: 0` |
| 失败集合 | **空集** ⇒ 比判据**更严**：4 例已批准偏差**本次一例也没红**（`IntegrationHubIT.integrationFlow`、`OpportunityIT.closeWithoutResultReturns422`、`UserIT.disableUserRevokesAccess`、`UserIT.userLifecycle`） |
| `spotless:check` | `Spotless.Java is keeping 798 files clean - 0 needs changes to be clean` |
| `jacoco:check` | ✅ 打印 **`All coverage checks have been met.`**；上一行 `Analyzed bundle 'crm-backend' with 250 classes` ⇒ 门禁**确实被判定过**（不是「没搜到失败」） |
| `-DargLine` 自证 | 日志第 24 行：`argLine set to -javaagent:…org.jacoco.agent-0.8.11-runtime.jar=destfile=E:\code\crm\backend\target\jacoco.exec,excludes=…` ⇒ 代理**在位**，未被人为挤掉 |
| `target/jacoco.exec` | **88 346 260 字节** · mtime `2026-09-17 12:54:20.664 +0800` |
| JaCoCo 四项 | INSTRUCTION **48093/59101 = 0.8137** · BRANCH **3087/4906 = 0.6292** · LINE **11206/13440 = 0.8338** · METHOD **1792/2091 = 0.8570**（阈值 INSTRUCTION **0.73**、粒度 BUNDLE，**未改**） |
| 新类 `MailInboundStatus` | INSTRUCTION **0 missed / 9 covered**、BRANCH **0/0** —— 该类**在覆盖率分母内**（`com/crm/config/**` 未被排除），T8 把它 9 条指令全盖住；它是**无分支**的一行 `@Value` + 一个 `isDemoEnabled()` 返回，故 BRANCH 无计数 |
| 本项新增/改动的用例（**全绿**） | `MailInboundStatusTest`（**新**）3/0F · `MailSyncRecordServiceTest`（T1–T3 重写）3/0F · `EmailSyncIT`（默认段改 409 + `total=0`）2/0F · `MailInboundDemoIT`（**新**）1/0F · `PermissionEnforcementIT`（086 探针 200→409）14/0F |
| 前端五道门禁 + 定向 vitest | `i18n:check` 2963/2963（路由 58 / 清单 56 不变）· `lint` · `typecheck` · `ui:check`（白名单内既存债 54 处、**未新增违规**）· `zh:check`（未登记命中 0 处）**全绿**；`MailSyncPage.perm.test.tsx` **6 passed** |

⚠️ **两处读数需要与「交付态」一词一起读**：

1. **本节四项覆盖率与 `jacoco.exec` 的字节/mtime 属那一次完整 `verify`**。门禁跑完后**没有再跑任何 Maven**
   —— 再跑一次 `mvn test` 会**重写** `jacoco.exec`（字节数与 mtime 当场变成假话），这是本仓已登记的坑。
   ⇒ 若后来有人复跑，本节读数的**出处**是 `/tmp/gate-final.log`，不是「当前工区」。
2. **两次完整 verify 的分母不同（差异原因未完全查明，如实登记）**：本批先后跑过两次完整 `verify`，
   同一棵树、同一条命令，两次的**分母**不同（gate-1 INSTRUCTION 48142/59140、LINE 10904/13142；
   gate-2 即本节 48093/59101、11206/13440）。**已排查并留证据**：
   - 两次日志**都**打印 `Nothing to compile - all classes are up to date`（Maven 跳过了 javac）；
   - `target/classes` 的 **class 文件 mtime 分布**：577 个 @12:40、99 个 @12:49、2 个 @12:50、1 个 @12:52
     —— @12:49 那 99 个正是 D5（改 `ErrorCode`）的**依赖类**，由 **IDE 的语言服务（ECJ）原地编译**写入；
   - 按源文件/class 逐个比时间戳的陈旧检查：**602 个 main + 196 个 test 源全部 stale=0**（无陈旧产物）；
   - `jcmd 5032 VM.command_line` 在 8081 那份后端上只显示 `-XX:TieredStopAtLevel=1`，**没有 jacoco agent**
     ⇒ `target/jacoco.exec` **没有第二个写入者**。
   - ⇒ 归因：分母漂移来自**两次跑之间 `target/classes` 被 IDE 增量改写**（Maven 又跳过了编译），
     **不是**有人改了代码；比值漂移 ≤0.0041，0.73 阈值两次都通过。
     ⚠️ **未做**的实验：把 `target/` 清空后的干净重跑 —— 那会**重写交付态读数**，本批刻意不做；
     留作日后怀疑时的复核手段。

⚠️ **4 例已批准偏差若本次一例也没红，如实写「比判据更严」**，不得据此改变判据本身。

---

## §M 隔离实例冒烟（两档，**不碰共享开发库**）

| 项 | 值 |
|---|---|
| 端口 / schema / Redis db | **8099** / `crm_ib101` / **db 5** |
| 默认档读数 | ① 同步端点**连调两次**：两次都应是 **409 + `error.code == MAIL_INBOUND_NOT_CONFIGURED`**、message 含「未接入收信源」；② `GET /records` 的 `total` 与调用前**逐字相同**（连点不累积） |
| 演示档读数 | 重启（`--crm.mail.inbound.demo-enabled=true`）后调一次：**200**、`data.syncStatus == "SIMULATED"`、`data.subject` 带演示标记；`GET /records` 的 `total` **+1** |
| 只读冒烟（5173） | `/mail-sync` 的 Alert 新文案与状态列三向渲染；⚠️ **不点同步按钮**（默认档下它必然 409、无害，但演示档若误开就会写库） |

**实跑读数**（逐条，**两次启动**；日志 `/tmp/crm-ib101.log`（默认档）、`/tmp/crm-ib101-demo.log`（演示档））：

**① 默认档**（实例 PID 13200，12:57:18 起。先建冒烟账户 `smoke101@corp.com` → `HTTP:201`、`id=1`）：

```
=== 第 1 次 POST sync ===
{"success":false,"error":{"code":"MAIL_INBOUND_NOT_CONFIGURED","message":"未接入收信源（IMAP），同步未执行"}}
HTTP:409
=== 第 2 次 POST sync ===
{"success":false,"error":{"code":"MAIL_INBOUND_NOT_CONFIGURED","message":"未接入收信源（IMAP），同步未执行"}}
HTTP:409
=== GET records (1) ===
{"success":true,"data":{"items":[],"total":0,"page":1,"pageSize":10}}
HTTP:200
=== GET records (2) ===
{"success":true,"data":{"items":[],"total":0,"page":1,"pageSize":10}}
HTTP:200
```

⇒ **两条判据都成立**：连调两次**都是 409 + 同一个 code**；`items` 是**空数组**、`total` **两次都是 0**
⇒ **连点不累积**（这是「零副作用」在**端到端**上的读数，与 §A/§C 的单测断言互为两层）。
后端侧另有 `LoggingFilter` 的旁证，两条请求的**耗时**都只有个位数毫秒：

```
12:57:24.753  method=POST uri=/api/v1/mail-accounts/1/sync  status=409 elapsedMs=8
12:57:24.804  method=POST uri=/api/v1/mail-accounts/1/sync  status=409 elapsedMs=5
```

**② 演示档**（同一 schema，加 `--crm.mail.inbound.demo-enabled=true` 重启，实例 PID 2764）：

```
=== 演示档：先确认起点仍是 0 条 ===
total=0
=== POST sync（演示档）===
{"success":true,"data":{"id":1,"accountId":1,"direction":"INBOUND","subject":"演示同步邮件（非真实收信）",
 "fromAddress":"customer@example.com","toAddress":"smoke101@corp.com","syncStatus":"SIMULATED",
 "externalId":"demo-120209608352200","syncTime":"2026-09-17T12:57:56"}}
HTTP:200
=== GET records（演示档）===
{"success":true,"data":{"items":[{…"syncStatus":"SIMULATED","externalId":"demo-120209608352200"…}],"total":1,…}}
HTTP:200
```

⇒ **起点确实是 0 条**（同一 schema 沿用默认档的两次拒绝之后）⇒ **+1 是这一次调用造成的**，不是残留；
`syncStatus` = `SIMULATED`、`subject` = 「演示同步邮件（非真实收信）」、`externalId` = `demo-…` **三处标记同时在场**。
落库那一行在 SQL 日志里逐字段可见（`MailSyncRecordMapper.insert` 的 `Parameters`）——含 `SIMULATED` 与 `demo-…`。

**③ 只读冒烟（已在跑的 5173，`/mail-sync`）** —— **未点同步按钮、未写共享库**：

```
/mail-sync HTTP:200
=== 下发的 i18n 模块 ===
2934:      tagSimulated: "模拟",
2957:      btnSyncInbox: "同步收件",
2960:      demoDataNoticeTitle: "未接入真实收信源（IMAP）",
2961:      demoDataNoticeDesc: "点「同步收件」会被拒绝，不会产生任何记录。只有部署方显式打开演示开关时，才会生成一条标注为「模拟」的示例记录，不代表任何真实收发。"
=== 旧键是否已从下发的模块里消失 ===   0
=== 下发的邮件页模块 ===
29:  triggerSync,
335:  }, this) : row.syncStatus === "SIMULATED" ? jsxDEV(Tag, { color: "orange", children: t("pages.mail.tagSimulated") } …
422: await triggerSync(accountId);
```

⇒ **Vite 实际下发的模块**（不是源文件读数）里：新键在位、`btnSimulateSync` **零命中**、
`SIMULATED → orange` 的三向分支与 `triggerSync` 调用都在。⚠️ 这一步**只读模块**，
**不点按钮**：默认档下点它必然 409（无害），但演示档若误开就会真写库。

**收尾（三项都做，并核对共享库未动、8081 未重启）**：

```
8099 pid=2764   → 成功: 已终止 PID 为 2764 的进程。
=== DROP / REVOKE / FLUSHDB ===   OK
=== 核对：库列表 / 共享库表数 / 端口 ===
   库列表中 crm_ib101 计数 = 0
   tables_in_crm_db = 87
   TCP  0.0.0.0:8081  LISTENING  5032
   TCP  [::1]:5173    LISTENING  14116
```

⇒ 隔离件**已全部拆除**（库 DROP、授权 REVOKE、Redis db 5 `FLUSHDB`）；**共享库 `crm_db` 的 87 张表原样**；
**8081（PID 5032）与 5173（PID 14116）的 PID 与冒烟前一致 ⇒ 全程未重启**（8081 可能归并行会话所有）。
⚠️ 上一档的**进程杀掉不等于端口释放**：第一次 `TaskStop` 只杀掉了 bash 包装进程，JVM（PID 13200）仍在监听 8099，
是靠 `netstat -ano | grep :8099` 找出来再 `taskkill //F //PID` 才收干净的 —— **收尾判据必须是「端口不再 LISTENING + PID 不在了」**，不是「`TaskStop` 返回成功」。

⚠️ `GRANT` 的主机名**先查再写**：`SELECT user,host FROM mysql.user;` —— 本仓实测 `crm_user` 的 host 是
**`localhost`**（用 `%` 会 `ERROR 1410`）。`quickstart.md` §4 已按 `localhost` 写。

---

## §N 可核判据（`quickstart.md` §5）实跑

| # | 判据 | 期望 | 实跑读数 |
|---|---|---|---|
| ① | `simulateSync` / `btnSimulateSync` / `模拟同步邮件` 在 `backend/src` + `frontend/src` | **0 命中** | ⚠️ **原文判据按字面跑得 4，不是 0**（见下方订正）；**排除行首注释后 = 0** ✅ |
| ② | `STATUS_SIMULATED` 等三常量在实体上、`SIMULATED` 在 `types/mail.ts` | 都在 | ✅ `MailSyncRecord.java:25/28/31` 三常量齐备；`types/mail.ts:21-22` 联合类型 `'SYNCED' \| 'FAILED' \| 'SIMULATED'` |
| ③ | `demo-enabled` 两处默认值同向（都 `false`） | 两处都 `false` | ✅ `application.yml:62` = `${CRM_MAIL_INBOUND_DEMO_ENABLED:false}`；`MailInboundStatus.java:26` = `@Value("${crm.mail.inbound.demo-enabled:false}")`；另 `.env.example:30` 留空、`docker-compose.yml:40` = `${CRM_MAIL_INBOUND_DEMO_ENABLED:-false}` |
| ④ | `git diff --stat 66bb71f..HEAD -- specs/062-email-sync/` | **空输出** | ✅ **空输出**（062 的全部工件一字未改） |
| ⑤ | 两个权限码仍在 `MailAccountController` | 都在 | ✅ `mail_account:manage` 4 处（`:64/72/79/87`）、`mail_sync:manage` 3 处（`:109/116/127`），**086 的两码未合并** |
| ⑥ | `git diff --stat 66bb71f..HEAD -- backend/src/main/resources/db/migration/` | **空输出**（零迁移） | ✅ **空输出** |

**① 的订正（这是本批**唯一**一条「判据按字面跑不通」的地方，照实登记而不是改口径蒙过去）**：

```
$ grep -rn "simulateSync"        backend/src frontend/src | wc -l   →  2
$ grep -rn "btnSimulateSync"     backend/src frontend/src | wc -l   →  0
$ grep -rn "模拟同步邮件"         backend/src               | wc -l   →  2
$ # 三条合起来 4 命中，逐条是：
  MailInboundStatus.java:10        （javadoc，说明旧主题是什么）
  MailSyncRecordService.java:52    （javadoc，「062 里叫 simulateSync」）
  MailSyncRecordServiceTest.java:104（javadoc，解释为什么只换常量不够）
  services/mailService.ts:24       （注释，「062 的 simulateSync 改名」）
$ grep -rn "simulateSync\|btnSimulateSync\|模拟同步邮件" backend/src frontend/src \
    | grep -vE ':[0-9]+:\s*(\*|//)' | wc -l   →  0        ← 排除行首注释后
```

⚠️ **为什么 4 命中不是缺陷**：这四处**全部是注释**，且正是**留痕规则要求**留下的
（「旧值仍能被 grep 到」）——**产品代码里旧标识符已经一个都不剩**（非注释命中 **0**）。
⇒ **该冲突是本仓的已知形状**（「**零命中判据与订正不静默互斥**」）：
**判据要 grep 的恰恰是留痕规则要求保留的那个名字**。
**处置**：口径**补上「排除行首注释」**（`grep -vE ':[0-9]+:\s*(\*|//)'`），
**而不是**把注释删掉去凑「0 命中」——后者是**用一次静默改写去满足一条自查判据**，
恰是本项要防的东西。⚠️ **这条订正必须同时记进 `tasks.md` 的「实做订正」**（本项的自查口径所以变了，
后来者拿 `quickstart.md` §5 原文跑会得到 4，不该被读成「没改干净」）。
✅ 另两条**按字面就过**：`btnSimulateSync` **全仓源目录 0 命中**（i18n 死键已删干净、
`i18n:check` 的孤儿键分支未报警），`types/mail.ts` 的联合类型确实在（② 行）。

---

## §O 订正不静默自查（`quickstart.md` §6）命中数

⚠️ **口径**：本节的判据要 grep 的正是**旧值**，而留痕规则要求旧值**仍留在 ⚠️ 块里**
⇒ **命中数 ≥1 才是「订正不静默」的正向证据**，**不是**「没改干净」。

| # | 要 grep 的旧值 | 期望 | 实跑命中 |
|---|---|---|---|
| ① | `收信侧 \`simulateSync\` 仍在` / `接 IMAP 或从功能清单移除`（`CRM_FEATURE_COMPARISON.md`） | 非零 | ✅ **1 / 1** |
| ② | `不可对客户宣称已具备邮件同步` / `它是"诚信缺口"里唯一没被一期关掉的一条` | 非零 | ✅ **1 / 1** |
| ③ | `邮件同步收信侧仍为模拟` | 非零 | ✅ **1**（`:318` 评分行；`:347` 深度行那处措辞不同，见下注） |
| ④ | `邮件同步为 \`simulateSync\` 模拟实现` | 非零 | ✅ **1** |
| ⑤ | `同步流程为模拟实现，尚未接入 IMAP`（`CRM_FEATURE_COMPARISON.md` 的引文）/ `邮件账户配置、同步记录框架`（`PROJECT_FEATURES.md`） | 非零 | ✅ **1 / 1** |
| ⑥ | `2962` / `3452` / `99（001–100`（`PROJECT_FEATURES.md`） | 非零 | ✅ **6 / 6 / 3** |

**新值到位**（与上表**成对**读）：`grep -c "2963"` = **5** · `grep -c "3453"` = **5** ·
`grep -c "100（001–101"` = **2** · `i18n:check` 实测 **2963/2963**（C4 已跑，见 §L）。

⚠️ **③ 的一处口径要说清**：`邮件同步收信侧仍为模拟` 在 `:318`（评分行）命中 **1**；
而 `:347`（深度结论行）的原文措辞是「**邮件同步收信侧仍是模拟**」（**是**不是**为**），
它**不在本条 grep 的字面量里** —— 这是**判据本身的字面量覆盖不全**，不是漏改：
`:347` 的原文**同样逐字保留**在该行的 ⚠️ 块内（可用 `grep -c "邮件同步收信侧仍是模拟"` 自证，实测 **1**）。
⇒ **登记为口径边界**：六条 grep 各自只覆盖了它字面量所指的那一处，
**「旧值全部保留」这件事不由这六条共同证明**，而由**每一处各自的 ⚠️ 块**证明。

⚠️ **六条全部 ≥1，一条 0 命中都没有** ⇒ **没有触发**「先分辨『不存在』还是『被折行断成两行』」那条处置；
本仓曾有的**格式化器折行打断行式 grep** 那个坑，本批**未遇到**（本批的留痕长句多为表格单元格内，
天然不跨行；跨行的长句都在 `specs/` 的 Markdown 里，不受 `spotless` 管辖）。

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

## §R 各节由哪次实跑填

| 节 | 来源 |
|---|---|
| §0 · §P | 立项期勘察事实 + `quickstart.md` §2 的既定口径（**非**交付批新增读数） |
| §A–§G | `D1`–`D7` 逐条定向破坏（**未留 `D*.log`**，见下） |
| §L | 交付批的完整 `cd backend && mvn -B spotless:apply && mvn -B verify`（日志 `/tmp/gate-final.log`） |
| §M | 隔离实例（8099 / `crm_ib101` / Redis db 5）两档冒烟（日志 `/tmp/crm-ib101.log`、`/tmp/crm-ib101-demo.log`） |
| §N · §O | `quickstart.md` §5 / §6 逐条 grep（**只读**，不含任何 Maven） |

⚠️ **两处对 `plan.md` 原定的订正（照实登记，不静默）**：

1. **破坏的备份文件命名与 100 批不同**。计划与本文骨架里写的是
   `/tmp/destruct/101/D<n>-<FileName>.bak` 与配套的 `/tmp/destruct/101/D<n>.log`（那是 100 批的形制，
   `/tmp/destruct/` 下仍能看到 `D1-RateLimitStore.bak`、`D1.log` 那一组）。**101 批实际用的是**
   `/tmp/destruct/101/<SimpleName>.java.bak`（**5 个**：`ErrorCode.java.bak`、`MailInboundStatus.java.bak`、
   `MailSyncPage.tsx.bak`、`MailSyncRecordService.java.bak`、`MailSyncRecordServiceTest.java.bak`，
   全部 12:47 建立、一次备份多跑复用），**并且没有保留任何 `D*.log`**。
   ⇒ 各节的「备份 / 日志」行已按**实际路径**写；**读数不是从日志文件抄的**，而是当场记下、
   交付时逐字核对恢复（见 3）。
2. **D3 实际拆成了 D3a / D3b 两跑**（计划写的是单跑）——因为 D3a 之后**计划预期的那条红没有出现**，
   就地加跑 D3b 才把它证完。偏差与理由见 §C 与 `tasks.md` 的「实做订正」。
3. **§A–§G 的读数来源是随跑随记的原始输出，交付时从会话记录里逐字恢复后填入**（12:47–12:52 那一段）。
   ⚠️ **这样做的前提是记账口径**：这些读数在跑的那一刻就写进了对话，**不是**事后凭印象补写的；
   `git hash-object` 的还原判据是当场跑的。**没有第二次核对机会**（跑过就是跑过）——
   这一点如实写出，供后来者判断这些读数该被信到什么程度。
