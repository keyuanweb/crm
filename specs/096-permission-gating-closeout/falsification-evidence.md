# 定向破坏留痕（096）

本文件记录**每条声称是护栏的断言被观测到在破坏下转红**的逐字证据。
**破坏期间未提交**，每条破坏后**逐字节还原**（还原判据：`git diff` 对该文件为空）。

> ⚠️ **如实声明的边界**：本项编写顺序是**先实现、后补用例**。本文件的留痕证明的是
> **护栏有牙齿**（断言真的会失败），**不是**「红先出现」——不得据此声称走过 spec-first。

---

## §0 门禁实测读数（2026-09-16 交付时实跑，**不沿用任何历史数字**）

> 本表是**交付前最后一次全量复跑**（提交 7/8 之前，同一工区）的读数，与首次读数**逐项相同**，
> **唯一例外是 `test:coverage` 的 `Branch`**——见 §0.1，那里如实记了两次跑的差值与对齐处置。

| 门禁 | 读数 |
|---|---|
| `mvn -B verify` | **退出码 0 / BUILD SUCCESS**；surefire `Tests run: 695, Failures: 0, Errors: 0, Skipped: 0`；failsafe `Tests run: 325, Failures: 0, Errors: 0, Skipped: 0`（两个计数都是**跑出来的整行**，未做差） |
| JaCoCo INSTRUCTION | **0.8109**（covered 47781 / (47781+11140)，由 `target/site/jacoco/jacoco.csv` 逐行求和算得）；`jacoco:check` 打印 **"All coverage checks have been met."**（pom 阈值 `<minimum>0.73</minimum>`，**未下调**） |
| `pnpm typecheck` / `lint` | **退出码 0 / 0** |
| `pnpm i18n:check` | **退出码 0**：zh-CN **2935** 键 / en **2935** 键；路由 **58** 条 / 清单 **56** 项，粗粒度别名 3 条 |
| `pnpm menu:check` | **退出码 0**：清单最新（**56** 个菜单项，来源 `RoleConstants.MENU_TREE`） |
| `pnpm perms:check` | **退出码 0**：**68** 个权限码（63 + 本项 5）；**8** 个文件含已登记的 ADMIN 判断，共 **9** 处（与改动前同数——`CommentSection` 那处例外保留，**count 未变**） |
| `pnpm ui:check` | **退出码 0**：扫描 **272** 个产品文件（126 个 tsx）、**304** 个 `Form.Item`；**白名单内冻结的既存债 56 处，未新增违规**（⚠️ **以本次实跑 56 为准**；095 交付时记的 54 是历史值，本项未沿用） |
| `pnpm test:coverage` | **退出码 0**：**91** 个测试文件 / **464** 个用例全通过；All files **Stmts 71.28 / Branch 75.26 / Funcs 39.24 / Lines 71.28**（阈值 **33.6 / 47.2 / 21.4**，**未下调**；`Branch` 一处的两次跑差异见 §0.1） |
| 迁移脚本总数 | **89**（`ls backend/src/main/resources/db/migration \| wc -l`；V1~V90，**V72 不存在**） |
| 定向破坏 | **11 条**（后端 8：§A–§G 含 G 的两向；前端 3：§H(i)/§H(ii)/§I），每条观测到转红、逐字节还原；前端三条还原后复跑 **20 用例全绿** |

⚠️ **本表是"白板工区"读数**：跑之前 `ListAgents` 无其它会话、`git status --porcelain` 里除本项自有文件外无他人未跟踪文件
（他人的未跟踪 `*.test.tsx` 会被 vitest 静默计入文件数/用例数，制造假绿——这正是本项每次取值前都核对工区的原因）。

### §0.1 一处 0.01 的读数差（如实登记，不静默对齐）

`pnpm test:coverage` 在本项里**跑过两次**，两次都是 **91 文件 / 464 用例、退出码 0**，
`Stmts 71.28 / Funcs 39.24 / Lines 71.28` **逐字相同**，**只有 `Branch` 差了 0.01**：

| 次 | 场景 | Branch 读数 |
|---|---|---|
| 第 1 次 | 破坏全部还原后首次取值 | **75.25** |
| 第 2 次 | **交付前最后一次全量复跑**（提交前，同一工区、同一命令） | **75.26** |

**处置**：所有工件（本表、`tasks.md` 交付块、`roadmap.md` 后记、`specs/README.md` 的 096 行）
一律记**第 2 次**（75.26）——它是**交付时**的读数，且是**同一条命令、同一个工区**下的最后一次。
**差异原因未查明**（两次的测试文件数与用例数完全相同，故不是"多跑了文件"；
本项**不声称**知道它是舍入还是 runner 侧的分支计数抖动）。**对判定无影响**：
`75.25` 与 `75.26` 都远高于阈值 `47.2`，且**阈值两次都未被下调**。
**写明这一点，是为了让后来者发现自己的读数与本文差 0.01 时，知道这不是被谁改过。**


## §A …§G 定向破坏（后端，提交 3/4 落地后即做）

每条格式：**破坏内容 → 命令 → 转红的断言（逐字） → 还原证据**。
还原一律 `git checkout -- <路径>`；每条之后 `git status --porcelain` **为空**（可核）。

### §A 注解用码不在字典里 ⇒ `RequirePermissionCatalogTest` 转红

- **破坏**：`CommentController.list` 的 `@RequirePermission("comment:read")` 改成 `@RequirePermission("comment:readX")`。
- **命令**：`mvn -B test -Dtest=RequirePermissionCatalogTest`
- **转红**（逐字）：
  ```
  Tests run: 1, Failures: 1, Errors: 0, Skipped: 0 <<< FAILURE!
  Expecting empty but was: {"comment:readX"=["com.crm.controller.CommentController#list"]}
  ```
- **还原**：`git checkout -- backend/src/main/java/com/crm/controller/CommentController.java` → `git status --porcelain` 为空。

### §B 授予了字典里没有的码 ⇒ `PermissionMatrixIT.everyGrantedCodeExistsInDictionary` 转红

- **破坏**：在镜像 `schema-h2.sql` 末尾追加
  `INSERT INTO role_permission (role_id, permission_code) SELECT r.id, 'bogus:code' FROM role r WHERE r.code = 'ADMIN';`
- **命令**：`mvn -B process-test-resources failsafe:integration-test failsafe:verify -Dit.test=PermissionMatrixIT`
- **转红**（逐字）：
  ```
  Tests run: 3, Failures: 1, Errors: 0, Skipped: 0 <<< FAILURE! -- in com.crm.integration.PermissionMatrixIT
  Expecting empty but was: ["bogus:code"]
  ```
- **还原**：`git checkout -- backend/src/test/resources/schema-h2.sql` → 工区为空。

> ⚠️ **本条打的是 `schema-h2.sql`，不是 `V90`** —— 这不是走捷径，而是**测试库的真实加载路径**：
> `application-test.yml:21` 是 `flyway.enabled: false`，`sql.init.mode: always`，H2 的授权行
> **全部来自镜像文件**。其后果必须写明：**「V90 的 SQL 文本本身写错」没有任何自动化用例能发现**
> （它从不被执行）。V90 与镜像是否一致，只由 `SchemaParityIT` 的「镜像文件被改动过」这一**弱**检查兜底
> ——该守卫自己的类注释已声明这条边界。见 §Z。

### §C 漏授 SUPPORT ⇒ 「保留事实能力」的正面证据转红

- **破坏**：镜像里评论那组的 `WHERE r.code IN ('ADMIN', 'SALES', 'SUPPORT');` 改成 `('ADMIN', 'SALES');`
  （只改第 2072 行那一处，脚本内以行号断言锚定，改后逐字回显确认）。
- **命令**：`mvn -B process-test-resources failsafe:integration-test failsafe:verify -Dit.test=PermissionEnforcementIT`
- **转红**（逐字）：
  ```
  PermissionEnforcementIT.commentCodesPreserveTheOriginalGateAndSeparateTheTwoForbiddenLayers
      -- Time elapsed: 0.508 s <<< FAILURE!
  java.lang.AssertionError: Status expected:<200> but was:<403>
      at ...commentCodesPreserveTheOriginalGateAndSeparateTheTwoForbiddenLayers(PermissionEnforcementIT.java:1018)
  [ERROR] ...:1018->createComment:1133 Status expected:<200> but was:<403>
  ```
  **红在 `createComment(support, …)`** —— 即「客服改造前能发言、改造后仍能」这条**正是**被破坏打中的那一条。
  若 V90 漏授 SUPPORT，客服不是"少一个可勾选项"，而是**已被使用的能力变成 403**（1.5 风险清单里的头号风险）。
- **还原**：同上 → 工区为空。

### §D 多授 ANALYST（记录面）⇒ 「不扩权」断言转红

- **破坏**：镜像末尾追加把 `custom_object_record:read` 与 `:create` 授予 `ANALYST` 的 INSERT。
- **命令**：同 §C。
- **转红**（逐字）：
  ```
  PermissionEnforcementIT.customObjectRecordCodesPreserveTheOriginalGate:1095->assertDeniedByPermissionCode:1191
      Status expected:<403> but was:<200>
  ```
- **还原**：同上 → 工区为空。

### §E 多授 SALES_REP（评论面）⇒ 「不扩权」断言转红

- **破坏**：镜像末尾追加把 `comment:read/create/delete` 授予 `SALES_REP` 的 INSERT。
  （§D 与 §E 同一次运行施加，但**打的是两个不同测试方法、两个不同角色**，故归因不混淆：
  ANALYST 只出现在记录族用例里，SALES_REP 只出现在评论族用例里。）
- **命令**：同 §C。结果 `Tests run: 14, Failures: 2` —— 两个方法**各自**转红。
- **转红**（逐字）：
  ```
  PermissionEnforcementIT.commentCodesPreserveTheOriginalGateAndSeparateTheTwoForbiddenLayers:1035
      ->assertDeniedByPermissionCode:1192
      JSON path "$.error.code" expected:<PERMISSION_DENIED> but was:<FORBIDDEN>
  ```
  ⚠️ **值得记下**：这里**没有**变成 200，而是从 `PERMISSION_DENIED` 变成了 `FORBIDDEN` ——
  补上读码后切面放行，紧接着被 `CommentService.checkEntityVisible` 的**数据范围**挡住。
  也就是说这一次破坏同时印证了**两层各自独立**：码层放人，范围层照旧在拦。
  （若断言只写「403」而不写 `error.code`，这个破坏**不会转红**。）
- **还原**：同上 → 工区为空。

### §F 归属拒绝改抛 `PERMISSION_DENIED` ⇒ 「两层 403 分得开」转红

- **破坏**：`CommentService` 第 120 行（`delete` 里那条「非作者且非 ADMIN」）的
  `ErrorCode.FORBIDDEN` 改成 `ErrorCode.PERMISSION_DENIED`。`checkEntityVisible` 那条**不动**。
- **命令**：`mvn -B -q compile` 后同 §C。
- **转红**（逐字）：
  ```
  PermissionEnforcementIT.commentCodesPreserveTheOriginalGateAndSeparateTheTwoForbiddenLayers:1050
      JSON path "$.error.code" expected:<FORBIDDEN> but was:<PERMISSION_DENIED>
  ```
  **这条破坏的性质**：破坏之后，SUPPORT（持码、非作者）与 VIEWER（无码）对**同一个 URL**
  得到**完全一样**的响应体。断言若只判 HTTP 403，两者不可分——这正是本类类头那条判据
  「两层 403 必须分开断言」的可执行形态。
- **还原**：`git checkout -- backend/src/main/java/com/crm/service/CommentService.java` → 工区为空，并重新 `compile` 成功。

### §G `SchemaParityIT` 两向（加迁移必须动镜像）

- **G(i) 只进清单、不进镜像的另一半**：把 `MIRRORED_MIGRATIONS` 里的 `"90"` 去掉。
  - 命令：`mvn -B test-compile failsafe:integration-test failsafe:verify -Dit.test=SchemaParityIT`
  - 转红（逐字）：`Tests run: 4, Failures: 1`，
    `SchemaParityIT.everyMigrationIsMirroredInTestSchema -- Time elapsed: 0.138 s <<< FAILURE!`，
    `at com.crm.integration.SchemaParityIT.everyMigrationIsMirroredInTestSchema(SchemaParityIT.java:69)`
    （第 69 行即那条 `assertTrue(missing.isEmpty(), …)`；断言的消息文本被本次抓取的管道过滤掉，
    故以**类名 + 方法名 + 行号**定位，不臆造文本）。
- **G(ii) 进了清单、镜像里却没有段头标记**：把镜像里的段头
  `-- ---------- V90__comment_and_custom_object_record_codes：… ----------` 改成 `V9X__…`
  （改前 `grep -c "V90" schema-h2.sql` = 1，改后 = 0，逐字回显确认）。
  - 命令：同 G(i)（另加 `process-test-resources`）。
  - 转红（逐字）：`Tests run: 4, Failures: 1`，
    `SchemaParityIT.everyMirroredMigrationWithoutDdlIsMarkedInTheMirror -- Time elapsed: 0.035 s <<< FAILURE!`，
    `at com.crm.integration.SchemaParityIT.everyMirroredMigrationWithoutDdlIsMarkedInTheMirror(SchemaParityIT.java:151)`
- **还原**：两条各自 `git checkout --` 对应文件 → 工区为空。**注意 G(i) 与 G(ii) 是两次独立运行**，
  不叠加：否则「清单缺项」会先红，掩盖标记那一向。

## §H 定向破坏（前端，提交 5/6 落地后做）

三条对应 [quickstart.md](./quickstart.md) §2 的 D8 / D9 / D10。破坏一律用定向文本替换施加，
还原一律 `git checkout -- <路径>`；还原后**重跑同一批用例确认转绿**（20 用例，见 §H 末）。
还原判据同 §A：`git status --porcelain` 里该文件回到未修改态（工区另有本项自己的新测试文件，属预期）。

### §H(i) 删掉评论删除的**码**判据、只留归属 ⇒ D8 用例转红

- **破坏**：`CommentSection.tsx` 的
  `hasPerm(PERMS.commentDelete, user) && (user?.role === 'ADMIN' || user?.id === c.authorId)`
  改成 `user?.role === 'ADMIN' || user?.id === c.authorId`（即 096 之前的那一行）。
- **命令**：`npx vitest run --minWorkers=1 --maxWorkers=4 src/pages/customers/CustomerDetailPage.perm.test.tsx -t "无码"`
- **转红**（逐字，`<svg>` 一段以 `…` 略去）：
  ```
  Error: expect(element).not.toBeInTheDocument()

  expected document not to contain element, found <a
    style="font-size: 12px; color: rgb(255, 77, 79);"
  >
    <span aria-label="delete" class="anticon anticon-delete" role="img"> … </span>
    pages.commentSection.btnDelete
  </a> instead
   ❯ src/pages/customers/CustomerDetailPage.perm.test.tsx:225:30
      223|     expect(screen.getByText('我自己的留言')).toBeInTheDocument()
      224|     expect(submitButton()).not.toBeInTheDocument()
      225|     expect(deleteLink()).not.toBeInTheDocument()
  ```
  `Tests  1 failed | 8 skipped (9)` —— 红的正是第 ④ 例「**无码**但是作者」，
  也就是「改造前作者看得见这个必然 403 的删除」那一条。**这是本项码判据有牙齿的直接证据。**
- **还原**：`git checkout -- frontend/src/components/CommentSection.tsx` → 该文件回到未修改态，
  并复跑通过（§H 末）。

### §H(ii) 删掉记录面的三个码判据 ⇒ D10 用例转红

- **破坏**：`CustomObjectRecordPage.tsx` 三处渲染条件各换成常量真值
  （`can[PERMS.customObjectRecordUpdate] && (` → `true && (`、`:delete` 同理、
  `toolBarRender` 的 `can[PERMS.customObjectRecordCreate] ?` → `true ?`）——
  即「去掉 `usePerms` 判据、删除/编辑/新建恒渲染」。
- **命令**：`npx vitest run --minWorkers=1 --maxWorkers=4 src/pages/custom-object/CustomObjectRecordPage.perm.test.tsx`
- **转红**（逐字的方法名与计数）：
  ```
  Failed Tests 5
  FAIL … > ② 零权限码的 SALES：三者都不可见（页面与记录行本身照常渲染）
  FAIL … > ③ 只持**读**码（custom_object_record:read）的 SALES：仍然三者都不可见（读码不放行写）
  FAIL … > ④ 只持 custom_object_record:create：看得见新建，看不见编辑与删除
  FAIL … > ⑤ 只持 custom_object_record:update：看得见编辑，看不见新建与删除
  FAIL … > ⑥ 只持 custom_object_record:delete：看得见删除，看不见新建与编辑
  Tests  5 failed | 1 passed (6)
  ```
  第 ① 例（ADMIN）**保持绿**是正确的：管理员本就该看见全部，这条破坏打不中它。
  ⚠️ 与 §G 同理如实声明：上表是**方法名 + 计数**，逐条断言文本被本次抓取的管道（`grep -E "FAIL|Tests "`）
  过滤掉了，**不臆造文本**；行号未采到，故以方法名定位。
- **还原**：`git checkout -- frontend/src/pages/custom-object/CustomObjectRecordPage.tsx` → 未修改态。

## §I 审批归属：渲染条件改回只判业务状态 ⇒ D9 用例转红

- **破坏**：`ApprovalCenterPage.tsx` 的 `{canAct(row) ? (` 改回 `{row.status === 'PENDING' ? (`。
- **命令**：`npx vitest run --minWorkers=1 --maxWorkers=4 src/pages/approval/ApprovalCenterPage.test.tsx`
- **转红**（逐字）：
  ```
  Failed Tests 3
  FAIL … > ② 他人的待办（approverId ≠ 我）：三个都不可见，「详情」照常可见
  FAIL … > ③ approverId 为 null 的待办：三个都不可见（镜像 checkApprover 的 null 分支）
  FAIL … > ⑤ ADMIN 看**他人**的待办：仍不可见——本判据对 ADMIN 也不通融

  Error: expect(element).not.toBeInTheDocument()
  expected document not to contain element, found <a>
    pages.approval.list.approve
  </a> instead
   ❯ src/pages/approval/ApprovalCenterPage.test.tsx:94:31
  ```
  `Tests  3 failed | 2 passed (5)`。第 ⑤ 例的红值得单独记：**破坏后连 ADMIN 也看见了**
  ——它证明这条断言测的确实是「归属」，不是「角色」（换成权限码判据的话，ADMIN 会直通、
  这一例就不会红）。
- **还原**：`git checkout -- frontend/src/pages/approval/ApprovalCenterPage.tsx` → 未修改态。
- ⚠️ **本条用例成立的前提是把取数桩住**：真端点 `/approvals/todos`、`/approvals/done` 按
  `approverId` 过滤（`ApprovalEngineService.todos/done`），**跑真后端时页面上不会出现他人的任务**
  ——那样「只判 status」与「判归属」走的是同一批数据，这条破坏**打不出红**。
  用例里 `fetchApprovalTodos` 被 mock 成返回一条 `approverId ≠ 我` 的任务，正是为此。
- ⚠️ **顺带订正**：本项调研初稿称此处是「非被分配人看得见必然 403 的按钮」的**当下可观察**缺陷，
  实测**不成立**（列表端点本就过滤）。改法不变、理由已改为「把隐式成立的判据显式写出来」，
  逐字留痕见 `spec.md` US3 与 `research.md` **§4 末**的 2026-09-16 ⚠️ 块（该订正块落在「审批权改按角色码」
  那一节的尾部，**不在 §3**——§3 是补授名单）。

### §H/§I 还原后的复跑（20 用例全绿）

```
 ✓ src/pages/approval/ApprovalCenterPage.test.tsx              (5 tests)
 ✓ src/pages/custom-object/CustomObjectRecordPage.perm.test.tsx (6 tests)
 ✓ src/pages/customers/CustomerDetailPage.perm.test.tsx         (9 tests)
 Test Files  3 passed (3)
      Tests  20 passed (20)
```

## §Z 未验证 / 未涉及（**如实写在前面，不沉默**）

- **手工冒烟**：待定（需用户放行 8081 上的后端）。
  ⚠️ **2026-09-16 当日订正：用户当日放行，冒烟已跑完**——但**未用 8081**，改用**隔离实例**
  （18096 + `crm_096_smoke` + Redis db 5），理由是本仓规「不得擅自重启可能归并行会话所有的共享后端」。
  读数、覆盖项与**未覆盖项**见 §J。**原文保留于此。**
- **断点分支：未涉及**。`src/test/setup.ts` 把 `matchMedia` **恒桩成 `matches: false`** ⇒ 断点分支
  默认不执行。本项三个新/改用例（评论、记录面、审批归属）判的都是**渲染与不渲染**，
  与视口宽度无关，没有任何几何或断点类断言，故**没有**在测试文件内覆盖 `matchMedia`。
- **e2e / 浏览器 UI**：本项**未新增任何 e2e**，不做视觉验证。
- **jsdom 无布局引擎**：几何类判据在本项不存在，无需声明例外。
- ⚠️ **`V90` 的 SQL 文本本身没有被任何自动化用例执行过**（本项最该知道的一条边界）：
  测试库是 `schema-h2.sql` 建的（`application-test.yml:21` `flyway.enabled: false` +
  `sql.init.mode: always`），Flyway 在测试里**根本不跑**。因此：
  - 本批所有「授权范围」类断言，验的是**镜像文件**里的授权行，不是 `V90` 里的 INSERT；
  - `V90` 与镜像是否逐条一致，只由 `SchemaParityIT` 的「不建表的迁移必须在镜像里留下版本标记」
    这一**弱**检查兜底（它只证明"镜像被改动过"，不证明改动内容与 `V90` 一致）——
    该守卫自己的类注释已把这条列为「本守卫不覆盖」；
  - 结论：**`V90` 的内容一致性目前只能靠人工复核与真库冒烟**。这也是 §Z 首条「手工冒烟」
    不只是走形式的原因之一（真库冒烟会在 MySQL 上真正执行 `V90`）。
  本项**不声称** `V90` 已被自动化验证——它没有。
  ⚠️ **2026-09-16 当日补记：这一条的「真库冒烟」那一半已被 §J 兑现**——`V90` 在 MySQL 8.0.46 上
  **真正执行过**，且「迁移声明 / H2 镜像 / 真库结果」三方各 17 行**逐条相等**。
  **但自动化侧仍如本条所述**：测试套件里 `flyway.enabled: false` 一字未改，`V90` 在 CI 侧依旧
  不执行，`SchemaParityIT` 依旧是那一条弱检查。**本条原文保留。**

---

## §J 手工冒烟（隔离实例，2026-09-16 用户放行后当日实跑）

### J.0 环境与隔离（**先写这一节：本项是否碰了共享资源**）

| 项 | 值 |
|---|---|
| 隔离实例 | `SERVER_PORT=18096`、`DB_NAME=crm_096_smoke`、`SPRING_DATA_REDIS_DATABASE=5` |
| 数据库 | 真 **MySQL 8.0.46**（WSL），库 `crm_096_smoke` 由 root 现建、只授给 `crm_user@localhost` |
| Flyway | **在隔离库上从零跑完 V1–V90**：`Successfully applied 89 migrations to schema crm_096_smoke, now at version v90`；其中 `Migrating schema … to version "90 - comment and custom object record codes"` |
| 未碰的共享资源 | 8081（跑前 `netstat` 显示**无进程监听**，全程未起）、5173、共享库 `crm_db`、Redis db 0 |
| 收尾 | 隔离库 `DROP` + 授权 `REVOKE` + `FLUSHDB`（db 5：6 键 → 0）；端口 18096 已释放；进程 8132 已终止；临时令牌文件已删 |

⚠️ **一处环境事实（与本项代码无关，但下次会再撞上）**：经 Git Bash 的 `curl --data` 提交**含中文**
的 JSON 体时，后端判 `400 BAD_REQUEST 请求体格式错误`（终端编码把中文发成了 GBK 字节）；
改用 **ASCII 载荷**后一切正常。冒烟夹具因此一律用 ASCII 命名。

### J.1 分角色令牌打真端点（`quickstart.md` §3 的矩阵，逐格实跑）

夹具（全部经真端点创建）：隔离库的 `DataInitializer` 种子 `admin/admin123`（ADMIN）；
另建 5 个冒烟用户（`SALES` / `SUPPORT` / `SALES_REP` / `ANALYST` / `VIEWER`，复用同一 bcrypt 哈希、
`data_scope=ALL`，**只存在于隔离库**）；自定义对象 `EQ_096_SMOKE`（id 1）及其记录（id 1）；
评论挂在 `entityType=TICKET`（`CommentService.checkEntityVisible` 对非 CUSTOMER/LEAD/OPPORTUNITY
走 `default -> true`，故**评论面的观测不被数据范围层污染**——这正是能把两层分开看的前提）。

| 角色 | `GET /comments` | `POST /comments` | `DELETE /comments/{id}` | `GET /objects/{id}/records` | `POST …/records` |
|---|---|---|---|---|---|
| （无令牌） | **401** | — | — | — | — |
| ADMIN | 200 | 200 | **200**（非作者，服务层的 ADMIN 例外） | 200 | 201 |
| SALES | 200 | 200 | **200**（作者本人） | 200 | 201 |
| SUPPORT | 200 | 200 | **403 `FORBIDDEN`** | **403 `PERMISSION_DENIED`** | **403 `PERMISSION_DENIED`** |
| SALES_REP | **403 `PERMISSION_DENIED`** | 403 `PERMISSION_DENIED` | 403 `PERMISSION_DENIED` | **403 `PERMISSION_DENIED`** | 403 `PERMISSION_DENIED` |
| ANALYST | **403 `PERMISSION_DENIED`** | 403 `PERMISSION_DENIED` | 403 `PERMISSION_DENIED` | **403 `PERMISSION_DENIED`** | 403 `PERMISSION_DENIED` |
| VIEWER | **403 `PERMISSION_DENIED`** | 403 `PERMISSION_DENIED` | 403 `PERMISSION_DENIED` | **403 `PERMISSION_DENIED`** | 403 `PERMISSION_DENIED` |

**这张表逐格与 `quickstart.md` §3 的预期一致**，且给出了三条只有真端点才能给的证据：

1. **两层 403 在同一个端点、同一个对象上分开出现**（这是最硬的一条）：
   删除**同一条 SALES 写的评论**，`SUPPORT` 拿到 **`FORBIDDEN`**（码放行、归属拒绝），
   而 `SALES_REP` 拿到 **`PERMISSION_DENIED`**（码拒绝）。**只断言 HTTP 403 看不出这个区别**。
2. **改门前后「不扩权」的可核证据**：`SALES_REP`（081 新增的一线销售角色）与 `ANALYST`
   （持有 `custom_object:*` 定义面码与「自定义对象」菜单）**在两个族上都被挡**——
   它们改造前就被 `hasAnyRole` 的角色字面量挡着（`SALES_REP ≠ 'SALES'`）。
3. **SUPPORT 的「半开」形态与判据① 逐字吻合**：评论面全通（原类级门放行它）、记录面全挡
   （原方法级门没放行它）。

### J.2 「7 个码在角色页上勾得出来」——用**页面所消费的同一对 API** 验证

| 调用 | 读数 |
|---|---|
| `GET /api/v1/roles/permission-defs`（ADMIN） | 暴露 **145** 条码/组条目，本项 **7 个码全部在列**（`comment:read/create/delete`、`custom_object_record:read/create/update/delete`） |
| `GET /api/v1/roles`（ADMIN） | `ADMIN` 81 码含本项 **7**；`SALES` 58 码含 **7**；`SUPPORT` 36 码含 **3**（评论族）；`SALES_REP` 48 码含 **0**；`ANALYST` 11 码含 **0**；`VIEWER` 6 码含 **0** |

⇒ 与 `V90` 落库的 17 行（`comment:*` × {ADMIN,SALES,SUPPORT} = 9；`custom_object_record:*` × {ADMIN,SALES} = 8）
**逐条吻合**。本项对「锁死」的兑现是**新增可勾选性**，不是权限本身。

### J.3 `V90` 三方一致性（**本项原先最大的空白，在此补上**）

同一批数据在三个地方各算一遍，**三方逐条相等**：

| 来源 | 行数 |
|---|---|
| `V90__comment_and_custom_object_record_codes.sql` 的 INSERT 声明 | **17** |
| `schema-h2.sql` 的 V90 镜像段声明 | **17** |
| **真 MySQL 8.0.46 上 `V90` 实际执行后的 `role_permission`** | **17** |

**三方 == 三方**（脚本比对，非目测）。这**不是**在说 `SchemaParityIT` 那条弱检查变强了——
那条检查仍然只证明「镜像被改动过」；它证明的是**这一次的镜像内容与 `V90` 一致**，
而这一点此前只能靠人工复核。

### J.4 本次冒烟**没有覆盖**的（不得读成「全验了」）

- **浏览器 UI 一律未跑**：未起 5173、未开浏览器。故「角色页上勾得出来」是用**页面所消费的
  同一对 API** 验证的（J.2），**不是**在页面上点出来的——勾选框的渲染、禁用态、保存交互**均未验证**。
- **审批归属判据**不在本矩阵里：它**不是权限码**，证据是前端的 `ApprovalCenterPage.test.tsx`（§I）。
- **未跑前端 e2e**，未做视觉验证。
- 冒烟用户与夹具数据**只在隔离库内存在**，收尾已随库一起删除；**不代表任何真实环境状态**。

