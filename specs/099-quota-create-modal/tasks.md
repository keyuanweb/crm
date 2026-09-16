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

- [x] T001 写 `spec.md` / `plan.md` / `research.md` / `quickstart.md` / `falsification-evidence.md` / `tasks.md` / `checklists/requirements.md`
- [x] T002 `research.md` 必须装齐五件：① 与 `078-sales-quota` 的射程关系；② **R2 对 `<FormModal>` 隐形的机制**（读实现得出，附行号）；③ **逐门禁的删除敏感性**（每条附**判据**，不是「跑一遍没红」）；④ **失去对象的历史基线**清单；⑤ **三条既有空档**登记（权限码 / 子页无入口 / `navigate` 目标无校验）
- [x] T003 `falsification-evidence.md` 的 §0 填入**本项开工前**的五道门禁实跑读数（工区干净时取的）
- [x] T004 `specs/README.md` 模块表加 099 行（**状态列如实写「⏳ 进行中」**，**不预勾**）
- [x] T005 `specs/README.md` 的**编号说明**纳入 099（**按文本锚定位，不按行号**——行号会漂）
- [x] T006 `specs/roadmap.md` 加 099 行（**勾选框留空、不预勾**）；`最后更新` 前置 099 立项条目（旧值降级为「**上一条（原文保留）**」，逐字不改）。⚠️ **实做订正（2026-09-16，提交 1 编写期记录）**：计划原写「`整体覆盖度` 的 `001–098` → **`001–099`**」，**实做改为不改那句的交付态断言**——该句说的是**已交付**的编号面，而 099 **尚未交付**，改成 `001–099` 等于把在办项写成已交付（**假话**）；照 **097 立项时**的同一处置，只前置一段带日期的 ⚠️ 块登记两处**随入列漂移的计数**：**编号面判据** `ls -d specs/[0-9]* | wc -l` **97 → 98**（001–099，**缺 069**）、`## 当前进度` **97 勾 / 0 未勾 → 97 勾 / 1 未勾**（实测复核：`awk` 计勾 97 / 未勾 1）。**旧值逐字保留**（`001–098 全部交付` 仍在句中，可 grep 到）；交付时（T038）再把该句改成 `001–099 全部交付`。
- [x] T007 ⚠️ **实做订正（2026-09-16，提交 1 编写期记录）：本项**不在提交 1 做**，前移到提交 4（交付）** —— `README.md:163` 的 spec 计数 97 → **98（001~099，缺 069）**（**旧值逐字保留 + 带日期 ⚠️**）。**理由**：① 它与 `PROJECT_FEATURES.md:20` 的「Spec 模块」是**同一类对外规模数字**，必须**同批改齐**（只改一处等于用一次订正造出两处新矛盾）；② `PROJECT_FEATURES.md` 的 i18n 行要等 T018 落地后**实测**才能写 ⇒ 该文件整体在提交 4 收口；③ 仓内先例本就是**交付时**改这处（098 的 `63ea0e7` 即其交付提交）。⇒ **提交 1 只登记「本批自己的状态」**（`specs/README.md` 模块表 + `specs/roadmap.md` 头部与 099 行），**不动对外规模数字**。

## 阶段 B 弹窗落地与旧实现删除（提交 2 —— 本项的结构性交付）

- [x] T008 `QuotaListPage.tsx` 加 `FormModal`（`size="lg"`）承载创建表单：**6 字段 / 3 条 `required` / 控件范围 / `initialValues` 逐字搬运**（FR-002）
- [x] T009 `title` = `pages.quotaCreate.title`；`okText` **显式传** `pages.quotaCreate.btnCreate`；取消键**不传**（吃原语默认 `common.button.cancel`）（FR-003）
- [x] T010 `<FormGrid minItemWidth={VERTICAL_MIN_ITEM_WIDTH}>` **保留**、**`maxCols={3}` 删掉**；`layout="vertical"` 保留（FR-004，理由见 `FormGrid.tsx:63-65`）
- [x] T011 提交通路：`validateFields` → 组 payload（`periodStart/periodEnd` 走 `format('YYYY-MM-DD')`）→ `quotaApi.create` → `message.success` → 关弹窗 → `actionRef.current?.reload()` → `setRefreshToken(n => n + 1)`（FR-006/FR-007）
- [x] T012 KPI 重取通路：新增 `refreshToken` state 并**加进既有 `useEffect` 的依赖数组**（**不引入第二套取数路径**）（FR-008）
- [x] T013 失败路径：`catch` 里 `message.error` + **弹窗保持打开** + **不 rethrow**（附代码注释写明理由：`FormModal.handleOk` 是 `try/finally` 无 `catch`）（FR-009）
- [x] T014 消息 API 用 `App.useApp()`（本仓 69 个文件的主流写法；测试侧 `renderWithProviders` 已包 `<AntApp>`）（FR-010）
- [x] T015 工具栏「新建」按钮改 `setModalOpen(true)`，**不再 `navigate`**（按钮文案与图标不变）（FR-005）
- [x] T016 **删** `frontend/src/pages/quotas/QuotaCreatePage.tsx`（FR-011）
- [x] T017 **删** `App.tsx` 里该页的 `lazy` import 与 `<Route path="quotas/create" ...>`（FR-012）
- [x] T018 i18n：**复用** `pages.quotaCreate.*` 的 16 键（值一字不改）；**同批删死键 `btnBack`**（`zh-CN.ts` 与 `en.ts` **两侧都删**）（FR-014/FR-015）
- [x] T019 e2e `module-page-auth.spec.ts`：`FORM_PAGES` **摘掉 `'/quotas/create'`**；`:143-145` 以它为「纯表单页」例子的注释**原文保留 + 带日期 ⚠️ 订正**（FR-017）
- [x] T020 复跑门禁：`i18n:check`（**2962** 两侧相等、路由仍 58 / 清单 56）、`ui:check`（文件 271 / `Form.Item` 303（⚠️ **实做订正 2026-09-16**：立项期推算写的 `304 → 298` **有误**，提交 2 后实跑为 **303**；理由见 `falsification-evidence.md` §0） / 冻结 54 / `R2` 55）、`zh:check`（文件 268 / 命中与台账不变）、`menu:check`（56）、`perms:check`（68）、`typecheck`、`lint`、`build`

## 阶段 C 行为层用例（提交 3）

- [x] T021 新建 `frontend/src/pages/quotas/QuotaListPage.form.test.tsx`（命名照 088 的 `<Page>.form.test.tsx` 先例）
- [x] T022 **单测 1 弹窗契约**：点「新建」⇒ 弹窗开、标题键 `pages.quotaCreate.title` 在场、`cancelText` 吃到**默认值** `common.button.cancel`、宽度 = **`lg` 档 800**（读 `.ant-modal` 内联 `style`，照 `CustomerListPage.form.test.tsx:132`，**不用** `getComputedStyle`）
- [x] T023 **单测 2 校验拦截**：必填留空点保存 ⇒ **字段级错误出现** **且** `quotaApi.create` **未被调用**（**只断「没调用」是假绿**）
- [x] T024 **单测 3 payload**：填全提交 ⇒ `create` 收到的 `SalesQuotaRequest` 含 `year` 默认值、`amount`，且 `periodStart`/`periodEnd` 是 **`YYYY-MM-DD`**
- [x] T025 **单测 4 两条刷新分开断言**：成功后弹窗关闭 + **`quotaApi.list` 再被调一次**（表格重拉）+ **`quotaApi.getSummary` 调用次数 +1**（KPI 重取）
- [x] T026 **单测 5 失败保开**：`create` 拒绝 ⇒ 弹窗**仍开着**（不许静默关掉）
- [x] T027 遵守三条既有坑：断言**键名**（`src/test/setup.ts` 把 `t(key)` mock 成键名）、取值类断言用 `waitFor`（`destroyOnClose` 下字段是开弹窗时才挂载）、必要时用文件内 `it(name, fn, 60_000)` 放宽耐心（**不动断言**）

## 阶段 D 实测读数、勾选与数字收口（提交 4 = 交付）

- [x] T028 定向破坏 **D1–D7** 逐条做、逐条**观测到转红/（D5）观测到仍绿**，逐条还原；结果填入 `falsification-evidence.md` §A–§G
- [x] T029 九道门禁 + `test:coverage` **实跑**，读数填入 §H（含覆盖率四行、测试文件/用例数、Duration；Branch 若差 0.01 **单列一节**写明原因未查明）
- [x] T030 覆盖率读数的**四舍五入**与阈值比对（**不与上次的小数位比**、**不下调阈值**）
- [x] T031 **只读冒烟**（§H 附 1）：弹窗开合 / 6 字段 / 校验 / Esc；⚠️ **不点提交**（不写库），并写明 8081 的后端是**本会话自己起的**
- [x] T032 **e2e**：单跑 `module-page-auth.spec.ts`；没跑就**如实写明未跑**并给理由（**不跑整套**）
- [x] T033 删除的可核判据（`quickstart.md` §5 四条命令）实跑并留痕
- [x] T034 订正不静默自查（`quickstart.md` §6 五条命令）实跑，命中数填入 §I
- [x] T035 `PROJECT_FEATURES.md`：**i18n 资源行**按实跑改写（键 **2962**、行数实测）；**Spec 模块行** 97 → **98（001–099，缺 069）**
- [x] T036 `PROJECT_FEATURES.md` 的**配额段**（记述「新增 `QuotaCreatePage` 与 `/quotas/create` 路由」）：**原文逐字保留 + 带日期 ⚠️ 订正**
- [x] T037 `specs/083-engineering-consolidation/quickstart.md` 的必访路由清单：**原文保留 + 带日期 ⚠️**（该路由已移除；改访 `/quotas` 并点「新建」）
- [x] T038 `specs/README.md` / `specs/roadmap.md` 的状态列改交付态（**勾选在交付时**）；`roadmap.md` 的 099 行补【交付后记】
- [x] T039 `tasks.md` 全部勾选（**交付时才勾**）+「实做订正」小节（如实记录与本计划的偏差）
- [x] T040 数字落点一致自查（**逐处点名，不写「若干处」**）：**i18n 键数** = `pnpm i18n:check` 的实跑打印 = `PROJECT_FEATURES.md:19` = `specs/README.md` 099 行（若该行写了键数）；**Spec 模块数** = `ls -d specs/[0-9]* | wc -l` = `README.md:163` = `PROJECT_FEATURES.md:20` = `specs/roadmap.md` 第 6 行的编号面判据；**用例/测试文件数** = `pnpm test:coverage` 的实跑打印 = `PROJECT_FEATURES.md` 的前端单测行

---

## 实做订正（**如实记录与本计划的偏差**，交付时填）

1. **T006 的「交付态断言」不改**（提交 1 编写期即记录）：计划原写把 `roadmap.md` 第 6 行的 `001–098` 改成
   `001–099`，实做**只前置一段带日期的 ⚠️ 块**、**不动那句交付态断言**——099 当时尚未交付，改了就是把在办项
   写成已交付。**照 097 立项时的同一处置**；交付时（T038）才改。**旧值逐字保留可 grep**。
2. **T007 由提交 1 前移到提交 4**（理由见 T007 条目本身）：同类对外规模数字必须同批改齐、且 `PROJECT_FEATURES.md`
   的 i18n 行要等 T018 落地后实测 ⇒ 该文件整体在交付提交收口。**两条与已批准计划的偏差都写在这里，不是事后补记。**
3. **`ui:check` 的 `Form.Item` 实测 303，立项期推算的 `304 → 298` 有误**：删掉那页有 **7** 个 `<Form.Item>` **开标签**
   （6 个字段 + **1 个包按钮的**），而弹窗里只有 6 个 ⇒ `304 − 7 + 6 = 303`。已定点订正 **6 处工件**
   （`plan.md` / `research.md` ×2 / `quickstart.md` / `tasks.md` 的 T020 / `falsification-evidence.md` §0 /
   `checklists/requirements.md`），**旧值 `304 → 298` 保留可 grep**。
4. **D5 的破坏形态改过两次**（细节见 `falsification-evidence.md` §E）：① 第一次把 `openCreate` 整体换成
   `navigate(...)` ⇒ `setModalOpen` 成未使用符号 ⇒ `TS6133` ⇒ `typecheck`/`build` 红——**那是未使用符号导致的红，
   不是「门禁看得见路由字符串」**；② 第二次只把 `navigate(...)` 插到函数开头、仍保留 `setModalOpen(true)`
   ⇒ **八道门禁与 5 条单测全绿**（弹窗照样开）⇒ 会得出「看起来没红 = 没有空档」的**假结论**。
   最终成立的形态：`openCreate` 的函数体**只做** `navigate('/quotas/create')`、`setModalOpen` 仍被 `onCancel`
   与成功路径使用 ⇒ 八道门禁 **全 exit 0** 而 5 条单测 **全红**。
5. **D6 的破坏换了对象**：原设计是「只从 `en.ts` 删 `btnBack`」，但 `btnBack` **在提交 2 里已经删掉了**
   ⇒ 该破坏**无从做起**。改用**同一命名空间、且仍在被使用**的 `msgCreateFailed`（只从 `en.ts` 单侧删），
   目的与形态完全相同，实跑 `i18n:check` **exit 1 并指名缺键**。
6. **D2 的破坏比计划多两处 `?.`**：不加的话，`periodRange` 为 `undefined` 时会在组装 payload 处抛 `TypeError`，
   `create` 依旧不被调用 ⇒「没调用」那半**假绿**。加了才看得到真症状（**校验不再拦**）。
   ⚠️ 与计划措辞还有一处出入：计划写「期望 `create` 被调用」，实测红在**同一用例里更前面的**那条正向断言上
   （`msgAmountRequired` 不再出现），后面的 `not.toHaveBeenCalled()` **没被执行到**。
7. **只读冒烟的三处措辞订正**（`quickstart.md` §2 已同步、**原文保留 + 带日期 ⚠️**）：
   ① 按钮文案是**「创建配额」**不是「新建」；② 金额标签是**「配额金额（万元）」**；
   ③ 必填留空只有 **2** 条字段级错误（金额、期间），**年份不报错**（有 `initialValues`）。
   ⇒ 这三处是**立项期凭简写写下、跑一次真浏览器就露**的错，**不是**实现偏差。
   ⚠️ **同日补记**：本批**自己做订正的那两处**（`roadmap.md` 的【099 交付】段、`PROJECT_FEATURES.md` 配额段 ⚠️ 块）
   **自身也曾沿用「新建」简写** —— 即**订正块自己带着被订正的那个错**，且 `roadmap.md` 同一行后文已写「主按钮均
   「创建配额」」，**同行自相矛盾**。这类「订正块的措辞未经复核」不会被任何 grep 判据抓到（订正块出现的旧值是
   有意保留的，判「零命中」查不出来）⇒ 已在**同一次提交内**改为「创建配额」（未另起第 5 次提交，故「四次提交」
   这句仍然成立）。**复核订正块自身的措辞，要单独过一次。**
8. **`quickstart.md` §6 的第 2 条 grep 模式写法不精确**（`97（001–098` 少了「个」）：实测两种写法**各命中一处**、
   都是本项有意保留的旧值，**判据成立**，但命令写法须记明（见 `falsification-evidence.md` §I 的口径自证 ②）。
9. **覆盖率「改动前基线」这一次没有单独采集**（本项是纯前端页面改造，开工前没单独跑过一次）
   ⇒ 只声称「四项均高于阈值且阈值未改」，**不声称涨幅**（`falsification-evidence.md` §0 末）。
10. **`e2e/` 下没有为本项留下任何常驻用例**：只读冒烟的临时探针**已删除**（复核 `ls e2e/*.spec.ts | wc -l` = **7**），
    `module-page-auth.spec.ts` 只是**被摘掉一条**。⇒「弹窗能开、字段在、校验拦得住」的**常驻**证据
    只有那 5 条单测。

---

## 交付块（**交付时填**，不得预填）

| 项 | 值 |
|---|---|
| 提交数 / 末条提交主题 | **4 次**（① `docs(099): 立项` ② `refactor(099): 配额创建改为列表页内 FormModal 弹窗` ③ `test(099): 配额创建弹窗的行为层用例` ④ **`docs(099): 实测读数、勾选与数字收口`** = 交付提交） |
| `i18n:check` | `✓ zh-CN 2962 键 / en 2962 键；路由 58 条 / 清单 56 项`，**exit 0** |
| `menu:check` / `perms:check` | `✓ 56 个菜单项` / `✓ 68 个权限码；8 个文件 9 处`，**均 exit 0** |
| `ui:check` | `扫描 271 个产品文件（其中 125 个 tsx）、303 个 Form.Item`；**冻结台账 54（未增长）**；`R2` 候选 **55**；**exit 0** |
| `zh:check` | `扫描 268 个产品文件、候选点 9150 个`；`未登记命中 0 处；台账内冻结 266 处、4 条`；口径外 55；**exit 0** |
| 覆盖率（statements / branch / functions / lines） | **72.13 / 75.36 / 39.54 / 72.13**（阈值 33.6 / 47.2 / 21.4，**未改**，四项**均高于**） |
| 测试文件数 / 用例数 · Duration | **92 / 469**（098 交付时 91 / 464 ⇒ **+1 文件 / +5 用例**，与本项对得上）· **192.27s** |
| `typecheck` / `lint` / `build` | **全 exit 0**（`build`: `✓ built in 15.27s`）；⚠️ `build` **不证明**按钮指向的路由存在（D5） |
| 单测（本项新增） | **5 组全绿**（`src/pages/quotas/QuotaListPage.form.test.tsx`） |
| 定向破坏 D1–D7 | **D1 红**（`expected '640px' to be '800px'`）· **D2 红**（`msgAmountRequired` 不出现）· **D3 红**（`list` 1≠2）· **D4 红**（失败点 **`:195`** = KPI，`:191` 表格那半**绿**）· **D5 预期仍绿**：八道门禁 + `build` 全 exit 0 而 **5 条单测全红**（`Unable to find role="dialog"`）· **D6 红**（exit 1，指名缺 `pages.quotaCreate.msgCreateFailed`）· **D7 红**（exit 1，R2 第 228 行 + 建议改用 `FormModal`）。**七条逐条还原**，两文件哈希 == HEAD |
| 只读冒烟 | 宽 **800px** / 标题与主按钮「创建配额」/ 6 字段全在 / 默认年 2026 / 金额与期间两条必填提示出现（年份不报错）/ Esc 后关闭且仍在 `/quotas` / **业务写请求 0 条**。⚠️ 临时探针**已删**，**不是**常驻护栏 |
| e2e | `module-page-auth.spec.ts` **单跑 13 passed (44.1s)、exit 0**；**其余 6 个 spec 未跑** |
| 删除的可核判据（`quickstart.md` §5） | ①a 活引用**零命中**（✓）· ①b 注释内提及 **5 处 / 2 文件**（非判据，已逐条确认落在注释里）· ② 旧文件**已删除**（✓）· ③ 列表页只剩 `navigate('/quotas/comparison')` 一条 · ④ 死键 `btnBack` **已两侧同删**（✓） |
| 订正不静默自查（`quickstart.md` §6） | **五条均非零**：`roadmap 2 / README 1 / specs/README 0（本就无此串）`、`PROJECT_FEATURES:93` + `README:176`、`083/quickstart 2`、`PROJECT_FEATURES 4`、`PROJECT_FEATURES 3` |
| ⚠️ 本项**不把提交自己的哈希**写进任何被它携带的文件 | 遵守（只引用**提交主题/序号**） |
