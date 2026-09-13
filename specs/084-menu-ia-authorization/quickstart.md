# Quickstart: 菜单信息架构与授权可见性收口（084）

**Date**: 2026-09-12 | **Plan**: [plan.md](./plan.md) | **Contract**: [contracts/authorization-semantics.md](./contracts/authorization-semantics.md)

本文件是**验收指南**，不是实施说明。每一节都给出「可运行的命令 / 可观察的动作」与「期望结果」，
其中 A–C 三节是**必须真的跑一遍并留痕**的（对应 FR-N22 与 SC-N06 明确要求的「以实验证明，而非以设计论证代替」）。

前置：JDK 17 + Maven；Node 20+ + pnpm 11.7.0；`frontend/` 与 `backend/` 同处一个检出（护栏需要）。

---

## A. 静态护栏（正向：正常情况下零误报）

```bash
# 后端：三维一致性 + 授权 ⇒ 读码
cd backend && mvn -B -o test -Dtest='MenuRouteAlignmentTest,MenuAccessGrantAlignmentTest'

# 前端：生成物陈旧性 + 既有文案键对齐
cd frontend && pnpm menu:check && pnpm i18n:check
```

**期望**：全部退出码 0，且**不是靠「没读到输入」通过的**。两条护栏都自带防呆断言（后端要求解析到的菜单键 > 50、授予语句数 == 文件里出现的语句数；`menu:check` 要求生成物与重生成结果逐字节一致）。若某条断言因「输入为空」而通过，视为护栏失效，按缺陷处理。

**留痕方向**：把两条命令的输出贴进验收记录（或提交信息）。

## B. 反向验证（FR-N22：人为制造一处不一致，必须变红）

各做一次，**做完立即还原**。三处分别打在三道不同的断言上：

| # | 制造的破坏 | 命令 | 期望报错（关键在「指出具体项」） |
|---|---|---|---|
| B1 | 改 `frontend/src/i18n/zh-CN.ts` 里 `menu.tickets` 的一个字（如「客户服务」→「客户服务x」） | `mvn -B -o test -Dtest=MenuRouteAlignmentTest` | 失败，并**指名** `tickets` 两侧名称不等 |
| B2 | 把 `V85` 第 21 行的授权对象从 `('ADMIN', 'ANALYST')` 改成只含 `'ADMIN'`，即去掉 `ANALYST` 的 `custom_object:read`（菜单项 `custom-objects` 所需的正是这个码；`V75` 给 ANALYST 的是 `custom_object:create/update/delete`，删它**不会**让本断言变红） | `mvn -B -o test -Dtest=MenuAccessGrantAlignmentTest` | 失败，并列出「`ANALYST` / 菜单项 `custom-objects` / 缺码 `custom_object:read` / 该码的依据出处」 |
| B3 | 在 `RoleConstants.MENU_TREE` 里把 `quotas` 挪到另一个分组，**不**重生成 | `cd frontend && pnpm menu:check` | 失败，并打印生成物与重生成结果的差异行 |

**还原**：只还原你自己动过的那一个文件（`git restore <该文件>`）。
**不要**在本仓库执行 `git checkout -- .` 或 `git restore .`——本工作区常有多会话并行，会抹掉他人的在飞改动。

## C. 单一真相源实验（SC-N06：单一真相源到什么程度）

三种情形**结论不同**，都做一遍（逐条做法与实测输出见 `verification.md` §T032，结论表见 `spec.md`）：

**C1 移动（最轻）**：在 `RoleConstants.MENU_TREE` 里把某个项挪到另一个分组（仅此一处手改）

1. `cd frontend && pnpm menu:check` → **期望报红**：生成物陈旧，并逐行打印两侧差异
2. `pnpm menu:gen` → 再跑 `pnpm menu:check` → **期望通过**；生成物里该项已落在新分组
3. `mvn -B -o test -Dtest='MenuRouteAlignmentTest,MenuAccessGrantAlignmentTest'` → **期望全绿**（两侧一致）
4. 结论：**归属与排列只有一处作者**，改一处 + 重生成即两侧正确

**C2 新增**（在 C1 之后继续，或单独做）：在某个分组里加一行

1. `pnpm menu:check` → 报红；`pnpm menu:gen` → 通过
2. `pnpm i18n:check` → **期望报红**，且**指名**新项：清单引用了不存在的 `menu.<新键>`、新项没有对应路由
3. 后端两个测试类 → **按预期报红**，且每条都打出新项的键：测试侧对照表缺项（C3 完整性断言），
   以及三处计数下限（对照表 56、文案键 66、examined 55）需要一并更新——这是刻意的：一个菜单项要"长出来"，
   必须让每一处知道它的人**当场**知道，而不是静默漏过
4. 结论：**新增不止一处**——除权威处外还需补路由、两个语言文件的文案、以及测试侧台账。
   这些描述的是"页面是否存在"与"它叫什么名字"，不是"它属于哪组、排在哪"，故不构成菜单定义的第二个作者

**C3 改名**：改 `MENU_TREE` 里某一项的名称

1. `pnpm menu:check` → 报红；`pnpm menu:gen` → 通过；`pnpm i18n:check` → 通过（键没有变）
2. `mvn -B -o test -Dtest=MenuRouteAlignmentTest` → **期望报红**并指名该项：
   `menuTitlesMatchTheAuthorityVerbally` 要求侧边栏文案逐字等于权威名
3. 结论：**改名是两处**（权威处 + `zh-CN.ts`）。侧边栏渲染的是文案表的值，而 FR-N08–N10 刻意要求两者相等——
   护栏的作用就是让"改了权威处、忘了改文案"当场变红，而不是在界面上留下两个名字


## D. 端到端手工验收（对静态断言的独立校验）

前置：后端与前端起起来（`cd backend && mvn spring-boot:run`、`cd frontend && pnpm dev`），用内置管理员登录。

### D1. 主场景：预置角色的已授菜单真的出现（FR-N01/N02，SC-N01/N02）

1. 在「系统管理 → 用户」里建一个（或改一个）用户，角色设为 `ANALYST`
2. 用该用户登录，观察侧边栏：
   - 「系统管理 / 流程与配置 / 审计与维护」三组中，**已被授权的项**（如「自定义字段」「自定义对象」）出现
   - ~~「数据分析」组中存在「自定义对象」~~ → **「流程配置」组中存在「自定义对象」**
     （【订正，2026-09-13，T021 实测】原句写的「数据分析」组与事实不符：`custom-objects` 按 FR-N18 归入**流程配置**，
     权威处 `MENU_TREE`、生成物 `menuManifest.ts`、浏览器 DOM 三处一致。原文保留以留痕。另注：本文件这一段的组名用的是
     旧称「流程与配置」，显示名以权威处为准＝**「流程配置」**，见 `tasks.md` Phase 4 的口径说明）
   - **未授权的项不出现**；某组内一项都没授权时**整组不渲染**
3. 点开「自定义对象」→ **期望正常加载**（不是 403、不是空白页）
   - 这一步是 `contracts/authorization-semantics.md` §4 那条「`ANALYST` 从一律 403 → 可访问」的独立验收
4. 对该角色**未被授予**的项（如「角色管理」）直接输 URL 访问 → **期望 403**（服务端仍强制，FR-N05）

### D2. 逐个角色核对（SC-N02/SC-N08：10 个预置角色，不止点名的 5 个）

**10 个预置角色**（即迁移里实际建出的那 10 个，SC-N08 已枚举）：`ANALYST`、`FINANCE_ACCOUNTANT`、`FINANCE_MANAGER`、`MARKETING_MANAGER`、`MARKETING_SPECIALIST`、`SALES_MANAGER`、`SALES_REP`、`SUPPORT_AGENT`、`SUPPORT_MANAGER`、`VIEWER`

**另加 2 个既有角色作回归项**（不属预置批次）：`ADMIN`（验全量兜底，等价 D3）、`SALES`（验既有可见集合未变）

对上述每个角色各做一次 D1 的第 2 步：

- 侧边栏可见集合 **==** 该角色的菜单授权集合（可在「系统管理 → 角色」的勾选状态上对照）
- 逐一确认无「勾了看不到」，也**无**「没勾却出现」

> 这张清单本身由 `MenuAccessGrantAlignmentTest` 的 C1 通用断言覆盖（它不枚举角色，而是对任意角色断言）。手工这一遍是对它的独立校验，不是替代。核对时以迁移里实际建出的角色名为准——若与上表不一致，以迁移为准并回改本文件。

### D3. 内置管理员兜底（FR-N04）

用 `ADMIN` 登录：侧边栏仍显示**全部**菜单项。撤掉 UI 硬门之后，「系统管理 / 流程与配置 / 审计与维护」三组对 `ADMIN` 的显示不应有任何变化。

### D4. 边界情况抽查（规格「边界情况」小节）

- **缺文案降级**：临时从 `en.ts` 删掉某菜单项的文案，切界面语言为英文 → **期望**显示权威定义里的中文名，**不得**渲染出 `menu.xxx` 这样的键名；测完还原
- **同名冲突**：确认归位后没有出现「组内唯一项与组名同名」导致界面出现两个相同标签的情况
- **借分组显示的子页面**：`/marketing/roi`（渠道 ROI）与 `/workflows/logs`（工作流日志）的可见范围与改造前**一致**，未因归位而扩大或缩小

## E. 回归门禁（不得因本次改动而降低任何既有门槛）

```bash
cd backend && mvn -B -o spotless:check && mvn -B verify   # 先 spotless：它在 verify 里排在 failsafe 与 jacoco 之前
cd frontend && pnpm lint && pnpm typecheck && pnpm i18n:check && pnpm menu:check && pnpm test:coverage
```

**期望**：全绿；覆盖率阈值**不得**下调；`pnpm lint` 0 problems；既有 e2e 用例零失败（SC-N07）。

## F. 本 quickstart **不**覆盖的两件事（避免范围混淆）

- **不**修复 `backend/src/test/resources/schema-h2.sql` 缺失 V70–V77 镜像的存量问题（083 的 B 块 / T072）。本次只镜像新增的 `V85` 授予
- **不**重审权限矩阵内容（规格已排除）；只处理 FR-N06/FR-N07 点名的缺口
