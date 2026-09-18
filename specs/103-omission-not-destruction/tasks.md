# 任务清单（103）

> **勾选框纪律**：立项时**全部留空、绝不预勾**；只在**真正做完**时勾。
> **破坏表与用例表在开工前定稿**（`plan.md` §验证、`falsification-evidence.md`），留空的**只有读数**。

---

## C1 `docs(103): 立项`

- [x] T001 八件工件手写落库：`spec.md` / `plan.md` / `research.md` / `contracts/omission-restore.md` / `quickstart.md` / `falsification-evidence.md` / `tasks.md` / `checklists/requirements.md`（**`data-model.md` 刻意不产**，理由见 `plan.md` D7）
- [x] T002 `research.md` 交付**两级判据**盘点（1a/1b/1c + 可达性三标签），每条带 file:line 与**验证状态**（★/☆）
- [x] T003 `research.md` §0.1 记下唯一索引陷阱（**两处 file:line**）、§1 记下 T0 的**设计与实测结果**、§5 记下环境偏离
- [x] T004 `contracts/omission-restore.md`：**恰好 4 列**表；§2 逐字收存 056 原文（带 file:line）；§1 **三个方向并列**（085 违反 / 101 偏离 / 103 实现侧缺口）并**逐字纠正 102 的过宽理由**
- [x] T005 `spec.md` §1.3 记下**那条被证伪的核实**（错在哪一步、机制、实测、处置），**不得删改**
- [x] T006 `specs/README.md`：模块表加 103 行（**5 列同形**）、`:3` 版本行、编号说明段（写明 103 无需例外条款、**迁移表不加行**）
- [x] T007 `specs/roadmap.md`：`## 当前进度` 加 103 行（**不预勾**）+ 计数移动 + 债务 blockquote（8 条）+ `:4` 最后更新（**上一条逐字保留**）
- [x] T008 立项提交（**不含任何代码**）

## C2 `feat(103): 自定义字段受保护值回补`（唯一的行为变更）

- [x] T009 `CustomFieldService` 加私有 `protectedFieldIds`（紧跟 `hiddenFieldIds:73-78`），判据 `!EDITABLE`，javadoc 写清三件事：同口径于 `FieldMaskPlanner`、**为何不枚举两值**、**为何是私有**（含「将来第二消费者再提升」）
- [x] T010 回补块 `:252-265` 重写：谓词换 `protectedIds` + **新增 `submittedIds` 排除**（过滤条件**逐字对齐主循环 `:241`**）
- [x] T011 改写 `:252-253` 注释，含四件事（类名 / 为何含 READ_ONLY / **为何绝不含 EDITABLE** / 重复插入陷阱与索引名）；「看不见不等于该被删除」**留下并扩展**为「看不见 / 改不动，都不等于该被删除」
- [x] T012 编译自证 `mvn -B -o -q compile`（**允许暂无对应用例——显式红窗**，门禁在 C3 之后跑第一次）
- [x] T013 C2 提交

## C3 `test(103): 自定义字段受保护值用例`

- [x] T014 单元 U1–U6（复用 `asRole` / `stored` / 插桩捕获形态）
- [x] T015 集成 I1–I5 **扩展 `FieldPermissionIT`**（**不新开 IT 类**；加本地 `configureCustom`；**不得** `@Transactional` / `@TestMethodOrder`；只以 **ADMIN 读回**判定销毁）
- [x] T016 LEAD 钉住式用例 `omittedOwnerIdSurvivesLeadUpdate`（T0 时已写入 `LeadIT`，此处**复核**其断言形态与正对照）
- [x] T017 C3 提交

## C4 `feat(103): 前端表单与筛选列遵循字段权限标记`

- [x] T018 `types/customField.ts` 加**可选** `permission` + 一行注释指向 `types/fieldPermission.ts` 说明**不可统一**
- [x] T019 `CustomFieldItems.tsx`：HIDDEN **不渲染**、READ_ONLY 加 `disabled`
- [x] T020 `useCustomFieldFilters.ts:13` 过滤 HIDDEN
- [x] T021 前端用例：`CustomFieldItems.test.tsx` + `useCustomFieldFilters`（含 `permission` 为 `undefined` **仍产列**的可选链分支）
- [x] T022 前端五道门禁（含 `ui:check` 的冻结计数**实测**——新增同目录测试文件是否移动计数**不得假设**）
- [x] T023 C4 提交

## C5 `docs(103): 交付边界说明`

- [x] T024 根目录 `DELIVERY_SCOPE.md`：六节；**全文零快照数字**；每条「不在范围内」带**判据列**；失效触发项写清；自陈「无门禁看着」
- [x] T025 ⚠️ 限流相关措辞**逐字沿用 100 的口径降级**（「不是抗敌手措施」），**不得升级**
- [x] T026 `README.md` 目录树加行 + 文档索引加指针
- [x] T027 自查：`grep -nE "[0-9]+ *个|[0-9]{3,}" DELIVERY_SCOPE.md` ⇒ **预期 0 命中**
- [x] T028 C5 提交

## C6 验证与留痕

- [x] T029 定向破坏 D1–D11 逐条执行，**每条先写「它该改变哪条可观察行为」**；`cp` 备份回写还原，判据 `git diff --quiet`
- [x] T030 **D2 与 D9 的读数按 `falsification-evidence.md` 的特别说明如实记录**（不变红就是不变红，**不得回填**）
- [x] T031 门禁：`spotless:apply` → `verify` → 核对 `jacoco.exec` 存在 **且 `jacoco:check` 打印了结论行**
- [x] T032 前端五道
- [x] T033 收尾检查：`specs/056-field-permission/**` 的 `git diff` **为零**；`specs/102-*/**`、`specs/101-*/**`、`db/migration/**`、`schema-h2.sql` 同样为零；`留痕后还原` **0 命中**

## C7 `docs(103): 交付登记与文档订正`

- [x] T034 `specs/README.md` 103 行状态改 ✅ + 【交付后记】
- [x] T035 `specs/roadmap.md` 交付读数 + 勾选 + 最后更新段
- [x] T036 `PROJECT_FEATURES.md` 重测块（只写移动的行；**i18n 不动**；**Flyway 迁移不变要明写**）
- [x] T037 读 `CRM_FEATURE_COMPARISON.md` §2.9 与 P0/P1 列表**再决定**改不改（**不得静默跳过**）；若改，判定列与分值**一律不动**
- [x] T038 登记**环境偏离**（`-Djava.version=21`）与那处**不属于本批**的 `pom.xml` 未提交改动
- [x] T039 `falsification-evidence.md` 填实测读数；`tasks.md` 勾选 + 填交付块（**不写任何提交哈希**）+ 在「实做订正」里如实记录偏差
- [x] T040 交付前复跑 `quickstart.md` §6 的复算命令（**不跑 Maven**——门禁读数已取过）

---

## 交付块（**交付时填，不得预填**）

| 项 | 值 |
|---|---|
| 提交 | C1 `docs(103): 立项` / C2 `feat(103): 自定义字段受保护值回补` / C3 `test(103): 自定义字段受保护值用例` / C4 `feat(103): 前端表单与筛选列遵循字段权限标记` / C5 `docs(103): 交付边界说明` / C6 `docs(103): 交付登记与文档订正`（**如实按实做增删**；**不写哈希**）<br>**⚠️ 交付时按实做订正（上句逐字保留）**：① 实做共 **6 次提交**，顺序 = 立项 / 回补 / 用例 / 前端 / **交付边界说明** / **交付登记**；② **`plan.md` 提交拆分表里的 C5 `fix(103): 线索编辑保留负责人` 不存在**（T0 实测 ⇒ **分支 A** ⇒ `LeadService` 零生产改动）；③ 同一份 `docs(103): 交付边界说明` 在 `plan.md` 里叫 **C6**、在 `tasks.md` 的相位分组里叫 **C5**——**两者指同一个提交**，登记见 §实做订正 第 5 行 |
| 门禁 | `cd backend && mvn -B -Djava.version=21 spotless:apply && mvn -B -Djava.version=21 verify` ⇒ 退出码 **0 / BUILD SUCCESS / 01:57 min**（日志 `/tmp/gate103.log`；`-Djava.version=21` 是**环境偏离**，见 §实做订正 第 6 行）；spotless **0 needs changes**（⚠️ 门禁那次是**增量缓存命中**——逐字「812 were skipped because caching determined they were already clean」⇒ **它不足以证明真解析过**；交付时移走 `target/spotless-index` 复跑 `spotless:check` ⇒ **812 files clean / 0 needs changes / 812 already clean / 0 skipped**，且复核前后 `jacoco.exec` 字节与 mtime **逐字未变**）；surefire **779 例** / failsafe **353 例**（Failures · Errors · Skipped 全 0）；`jacoco:check` 打印结论行「**All coverage checks have been met.**」 |
| `jacoco.exec` | **128 413 263 字节 / mtime 2026-09-18T08:12:21**（报告 **257 个类**）；覆盖率 INSTRUCTION **82.18%**（49 993/60 835）、BRANCH **64.51%**（3 307/5 126）、LINE **83.68%**（11 415/13 641）、METHOD **86.39%**（1 853/2 145）——与 102 的 82.17 / 64.37 / 83.78 / 86.32 **逐项不同**，差额来自本批改动的 `CustomFieldService`（**既有类**；本批**零新增类**、**零迁移**）。⚠️ 读数**只取那一次完整 `verify`**；之后**未再跑任何会写 `jacoco.exec` 的 Maven 目标**（唯一那次 `spotless:check` 复核前后字节与 mtime 逐字不变，故未污染本行） |
| 前端五道 | `i18n:check` **2966/2966 键**（路由 58 条 / 清单 56 项、粗粒度别名 3 条）、`lint` 与 `typecheck` 通过、`ui:check` **271** 个产品文件（**125** tsx）/ **303** 个 `Form.Item`（白名单内冻结 **54** 处、**未新增**）、`zh:check` **268** 个产品文件 / 候选点 **9162** / **未登记命中 0**（台账内冻结 **266** 处 + 4 条；口径外只印不判 **55**）。**另跑定向 vitest**：`CustomFieldItems.test.tsx` 4 + `useCustomFieldFilters.test.tsx` 3 = **2 文件 / 7 passed**——⚠️ 那五道**不含任何全量 vitest**，前端的用例证据就是这条定向读数 |
| 定向破坏 | D1–D11 见 `falsification-evidence.md`（**含不变红的行**）：**D1–D9、D11 观测到转红；D10 无判据**（分支 A 下**不存在守卫** ⇒ 如实记为「没有可破坏的对象」，**不是「跑绿了」**）；**四处与预测不符并逐字记录**——D2（预登记 500、实测 **409 `DUPLICATE_KEY`**）、D5（单测看不见顺序 ⇒ 只红一半，IT 的红是**撞唯一键 409**）、D6（`fieldPermissionFlow` 那半**没有判据**）、D1/D3（覆盖面在单测层重叠）。还原判据「与 HEAD 逐字相等」（**禁用 `git checkout`**，用 `cp` 回写）；探针残留标记 `留痕后还原` **零命中** |
| 分支 | **A**（LEAD `ownerId` 实测被保留 ⇒ C5 那个 `fix` 提交**不存在**）——**该格为立项期原文，逐字保留**；交付补记：**D9 实测转红** ⇒ 那条钉住式用例 `omittedOwnerIdSurvivesLeadUpdate` **有牙齿**（SC-006 成立），且 **D11 换一个破坏面同样只被它抓住** |

---

## 实做订正（**如实记录与本计划的偏差**，交付时填）

⚠️ 本表**可以在交付前就被追加**（第 1 / 2 行即如此）：只要是**已落地的偏差**就立刻写进来，
免得某份文档先引用了它、而它还没被登记（`DELIVERY_SCOPE.md` §6.2 结尾正是这样引用本表的）。
其余行仍照常在交付时补齐。**交付时定稿 = 6 行**：第 1 / 2 行**立项期**就落地（`DELIVERY_SCOPE.md` 的口径与排版），第 3 行随 **500 → 409** 的订正落地，第 4 / 5 / 6 行在**交付**时补齐；**占位行 `…` 已按本句删除**（交付即定稿，留着会被读成「还有没填的东西」）。

| # | 偏差 | 处置与理由 |
|---|---|---|
| 1 | `DELIVERY_SCOPE.md` §6.2 的判据**不采用**计划里那版「全文 `[0-9]{3,}` 零命中」 | 改用「`[0-9]+` 后紧跟任一数量量词（个 / 条 / 处 / 项 / 张 / 次 / 行 / 键 / 页 / 种 / 类 / 万）⇒ 输出为 0」（命令逐字见该文件 §6.2，本单元格不复制正则，免得单元格里出现竖线）。理由：三位数口径会**同时命中批次目录名（`specs/101-…`）与复核日期**，而这两者恰恰是该文件要求写的东西 ⇒ 口径比规则本身还严只会制造**假红**，不制造安全。规则本身（**不含数量断言**）未放宽，替代的"不多不少"判据是 §6.2 里对残存数字的三类穷举（批次标识 / 端点与错误码 / 复核日期），实测 `[0-9]{3,}` 的命中**全部落在这三类里**。 |
| 2 | `DELIVERY_SCOPE.md` 的**表行一律用名称引用、不编号**（不写"§3 第 2 行"这种写法） | 交叉引用里的**「N 行」**会被第 1 行那条判据的 `行` 误判成数量断言。**改的是排版，不是判据**——同一处我第一版正是用行号引用的，实测被判据命中 5 次（自证失败），故删掉表头的 `#` 列并在文件开头加 📌 说明。副作用是读者不必回头数行，比原样更清楚。 |
| 3 | 各处**原写「只放宽谓词 ⇒ 每次 PUT 500」**，而实测是 **409**（`research.md` §0.1 标题与末句、`spec.md` FR-003 与 SC-002、`quickstart.md` §2、`plan.md` 的 D2 回补块行 / D2 表行 / I2 表行、`falsification-evidence.md` 的 D2 行、`CustomFieldService` 的生产注释——**六份文件、共十处**）。订正另落 `falsification-evidence.md` 的 D2 特别说明与 D5 行 | **机制对、状态码错**：D2 实测抛的确实是 `DuplicateKeyException`（撞 `uk_field_entity_value`），但 063 的 `GlobalExceptionHandler:130-138` 把它渲染成 **409 `DUPLICATE_KEY`**。处置：**十处旧值「500」全部逐字保留**（不是静默改写），各处加**带日期 ⚠️** 块。**结论一律不变**（排除仍必需、原样回传的 PUT 仍一律失败、I2 的成功判据不变）。理由：「一个数字住在好几个地方」——只改一处等于用一次订正造出两处新矛盾。本行只登记**不一致的读数**，不含沉默改写。 |
| 4 | `CRM_FEATURE_COMPARISON.md` **改了**（`plan.md` 的文档落点表预判「**很可能不改**——103 不在其 P0/P1 列表上闭合任何缺口、也不翻转任何判定」） | 实做按 T037 的要求**先读了 §2.9 与 P0/P1 列表再决定**（未静默跳过），结论是**必须改**：§2.9 的 FLS 行与 P0 第 1 条各有一句「写侧…**省略受保护字段 ⇒ 回补库中原值**」的护栏，而该句里的「**受保护**」**在 102 时点只在内置字段那一侧覆盖到 READ_ONLY**（`BuiltinWriteGuard:61` → `FieldMaskPlanner.protectedKeys:58-67`，判据 `!EDITABLE`）；**自定义字段**侧当时只回补 `HIDDEN` ⇒ 那句话作为**全仓**陈述在 102 时点为**部分不实**。处置：**两处原句逐字保留**、各加**带日期 ⚠️** 块（另加头部**第六轮局部刷新**段，与 08-16/09-17 五轮同体例）。**判定列 / 状态列 / 第三节分值与十域均值（3.35 / 显示值 3.4）一分未动**——103 不构成能力增量（它闭合的是写侧回补的**射程缺口**，不改变该行的实体范围结论，也不改变 P0 第 1 条的差距描述）。 |
| 5 | **提交编号在 `plan.md` 与 `tasks.md` 之间错位一格**：同一份 `docs(103): 交付边界说明`，`plan.md` 的提交拆分表里叫 **C6**，`tasks.md` 的相位分组里叫 **C5** | 根因：`plan.md` 的 C5 是**只存在于分支 B 的** `fix(103): 线索编辑保留负责人`，而本批实测走**分支 A**（`research.md` §1）⇒ **那个提交不存在**，plan 表里的编号 C6/C7 因此对着 tasks.md 的 C5/C7。**实做顺序 = tasks.md 的相位顺序**（边界说明在验证相位**之前**落盘）。**两者指的是同一个提交，登记以免读者按 `plan.md` 去找一个不存在的 C5 `fix` 时把它读成漏做**（本行即该口径的唯一落点）。 |
| 6 | **门禁一律以命令行覆盖 JDK 版本**（`mvn -B -Djava.version=21 …`），并**放任**工区里一处**不属于本批**的未提交改动（`backend/pom.xml` 的 `<java.version>` 由 **21 改成 25**，属并行的 `appmod/java-upgrade-*` 线） | 本批属**环境偏离**（详见 `research.md` §5）：不覆盖则该属性为 **25** ⇒ 门禁**在任一 JVM 下都跑不完**（JDK 25 下 spotless 崩在 `NoSuchMethodError: Log$DeferredDiagnosticHandler.getDiagnostics()`；JDK 21 下 `release 25` 编译不过）。处置：**磁盘零改动**、不碰那处改动（**不得修改或丢弃另一个会话的未提交工作**）、**不传 `-DargLine`**（会静默废掉 JaCoCo）。**副作用**：门禁读数都带这个前提，本行即其登记；**交付块与各处读数不得脱离本行单独引用**。 |
