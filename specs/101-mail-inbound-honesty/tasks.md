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

- [x] T001 写八件工件：`spec.md` / `plan.md` / `research.md` / `contracts/mail-inbound.md` / `quickstart.md` / `tasks.md` / `falsification-evidence.md` / `checklists/requirements.md`
      ⚠️ **`falsification-evidence.md` 在立项期就建全**（D1–D7 的破坏表与「该红的判据」在开工前定稿，读数留空待 T034）——**不得**等交付时再补一张「照着结果编的」破坏表
- [x] T002 `contracts/mail-inbound.md` 必须装齐：① **逐字收存** 062 的端点约定（`Response 200` 与 `"syncStatus": "SYNCED"` 必须可 grep）；
      ② 062 的错误码表（4 行）与范围声明（`spec.md:86`）、FR-E05 的原文；③ **变更对照表**（默认/演示/状态值域/主题与外部 id/账户不存在/错误码表）；
      ④ 新错误码与**为什么是 409**（否掉 501 与 503 的理由）；⑤ 生效后的可观测行为四条；⑥ 与 062 的关系（谁不改、谁为准、不宣称 062 有错）
- [x] T003 `research.md` 必须装齐：① 与对比文档 7 处的关系与「**状态列不翻**」的理由；② 三条路的代价对照（为什么选配置门）；
      ③ 409 vs 501 vs 503；④ 零迁移的依据（030 的 `SKIPPED` 先例 + 无 CHECK）；⑤ 异常为什么继承 `BusinessException`；
      ⑥ 配置默认值两处同向 + **不给前端加探针**；⑦ 前端为什么必须扩成三向；⑧ i18n 为什么改名 + 数字落点；
      ⑨ 契约偏差为什么不能沿用 085 判例；⑩ 「零插入」为什么不能靠事务推理；⑪ jacoco 分母；⑫ `isVersionConflict` 的口径；
      ⑬ **覆盖缺口与假绿通道**（5 条）
- [x] T004 `specs/README.md` 模块表加 **101 行**（**状态列如实写「⏳ 进行中」**，**不预勾**）
- [x] T005 `specs/README.md` 的**编号说明**段纳入 101（**按文本锚定位，不按行号**），必须写明两条：① 本项的形制（后端+前端，产 `contracts/`、零迁移）；
      ② ⚠️ **本项偏离 062 的冻结契约**（方向与 085 判例相反，偏差台账在 101 的 `contracts/`）
- [x] T006 `specs/roadmap.md` 加 101 行（**勾选框留空、不预勾**）；`最后更新` 前置 101 立项条目（旧值降级为「**上一条（原文保留）**」，逐字不改）；
      `## 当前进度` 计数由 **99 勾 / 0 未勾** 变 **99 勾 / 1 未勾**（实测复核：`grep -c '^- \[x\]' specs/roadmap.md` 与 `'- \[ \]'` 各计一次）
- [x] T007 ⚠️ **立项阶段不改「整体覆盖度」那句交付态断言**（它说的是**已交付**的编号面，而 101 **尚未交付** ⇒ 改成 `001–101` 等于把在办项写成已交付）。
      照 097/099/100 立项时的同一处置：只前置带日期的 ⚠️ 块登记**随入列漂移的编号面计数**（**100 → 101** 个目录），**旧值逐字保留**；交付时（T033）再改那句
- [x] T008 ⚠️ **立项阶段不动任何「对外规模数字」**（i18n 键数/行数、Spec 模块数）：它们必须与**交付时的实测**同批改齐
      ⇒ 前移到 **T029（前端改键后）与 T032（交付收口）**。只改一处等于用一次订正造出两处新矛盾。**该前移在此登记**

---

## 阶段 B 配置门与 `SIMULATED` 状态（提交 C2 = `feat(101): 收信侧配置门与 SIMULATED 状态`）

- [x] T009 新增 `backend/src/main/java/com/crm/config/MailInboundStatus.java`：`@Component`、`@Value("${crm.mail.inbound.demo-enabled:false}")`、
      `isDemoEnabled()`、常量 `NOT_CONFIGURED_MESSAGE = "未接入收信源（IMAP），同步未执行"`；javadoc 照 `MailStatus` 写明
      「**本类是收信侧能否生成记录的唯一判据**、真接 IMAP 时改这里、别处不要重复判断配置」，并写清「键为 true 只表示**允许生成演示记录**，**不是**连通性探测」
- [x] T010 `backend/src/main/java/com/crm/common/ErrorCode.java`：加 `MAIL_INBOUND_NOT_CONFIGURED(409, "MAIL_INBOUND_NOT_CONFIGURED", "未接入收信源（IMAP），同步未执行")`，
      紧邻 mail 块（`MAIL_RECORD_NOT_FOUND` 之后）；**不合并**任何既有码、不动 `MFA_LOCKED` 的注释
- [x] T011 新增 `backend/src/main/java/com/crm/common/MailInboundNotConfiguredException.java`：`extends BusinessException`，
      构造 `super(ErrorCode.MAIL_INBOUND_NOT_CONFIGURED, MailInboundStatus.NOT_CONFIGURED_MESSAGE)`；javadoc 写明**为什么与发信侧的裸 `RuntimeException` 不同**
- [x] T012 `backend/src/main/java/com/crm/entity/MailSyncRecord.java`：加 `STATUS_SYNCED` / `STATUS_FAILED` / `STATUS_SIMULATED` 三个常量；
      把类内 `/** SYNCED / FAILED。 */` 订正为三值（形制照 `EmailSendLog.STATUS_SENT`/`STATUS_SKIPPED`）
- [x] T013 `backend/src/main/java/com/crm/service/MailSyncRecordService.java`：构造函数加 `MailInboundStatus`；`simulateSync` → **`triggerSync`**；
      方法体顺序固定为 `require` → 判门 → 分支；演示分支写 `STATUS_SIMULATED`、主题带演示标记、`externalId` 用 `demo-` 前缀；
      javadoc 重写（默认与演示两条路、真实 IMAP 落地时替换哪一段）；**列表/删除两个方法一字不动**
- [x] T014 `backend/src/main/java/com/crm/controller/MailAccountController.java`：`@Operation(summary = "触发收信同步（未接入收信源时拒绝）")`、
      javadoc 补齐两种结果、调用点改名；⚠️ **`@RequirePermission("mail_sync:manage")` 与类 javadoc 的权限拆分论述一字不动**（086 的两码不合并）
- [x] T015 配置三处（**同一提交**）：`backend/src/main/resources/application.yml` 的 `crm.mail` 块内加 `inbound.demo-enabled: ${CRM_MAIL_INBOUND_DEMO_ENABLED:false}`
      + 注释写明「**唯一真源是本行的 `false`；Java 侧的 `:false` 只是缺失兜底**」；`.env.example` 邮件段加 `CRM_MAIL_INBOUND_DEMO_ENABLED=`；
      `docker-compose.yml` 的 `CRM_MAIL_*` 那组加同名项。两处注释都要**解释不设置会怎样**（照 030 的体例）
- [x] T016 **编译自证**（本提交不跑全绿门禁——`MailSyncRecordServiceTest` / `EmailSyncIT` 此刻**必然红**，是预期的）：
      `cd backend && mvn -B -o -q compile` 通过即可提交；在提交信息里写明「测试改动在 C3」

---

## 阶段 C 用例（提交 C3 = `test(101): 收信侧默认拒绝与演示路径用例`）

- [x] T017 新增 `backend/src/test/java/com/crm/config/MailInboundStatusTest.java`（**T8**）：直接构造 `new MailInboundStatus(true/false)`，
      断言 `isDemoEnabled()` 两个分支 —— ⚠️ **这是本项唯一能钉住 Java 侧缺省值的判据**（IT 读 yml，改 `@Value` 的 `:false` 不会让 IT 变红）
- [x] T018 重写 `backend/src/test/java/com/crm/service/MailSyncRecordServiceTest.java`（**T1**）：默认路径 ⇒ 抛 `MailInboundNotConfiguredException`
      **且 `verify(recordMapper, never()).insert(any())`**；演示路径 ⇒ 返回 `SIMULATED` 记录且 `insert` 恰好一次。
      ⚠️ 构造函数加了参数，`setUp()` 要同步改
- [x] T019 `backend/src/test/java/com/crm/integration/EmailSyncIT.java`（**T2**）：同步段改为断言 **409** + `error.code == MAIL_INBOUND_NOT_CONFIGURED`，
      **并正面断言** `GET /records` 的 `total == 0`（正对照：专杀「靠事务会回滚」的推理）；旧的 `SYNCED` / 主题断言**删除**（`spec.md` 的 FR-009 已给出新形态）。
      ⚠️ 中文文案断言必须显式 `StandardCharsets.UTF_8`（本仓有 ISO-8859-1 假红先例）；**优先断 `error.code`**
- [x] T020 新增 `backend/src/test/java/com/crm/integration/MailInboundDemoIT.java`（**T3/T4**）：`@TestPropertySource(properties = "crm.mail.inbound.demo-enabled=true")`；
      断言 200 + `data.syncStatus == "SIMULATED"` + `total == 1`，且 `subject` / `externalId` 带演示标记
- [x] T021 `backend/src/test/java/com/crm/integration/PermissionEnforcementIT.java` 里 SALES 调同步端点的探针（**T5**）：`isOk()` → **`isConflict()`**，
      并把注释改成「断言的是**不是 403**——权限通过后落在『未接入收信源』上」。⚠️ **只改这一处**，该用例的其余断言（含 `mail_account:manage` 面的 403）一字不动
- [x] T022 **本项第一次跑全绿门禁**：`cd backend && mvn -B spotless:apply && mvn -B verify`（不传 `-DargLine`）；
      判据 = 失败集合 ⊆ 4 例已批准偏差 + T1–T5/T8 全绿；`ls backend/target/jacoco.exec`
- [x] T023 定向破坏 **D1–D7** 逐条做、逐条观测、逐条 `cp` 备份回写还原（判据 `git hash-object` 与破坏前相等）；
      ⚠️ **禁用 `git checkout`**；逐条记进 `falsification-evidence.md`（若某条「该红的没红」，就地补强判据后重跑，**如实登记**而不是记成绿）

---

## 阶段 D 前端诚实化（提交 C4 = `feat(101): 收信侧前端诚实化`）

- [x] T024 `frontend/src/types/mail.ts`：`syncStatus` 联合类型扩为 `'SYNCED' | 'FAILED' | 'SIMULATED'`
- [x] T025 `frontend/src/services/mailService.ts`：`simulateSync` → **`triggerSync`**（路径与方法不变）
- [x] T026 `frontend/src/pages/mail/MailSyncPage.tsx`：
      ① 状态列改**三向**（`SYNCED` 绿 `tagSynced` / `SIMULATED` **橙** `tagSimulated` / **其余含未知值** 红 `tagFailed`）；
      ② 按钮改调 `triggerSync`；
      ③ Alert **保留**，`demoDataNoticeTitle` / `demoDataNoticeDesc` / `msgSyncTriggered` **只改值不改键**（086 的挂载守卫依赖前者）
- [x] T027 i18n：`zh-CN.ts` 与 `en.ts` **删** `pages.mail.btnSimulateSync`、**增** `pages.mail.btnSyncInbox` 与 `pages.mail.tagSimulated`、
      **改值**三处。⇒ 键 **2962 → 2963**、行 **3452/3425 → 3453/3426**（差仍 27）
- [x] T028 `frontend/src/pages/mail/MailSyncPage.perm.test.tsx`：mock 名与按键键名同步改名（**四条既有用例的结构与断言对象不变**）；
      **新增**「`SIMULATED` 渲染成『模拟』且**不是**『已同步』」与「未知状态仍渲染成『失败』」两条断言（**T6/T7**）
- [x] T029 **同批**改 `PROJECT_FEATURES.md` 的三个 i18n 数字落点（`:19` / `:77` / `:111`）：键 **2963**、行 **3453/3426**（差仍 27）；
      照 099 的体例**追加带日期 ⚠️**、**旧值（2962 / 3452 / 3425）逐字保留可 grep**
- [x] T030 前端五道门禁 + 定向 vitest（`quickstart.md` §1.2）；判据：`i18n:check` 报 **2963/2963**、路由 **58** / manifest **56** **不变**；
      `zh:check` 的硬编码中文台账**不得增加**

---

## 阶段 E 交付登记与文档订正（提交 C5 = `docs(101): 交付登记与文档订正` = 交付）

- [x] T031 对比文档 7 处按「**订正不静默**」改写（原文逐字保留 + 带日期 ⚠️，粒度到每一列，判据 = **旧值仍可 grep**）：
      `:223`（模块表行）/ `:228`（小结）/ `:318` 与 `:347`（评分与深度，**分值一字不动**）/ `:371`（**P0 第 6 条：状态列刻意不翻**，写明「动作列的两条出路一条都没做、本项关掉的是『对外承诺不实』这一半」）/
      `:448`（**与 T032 同批**，它是引用 README 现文的证据行）/ `:477`（新发现表）
- [x] T032 `README.md` 的「邮件同步」功能行改写为如实的收信侧说明 + **同批**改 `CRM_FEATURE_COMPARISON.md:448` 的引文（旧引文逐字留在 ⚠️ 内）；
      `PROJECT_FEATURES.md:194` 的模块表行同批补如实说明
- [x] T033 交付收口：`specs/README.md` 的 101 行状态列改 ✅ 并补实测读数；`specs/roadmap.md` 的 101 行勾上、`最后更新` 前置交付段、
      「整体覆盖度」那句交付态断言改为 **001–101**、编号面计数 **100 → 101**（**旧值逐字保留**）；
      `PROJECT_FEATURES.md:20` / `:107` 的 Spec 模块数改 **100（001–101，缺 069）**
- [x] T034 写 `falsification-evidence.md` 的实测读数：D1–D7 逐条观测、门禁读数（surefire/failsafe/失败集合/jacoco 结论行与覆盖率/`jacoco.exec` 字节数）、
      隔离实例冒烟两次启动的读数与收尾核对、§订正不静默自查的命中数、§可核判据的实跑读数
- [x] T035 `tasks.md` 勾选（**只勾真正做完的**）+ 填**交付块**（不得预填、**不写任何提交哈希**）+ 在「实做订正」里如实记录与计划的偏差
- [x] T036 交付前复跑 `quickstart.md` §5/§6 两节的全部判据（**不跑 Maven**——门禁的读数已经取过，再跑会覆盖 `jacoco.exec`）

---

## 实做订正（**如实记录与本计划的偏差**，交付时填）

| # | 偏差 | 处置与理由 |
|---|---|---|
| 1 | **D3 拆成 D3a / D3b 两跑**（T023 的破坏表按单跑设计） | **计划预期的红没有出现**：D3a（只把 `insert` 挪到抛异常之前、`@Transactional` 保留）跑完，`EmailSyncIT` **2/0F 全绿** —— 方法上的事务把那条插入**回滚了**，端到端看不出任何区别；只有单测的 `never().insert` 抓住了它。**就地加跑 D3b**（在 D3a 之上再去掉 `@Transactional`）才拿到 `expected:<0> but was:<2>`。⇒ **这条「计划预测失败」本身就是最值钱的留痕**：若只写 IT 的 `total == 0` 而漏掉单测的副作用断言，**D3a 那种形会全绿通过**（已写进 `falsification-evidence.md` §C 与 §0） |
| 2 | **T8 的实现方式改了**：用裸 `AnnotationConfigApplicationContext` 注册该类来承载 `@Value`，**不是**计划写的「直接 `new MailInboundStatus(true/false)`」 | `@Value` **只在容器里被解析**，直接 `new` 只能验构造参数、**验不到「缺省值」那条路**（而缺省值正是 T8 要钉的东西）。改成容器 + **不加载 `application.yml`** ⇒ 容器里生效的就是 `@Value` 的 `:false` ⇒ **D6（把 `:false` 改 `:true`）能红在 T8 上**（已实测，见 §F）。若要更贴近原文，可读作「计划那句话的**意图**（直接钉 Java 侧缺省值）被满足、**字面手段**被更强的形态替换」 |
| 3 | **T029 / T033 的落点比计划少两处**：i18n 的 ⚠️ **只落在 `PROJECT_FEATURES.md:19`**（**未动 `:77`**），spec 计数的 ⚠️ **只落在 `:20`**（**未动 `:107`**）；T029 原文写 `:19`/`:77`/`:111`，T033 原文写 `:20`/`:107` | `:77` 与 `:111` 是 **099 那次的 ⚠️ 块**、`:107` 是 **100 那次的 ⚠️ 块** —— 它们是**那两批的留痕**。往里面塞 101 的数字等于**改写历史批次的留痕**（「订正不静默」的对象是**本批自己的订正**，不是**别的批次的留痕**）。⇒ 本批只改**现行值所在的行**，旧值仍逐字留在 `:19`/`:20` 的 ⚠️ 段里（可 grep，见 `falsification-evidence.md` §O） |
| 4 | **`PROJECT_FEATURES.md:14`（后端测试类）是计划未列的落点** | 本项自己新增 2 个测试类（`MailInboundStatusTest` + `MailInboundDemoIT`）⇒ **188/194 → 190/196**，交付时一并改齐，并追加本次「第五次重测」块（共动 **3 行**：后端测试类 / i18n / Spec 模块）。**照 097 那条「§一 改的是 5 行不是 4 行」的先例**：漏掉它等于**用一次订正当场造出一处新矛盾**（`plan.md` 的落点表没列它，是计划的疏漏，不是执行的偏差） |
| 5 | **`quickstart.md` §5 ① 的「0 命中」判据按字面跑不通** | 字面跑 **4 命中**，逐条都是**注释**（3 处 javadoc + 1 处 TS 注释），且**正是留痕规则要求留下的**（`simulateSync` 2 + `模拟同步邮件` 2）。**产品代码里旧标识符一个不剩**（`grep -vE ':[0-9]+:\s*(\*|//)'` 后 **0**）。⇒ **口径补「排除行首注释」**，**不改注释去凑 0** —— 后者是**用一次静默改写去满足一条自查判据**，恰是本项要防的东西（本仓的已知形状：**零命中判据与订正不静默互斥**）。读数与逐条明细见 `falsification-evidence.md` §N |
| 6 | **破坏的备份 / 日志形制与计划不同** | 计划（照 100 批的形制）写 `/tmp/destruct/101/D<n>-<FileName>.bak` + `D<n>.log`；**实际**用的是 `/tmp/destruct/101/<SimpleName>.java.bak`（**5 个**，12:47 一次备齐、多跑复用），**未保留任何 `D*.log`**。⇒ 各节的「备份 / 日志」行按**实际路径**写；读数来源是**随跑随记的原始输出**、交付时逐字恢复（`falsification-evidence.md` §R 已按此如实登记） |
| 7 | **D6 那一次跑的构建结论是 `BUILD FAILURE`，但不是用例的红** | 成因是 `spotless:check` 拒绝了那次改写造成的 **CRLF → LF** 行尾变化（改写起初用 `python` 原地重写）。**判据取测试层读数**（只有 T8 红、其余三类全绿），构建退出码在该条不作判据。⇒ **手段的红不算目的的红**；**教训**：本仓 Java 源是 **CRLF**，原地改**必须用 Edit 工具**，不得用脚本重写（该条已写进 `falsification-evidence.md` §F） |

---

## 交付块（**交付时填**，不得预填）

| 项 | 值 |
|---|---|
| 提交数 / 末条提交主题 | **5 次**（`docs(101): 立项` → `feat(101): 收信侧配置门与 SIMULATED 状态` → `test(101): 收信侧默认拒绝与演示路径用例` → `feat(101): 收信侧前端诚实化` → 末条 **`docs(101): 交付登记与文档订正`**）；**开工与交付同日 2026-09-17** |
| `mvn -B verify`（surefire / failsafe） | surefire **738**（F/E/S **全 0**）/ failsafe **334**（F/E/S **全 0**）⇒ **失败集合 = ∅ ⊆ 4 例已批准偏差**（比判据更严：那 4 例本次**一例也没红**）；`spotless:check` **798 文件 clean**，退出码 **0 / BUILD SUCCESS** |
| `jacoco:check` 结论行 | **`All coverage checks have been met.`**（上一行 `Analyzed bundle 'crm-backend' with 250 classes` ⇒ 门禁确实被判定过）；`-DargLine` **未传**（日志第 24 行可见 `argLine set to …jacoco.agent-0.8.11-runtime.jar…`） |
| 覆盖率（INSTRUCTION / BUNDLE） | **INSTRUCTION 48093/59101 = 0.8137**（阈值 **0.73**，粒度 BUNDLE，**未改**）/ **BRANCH 3087/4906 = 0.6292** / **LINE 11206/13440 = 0.8338** / **METHOD 1792/2091 = 0.8570**。⚠️ 新类 `MailInboundStatus` **在分母内**（`com/crm/config/**` 未被排除）：**0 missed / 9 covered**、0 分支。⚠️ 两次完整 verify 的**分母不同**（原因见 `falsification-evidence.md` §L：两次都跳过 javac，而跑之间 `target/classes` 被 IDE 原地增量编译改写过），**比值漂移 ≤0.0041**、两次都过阈值 |
| `ls backend/target/jacoco.exec` | **存在**：**88,346,260** 字节，mtime **2026-09-17 12:54:20**（**门禁跑完后未再跑任何 Maven**） |
| 新增/改动的测试文件与用例数 | **新增 2 个文件 / 4 条**：`config/MailInboundStatusTest`（T8）**3** · `integration/MailInboundDemoIT`（T3/T4）**1**；**改动 3 个文件**：`service/MailSyncRecordServiceTest`（T1–T3 重写）**3** · `integration/EmailSyncIT`（T2 默认段改 409 + `total=0`）**2** · `integration/PermissionEnforcementIT`（T5 探针 200→409，**只改这一处**）**14**。前端：`MailSyncPage.perm.test.tsx` **6**（改 mock 名 + **新增 2 条** T6/T7）。**全部全绿** |
| 定向破坏 D1–D7 | **7 条全部观测到计划点名的那条转红**，**逐条 `cp` 回写还原**（判据 `git hash-object` 与破坏前相等、**禁用 `git checkout`**），还原后 `git diff --stat -- backend/` 为空。**D3 拆成 D3a/D3b 两跑**（见「实做订正」#1）；**D7 与 D3a 组合跑 → 全绿** ⇒ 证明 `never().insert` 是那一层**唯一**看着副作用的护栏。逐条读数与哈希见 `falsification-evidence.md` §A–§G |
| 隔离实例冒烟 | 端口 **8099** / schema **`crm_ib101`** / Redis **db 5**。**默认档**（实例 PID 13200）：`POST /mail-accounts/1/sync` **连点两次都是 409 + `error.code = MAIL_INBOUND_NOT_CONFIGURED`**（message「未接入收信源（IMAP），同步未执行」）、`GET /records` **两次都是 `items:[]` / `total:0`**（连点不累积）。**演示档**（加 `--crm.mail.inbound.demo-enabled=true` 重启，PID 2764）：起点仍 `total=0` ⇒ 调一次得 **200 + `syncStatus:"SIMULATED"`**、`subject`「演示同步邮件（非真实收信）」、`externalId` `demo-…` ⇒ `total` **0 → 1**。**只读前端冒烟**（已在跑的 5173，**未点按钮、未写共享库**）：新键在位、`btnSimulateSync` 零命中、`SIMULATED → orange` 分支在。**收尾核对**：`DROP DATABASE` / `REVOKE` / `FLUSHDB` **OK**、库列表中 `crm_ib101` **计数 0**、共享库 `crm_db` **87 张表原样**、**8081（PID 5032）与 5173（PID 14116）PID 与冒烟前一致 ⇒ 全程未重启** |
| 可核判据（`quickstart.md` §5） | ②–⑥ **五条直接通过**（三常量与联合类型齐备 / 两处默认值都 `false` / `062` diff **空输出** / 两个权限码 4+3 处仍在且未合并 / 迁移 diff **空输出**）；① **按字面 4 命中、排除行首注释后 0**（4 处全是留痕注释，见「实做订正」#5） |
| 订正不静默自查（`quickstart.md` §6） | **六组全部 ≥1 命中，无一条 0**：① **1 / 1**；② **1 / 1**；③ **1**；④ **1**；⑤ **1 / 1**；⑥ **6 / 6 / 3**（`2962` / `3452` / `99（001–100`）。**新值到位**：`2963` **5** 命中 · `3453` **5** 命中 · `100（001–101` **2** 命中 · `i18n:check` **2963/2963**。⚠️ ③ 的字面量只覆盖 `:318`（`仍为模拟`），而 `:347` 的原文措辞是 `仍是模拟` ⇒ **登记为口径边界**：六条 grep 各自只覆盖它字面量所指的那一处，「旧值全部保留」由**每处各自的 ⚠️ 块**证明 |
| ⚠️ 本项**不把提交自己的哈希**写进任何被它携带的文件 | 遵守（只引用**提交主题/序号**） |
