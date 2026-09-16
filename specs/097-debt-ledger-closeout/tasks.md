# 任务：五笔登记遗留账的收口（097）

**Created**: 2026-09-16
**图例**：`[ ]` 未完成 / `[x]` 已完成。**立项阶段一律不预勾。**
**⚠️ 本项的实际编写顺序是「先实现、后补用例」**（沿用 087/088/092/095/096 的既有做法）——
**不得**据此声称走过 spec-first；§D 的定向破坏留痕证明的是**护栏有牙齿**，不是「红先出现」。
**⚠️ 本项的活动半径**：**只改一行生产侧代码（`check-ui.mjs` 的注释）**，其余全是测试、文档与一次删除。
**不加迁移、不改端点、不改授权语义、不动任何 `role_permission` 行。**

---

## 阶段 A 工件与登记（提交 1）

- [ ] T001 写 `spec.md` / `plan.md` / `research.md` / `quickstart.md` / `falsification-evidence.md` / `tasks.md` / `checklists/requirements.md`
- [ ] T002 `specs/README.md` 模块表加 097 行（**状态列如实写「⏳ 进行中」**）
- [ ] T003 `specs/roadmap.md` 的 `## 当前进度` 加 097 行，**勾选框留空、不预勾**（照 095/096 先例）

## 阶段 B 未接线码台账（提交 2，①）

- [ ] T004 新建 `backend/src/test/java/com/crm/security/UnwiredPermissionCodeTest.java`：
      `PermissionDictionaryTestSupport.codes() − RequirePermissionScanTestSupport.usages().keySet()`
      == 冻结白名单（22 条），`containsExactlyElementsOf` **双向断言**
- [ ] T005 白名单每条带 **A 类 / B 类** 标注与理由（A 类写明「由哪个码放行」）；
      **先做自证扫描没坏的断言**（照 `RequirePermissionCatalogTest:34` 的 `containsKey("customer:merge")` 先例）
- [ ] T006 失败信息**点名**「多的是哪个码 / 少的是哪个码」（不许只报集合不等）
- [ ] T007 `RoleConstants` 的 `follow_up` 组注释、`QuoteController` 类 javadoc、`TicketController` 类 javadoc
      各**追加一行**指向 `UnwiredPermissionCodeTest`（**原文一字不动** —— 它们是 ① 的历史证据）
- [ ] T008 类注释写明**射程**：本测试断言「**冻结的 22 条**」，**不是**「必须为空」，
      故**不推翻** `RequirePermissionCatalogTest` 类注释里那条「反向刻意不断言」的裁决

## 阶段 C R6 注释订正（提交 3，②）

- [ ] T009 `frontend/scripts/check-ui.mjs` 的 `R6_ALLOWED` 块头注释：加**带日期**的 ⚠️ 订正块
      （「11/9 → **10/8**」+ 根因「`Currency` 已修、聚合数未回改」），**原文逐字保留**
- [ ] T010 同处给出**一行复算命令**（`awk` 取 `R6_ALLOWED` 的条目数与 count 合计），让读者可自证
- [ ] T011 同处补一句：**条目数（8）** 与 **count 合计（10）** 是两个不同的数，别混
- [ ] T012 `R6_ALLOWED` 的 **8 个条目本身不增不减**（SC-097-005 只要求注释与代码对得上）

## 阶段 D 081 回填（提交 4，④）

- [ ] T013 新建 `specs/081-role-permissions-update/spec.md`：首段**明写**「**事后回填**（2026-09-16），
      非立项期产物；081 的实际编写顺序无法追溯，**不得**据此声称走过 spec-first」
- [ ] T014 用户故事与验收写成**兑现事实**：13 个预置角色（`V46` 三个 + `V75` 十个）、`role`/`role_menu`/`role_permission`
      三表、`permission_code` 码表模型、菜单双射（`MENU_TREE` + `role_menu`）、内置角色不可删、数据范围为**手动调用**
- [ ] T015 **偏差表**逐条给证据（`tasks.md` T001–T017 与 `design.md` §4/§5 对照实际实现）：
      `action` 列、`path` 列、`RolePermissionInitializerService`、11×40+ 矩阵、E2E 五项
      —— 每条写「计划写了什么 / 实际是什么 / 证据在哪」；**未查见当时裁决的，照实写「未查见」**
- [ ] T016 明写「**11 个预置角色**」与「**库存 13 个**」的口径差（前者是 `design.md` §1.1 的口径且含 ADMIN）
- [ ] T017 `specs/README.md:111` 与 `:137` **原文保留** + 带日期 ⚠️ 指向回填件
- [ ] T018 `specs/081-role-permissions-update/` 的 `design.md` / `tasks.md` **原文一字不动**

## 阶段 E 编号与数字（提交 5，⑤ + ③）

- [ ] T019 `PROJECT_FEATURES.md` §九：把「仍待处理」改为「**已查明、无须动作**」，
      写明 `V72`/`specs/069` **全历史从未存在**（两条 `--diff-filter=A` 命令）+ **不可补号**（空迁移 = 假工件）
      + **不可改名**（重排 `V73`+ 会破坏**已应用**迁移的 Flyway checksum）
- [ ] T020 `PROJECT_FEATURES.md` §一 4 行改为实测值：迁移 **89**、spec 模块 **95**、前端单测 **91**、页面括注 **176**
- [ ] T021 同处表头把 **096** 补进「此后落地的批次」，并写明这 4 行是 **096 的遗留**
- [ ] T022 **订正留痕**：旧值（`88 个（V1–V89`、`94 个（001–095`、`89 / 7`、`含测试共 174`）
      **在文件中仍可 grep 到**（随带日期 ⚠️ 块保留原文）
- [ ] T023 核对迁移计数的**六个落点一致**（`specs/README.md` 标题与版本行、`INSTALL.md` ×2、根 `README.md`、
      `PROJECT_FEATURES.md`）——096 改了 5 处，**第 6 处就是这里**
- [ ] T024 删 `frontend/src/App.tsx.bak`（**未跟踪、被 `.gitignore:45` 覆盖 ⇒ 不产生提交**）；
      删除**前**取两条读数（`check-ignore` 命中 / `--find-object` 为空 / 全历史无 498 行）
- [ ] T025 删除的**不可逆**这件事与两条读数写进 `falsification-evidence.md`（**留痕是唯一凭据**）

## 阶段 F 勾选与留痕（提交 6）

- [ ] T026 **4 条定向破坏**逐条做、逐条逐字节还原（清单见 `quickstart.md` §2），留痕进 `falsification-evidence.md`
      ——D1（字典加无人用的码 ⇒ 红）、D2（白名单删一条 ⇒ 红）、D3（改 R6 count ⇒ `ui:check` 红）、
      D4（**反向**：给 `RequirePermissionCatalogTest` 补「必须为空」⇒ 红，证明那条路走不通）
- [ ] T027 ②/④/⑤ 的**可核替代**读数进留痕：② 的两条 `awk` 读数（8 / 10）+ 七条旧值 grep 命中行
      （**零命中 = 静默改写**，须逐条确认非零）
- [ ] T028 门禁实跑读数进交付块（`mvn -B verify` 覆盖率、前端四项覆盖率、`ui:check` 冻结台账与 R6 自身）
- [ ] T029 本文件勾选；`specs/README.md` 与 `specs/roadmap.md` 的 097 行状态改为交付态
- [ ] T030 交付后在 `specs/README.md` 的 097 行留后记（**只引用提交主题、不写哈希**）

---

## 交付块（**交付后填**，立项阶段留空）

| 项 | 读数 |
|---|---|
| 本项新增用例 | 后端 **1 个测试类 / 2 个 `@Test`**；前端 **0**（不改前端用例） |
| `mvn -B verify` | 待填（退出码 / INSTRUCTION%） |
| 前端四项覆盖率 | 待填（对 33.6 / 47.2 / 21.4） |
| `ui:check` 冻结台账 / R6 自身 | 待填 / 待填（期望 R6 = 10） |
| 定向破坏 | 待填（D1–D4 逐条转红） |
| 提交 | 6 次（**③ 不产生提交**：未跟踪且被 ignore） |

⚠️ **本项不写「本次提交」的哈希**（写进提交自己携带的文件会在 `--amend` 后变成不存在的对象）。
