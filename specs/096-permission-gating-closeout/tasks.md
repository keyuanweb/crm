# 任务：086「不可收口清单」收尾（096）

**Created**: 2026-09-16
**图例**：`[ ]` 未完成 / `[x]` 已完成。**立项阶段一律不预勾。**
**⚠️ 本项的实际编写顺序是「先实现、后补用例」**（沿用 087/088/092/095 的既有做法）——
**不得**据此声称走过 spec-first；§D 的定向破坏留痕证明的是**护栏有牙齿**，不是「红先出现」。

---

## 阶段 A 工件与登记（提交 1）

- [x] T001 写 `spec.md` / `plan.md` / `research.md` / `quickstart.md` / `falsification-evidence.md` / `tasks.md` / `checklists/requirements.md`
- [x] T002 `specs/README.md` 模块表加 096 行（**状态列如实写「⏳ 进行中」**）
- [x] T003 `specs/roadmap.md` 的 `## 当前进度` 加 096 行，**勾选框留空、不预勾**（照 095 先例）

## 阶段 B 后端（提交 2–4）

- [x] T004 `RoleConstants.PERMISSION_DEFS` 新增 `comment:read` / `comment:create` / `comment:delete`
      （新开「评论协作」组——评论在 `MENU_TREE` 里**没有**菜单）
- [x] T005 `RoleConstants.PERMISSION_DEFS` 在既有「自定义对象」组新增
      `custom_object_record:read` / `:create` / `:update` / `:delete`
- [x] T006 订正 T005 所在组的注释（「不设码——靠菜单 + 数据范围」**两半都不成立**），
      **原文逐字保留 + 带日期 ⚠️**
- [x] T007 `CommentController`：撤类级 `@PreAuthorize`，3 个方法各挂 `@RequirePermission`
- [x] T008 `CommentController` 类 javadoc：改写「刻意保留」的书面裁决为「已建码族并明确授予名单」，
      **原文逐字保留 + 带日期 ⚠️**
- [x] T009 `CustomObjectController`：5 个记录端点改挂 `@RequirePermission`（定义面 6 个端点一行不动）
- [x] T010 `CustomObjectController` 类 javadoc 订正「且靠数据范围过滤」（原文留痕 + 带日期 ⚠️）
- [x] T011 新增 `V90__comment_and_custom_object_record_codes.sql`（纯授权 INSERT，判据①，两族各一组）
- [x] T012 `backend/src/test/resources/schema-h2.sql` 落 `-- ---------- V90__… ----------` 段头
- [x] T013 `SchemaParityIT.MIRRORED_MIGRATIONS` 加 `"90"`
- [x] T014 `specs/README.md` 迁移对照表加 V90 行，并订正该表标题的迁移计数与上界
- [x] T015 `PermissionEnforcementIT` 追加两族分角色断言（有码 ⇒ 非 403；无码 ⇒ `PERMISSION_DENIED`；
      **两层 403 分离**；**负向必须用非 ADMIN 令牌**）

## 阶段 C 前端（提交 5–6）

- [x] T016 `constants/permissions.ts` 登记本项**真要 gating** 的码（每条必须被端点真实校验）
- [x] T017 `CommentSection.tsx`：删除判据改为 `hasPerm(comment:delete) && (ADMIN || 作者)`；
      发布区按 `comment:create` 渲染
- [x] T018 `CustomObjectRecordPage.tsx`：`usePerms` 收口新建/编辑/删除
- [x] T019 若新增文案：`i18n/{zh-CN,en}.ts` **双语同一次提交**（**条件未触发**：本项只改渲染条件，
      未新增任何文案，故 `i18n:check` 双向仍为 zh 2935 / en 2935）
- [x] T020 `ApprovalCenterPage.tsx`：三个操作链接加 `&& row.approverId === user?.id`
- [x] T021 `scripts/check-perms.mjs` 白名单**理由**更新（`CommentSection.tsx` 与 `DashboardPage.tsx`
      两条，**count 均不变**）

## 阶段 D 测试与留痕（提交 7）

- [x] T022 新建 `CustomObjectRecordPage.perm.test.tsx`（负向必须用非 ADMIN、先 `findByText` 证明页面已渲染）
- [x] T023 扩 `CustomerDetailPage.perm.test.tsx` 覆盖评论删除码
- [x] T024 `ApprovalCenterPage` 归属渲染用例（**不是 perm 用例**）
- [x] T025 **9 条定向破坏**逐条做、逐条逐字节还原（清单见 `quickstart.md` §2），留痕进 `falsification-evidence.md`
      ——**实做 11 条**：`quickstart.md` §2 的 **D1–D10** 全覆盖（D1→§A、D2→§B、D3→§E、D4→§D、D5→§F、
      D6→§G(i)、D7→§G(ii)、D8→§H(i)、D9→§I、D10→§H(ii)），**外加 §C**（评论族「漏授」方向：
      把 SUPPORT 从补授集合里去掉 ⇒ 「保留事实能力」的正面断言转红）。
      即后端 8 条记录（§A–§G 含 G 的两向）、前端 3 条（§H(i)/§H(ii)/§I）；**每次还原后复跑该批用例确认转绿**
      （前端 20 用例）。**两条断言文本未逐字采到的**（§G、§H(ii)）按 §G 的先例如实标注，
      以类名/方法名 + 计数/行号定位，**不臆造文本**。
- [x] T026 订正 `specs/086-frontend-button-gating/research.md` §2 清单第 2、5 行（**原文逐列逐字保留 +
      带日期 ⚠️**），并做「旧值仍可 grep 到」自查
- [x] T027 跑全部门禁，实测读数写进 `falsification-evidence.md` §0 与 `tasks.md` 交付块
      （全部实跑，读数见下方交付块；**未沿用任何历史数字**）
- [x] T028 `roadmap.md` 勾选 096 行 + 加【交付后记】；`README.md` 状态列改 ✅
      ——两处**都不是静默改**：`roadmap.md` 的行首加「**✅ 2026-09-16 交付**」并声明其后是立项期原文逐字保留，
      原文「对非被分配人必然 403」处**就地插入带日期 ⚠️ 订正**；`README.md` 的 096 行保留立项期原文整段，
      在**同一表格行内**追加「**｜ ✅ 交付…**」+ 两处订正（⏳ 承诺已履行／「必然 403」不成立／9 条 → **实做 11 条**）
      + 一条口径边界（`V90` 的 SQL 文本未被任何自动化用例执行）。**`README.md` 的修订只动本行、表格结构未破**
      （核验：该行仍为单行 4258 字符，其下仍是既有的 `> 【后记…】` 段落）。
- [x] T029 登记 `V87` 头注释那处不实表述（**已应用迁移不可改**，只登记）

---

## 交付块（2026-09-16 实跑读数，**不引用任何历史数字**）

**提交**（**8 次，不是计划里的 7 次**——拆分理由见下方「计划外但已做」第 1 条）：
`4d56844`（立项）→ `4783450`（字典 7 码）→ `80f2b6d`（两族端点换码 + `V90` + 镜像 +
`SchemaParityIT` + 5 处计数）→ `d269c46`（`PermissionEnforcementIT` 两族断言）→ `5c78695`（前端登记与收口）
→ `fb2dee3`（审批中心按归属渲染 **+ 订正「必然 403」**）→ `1488d34`（前端三处用例 + 白名单理由
+ `falsification-evidence.md`）→ **提交 8** = `docs(096): 086 清单判据订正、096 登记勾选与交付读数`
（**本块即在该提交里落定，故不写它自己的哈希**——写进去会因 `--amend` 而指向一个不存在的对象）。

| 项 | 读数 |
|---|---|
| `mvn -B verify` | 退出码 **0** / BUILD SUCCESS；surefire **695** / failsafe **325**，Failures·Errors·Skipped **全 0** |
| JaCoCo INSTRUCTION | **0.8109** ≥ 0.73（阈值未下调）；`jacoco:check` 打印 **"All coverage checks have been met."** |
| 前端六道门禁 | `typecheck` / `lint` / `i18n:check` / `menu:check` / `perms:check` / `ui:check` **退出码全 0** |
| `pnpm perms:check` | **68** 码（63+5）、**9** 处已登记 ADMIN 判断（与改动前同数） |
| `pnpm ui:check` | **272** 产品文件 / **304** `Form.Item`；冻结台账 **56** 处、未新增违规（**实跑值**） |
| `pnpm test:coverage` | 退出码 **0**；**91** 文件 / **464** 用例；Stmts **71.28** / Branch **75.26** / Funcs **39.24** / Lines **71.28**（`Branch` 两次跑差 **0.01**：75.25 → 75.26，取**交付时**那次；见 `falsification-evidence.md` §0.1） |
| `i18n:check` | zh **2935** / en **2935** 键；路由 58 / 清单 56 |
| 迁移脚本总数 | **89**（V1~V90，缺 V72） |
| 定向破坏 | **11 条**（后端 8 / 前端 3），转红与还原逐条留痕见 `falsification-evidence.md` |

**计划外但已做**（如实登记，避免"计划看起来比实际干净"）：
1. 把提交 7 拆成「用例 + 留痕」与「文档订正与勾选」两次提交（计划原为一次）——本目录工件与 086 的订正
   混在同一次提交里会让 `git show` 难以判读。
2. 提交 6 的**类型从 `docs`/`fix` 落到 `fix` 并把「订正调研结论」写进标题**：交付前复核调用链时发现
   本项调研的「非被分配人必然 403」**不成立**（列表端点本就按 `approverId` 过滤）。
   改法不变、理由改为「把隐式成立的判据显式写出来」，订正照仓规留痕（原文逐字保留 + 带日期 ⚠️）。
3. `quickstart.md` §2 的 **D10**（原先标为"可选补做"）**已做**，因为它所守的码本项真的登记了。
4. `check-perms.mjs` 的两条白名单**理由**订正原计划随提交 5 落地，**实际落到提交 7**（`1488d34`）——
   提交 5 时该文件改的是上一批内容，本批只改理由文本、**count 与判定一字未变**，故前后秩序不影响门禁。
5. **`test:coverage` 的 `Branch` 两次跑差 0.01**（75.25 → 75.26）：不是计划偏差，是读数处置，
   已单列 `falsification-evidence.md` **§0.1**，所有工件一律改记**交付时**那次（75.26）。
   **差异原因未查明**（两次的文件数与用例数完全相同），**不声称**知道它是舍入还是 runner 抖动。
