# Feature Specification: 088 交付后三笔账的收口（列数护栏、零候选诊断、`span="filled"` 作废）

**Feature Branch**: `094-088-debt-closeout`

**Created**: 2026-09-15

**Status**: Draft

**Input**: User description: "088 收口小批：① 作废 `span=\"filled\"` 收口并订正记录；③ 补做 3 处 FR-015 违规并订正 FR-015 措辞；② 改进 `check-ui.mjs` 零候选失败的诊断信息。"

---

## 背景：这一项为什么存在

**088 于 2026-09-15 交付**（用户「签字」，见 `specs/088-frontend-layout-consistency/spec.md` 的 SC-005 验收记录）。
交付不等于没有尾巴。088 自己的记录里留着**三笔性质不同的账**，用户 2026-09-15 对前两笔逐笔裁决、第三笔交我判断。
本项就是这三笔的收口——**它不改 088 的结论，只清算 088 自己写下的欠账**。

| # | 账 | 出处 | 今天的状态 | 本项做什么 |
|---|---|---|---|---|
| ① | 「全宽项改 `span="filled"`」被记成收口办法 | `plan.md`、`tasks.md`、`spec.md` 共 6 处 | **该办法不成立**：`span="filled"` 在 JSX children 写法下被 antd 的类型定义堵死（实测 `TS2322`） | **作废 + 订正记录**（不改代码），并写明三条真实路径 |
| ③ | FR-015 的存量违规 | `tasks.md` 的 T044 遗留项 2（原报 **5** 处） | **实测缩为 3 处真违规**（另有 2 处是设计决定/已合规） | **补做 3 处**并**订正 FR-015 的过宽措辞** |
| ② | `MIN_CANDIDATES.R3 = 1` 的零余量 | `spec.md` SC-005 记录（三）-③ 把它记成「零余量」隐患 | 该「隐患」的定性**不准确**（见下），但它暴露出的**失败信息两义性**是真的 | **只改失败信息与注释**（零行为改动） |

### ② 的定性订正：不是「假红」，是「诊断信息错」

我此前把 `MIN_CANDIDATES.R3 = 1` 描述为**假红风险**。**这个定性是错的，就地订正**：

- **红色本身是可辩护的治理，不是缺陷**。T045/R2 的先例已经写明这套机制的立场（`check-ui.mjs` 文件头）：
  **一条再也判不到任何对象的规则，已经不再是一条护栏**，它该被**退役**而不是被放行。
  「候选点为 0 就红」正当地表达了这件事。
- **真正错的是那句话**：`check-ui.mjs` 的零候选失败信息只指向**一种**成因（「请先确认该规则的正则/扫描器没有失效」），
  而实测的候选池解剖是：**`<Col` 全库 88 处、其中在 `<Form>` 区域内仅 1 处**（`TagListPage.tsx`，且它带 `flex`、本就合规），
  **`<FormGrid` 用法 58 处**。也就是说这一条规则的候选池**在机制上就被 FormGrid 取代殆尽**，
  它离「无可判对象」只差那一个 `Col` 被重构掉。那时红色会打印「怀疑扫描器」——**指错方向**。
- ⇒ 本条**不改** `MIN_CANDIDATES` 的数值（改了才是放行），只让失败信息**把两种成因分开**，
  并给出各自的处置（修扫描器 / 按先例退役规则）。

---

## 用户故事与验收

### 故事一：窄屏下详情区块的列数不再是被写死的数字

详情区块（`Descriptions`）的列数在窄屏上该随视口分档。088 的 FR-015 立了这条规矩并改了 4 个详情页，
但**库里还有 3 处旧写法**（`column={2}` ×2、`column={3}` ×1），它们在 320–375px 屏上仍是写死列数——
即 FR-015 要治的那个**缺陷类**在原地留着。

- **Given** 我签过字的 FR-015（列数不得写死）
- **When** 我按它扫一遍全库的 `Descriptions`
- **Then** 剩下的 3 处真违规被改成断点对象，全库再没有写死的**多列** `column`

**验收**：3 处改完；`check-ui.mjs` 新增的 R8 规则报「命中 0 处」；
两处**不是违规**的落点（`PersonalCenterPage` 的 `{ xs: 1, sm: 2 }` ×2、`CustomerPortalPage` 的 `column={1}`）**不动**，
且理由写进规格（见 FR-094-003）。

### 故事二：护栏失败时，它说的是**哪一种**失败

R3 的候选池只剩 1 个对象。它哪天归零，门禁会红——**红得对**，但信息会说「怀疑扫描器失效」，
而真正的成因可能是「这条规则已经无可判对象，该退役了」。两种成因的处置完全相反，
错误的那一条会把人送去修一个没坏的扫描器。

- **Given** 一条规则候选点归零（造出来）
- **When** 我跑 `pnpm ui:check`
- **Then** 失败信息**同时给出两种成因与各自的处置**，并带上该规则的**历史实测候选数**供比对

**验收**：把 R8 的候选（`<Descriptions`）用一个探针改到扫不到，失败信息出现二分处置（留痕）。

### 故事三：`span="filled"` 的作废不留悬案

088 的三份文档共 6 处把「全宽项改 `span="filled"`」写成**待办**。它是**做不到的**：
`Descriptions.Item`（children 写法）的 `span` 类型是 `number`，只有 `items` 写法的 `DescriptionsItemType`
才允许 `'filled'`。留着这句话，下一个人会去踩同一个坑。

- **Given** 那 6 处记录
- **When** 我读到「收口办法：改 `span="filled"`」
- **Then** 紧邻处就有 ⚠️ 订正：**该办法不成立**（实测证据）＋**三条真实路径**，**原文一律保留不删**

**验收**：6 处逐处留痕，含实测报错原文与类型定义出处。

---

## 需求

- **FR-094-001 补做 3 处 FR-015 违规**（只动这三行式的东西，逐处给出落点）：
  - `src/components/SignSection.tsx`：`column={2}` → `column={{ xs: 1, sm: 2, md: 3 }}`；全宽项 `span={2}` → `span={3}`
  - `src/components/SurveyBlock.tsx`：同上
  - `src/pages/portal/CustomerPortalPage.tsx` 的服务状态块：`column={3}` → `column={{ xs: 1, sm: 2, md: 3 }}`；
    提交时间项 `span={2}` → `span={3}`
- **FR-094-002 改动效果必须留痕、且不得夸大**：改动前后各量一次**受影响的既有用例的 dev 告警计数**
  与**渲染出的格位结构（`colspan`）**（A/B 对拍，同文件同用例同计数法，形态照 T044 的先例），
  并与「读 antd `hooks/useRow.js` 的 clamp 语义」的推演相互印证。
  **必须把两件事分开陈述**，不得合并成一句「零像素变化」：
  - `span` 由 `{2}` 改 `{3}`：**中性**（实测格位逐格相同），代价是 dev 告警由 0 变 2（每次渲染）；
  - `column` 改写：**这是可见变化**，须逐档写出。
  **不得**把推演写成实测：jsdom 无布局引擎，量到的是 **DOM 结构量、不是几何量**；窄屏两档（xs/sm）仍是推演，
  记录里须显式标注哪一句是读数、哪一句是推演。**不做**渲染级几何实测（那属 092 的家族）。
  **本条为订正条款**：本项初稿曾把结论写成「列数变化只发生在 sm/xs、md 及以上不变」，
  被实测推翻（见 `research.md` §5.3），此处及 SC-094-006 按读数改写。
- **FR-094-003 订正 FR-015 的过宽措辞**（`specs/088-frontend-layout-consistency/spec.md`，**原文保留 + ⚠️ 订正**）：
  - 写出**适用边界**：本条管的是「详情页与详情区块的 `Descriptions` 列数」，不是「全库每一个 `column` 属性」；
  - 写出**两处明确的例外**并给理由：`PersonalCenterPage` 的两处 `{ xs: 1, sm: 2 }` **本来就合规**
    （`md` 及以上由 antd 的 `DEFAULT_COLUMN_MAP` 补齐 = 3，是响应式的），故**不在**存量违规里；
    `CustomerPortalPage` 查询结果面板的 `column={1}` 是**刻意的单列设计**（最窄档，机制上不可能因窄屏溢出），同样**不动**；
  - 记下本次签字之后新产生的**一处在册例外**：R8 的判据是「不得写死**大于 1** 的数字」，`column={1}` 天然不触发。
- **FR-094-004 新增 R8 规则**（`frontend/scripts/check-ui.mjs`）：`Descriptions` 的 `column` **不得写死为大于 1 的数字**，
  必须是断点对象（或不写，由 antd 的默认档自动响应）。**零容忍、无白名单**（`allowed: null`），
  与 R2/R3 同档；候选点定义 = 扫描到的 `<Descriptions>` 开标签数，`MIN_CANDIDATES.R8` 取 **12**（今天实测 14，
  余量 2 处合法删除；同时能抓住「扫描器只认单行开标签」这一档失效——那会掉到 10）。
- **FR-094-005 R8 必须双向自检留痕**：造一处违规（`column={2}` 写进一个真 `Descriptions`）→ `ui:check` 必须报 R8 且退出码非零
  → 逐字节还原 → 转绿。与 088 的 SC-004、092 的 SC-EG-006 同一纪律：**只报「新增规则、全绿」不构成证据**。
- **FR-094-006 零候选失败信息二分**：`check-ui.mjs` 的零候选分支须同时给出
  ①「扫描器失效」与 ②「规则已无可判对象 ⇒ 按 T045/R2 先例退役规则」两种成因与**各自的处置**，
  并打印该规则的**历史实测候选数**；`MIN_CANDIDATES` 表须**逐条**带上实测解剖注释，
  其中 `R3` 一条要写明「88 / 1 / 58」这组数（全库 `<Col`、表单内 `<Col`、`<FormGrid` 用法）
  与「FORMGrid 已取代该规则的对象」这一事实——这里正是「假红」误判的来源。
- **FR-094-007 `span="filled"` 作废留痕**（**只改文档，不改代码**）：088 的 `spec.md` / `plan.md` / `tasks.md`
  共 **6** 处逐处加 ⚠️ 订正，写明①实测报错（`TS2322`：`Type 'string' is not assignable to type 'number'`）、
  ②类型定义出处（`descriptions/Item.d.ts` 的 `span?: number` vs `descriptions/index.d.ts` 的
  `DescriptionsItemType.span?: number | 'filled' | {[key in Breakpoint]?: number}`）、
  ③**三条真实路径**（`items` 写法 / 类型化薄包装 / 保持 `span={3}` 并接受 dev 告警），
  ④本项**不做**其中任何一条（归入「Descriptions 现代化」）。**原文一律保留不删**；
  **不得**以类型断言（`as any` / `@ts-expect-error`）绕过——那是把类型检查关掉，不是收口。
- **FR-094-008 两处登记**：`specs/README.md` 的模块表 + `specs/roadmap.md`（`## 当前进度` 行、整体覆盖度、`**最后更新**`）。
- **FR-094-009 边界**：**不得触碰** `specs/093-dashboard-truthfulness/`（另一会话的未提交在飞工作）；
  **不得写** `.specify/feature.json`（共享单槽指针，现存 `specs/092-geometry-e2e-guard`，改动会打断并行会话）；
  提交**逐路径 `git add`**，禁用 `git add -A`。

---

## 成功标准

- **SC-094-001 门禁**：七道前端门禁全部退出码 0（`typecheck` / `lint` 零 warning / `i18n:check` /
  `menu:check` / `perms:check` / `ui:check` / `test:coverage`），且测试文件数/用例数**不比基线少**（83 文件 / 398 用例）。
- **SC-094-002 护栏可证伪**：R8 的定向破坏留痕（造违规 → 红在 R8 → 逐字节还原 → 绿）；
  `ui:check` 收尾读数为「白名单内冻结的既存债 **53** 处」（R8 零容忍、不进白名单 ⇒ **该数不变**，SC-006 的单调收缩不被扰动）。
- **SC-094-003 诊断可分辨**：零候选的失败信息含两种成因与处置（造一次候选归零留痕）。
- **SC-094-004 静态读数可复算**：`grep -c` 口径下全库 `Descriptions` 开标签 **14**、其中写死**多列**的 **0**、
  `column={1}` 的 **1**（在册例外）。
- **SC-094-005 作废留痕**：6 处 span 记录订正逐处可查，原文未删。
- **SC-094-006 可见变化如实披露**（**此处已按实测订正，见 `research.md` §5.3**）：逐档写出可见变化——
  `SignSection`/`SurveyBlock` 旧写 `column={2}` 是**每档 2 列**，故 xs 由 2 变 1、**md 及以上由 2 变 3**；
  portal 服务状态旧写 `column={3}`，故 xs 由 3 变 1、sm 由 3 变 2、md 及以上**不变**。
  连同新增的 dev 告警读数（0 → 2/次渲染）一并写出。
  **不得**把它读成「只动窄屏」——md 档的列数变化是**桌面宽度下肉眼可见的**。
  **本批必然产生新的可见变化 ⇒ 记为一笔新的 SC-005 欠账**，在用户复看前**不得**读成「已获视觉背书」。

---

## 非目标

- **`Descriptions` 改用 `items` API**（即真正的「现代化」）：它才能表达逐档 `span`，是本项记下的三条真实路径之一，
  但会改动正文渲染与既有用例，**另立一项**。
- **`CustomerPortalPage` 结果面板的 `column={1}`**：设计决定，不动（见 FR-094-003）。
- **渲染级几何用例**：本批只到**源码级**护栏（R8）；几何量属 092 的 Playwright 家族，**不在本项扩张**。
- **`index.css` 的死代码清理**、**082 双因素认证**：088 的既有非目标，本项不接。
- **回填 P3 版式的用例守护**（088 多处记录的遗留）：与本项同族但更大，**不在本批**。

## 关键实体

- **改动 3 个生产文件**（4 个属性）：`components/SignSection.tsx`、`components/SurveyBlock.tsx`、
  `pages/portal/CustomerPortalPage.tsx`。
- **改动 1 个门禁脚本**：`frontend/scripts/check-ui.mjs`（+R8 规则、+R8 的 `MIN_CANDIDATES`、
  改零候选失败信息、给 `MIN_CANDIDATES` 逐条加实测注释）。
- **订正 2 份规格文档**：`specs/088-frontend-layout-consistency/{spec,plan,tasks}.md`（共 6+1 处）。
- **登记 2 处**：`specs/README.md`、`specs/roadmap.md`。
- **本项产物**：`spec.md` / `plan.md` / `tasks.md` / `research.md` / `quickstart.md` / `checklists/requirements.md`。

## 假设

- **antd 的 clamp 语义**：全宽项的 `span` 超出当前行剩余列数时，antd 把该格**截到剩余列数**并（仅 dev）告警——
  读自 `node_modules/antd/es/descriptions/hooks/useRow.js` 的 `getCalcRows`。本项「`span` 改写**自身**不改变格位」的结论**建立在它之上**，
  且已在 FR-094-002 要求下用 A/B 告警计数 + `colspan` 对拍印证；**若对拍读数与推演不符，以读数为准并如实改写记录**。
  ⚠️ 这条**不覆盖 `column` 的改写**——那部分改的是列数本身，是可见变化（已按实测写进 SC-094-006）。
- **`DEFAULT_COLUMN_MAP`**（`antd/es/descriptions/constant.js`）：`xs:1, sm:2, md:3, lg:3, xl:3, xxl:3` ⇒
  `column={{ xs: 1, sm: 2 }}` 在 md 以上是响应式的（不是写死 2）。「已合规」的两处据此判定。
- **候选池全量性**：R8 的 14 个候选来自 `walk(SRC)` 的 tsx（排除 `*.test.tsx` 与 `src/test/`）；
  `scanTagEvents` 是逐字符扫描、按标签名整体匹配（`<Descriptions.Item` 不会被误算）。**这是源码口径，不是渲染口径**。
- **dev 告警只在 `NODE_ENV !== 'production'` 出现**（看 antd 源码的守卫），不影响产物与用例。
