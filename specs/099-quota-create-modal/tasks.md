# 任务：配额创建表单改成列表页内弹窗（099）

**Created**: 2026-09-16
**图例**：`[ ]` 未完成 / `[x]` 已完成。**立项阶段一律不预勾。**
**⚠️ 本项的实际编写顺序是「先实现、后补用例」**（沿用 087/088/092/095–098 的既有做法）——
**不得**据此声称走过 spec-first；定向破坏留痕证明的是**护栏有牙齿**，不是「红先出现」。
**⚠️ 本项的活动半径**：**纯前端 + 文档**。**零后端改动、零迁移、零权限码、零新增门禁脚本、无 `contracts/`**。

**⚠️ 删与加必须同批**：`QuotaCreatePage.tsx` 的删除、`App.tsx` 的路由删除、`QuotaListPage.tsx` 的弹窗落地
**必须在同一次提交里**（T011–T014）——分两次会让中间提交留下「指向已删文件的路由」。

---

## 阶段 A 工件与登记（提交 1）

- [ ] T001 写 `spec.md` / `plan.md` / `research.md` / `quickstart.md` / `falsification-evidence.md` / `tasks.md` / `checklists/requirements.md`
- [ ] T002 `research.md` 必须装齐五件：① 与 `078-sales-quota` 的射程关系；② **R2 对 `<FormModal>` 隐形的机制**（读实现得出，附行号）；③ **逐门禁的删除敏感性**（每条附**判据**，不是「跑一遍没红」）；④ **失去对象的历史基线**清单；⑤ **三条既有空档**登记（权限码 / 子页无入口 / `navigate` 目标无校验）
- [ ] T003 `falsification-evidence.md` 的 §0 填入**本项开工前**的五道门禁实跑读数（工区干净时取的）
- [ ] T004 `specs/README.md` 模块表加 099 行（**状态列如实写「⏳ 进行中」**，**不预勾**）
- [ ] T005 `specs/README.md` 的**编号说明**纳入 099（**按文本锚定位，不按行号**——行号会漂）
- [ ] T006 `specs/roadmap.md` 加 099 行（**勾选框留空、不预勾**）；`最后更新` 前置 099 立项条目（旧值降级为「**上一条（原文保留）**」，逐字不改）。⚠️ **实做订正（2026-09-16，提交 1 编写期记录）**：计划原写「`整体覆盖度` 的 `001–098` → **`001–099`**」，**实做改为不改那句的交付态断言**——该句说的是**已交付**的编号面，而 099 **尚未交付**，改成 `001–099` 等于把在办项写成已交付（**假话**）；照 **097 立项时**的同一处置，只前置一段带日期的 ⚠️ 块登记两处**随入列漂移的计数**：**编号面判据** `ls -d specs/[0-9]* | wc -l` **97 → 98**（001–099，**缺 069**）、`## 当前进度` **97 勾 / 0 未勾 → 97 勾 / 1 未勾**（实测复核：`awk` 计勾 97 / 未勾 1）。**旧值逐字保留**（`001–098 全部交付` 仍在句中，可 grep 到）；交付时（T038）再把该句改成 `001–099 全部交付`。
- [ ] T007 ⚠️ **实做订正（2026-09-16，提交 1 编写期记录）：本项**不在提交 1 做**，前移到提交 4（交付）** —— `README.md:163` 的 spec 计数 97 → **98（001~099，缺 069）**（**旧值逐字保留 + 带日期 ⚠️**）。**理由**：① 它与 `PROJECT_FEATURES.md:20` 的「Spec 模块」是**同一类对外规模数字**，必须**同批改齐**（只改一处等于用一次订正造出两处新矛盾）；② `PROJECT_FEATURES.md` 的 i18n 行要等 T018 落地后**实测**才能写 ⇒ 该文件整体在提交 4 收口；③ 仓内先例本就是**交付时**改这处（098 的 `63ea0e7` 即其交付提交）。⇒ **提交 1 只登记「本批自己的状态」**（`specs/README.md` 模块表 + `specs/roadmap.md` 头部与 099 行），**不动对外规模数字**。

## 阶段 B 弹窗落地与旧实现删除（提交 2 —— 本项的结构性交付）

- [ ] T008 `QuotaListPage.tsx` 加 `FormModal`（`size="lg"`）承载创建表单：**6 字段 / 3 条 `required` / 控件范围 / `initialValues` 逐字搬运**（FR-002）
- [ ] T009 `title` = `pages.quotaCreate.title`；`okText` **显式传** `pages.quotaCreate.btnCreate`；取消键**不传**（吃原语默认 `common.button.cancel`）（FR-003）
- [ ] T010 `<FormGrid minItemWidth={VERTICAL_MIN_ITEM_WIDTH}>` **保留**、**`maxCols={3}` 删掉**；`layout="vertical"` 保留（FR-004，理由见 `FormGrid.tsx:63-65`）
- [ ] T011 提交通路：`validateFields` → 组 payload（`periodStart/periodEnd` 走 `format('YYYY-MM-DD')`）→ `quotaApi.create` → `message.success` → 关弹窗 → `actionRef.current?.reload()` → `setRefreshToken(n => n + 1)`（FR-006/FR-007）
- [ ] T012 KPI 重取通路：新增 `refreshToken` state 并**加进既有 `useEffect` 的依赖数组**（**不引入第二套取数路径**）（FR-008）
- [ ] T013 失败路径：`catch` 里 `message.error` + **弹窗保持打开** + **不 rethrow**（附代码注释写明理由：`FormModal.handleOk` 是 `try/finally` 无 `catch`）（FR-009）
- [ ] T014 消息 API 用 `App.useApp()`（本仓 69 个文件的主流写法；测试侧 `renderWithProviders` 已包 `<AntApp>`）（FR-010）
- [ ] T015 工具栏「新建」按钮改 `setModalOpen(true)`，**不再 `navigate`**（按钮文案与图标不变）（FR-005）
- [ ] T016 **删** `frontend/src/pages/quotas/QuotaCreatePage.tsx`（FR-011）
- [ ] T017 **删** `App.tsx` 里该页的 `lazy` import 与 `<Route path="quotas/create" ...>`（FR-012）
- [ ] T018 i18n：**复用** `pages.quotaCreate.*` 的 16 键（值一字不改）；**同批删死键 `btnBack`**（`zh-CN.ts` 与 `en.ts` **两侧都删**）（FR-014/FR-015）
- [ ] T019 e2e `module-page-auth.spec.ts`：`FORM_PAGES` **摘掉 `'/quotas/create'`**；`:143-145` 以它为「纯表单页」例子的注释**原文保留 + 带日期 ⚠️ 订正**（FR-017）
- [ ] T020 复跑门禁：`i18n:check`（**2962** 两侧相等、路由仍 58 / 清单 56）、`ui:check`（文件 271 / `Form.Item` 298 / 冻结 54 / `R2` 55）、`zh:check`（文件 268 / 命中与台账不变）、`menu:check`（56）、`perms:check`（68）、`typecheck`、`lint`、`build`

## 阶段 C 行为层用例（提交 3）

- [ ] T021 新建 `frontend/src/pages/quotas/QuotaListPage.form.test.tsx`（命名照 088 的 `<Page>.form.test.tsx` 先例）
- [ ] T022 **单测 1 弹窗契约**：点「新建」⇒ 弹窗开、标题键 `pages.quotaCreate.title` 在场、`cancelText` 吃到**默认值** `common.button.cancel`、宽度 = **`lg` 档 800**（读 `.ant-modal` 内联 `style`，照 `CustomerListPage.form.test.tsx:132`，**不用** `getComputedStyle`）
- [ ] T023 **单测 2 校验拦截**：必填留空点保存 ⇒ **字段级错误出现** **且** `quotaApi.create` **未被调用**（**只断「没调用」是假绿**）
- [ ] T024 **单测 3 payload**：填全提交 ⇒ `create` 收到的 `SalesQuotaRequest` 含 `year` 默认值、`amount`，且 `periodStart`/`periodEnd` 是 **`YYYY-MM-DD`**
- [ ] T025 **单测 4 两条刷新分开断言**：成功后弹窗关闭 + **`quotaApi.list` 再被调一次**（表格重拉）+ **`quotaApi.getSummary` 调用次数 +1**（KPI 重取）
- [ ] T026 **单测 5 失败保开**：`create` 拒绝 ⇒ 弹窗**仍开着**（不许静默关掉）
- [ ] T027 遵守三条既有坑：断言**键名**（`src/test/setup.ts` 把 `t(key)` mock 成键名）、取值类断言用 `waitFor`（`destroyOnClose` 下字段是开弹窗时才挂载）、必要时用文件内 `it(name, fn, 60_000)` 放宽耐心（**不动断言**）

## 阶段 D 实测读数、勾选与数字收口（提交 4 = 交付）

- [ ] T028 定向破坏 **D1–D7** 逐条做、逐条**观测到转红/（D5）观测到仍绿**，逐条还原；结果填入 `falsification-evidence.md` §A–§G
- [ ] T029 九道门禁 + `test:coverage` **实跑**，读数填入 §H（含覆盖率四行、测试文件/用例数、Duration；Branch 若差 0.01 **单列一节**写明原因未查明）
- [ ] T030 覆盖率读数的**四舍五入**与阈值比对（**不与上次的小数位比**、**不下调阈值**）
- [ ] T031 **只读冒烟**（§H 附 1）：弹窗开合 / 6 字段 / 校验 / Esc；⚠️ **不点提交**（不写库），并写明 8081 的后端是**本会话自己起的**
- [ ] T032 **e2e**：单跑 `module-page-auth.spec.ts`；没跑就**如实写明未跑**并给理由（**不跑整套**）
- [ ] T033 删除的可核判据（`quickstart.md` §5 四条命令）实跑并留痕
- [ ] T034 订正不静默自查（`quickstart.md` §6 五条命令）实跑，命中数填入 §I
- [ ] T035 `PROJECT_FEATURES.md`：**i18n 资源行**按实跑改写（键 **2962**、行数实测）；**Spec 模块行** 97 → **98（001–099，缺 069）**
- [ ] T036 `PROJECT_FEATURES.md` 的**配额段**（记述「新增 `QuotaCreatePage` 与 `/quotas/create` 路由」）：**原文逐字保留 + 带日期 ⚠️ 订正**
- [ ] T037 `specs/083-engineering-consolidation/quickstart.md` 的必访路由清单：**原文保留 + 带日期 ⚠️**（该路由已移除；改访 `/quotas` 并点「新建」）
- [ ] T038 `specs/README.md` / `specs/roadmap.md` 的状态列改交付态（**勾选在交付时**）；`roadmap.md` 的 099 行补【交付后记】
- [ ] T039 `tasks.md` 全部勾选（**交付时才勾**）+「实做订正」小节（如实记录与本计划的偏差）
- [ ] T040 数字落点一致自查（**逐处点名，不写「若干处」**）：**i18n 键数** = `pnpm i18n:check` 的实跑打印 = `PROJECT_FEATURES.md:19` = `specs/README.md` 099 行（若该行写了键数）；**Spec 模块数** = `ls -d specs/[0-9]* | wc -l` = `README.md:163` = `PROJECT_FEATURES.md:20` = `specs/roadmap.md` 第 6 行的编号面判据；**用例/测试文件数** = `pnpm test:coverage` 的实跑打印 = `PROJECT_FEATURES.md` 的前端单测行

---

## 交付块（**交付时填**，不得预填）

| 项 | 值 |
|---|---|
| 提交数 / 末条提交主题 | 〔交付时填〕 |
| `i18n:check` | 〔交付时填〕 |
| `menu:check` / `perms:check` | 〔交付时填〕 |
| `ui:check` | 〔交付时填：文件数 / `Form.Item` / 冻结台账 / `R2` 候选〕 |
| `zh:check` | 〔交付时填〕 |
| 覆盖率（statements / branch / functions / lines） | 〔交付时填〕 |
| 测试文件数 / 用例数 · Duration | 〔交付时填〕 |
| `typecheck` / `lint` / `build` | 〔交付时填〕 |
| 单测（本项新增） | 5 组全绿 |
| 定向破坏 D1–D7 | 〔交付时填：逐条转红/仍绿（D5）〕 |
| 只读冒烟 | 〔交付时填〕 |
| e2e | 〔交付时填，或如实写明「未跑」〕 |
| ⚠️ 本项**不把提交自己的哈希**写进任何被它携带的文件 | 遵守 |
