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
  逐字留痕见 `spec.md` US3 与 `research.md` §3 的 2026-09-16 ⚠️ 块。

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
