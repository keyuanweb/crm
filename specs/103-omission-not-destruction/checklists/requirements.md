# 规格质量自查（103）

**检查对象**：[spec.md](../spec.md) / [plan.md](../plan.md) / [research.md](../research.md) / [contracts/omission-restore.md](../contracts/omission-restore.md) / [quickstart.md](../quickstart.md) / [falsification-evidence.md](../falsification-evidence.md)
**日期**：2026-09-18（立项批 C1）
**图例**：`[x]` = **评审者已满意**（不是「已执行」）。

## 内容质量

- [x] **无实现细节泄漏进 `spec.md` 的需求项**：FR-001–FR-034 只写「哪条判据必须怎么写、哪个不变式不得破坏、哪个消费点必须接上、哪些数字必须一起改」；类名 / 方法签名 / 集合运算的形状在 `plan.md` 的「结构决策」，逐条落点在 `tasks.md`。
      ⚠️ `spec.md` §1.3/§1.4 与 `research.md` **引用**实现坐标，那是**由来与实测复核**，**不是需求项**——分工与仓内 087–102 一致。
- [x] **需求可测**：每条 FR 指向一条用例（U1–U6 / I1–I5 / 前端两条）、一次定向破坏（D1–D11）或一条可核判据（`quickstart.md` §6）；对应关系写在 `plan.md` §验证与 `spec.md` §3 的 SC-001–SC-007。
- [x] **成功判据双向且可核**：「不销毁」用**原值**断言而不是「请求成功」；SC-003 与 SC-004 是**反方向**的判据——一个防「多回补毁掉清空能力」，一个防「读路径被顺手接过去」。
- [x] **⚠️ 本批最重要的一条：那条钉住式用例必须可被证伪**（SC-006 + D9）。分支 A 下 `LeadService` **零生产改动**，那条用例之所以绿靠的是 ORM 默认策略——**若 D9 不变红，它就是一条没有判据看着的绿**，须如实记录。
- [x] **如实声明边界**：`spec.md` §5 六条 + `research.md` §6 债务八条 + `falsification-evidence.md` §边界六条，口径分工写明（前者 = 产品/语义边界，中者 = 设计缺口，后者 = 护栏缺口与不可证项）。其中必须被读到的三条：
      ① **必填 READ_ONLY 的省略不可达回补**（必填校验早于回补）；② **回传格式脆弱性**（今天安全只因初始值来自库中原始字符串，且**无判据看着它**）；③ **盘点中多数成员未经实测**（一律标 ☆）。
- [x] **非目标明确**：`spec.md` §4 共 10 条，**每条附理由**；其中「不修其余约 18 处」「不修 L-1」「不修必填校验盲区」「不产 `data-model.md`」都写明了**为什么不做**。
- [x] **无遗留占位符**：除 `tasks.md` 的交付块与「实做订正」表、`falsification-evidence.md` 的实测读数、`quickstart.md` §7 的读数表这类**刻意留到交付时填**的位置外，无 `TODO`/`待定` 式占位（**不预填未实测的数字**）。
      ⚠️ **破坏表不是占位符**：D1–D11 的「该改变哪条行为」与「该红哪条用例」在**开工前定稿**，留空的只有读数。

## 一致性

- [x] **与章程五原则逐条对齐**（`plan.md` §Constitution Check），无豁免、无 `Complexity Tracking`。
- [x] **与仓内既有判例不混淆**：`contracts/omission-restore.md` §1 把 **085（违反）/ 101（偏离）/ 103（实现侧缺口）** 三个方向**并列成表**；并**逐字纠正** 102 把该缺陷称作「056 契约覆盖的行为」的过宽理由（**其结论成立、理由不成立**，103 **不得复述**）。
- [x] **单一判据源**：受保护谓词写成 `!EDITABLE`，与 `FieldMaskPlanner.protectedKeys` 同口径；**本批只有一个消费者**，故以**私有孪生**落在 `CustomFieldService`，**不**提升为公开 API。
- [x] **订正不静默**：`spec.md` §1.3 逐字记下**被证伪的那条主张**、它**错在哪一步**（只核代码路径、未核持久化行为）、机制、实测结果与处置；**该节标注为不得删改**。`research.md` §1.4 记下元教训。
- [x] **一个数字住在好几个地方**：落点表列全（`specs/README.md` / `roadmap.md` / `README.md` / `PROJECT_FEATURES.md` / `CRM_FEATURE_COMPARISON.md`），并**明写 `INSTALL.md` 不动**（零迁移）——否则其缺席会被读成漏做。
- [x] **`data-model.md` 的缺席有理由**（`plan.md` D7）：本批唯一的数据形状事实是**既有的** `uk_field_entity_value`，其权威住处是 `V39:13`，**抄一份就造出第二个家**。

## 门禁与纪律

- [x] **零迁移、零 schema 变更、零新增类**（不新增 jacoco 分母条目）。
- [x] **不碰 `.specify/feature.json`**；**不跑任何 `/speckit-*`**；**不编辑任何已应用的迁移**。
- [x] **环境偏离已登记**（`research.md` §5 / `quickstart.md` §4）：工区里那处**不属于本批**的 `pom.xml` 未提交改动让门禁在任一 JVM 下都跑不完；处置是**不碰它** + 命令行 `-Djava.version=21` 覆盖，且该 flag **不碰 `-DargLine`**（JaCoCo 不受影响）。
- [x] **提交纪律**：`ListAgents` 先查、**逐路径 `git add`**、禁用 `git add -A` / `git commit -a`、尾行 `Co-Authored-By: Claude Code <noreply@anthropic.com>`、**不把提交自己的哈希写进它携带的文件**、**复选框不预勾**、`## 实做订正` **三列**。
- [x] **破坏-还原循环禁用 `git checkout`** ⇒ `cp` 备份回写 + `git diff --quiet` 判逐字相等；**就地改一律用 Edit 工具**（CRLF）。

## 交付前须复核（**未核实，不得当成既成事实**）

- [ ] `research.md` §7 列出的 ☆ 成员逐行核实后再写盘点结论（**未核实的保持标注为未核实**）
- [ ] `ui:check` 的冻结计数在新增同目录 `*.test.tsx` 时**是否移动**——**跑一次看**
- [ ] `CRM_FEATURE_COMPARISON.md` §2.9 与 P0/P1 列表**读过再决定**改不改（**不得静默跳过**）
