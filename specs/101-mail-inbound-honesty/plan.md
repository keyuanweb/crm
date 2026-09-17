# 实施计划：邮件同步收信侧诚实化（101）

**上游**: `CRM_FEATURE_COMPARISON.md` **P0 第 6 条**（`spec.md` §1.1 逐字引）
**用户裁决（2026-09-17）**: ① 方向 = 诚实化（不接 IMAP、不移除功能）；② 尺度 = 配置门（默认拒绝且零插入；演示开关下生成 `SIMULATED` 并标注）
**形制**: 后端 + 前端 + 文档；**零端点、零迁移、零权限码**；**产 `contracts/`（对外行为确有变更）**、**不产 `data-model.md`（无实体、无迁移）**

---

## Constitution Check

### 一、契约优先的 API 设计（不可协商）

✅ **通过，但需要一份新契约**。062 同意该端点为「模拟同步、200、`SYNCED`」，而该行为本身就是不实的。章程原则一要求「契约不得被静默修改，任何偏差必须明确说明理由并登记」——本项**偏离**契约，故产出 `contracts/mail-inbound.md`：给出新行为 + **逐字收存** 062 原文作偏差台账（`Response 200`、`syncStatus: "SYNCED"` 必须可 grep 到）。
⚠️ **与 085 判例的方向相反**，不得混用（`research.md` §9 与契约 §1 的对照表）。
062 的任何文件**一字不动**。

### 二、分层架构与关注点分离（不可协商）

✅ **通过**。判据类放 `com.crm.config`（与 `MailStatus` 同包同形）；异常放 `com.crm.common`（与 `MailNotConfiguredException` 同包）；状态常量放**实体**（与 `EmailSendLog.STATUS_*` 同形）；服务只做编排；控制器不判业务。**不新增包、不新增范式**（全仓 `@ConfigurationProperties` 0 命中 ⇒ 照旧用 `@Value`）。

### 三、数据完整性、安全与校验（不可协商）

✅ **通过**。权限码 `mail_sync:manage` 一字不动（086 收口的两个码不合并）；默认路径**零副作用**（正面断言，不靠事务推理）；演示路径的记录**明确标注**为演示（状态 + 主题 + 外部 id 三处）。**安全侧无放松**：本项让一条会写假数据的通路默认关闭，是**减少**攻击面（此前任何持有 `mail_sync:manage` 的主体都能无限制地灌数据）。
⚠️ 一处新登记：**409 与 `isVersionConflict`（按状态码判定）的口径**（`research.md` §12）。

### 四、测试优先与质量门禁（不可协商）

✅ **通过**。8 条判据（T1–T8，见「验证」节），覆盖默认拒绝/零插入/演示标注/权限面/界面三态/配置默认值；7 条定向破坏逐条做、逐条还原。
⚠️ **假绿通道已识别**：IT 读 yml ⇒ 改 Java 侧 `@Value` 的缺省值不会让任何 IT 变红 ⇒ 必须有一条直接构造该类的单测（T8）。
⚠️ 中文断言必须显式 UTF-8（本仓有 ISO-8859-1 假红的先例）；能断 `error.code` 的地方优先断 code。

### 五、简洁、可维护与可观测（不可协商）

✅ **通过**。新增 2 个小类（一个判据、一个异常）+ 1 个枚举项 + 1 个实体常量；拒绝路径复用全仓统一错误信封，不新增头、不新增处理器。可观测性：错误码稳定（`MAIL_INBOUND_NOT_CONFIGURED`），三处配置注释解释默认值。

---

## 已核实事实（机制级；均已读实现或实测）

1. **假数据的产生点**：`service/MailSyncRecordService.java` 的 `simulateSync` **无条件**插入 `syncStatus=SYNCED`、`subject=「模拟同步邮件」`、`fromAddress=customer@example.com`、`externalId=mock-<nanoTime>` 的记录；唯一生产调用点是 `controller/MailAccountController.java` 的 `POST /{id}/sync`（`@RequirePermission("mail_sync:manage")`）。
2. **界面把假数据渲染成事实**：`MailSyncPage.tsx:207` 是二向渲染（`SYNCED` ⇒ 绿「已同步」，其余 ⇒ 红「失败」）⇒ 若只改后端为 `SIMULATED`，该行会变成**红色「失败」**（同样是假象）。`types/mail.ts:21` 的联合类型 `'SYNCED' | 'FAILED'` 会在编译期挡住「只改后端」。
3. **062 契约的原文**：`specs/062-email-sync/contracts/email-sync.md:32-40`（「…验证链路）。**Response 200**」+ `"syncStatus": "SYNCED"`）、`:59-66`（4 行错误码表）、`spec.md:62-64`（FR-E05）、`spec.md:86`（v1 不接真实 IMAP）。
4. **零迁移的依据**：`V69__mail_sync.sql` 的 `sync_status VARCHAR(20)` 只有 `COMMENT 'SYNCED/FAILED'`、**无 CHECK**；`backend/src/test/resources/schema-h2.sql` 同样无约束；先例 030 的 `SKIPPED`（`V48` 的 COMMENT 至今只列 `'SENT/FAILED'`）⇒ 值域由**实体常量**维护。
5. **发信侧的零件落点**：`config/MailStatus.java`（`@Component` + `@Value("${crm.mail.host:}")` + `NOT_CONFIGURED_MESSAGE` + javadoc「唯一判据」）、`common/MailNotConfiguredException.java`、`entity/EmailSendLog.java:21,27`（`STATUS_SENT`/`STATUS_SKIPPED`）、`service/EmailCampaignService.java:51,63,73`（构造函数注入）。
6. **`ErrorCode` 状态码分布**：400×10 / 401×8 / 403×8 / 404×38 / **409×30** / 422×48 / 429×2 / 500×3 / 503×1（**501 零命中**）。
7. **jacoco 排除项**只有 `com/crm/CrmApplication.class`、`dto/**`、`entity/**`、`common/**` ⇒ 新增的 `com/crm/config/MailInboundStatus` **在分母里**（T8 必须真覆盖两个分支）。
8. **i18n 现值**：键 **2962/2962**、行 **3452/3425**（差 **27**）；落点在 `PROJECT_FEATURES.md:19`（模块规模行，体例是「就地订正 + 旧值仍可 grep」）、`:77`（交付后记的表格行）、`:111`（聚合行）。`i18n:check` 另判路由 58 / manifest 56 与**孤儿键**。
9. **`CRM_FEATURE_COMPARISON.md:448`** 把 `README.md` 的**现文逐字**当证据引用（「✅ 已修正：现文为『…（同步流程为模拟实现，尚未接入 IMAP）』」）⇒ 改 README 必须同批改它。
10. **`apiClient.ts` 的 `isVersionConflict` 只按状态码 409 判定**；邮件页只用 `extractErrorMessage` ⇒ 本项的 409 当前不会被误读（登记，不改）。
11. **门禁**：`cd backend && mvn -B verify`（不传 `-DargLine`）；判据是「失败集合 ⊆ 4 例已批准偏差 + 本项新增全绿」；前端 `i18n:check` / `lint` / `typecheck` / `ui:check` / `zh:check` / 定向 vitest。**spotless 是 verify 相位首个门禁**，比用例失败更早中止。
12. **`frontend/.i18n-keys/**` 是 gitignored 的遗留临时件**（09-12，`.gitignore` 注释自称「合并后即弃」）⇒ 不动、不新增（该目录仍在 `eslint .` 的扫描范围内）。

---

## 结构决策

### 语义（本项全部的行为变更）

| 配置 | `POST /mail-accounts/{id}/sync` |
|---|---|
| **默认**（`crm.mail.inbound.demo-enabled` 缺省 = `false`） | ① `require(accountId)`（不存在 ⇒ **404**，不变）→ ② 抛 `MailInboundNotConfiguredException` ⇒ **409 + `MAIL_INBOUND_NOT_CONFIGURED`** → ③ **零插入** |
| **演示**（显式 `true`） | 1 条 `direction=INBOUND`、`syncStatus=SIMULATED`、主题带演示标记、`externalId=demo-…` 的记录，**200** |

### 组件

- **`config/MailInboundStatus.java`**（新）：`@Component`、`@Value("${crm.mail.inbound.demo-enabled:false}")`、`isDemoEnabled()`、`NOT_CONFIGURED_MESSAGE`。javadoc 照 `MailStatus`：**唯一判据、别处不要重复判断、真接 IMAP 时改这里**。
- **`common/MailInboundNotConfiguredException.java`**（新）：`extends BusinessException`，消息取 `MailInboundStatus.NOT_CONFIGURED_MESSAGE`。理由见 `research.md` §5。
- **`common/ErrorCode.java`**：加 `MAIL_INBOUND_NOT_CONFIGURED(409, …)`（紧邻 mail 块）。
- **`entity/MailSyncRecord.java`**：加 `STATUS_SYNCED` / `STATUS_FAILED` / `STATUS_SIMULATED`，订正类内注释。
- **`service/MailSyncRecordService.java`**：构造加 `MailInboundStatus`；`simulateSync` → `triggerSync`；演示分支用常量；javadoc 重写（写清默认与演示两条路、真实 IMAP 落地时替换哪一段）。
- **`controller/MailAccountController.java`**：`@Operation` 与 javadoc 订正（`@RequirePermission` 不动）。
- **配置三处**：`application.yml`（`crm.mail` 块内，注释写明「唯一真源是本行的 false；Java 侧只是缺失兜底」）、`.env.example`、`docker-compose.yml`（照 030 那组的体例：**解释不设置会怎样**）。

### 前端

- `types/mail.ts` 扩联合类型；`mailService.ts` 改名 `triggerSync`；`MailSyncPage.tsx` 三向渲染 + Alert/按钮文案（**保留**控件与结构）；i18n **-1 / +2 键 + 三处改值**（净 +1 键 / +1 行）；`MailSyncPage.perm.test.tsx` 改名 + 两条新断言。

### 一个数字住在好几个地方（落点清单，交付时逐处收口）

| 数字 | 落点 | 本项 |
|---|---|---|
| i18n 键数 2962 / 行 3452·3425 | `PROJECT_FEATURES.md:19` / `:77` / `:111` | ⇒ **2963 / 3453·3426**（差仍 27），旧值逐字保留 |
| Spec 模块数 99（001–100） | `PROJECT_FEATURES.md:20` / `:107`；`specs/README.md`（模块表 + `:141` 编号说明）；`specs/roadmap.md`（`## 当前进度` + 整体覆盖度 + 最后更新） | ⇒ **100（001–101，缺 069）**；roadmap 的「整体覆盖度」**交付态断言照旧不改**（在办项不得写成已交付——与 100 同一处置） |
| 迁移数 89（V1~V90） | `specs/README.md:3`；`PROJECT_FEATURES.md:23,41,56,290` | **一处都不动**（本项零迁移） |
| 端点数 / DTO / 权限码 | — | 全不变 |

---

## Project Structure

### Documentation (this feature)

```
specs/101-mail-inbound-honesty/
├── spec.md                     # 本规格（US1–US3、FR-001~025、非目标、成功判据、未验证边界）
├── plan.md                     # 本文件
├── research.md                 # §1–§13 技术决策 + 覆盖缺口如实登记
├── contracts/mail-inbound.md   # 新行为 + 062 原文逐字偏差台账（FR-020）
├── quickstart.md               # 门禁、定向跑、隔离实例配方、可核判据
├── falsification-evidence.md   # D1–D7 逐条观测 + 门禁读数 + 订正不静默自查（交付时填）
├── tasks.md                    # 阶段 A–E 对应 5 次提交
└── checklists/requirements.md  # 规格质量自检
```

**不产 `data-model.md`**（无实体变更、零迁移）。

### Source Code

```
backend/src/main/java/com/crm/
├── config/MailInboundStatus.java                       # 新（判据）
├── common/MailInboundNotConfiguredException.java       # 新（异常）
├── common/ErrorCode.java                               # 改：+1 枚举项
├── entity/MailSyncRecord.java                          # 改：+3 常量、订正注释
├── service/MailSyncRecordService.java                  # 改：改名 + 判门 + 演示分支
└── controller/MailAccountController.java               # 改：@Operation / javadoc
backend/src/main/resources/application.yml              # 改：+1 配置键 + 注释
backend/src/test/java/com/crm/
├── config/MailInboundStatusTest.java                   # 新（T8）
├── service/MailSyncRecordServiceTest.java              # 改（T1）
└── integration/{EmailSyncIT,MailInboundDemoIT,PermissionEnforcementIT}.java  # 改/新（T2–T5）
frontend/src/
├── types/mail.ts                                       # 改：联合类型
├── services/mailService.ts                             # 改：改名
├── pages/mail/MailSyncPage.tsx                         # 改：三向渲染 + 文案
├── pages/mail/MailSyncPage.perm.test.tsx               # 改：改名 + 两条新断言
└── i18n/{zh-CN,en}.ts                                  # 改：-1/+2 键 + 三处改值
.env.example / docker-compose.yml                       # 改：+1 配置项 + 注释
```

---

## 分步与提交（5 次，每次可回退）

| # | 提交 | 内容 |
|---|---|---|
| C1 | `docs(101): 立项` | 八件工件 + 登记（`specs/README.md` 模块表 101 行与编号说明段、`specs/roadmap.md` 101 行与两条计数 99→100）。**不含代码** |
| C2 | `feat(101): 收信侧配置门与 SIMULATED 状态` | 后端全套 + 配置三处。⚠️ 此刻 `MailSyncRecordServiceTest` / `EmailSyncIT` **必然红**（构造函数变了、断言的是 200/`SYNCED`）⇒ 本提交**不跑全绿门禁**，只做编译自证；门禁在 C3 之后跑第一次 |
| C3 | `test(101): 收信侧默认拒绝与演示路径用例` | T1–T5、T8 的用例；`PermissionEnforcementIT` 探针 `isOk()` → `isConflict()` |
| C4 | `feat(101): 收信侧前端诚实化` | 前端全套（T6/T7）+ i18n 三处数字落点 |
| C5 | `docs(101): 交付登记与文档订正` | 对比文档 7 处 + `README.md` + `PROJECT_FEATURES.md`（模块行 + 数字落点）+ 101 行状态改 ✅ + roadmap 交付段 + `falsification-evidence.md` + `tasks.md` 勾选 |

⚠️ **C2 与 C4 之间仓处于「后端已改名、前端仍调旧名」的中间态**，且前端**不会编译失败**（HTTP 调用不是共享类型）⇒ C4 必须收掉它，且 C4 必须跑前端门禁。

---

## 验证

### 用例清单（本项唯一的**行为层**证据）

| # | 用例 | 文件 | 形态 | 会因什么缺陷变红 |
|---|---|---|---|---|
| T1 | 默认：抛 `MailInboundNotConfiguredException` 且 **0 插入** | `service/MailSyncRecordServiceTest` | 单测 | 仍然生成记录（本项核心主张）；`verify(mapper, never()).insert(any())` |
| T2 | 默认：**409** + `error.code`，且 `GET /records` 的 `total == 0` | `integration/EmailSyncIT` | MockMvc | 状态码/code 错；**偷偷写了记录**（正对照） |
| T3 | 演示：200 + `syncStatus == SIMULATED` + `total == 1` | `integration/MailInboundDemoIT` | MockMvc + `@TestPropertySource` | 门没生效 / 状态仍写 `SYNCED` |
| T4 | 演示：主题与 `externalId` 带演示标记 | 同上 | 同上 | 「换个名字继续冒充」 |
| T5 | 权限面：SALES 调该端点得 **409 而非 403** | `integration/PermissionEnforcementIT` | MockMvc | 086 拆的两个码被合并（会变 403） |
| T6 | 界面：`SIMULATED` 渲染成「模拟」且**不是**「已同步」 | `MailSyncPage.perm.test.tsx` | vitest | 状态列没扩三向（界面层最可见的那处） |
| T7 | 界面：未知状态仍渲染成「失败」 | 同上 | 同上 | 兜底被改成显示原值/绿色 |
| T8 | 配置缺省即 `false`（两个分支都覆盖） | `config/MailInboundStatusTest` | 纯单测 | 默认值被改成 true（缺省即生成假记录） |

### 门禁

```bash
cd backend && mvn -B spotless:apply              # spotless 比用例失败更早中止
cd backend && mvn -B verify                      # 不传 -DargLine
ls backend/target/jacoco.exec                    # 必须存在 + jacoco:check 有结论行
cd frontend && pnpm run i18n:check && pnpm run lint && pnpm run typecheck && pnpm run ui:check && pnpm run zh:check
cd frontend && pnpm exec vitest run src/pages/mail/MailSyncPage.perm.test.tsx   # 定向（本机默认 worker 池超订）
```

- **判据**：后端「失败集合 ⊆ 4 例已批准偏差 + 本项新增全绿」；前端全绿且 `i18n:check` 报 **2963/2963**、路由 58 / manifest 56 不变；`zh:check` 的硬编码中文台账**不得增加**。
- **交付态读数只取那一次完整 `verify`**，之后**不再跑 Maven**（会覆盖 `jacoco.exec`）。

### 定向破坏（逐条做、逐条被观测到转红、逐条 `cp` 还原；期间不提交）

| # | 破坏 | 该红的判据 |
|---|---|---|
| D1 | 撤掉配置门（默认直接生成记录） | T1 / T2 ⇒ 本项核心主张 |
| D2 | 演示路径仍写 `SYNCED` | T3 / T6 |
| D3 | 默认路径在抛异常**之前**先 `insert` | T2 的 `total == 0` 正对照 |
| D4 | 前端把 `SIMULATED` 也渲染成绿色「已同步」 | T6 |
| D5 | `ErrorCode` 的状态由 409 改成 500 | T2 / T5 |
| D6 | Java 侧 `@Value` 缺省值改 `:true` | T8（IT 侧**不会**红 ⇒ 证明 T8 不可省） |
| D7 | 删掉单测里的 `never().insert` 断言 | 无 —— 若 D3 之下仍绿，说明该断言名不副实 ⇒ 如实登记并就地补强 |

### 手工冒烟（隔离实例，两次启动）

默认档（端口 8099 / 独立 schema `crm_ib101` / Redis db 5）连调两次 ⇒ 两次 409 + `total` 不累积；演示档加 `--crm.mail.inbound.demo-enabled=true` 重启 ⇒ 200 + 一条 `SIMULATED`。收尾 `DROP DATABASE` / `REVOKE` / `FLUSHDB` 并核对共享库未动、8081 未重启。详见 `quickstart.md`。

---

## 明确不做

见 `spec.md` §4（八条，逐条有理由）：不接真实 IMAP、不移除功能、不改 062 工件、不新增迁移、不给前端加探针、不动 `.i18n-keys/**`、不修 `apiClient.isVersionConflict`、不动历史论述（`V87` / `RoleConstants` / 控制器类 javadoc）。
另：**不改对比文档的判定列与分值**；**P0 第 6 条状态列不翻**；**不碰 `.specify/feature.json`**；**不跑任何 `/speckit-*`**。

---

## 风险

| 风险 | 缓解 |
|---|---|
| 默认拒绝让默认部署下那个按钮**必然报错** | 这是诚实的代价（用户裁决保留入口）；Alert 讲清原因 + 三处配置注释；在 `spec.md` §6 如实声明「不声称不影响用户」 |
| 前后端改名不同批的中间态 | C4 必须收掉前端并跑前端门禁 |
| i18n 改名撞孤儿键分支 | 删前 grep 零命中（今天是唯一引用点，其余命中在 gitignored 的 `.i18n-keys/` 与 `coverage/` 生成物里） |
| 键数/行数 3 处落点漏改 | C4 与 C5 各收一次；判据 = 旧值仍可 grep + `i18n:check` 实测 2963 |
| `README.md` 改完忘了 `:448` 的引文 | 已列为 C5 的显式落点 |
| 409 被 `isVersionConflict` 误读 | 邮件页无调用点（实测）+ 登记在 `research.md` §12 |
| 中文断言踩 ISO-8859-1 | 优先断 `error.code`；确需断文案时显式 `StandardCharsets.UTF_8` |
| 新类进 jacoco 分母 | T8 覆盖两个分支；`jacoco:check` 必须打印结论行 |
| 交付读数被后一次 `mvn test` 冲掉 | 门禁后不再跑 Maven；引用整次 verify 的读数并写明出处 |
| 4 例已知 IT 偏差 | 判据是「失败集合 ⊆ 这 4 例 + 新增全绿」，非 exit 0 |
| spotless 重排打断行式留痕判据 | 引用长句的留痕用 `<br>` 自占一行，改完重跑 `spotless:apply` 确认不被折回 |

---

## 提交纪律

`ListAgents` 确认无并行会话写同一批文件 → **逐路径 `git add`，禁用 `git add -A` / `git commit -a`** → 提交信息末尾带 `Co-Authored-By: Claude Code <noreply@anthropic.com>`。**不把提交自己的哈希写进它携带的文件**；**复选框绝不回填/预勾**（`tasks.md` 只在交付时勾）。**订正不静默**：原文逐字保留 + 带日期 ⚠️，粒度到每一列，自查判据是「旧值仍能被 grep 到」。
