# Implementation Plan: 067/068 真缺口收口（部门页 UX 八条 + 使用地图警示用例）

**Branch**: `095-ux-gap-closeout` | **Date**: 2026-09-15 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/095-ux-gap-closeout/spec.md`

## Summary

把 2026-09-15 登记清扫盘出的真缺口做完：`068` 部门管理页 **8 条实现**（关键字高亮 / 300ms 防抖 / 空状态 / 删除风险提示 / 详情组件化 / 断点 Modal-Drawer / ARIA / 3 个既有测试任务落为 2 个文件）+ `067` **1 条测试补齐**（状态流转红色警示），并把 **6 处登记错误**就地订正（`068` 的 `FR-003` 三处 + T033/T034、`067` 的 T001、`086` 的注释）。

**纯前端**：**零后端改动、无迁移、无契约变更、无新数据实体**。三只新文件（`Highlight.tsx` 提取、`useDebouncedValue.ts` 新增、`DepartmentDetail.tsx` 提取）+ 两个新测试文件。

## Technical Context

**Language/Version**: TypeScript 5.x，React 18.2.0

**Primary Dependencies**: Ant Design **5.22.0**（锁定；`destroyOnClose` 是本版正确拼写）、Vite 5.4.21、`@antv/g6`（使用地图，本项只读其配置）

**Testing**: Vitest + @testing-library/react（jsdom，**无布局引擎**）；`src/test/setup.ts` 提供 `matchMedia`/`ResizeObserver` 桩、`react-i18next` mock（**缺键抛错**）、`renderWithProviders`

**Target Platform**: Web 浏览器

**Project Type**: 前后端分离的 Web 应用；本项只动前端

**Constraints**:
- 门禁七道：`typecheck` / `lint` / `i18n:check` / `menu:check` / `perms:check` / `ui:check` / `test:coverage`
- `check-ui.mjs` 的四条硬约束：**R1** 源码不得出现 5 个品牌色字面量；**R6** 裸 `placeholder`/`aria-label` 字面量命中（白名单是**冻结台账**，不得增）；**R7** `components/**` 下无人引用的组件＝孤儿（barrel 再导出算引用）；**R8** `Descriptions` 的 `column` 不得写死为 >1 的字面量
- i18n 双语**双向**比对 + 缺键抛错 ⇒ 新键必须同一次提交进 `zh-CN.ts` 与 `en.ts`
- 覆盖率阈值 statements/lines **33.6** / branches **47.2** / functions **21.4**，**本项不下调**

**Scale/Scope**: 1 个页面 + 1 个组件 + 1 个 hook + 1 个共用件 + 3 个测试文件；`068` 的 8 条 + `067` 的 1 条

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

### 一、契约优先的 API 设计（不可协商）

- [x] **无端点变更**：本项不新增、不修改任何 REST 端点，也不改前端 API 客户端（**唯一涉及取数的地方是复用既有 `GET /departments/tree`**）。
- [x] 故不产出 `contracts/` 变更，也不存在「契约被静默修改」的风险。**DR-095-001**（见下）记此取舍。

### 二、分层架构与关注点分离

- [x] 前端保持表现层纯净：`DepartmentDetail` 是**纯展示组件**（props 只有 `department`），不取数、不含业务规则。
- [x] 数据访问仍**只经 `services/departmentService.ts`** —— 本项**不新增 service 函数**（故 `perm.test.tsx` 的 mock 无需同步改）。
- [x] **无业务规则内嵌进 JSX**：删除风险提示里出现的子部门数/成员数是**后端已算好的值**（`childCount`/`memberCount`）的**呈现**，不是前端自己推断的规则；「有子部门或成员时删除会被拒绝」这条规则的执行点仍**只在后端**（`DEPARTMENT_HAS_CHILDREN_OR_MEMBERS`），前端文案只是**告知**。

### 三、数据完整性、安全与校验（不可协商）

- [x] 本项**不动授权**：不拆权限码、不改 `canManage`（`PERMS.departmentManage`）的判据。**086 已明文锁定**本页读端点挂 `department:manage`，拆码会牵动 `perm.test.tsx` 的三条断言 —— 明确列为非目标。
- [x] **不以隐藏 UI 充当访问控制**：本项新增的删除风险提示**不替代**后端的删除拦截，两者并存。
- [x] 无密码/密钥/事务涉及。

### 四、测试优先与质量门禁（不可协商）

- [x] 新增两处测试文件（`DepartmentListPage.test.tsx` / `DepartmentDetail.test.tsx`）与一处用例追加（`UsageMapPage.test.tsx`）。
- [x] **章程说「测试先于实现」（红→绿）**：本项遵守的方式是 **FR-095-011 的定向破坏留痕** —— 每条护栏都被**观测到在破坏下转红**、再还原转绿。⚠️ **如实说明边界**：本项的实际编写顺序是**先实现、后补用例**（沿用 087/088/092 的既有做法），因此**不得**据此声称走过「spec-first / 测试先行」；留痕证明的是**护栏有牙齿**，不是**红先出现**。
- [x] **测试金字塔**：本项全部是单元/组件层用例，**不新增 e2e**（几何与真实排版的 e2e 属 092）。
- [x] 七道门禁为合并关卡；覆盖率阈值不下调。

### 五、简洁、可维护与可观测

- [x] **YAGNI 是本项的主判据之一**：三处「不补做」的裁决（`067` 的 T001 逐节点样式值、`068` 的 T033/T034 详情两态、T028 的前端 `console.log`）**全部援引本条** —— 补出去的东西**没有消费者**（无读取方的样式字段、无网络窗口的加载态、无人看的控制台日志）。
- [x] **禁止投机性抽象**：`Highlight` 是**提取既有实现**（搜索页已在用），不是新造抽象；`useDebouncedValue` 是仓内**首个**防抖件，但它是三个具体需求的**最小**满足物（T015 的字面要求），且带单测。
- [x] **「列表端点必须分页」与本项的 `FR-003` 订正不冲突**：该条针对的是**可分页的列表端点**；部门树走的是**树形全量端点** `GET /departments/tree`（单次返回整棵树，`068` 的 Assumptions 已限定「全量加载、1000 个部门以内」），不属该条射程。**这一条须写进 `068` 的订正块**（否则订正看起来像是在绕开章程）。
- [x] 结构化日志/可观测性：前端无服务端日志义务，本项不新增日志（**尤其是刻意不加 `console.log`**）。

**Gate 结论**：五项原则**无违规、无需 `Complexity Tracking`**。

## 结构决策

**不产出 `data-model.md` 与 `contracts/`**：本项**不新增也不修改任何数据实体**（无新表、无新字段）、**不改任何 REST 端点**（全部复用既有 `GET /departments/tree`）。为凑齐工件而生成空壳文件，正是「为了流程而流程」——与 092 的先例一致（该批同样无 data-model）。本项的全部「实体」是 `frontend/src/types/department.ts` 里**已存在**的 `Department`（本项**不改它一行**）。

**三只新件的落位**（每一处都有反例在案）：

| 新件 | 落位 | 为什么不是别处 |
|---|---|---|
| `Highlight.tsx` | `frontend/src/components/ui/` | **提取既有实现**（`pages/search/SearchResultPage.tsx` 的页内私有件）。077 的教训：另起第二个平行 UI 目录会让「设计系统」退化成两套各覆盖一半的库（`components/ui/index.ts` 的注释记着这件事）。R7 靠 **barrel 再导出**满足。 |
| `useDebouncedValue.ts` | `frontend/src/hooks/` | 仓内**七个 hook 的既有目录**，非新建目录。全仓此前**无任何防抖件**（`grep -rn debounce src/` 零命中）⇒ 这是唯一需要**造**的新件。 |
| `DepartmentDetail.tsx` | `frontend/src/components/` 顶层 | 它是**业务组件**（懂 `Department`），不是设计系统原语 —— 放进 `components/ui/` 会稀释 088 那个 barrel 的语义。**必须被页面 import**，否则 R7 判孤儿。 |

**`Highlight` 的落位与 `Descriptions` 的取舍**：详情**维持**现有「逐字段 `<div>` + 标签」形态，**不**改用 `<Descriptions>` —— 后者会立刻撞门禁 **R8**（`column` 写死 >1 的数字即命中），而本项**不属**列数治理范围。

## Project Structure

### Documentation (this feature)

```text
specs/095-ux-gap-closeout/
├── spec.md                    # 本文件的上游
├── plan.md                    # 本文件
├── research.md                # Phase 0：实测依据与取舍（含三处推翻既有登记的复核）
├── quickstart.md              # 验证路径（门禁 + 手工冒烟）
├── falsification-evidence.md  # 定向破坏的逐字留痕（FR-095-011）
├── tasks.md                   # 任务分解
└── checklists/requirements.md # 规格质量自查
```

**不产出** `data-model.md` / `contracts/`（理由见上「结构决策」）。

### Source Code

```text
frontend/src/
├── components/
│   ├── ui/
│   │   ├── Highlight.tsx            # 新增（自 SearchResultPage 提取）
│   │   └── index.ts                 # 改：加 Highlight 出口
│   ├── DepartmentDetail.tsx         # 新增（自 DepartmentListPage 提取）
│   └── DepartmentDetail.test.tsx    # 新增（T035）
├── hooks/
│   └── useDebouncedValue.ts         # 新增（+ 同名 .test.ts）
├── pages/departments/
│   ├── DepartmentListPage.tsx       # 主改动：高亮/防抖/空态/展开态/断点/aria/风险提示
│   ├── DepartmentListPage.test.tsx  # 新增（承载 T018/T024/T029 三条）
│   └── DepartmentListPage.perm.test.tsx  # 不动（本项不新增 service 函数）
├── pages/map/
│   └── UsageMapPage.test.tsx        # 改：追加 T009 的红色警示用例（生产代码不动）
├── pages/search/
│   └── SearchResultPage.tsx         # 改：改从 barrel 引入 Highlight
└── i18n/{zh-CN,en}.ts               # 改：新增键（双语同一次提交）
```

## 分步与提交（7 次提交，各自可回退）

| # | 提交 | 内容 |
|---|---|---|
| 1 | `docs(095): 立项` | 本目录 6 件工件 + `specs/README.md` 与 `specs/roadmap.md` 两处登记 |
| 2 | `feat(095): 提取 Highlight、新增 useDebouncedValue` | 两只共用件 + hook 单测 |
| 3 | `feat(095): 部门树高亮/300ms 防抖/空状态，并修正展开态自相矛盾` | `DepartmentListPage.tsx` 第一组 |
| 4 | `feat(095): 抽 DepartmentDetail、按断点走 Modal/Drawer、aria 与删除风险提示` | `DepartmentListPage.tsx` 第二组 + `DepartmentDetail.tsx` + i18n 新键 |
| 5 | `test(095): 部门页三组单测与详情组件单测` | 两个新测试文件 |
| 6 | `test(095): 补使用地图状态流转红色警示用例（067 T009）` | `UsageMapPage.test.tsx` |
| 7 | `docs(095): 订正与勾选` | 6 处订正 + `tasks.md` 勾选 |

## Complexity Tracking

> **Fill ONLY if Constitution Check has violations that must be justified**

| Violation | Why Needed | Simpler Alternative Rejected Because |
|-----------|------------|-------------------------------------|
| 无 | 无违规 | 无 |
