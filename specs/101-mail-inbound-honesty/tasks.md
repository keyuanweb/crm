# 任务：邮件同步收信侧诚实化（101）

**Created**: 2026-09-17
**图例**：`[ ]` 未完成 / `[x]` 已完成。**立项阶段一律不预勾。**
**⚠️ 本项的实际编写顺序是「先实现、后补用例」**（沿用 087/088/092–100 的既有做法）——**不得**据此声称走过 spec-first；定向破坏留痕证明的是**护栏有牙齿**，不是「红先出现」。
**⚠️ 本项的活动半径**：**后端 + 前端 + 文档**。**零端点、零 DTO、零迁移、零权限码**；**产 `contracts/`（对外行为确有变更）**、**不产 `data-model.md`**。
**⚠️ 行号口径**：本文件里的行号是**立项时的实测值**，只作定位辅助。**权威锚点是符号名**（类名 / 方法名 / 字段名 / 字符串字面量）——本仓已实测过「行号引用会腐坏」。

**⚠️ 三处「必须同批」（分成两次就是错的）**：
1. **T031 与 T032 同批**：`README.md` 的功能行与 `CRM_FEATURE_COMPARISON.md:448` 那条**引用它的证据行**——后者把前者的**现文逐字**抄在里面，改一个不改另一个，那句引文当场变成假话。
2. **T025 与 T026 同批**：`mailService.ts` 的改名与 `MailSyncPage.tsx` 的调用点——中间态前端**不会编译失败**（HTTP 调用不是共享类型）⇒ 靠门禁抓不住，只能靠同批。
3. **T010 与 T011 同批**：`ErrorCode` 的新枚举项与抛出它的异常类——中间态编译不过（异常构造引用了那个常量）。
4. **T027 与 T028 同批**：i18n 删 `btnSimulateSync` 与测试里引用它的键名——删键而测试仍引用它 ⇒ `i18n:check` 的孤儿键分支与测试会同时红（这处**能**被门禁抓住，列在此处是为了说明为什么它们同属 C4）。

---

## 阶段 A 工件与登记（提交 C1 = `docs(101): 立项`）

- [ ] T001 写八件工件：`spec.md` / `plan.md` / `research.md` / `contracts/mail-inbound.md` / `quickstart.md` / `tasks.md` / `falsification-evidence.md` / `checklists/requirements.md`
      ⚠️ **`falsification-evidence.md` 在立项期就建全**（D1–D7 的破坏表与「该红的判据」在开工前定稿，读数留空待 T034）——**不得**等交付时再补一张「照着结果编的」破坏表
- [ ] T002 `contracts/mail-inbound.md` 必须装齐：① **逐字收存** 062 的端点约定（`Response 200` 与 `"syncStatus": "SYNCED"` 必须可 grep）；
      ② 062 的错误码表（4 行）与范围声明（`spec.md:86`）、FR-E05 的原文；③ **变更对照表**（默认/演示/状态值域/主题与外部 id/账户不存在/错误码表）；
      ④ 新错误码与**为什么是 409**（否掉 501 与 503 的理由）；⑤ 生效后的可观测行为四条；⑥ 与 062 的关系（谁不改、谁为准、不宣称 062 有错）
- [ ] T003 `research.md` 必须装齐：① 与对比文档 7 处的关系与「**状态列不翻**」的理由；② 三条路的代价对照（为什么选配置门）；
      ③ 409 vs 501 vs 503；④ 零迁移的依据（030 的 `SKIPPED` 先例 + 无 CHECK）；⑤ 异常为什么继承 `BusinessException`；
      ⑥ 配置默认值两处同向 + **不给前端加探针**；⑦ 前端为什么必须扩成三向；⑧ i18n 为什么改名 + 数字落点；
      ⑨ 契约偏差为什么不能沿用 085 判例；⑩ 「零插入」为什么不能靠事务推理；⑪ jacoco 分母；⑫ `isVersionConflict` 的口径；
      ⑬ **覆盖缺口与假绿通道**（5 条）
- [ ] T004 `specs/README.md` 模块表加 **101 行**（**状态列如实写「⏳ 进行中」**，**不预勾**）
- [ ] T005 `specs/README.md` 的**编号说明**段纳入 101（**按文本锚定位，不按行号**），必须写明两条：① 本项的形制（后端+前端，产 `contracts/`、零迁移）；
      ② ⚠️ **本项偏离 062 的冻结契约**（方向与 085 判例相反，偏差台账在 101 的 `contracts/`）
- [ ] T006 `specs/roadmap.md` 加 101 行（**勾选框留空、不预勾**）；`最后更新` 前置 101 立项条目（旧值降级为「**上一条（原文保留）**」，逐字不改）；
      `## 当前进度` 计数由 **99 勾 / 0 未勾** 变 **99 勾 / 1 未勾**（实测复核：`grep -c '^- \[x\]' specs/roadmap.md` 与 `'- \[ \]'` 各计一次）
- [ ] T007 ⚠️ **立项阶段不改「整体覆盖度」那句交付态断言**（它说的是**已交付**的编号面，而 101 **尚未交付** ⇒ 改成 `001–101` 等于把在办项写成已交付）。
      照 097/099/100 立项时的同一处置：只前置带日期的 ⚠️ 块登记**随入列漂移的编号面计数**（**100 → 101** 个目录），**旧值逐字保留**；交付时（T033）再改那句
- [ ] T008 ⚠️ **立项阶段不动任何「对外规模数字」**（i18n 键数/行数、Spec 模块数）：它们必须与**交付时的实测**同批改齐
      ⇒ 前移到 **T029（前端改键后）与 T032（交付收口）**。只改一处等于用一次订正造出两处新矛盾。**该前移在此登记**

---

## 阶段 B 配置门与 `SIMULATED` 状态（提交 C2 = `feat(101): 收信侧配置门与 SIMULATED 状态`）

- [ ] T009 新增 `backend/src/main/java/com/crm/config/MailInboundStatus.java`：`@Component`、`@Value("${crm.mail.inbound.demo-enabled:false}")`、
      `isDemoEnabled()`、常量 `NOT_CONFIGURED_MESSAGE = "未接入收信源（IMAP），同步未执行"`；javadoc 照 `MailStatus` 写明
      「**本类是收信侧能否生成记录的唯一判据**、真接 IMAP 时改这里、别处不要重复判断配置」，并写清「键为 true 只表示**允许生成演示记录**，**不是**连通性探测」
- [ ] T010 `backend/src/main/java/com/crm/common/ErrorCode.java`：加 `MAIL_INBOUND_NOT_CONFIGURED(409, "MAIL_INBOUND_NOT_CONFIGURED", "未接入收信源（IMAP），同步未执行")`，
      紧邻 mail 块（`MAIL_RECORD_NOT_FOUND` 之后）；**不合并**任何既有码、不动 `MFA_LOCKED` 的注释
- [ ] T011 新增 `backend/src/main/java/com/crm/common/MailInboundNotConfiguredException.java`：`extends BusinessException`，
      构造 `super(ErrorCode.MAIL_INBOUND_NOT_CONFIGURED, MailInboundStatus.NOT_CONFIGURED_MESSAGE)`；javadoc 写明**为什么与发信侧的裸 `RuntimeException` 不同**
- [ ] T012 `backend/src/main/java/com/crm/entity/MailSyncRecord.java`：加 `STATUS_SYNCED` / `STATUS_FAILED` / `STATUS_SIMULATED` 三个常量；
      把类内 `/** SYNCED / FAILED。 */` 订正为三值（形制照 `EmailSendLog.STATUS_SENT`/`STATUS_SKIPPED`）
- [ ] T013 `backend/src/main/java/com/crm/service/MailSyncRecordService.java`：构造函数加 `MailInboundStatus`；`simulateSync` → **`triggerSync`**；
      方法体顺序固定为 `require` → 判门 → 分支；演示分支写 `STATUS_SIMULATED`、主题带演示标记、`externalId` 用 `demo-` 前缀；
      javadoc 重写（默认与演示两条路、真实 IMAP 落地时替换哪一段）；**列表/删除两个方法一字不动**
- [ ] T014 `backend/src/main/java/com/crm/controller/MailAccountController.java`：`@Operation(summary = "触发收信同步（未接入收信源时拒绝）")`、
      javadoc 补齐两种结果、调用点改名；⚠️ **`@RequirePermission("mail_sync:manage")` 与类 javadoc 的权限拆分论述一字不动**（086 的两码不合并）
- [ ] T015 配置三处（**同一提交**）：`backend/src/main/resources/application.yml` 的 `crm.mail` 块内加 `inbound.demo-enabled: ${CRM_MAIL_INBOUND_DEMO_ENABLED:false}`
      + 注释写明「**唯一真源是本行的 `false`；Java 侧的 `:false` 只是缺失兜底**」；`.env.example` 邮件段加 `CRM_MAIL_INBOUND_DEMO_ENABLED=`；
      `docker-compose.yml` 的 `CRM_MAIL_*` 那组加同名项。两处注释都要**解释不设置会怎样**（照 030 的体例）
- [ ] T016 **编译自证**（本提交不跑全绿门禁——`MailSyncRecordServiceTest` / `EmailSyncIT` 此刻**必然红**，是预期的）：
      `cd backend && mvn -B -o -q compile` 通过即可提交；在提交信息里写明「测试改动在 C3」

---

## 阶段 C 用例（提交 C3 = `test(101): 收信侧默认拒绝与演示路径用例`）

- [ ] T017 新增 `backend/src/test/java/com/crm/config/MailInboundStatusTest.java`（**T8**）：直接构造 `new MailInboundStatus(true/false)`，
      断言 `isDemoEnabled()` 两个分支 —— ⚠️ **这是本项唯一能钉住 Java 侧缺省值的判据**（IT 读 yml，改 `@Value` 的 `:false` 不会让 IT 变红）
- [ ] T018 重写 `backend/src/test/java/com/crm/service/MailSyncRecordServiceTest.java`（**T1**）：默认路径 ⇒ 抛 `MailInboundNotConfiguredException`
      **且 `verify(recordMapper, never()).insert(any())`**；演示路径 ⇒ 返回 `SIMULATED` 记录且 `insert` 恰好一次。
      ⚠️ 构造函数加了参数，`setUp()` 要同步改
- [ ] T019 `backend/src/test/java/com/crm/integration/EmailSyncIT.java`（**T2**）：同步段改为断言 **409** + `error.code == MAIL_INBOUND_NOT_CONFIGURED`，
      **并正面断言** `GET /records` 的 `total == 0`（正对照：专杀「靠事务会回滚」的推理）；旧的 `SYNCED` / 主题断言**删除**（`spec.md` 的 FR-009 已给出新形态）。
      ⚠️ 中文文案断言必须显式 `StandardCharsets.UTF_8`（本仓有 ISO-8859-1 假红先例）；**优先断 `error.code`**
- [ ] T020 新增 `backend/src/test/java/com/crm/integration/MailInboundDemoIT.java`（**T3/T4**）：`@TestPropertySource(properties = "crm.mail.inbound.demo-enabled=true")`；
      断言 200 + `data.syncStatus == "SIMULATED"` + `total == 1`，且 `subject` / `externalId` 带演示标记
- [ ] T021 `backend/src/test/java/com/crm/integration/PermissionEnforcementIT.java` 里 SALES 调同步端点的探针（**T5**）：`isOk()` → **`isConflict()`**，
      并把注释改成「断言的是**不是 403**——权限通过后落在『未接入收信源』上」。⚠️ **只改这一处**，该用例的其余断言（含 `mail_account:manage` 面的 403）一字不动
- [ ] T022 **本项第一次跑全绿门禁**：`cd backend && mvn -B spotless:apply && mvn -B verify`（不传 `-DargLine`）；
      判据 = 失败集合 ⊆ 4 例已批准偏差 + T1–T5/T8 全绿；`ls backend/target/jacoco.exec`
- [ ] T023 定向破坏 **D1–D7** 逐条做、逐条观测、逐条 `cp` 备份回写还原（判据 `git hash-object` 与破坏前相等）；
      ⚠️ **禁用 `git checkout`**；逐条记进 `falsification-evidence.md`（若某条「该红的没红」，就地补强判据后重跑，**如实登记**而不是记成绿）

---

## 阶段 D 前端诚实化（提交 C4 = `feat(101): 收信侧前端诚实化`）

- [ ] T024 `frontend/src/types/mail.ts`：`syncStatus` 联合类型扩为 `'SYNCED' | 'FAILED' | 'SIMULATED'`
- [ ] T025 `frontend/src/services/mailService.ts`：`simulateSync` → **`triggerSync`**（路径与方法不变）
- [ ] T026 `frontend/src/pages/mail/MailSyncPage.tsx`：
      ① 状态列改**三向**（`SYNCED` 绿 `tagSynced` / `SIMULATED` **橙** `tagSimulated` / **其余含未知值** 红 `tagFailed`）；
      ② 按钮改调 `triggerSync`；
      ③ Alert **保留**，`demoDataNoticeTitle` / `demoDataNoticeDesc` / `msgSyncTriggered` **只改值不改键**（086 的挂载守卫依赖前者）
- [ ] T027 i18n：`zh-CN.ts` 与 `en.ts` **删** `pages.mail.btnSimulateSync`、**增** `pages.mail.btnSyncInbox` 与 `pages.mail.tagSimulated`、
      **改值**三处。⇒ 键 **2962 → 2963**、行 **3452/3425 → 3453/3426**（差仍 27）
- [ ] T028 `frontend/src/pages/mail/MailSyncPage.perm.test.tsx`：mock 名与按键键名同步改名（**四条既有用例的结构与断言对象不变**）；
      **新增**「`SIMULATED` 渲染成『模拟』且**不是**『已同步』」与「未知状态仍渲染成『失败』」两条断言（**T6/T7**）
- [ ] T029 **同批**改 `PROJECT_FEATURES.md` 的三个 i18n 数字落点（`:19` / `:77` / `:111`）：键 **2963**、行 **3453/3426**（差仍 27）；
      照 099 的体例**追加带日期 ⚠️**、**旧值（2962 / 3452 / 3425）逐字保留可 grep**
- [ ] T030 前端五道门禁 + 定向 vitest（`quickstart.md` §1.2）；判据：`i18n:check` 报 **2963/2963**、路由 **58** / manifest **56** **不变**；
      `zh:check` 的硬编码中文台账**不得增加**

---

## 阶段 E 交付登记与文档订正（提交 C5 = `docs(101): 交付登记与文档订正` = 交付）

- [ ] T031 对比文档 7 处按「**订正不静默**」改写（原文逐字保留 + 带日期 ⚠️，粒度到每一列，判据 = **旧值仍可 grep**）：
      `:223`（模块表行）/ `:228`（小结）/ `:318` 与 `:347`（评分与深度，**分值一字不动**）/ `:371`（**P0 第 6 条：状态列刻意不翻**，写明「动作列的两条出路一条都没做、本项关掉的是『对外承诺不实』这一半」）/
      `:448`（**与 T032 同批**，它是引用 README 现文的证据行）/ `:477`（新发现表）
- [ ] T032 `README.md` 的「邮件同步」功能行改写为如实的收信侧说明 + **同批**改 `CRM_FEATURE_COMPARISON.md:448` 的引文（旧引文逐字留在 ⚠️ 内）；
      `PROJECT_FEATURES.md:194` 的模块表行同批补如实说明
- [ ] T033 交付收口：`specs/README.md` 的 101 行状态列改 ✅ 并补实测读数；`specs/roadmap.md` 的 101 行勾上、`最后更新` 前置交付段、
      「整体覆盖度」那句交付态断言改为 **001–101**、编号面计数 **100 → 101**（**旧值逐字保留**）；
      `PROJECT_FEATURES.md:20` / `:107` 的 Spec 模块数改 **100（001–101，缺 069）**
- [ ] T034 写 `falsification-evidence.md` 的实测读数：D1–D7 逐条观测、门禁读数（surefire/failsafe/失败集合/jacoco 结论行与覆盖率/`jacoco.exec` 字节数）、
      隔离实例冒烟两次启动的读数与收尾核对、§订正不静默自查的命中数、§可核判据的实跑读数
- [ ] T035 `tasks.md` 勾选（**只勾真正做完的**）+ 填**交付块**（不得预填、**不写任何提交哈希**）+ 在「实做订正」里如实记录与计划的偏差
- [ ] T036 交付前复跑 `quickstart.md` §5/§6 两节的全部判据（**不跑 Maven**——门禁的读数已经取过，再跑会覆盖 `jacoco.exec`）

---

## 实做订正（**如实记录与本计划的偏差**，交付时填）

| # | 偏差 | 处置与理由 |
|---|---|---|
| — | （交付时填） | — |

---

## 交付块（**交付时填**，不得预填）

| 项 | 值 |
|---|---|
| 提交数 / 末条提交主题 | （交付时填；**不写任何提交哈希**） |
| `mvn -B verify`（surefire / failsafe） | （交付时填） |
| `jacoco:check` 结论行 | （交付时填） |
| 覆盖率（INSTRUCTION / BUNDLE） | （交付时填） |
| `ls backend/target/jacoco.exec` | （交付时填） |
| 新增/改动的测试文件与用例数 | （交付时填） |
| 定向破坏 D1–D7 | （交付时填） |
| 隔离实例冒烟 | （交付时填：端口 / schema / Redis db / 两档读数 / 收尾核对） |
| 可核判据（`quickstart.md` §5） | （交付时填） |
| 订正不静默自查（`quickstart.md` §6） | （交付时填：逐条命中数） |
| ⚠️ 本项**不把提交自己的哈希**写进任何被它携带的文件 | 遵守（只引用**提交主题/序号**） |
