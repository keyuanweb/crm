# Tasks: AI 文本生成（104）

**Input**: Design documents from `/specs/104-ai-content-generation/`

**Prerequisites**: [plan.md](./plan.md)（必需）、[spec.md](./spec.md)（必需）、[research.md](./research.md)、[contracts/ai-content-generation.md](./contracts/ai-content-generation.md)

**Tests**: `AiStatusTest`（新）· `AiContentServiceTest`（新）· `AiPromptCatalogTest`（新）· `AiContentIT`（新）· `AiGenerateButton.test.tsx`（新）

⚠️ **复选框一律不预勾**——本仓规矩：**只在本项交付时**才勾（103 批曾明文"不预勾"，此处兑现）。开工时**不得**回填。

✅ **2026-09-27 C5 交付：本条已兑现**——**已执行的任务此刻（且只在此刻）勾上**：Phase 0（T000–T004）· Phase 1（T010–T014 / T017 / T018）· Phase 2（T020–T022 / T023–T029）· Phase 3（T030–T036，含 D1 / D3）· Phase 4（T040–T046），**共 38 个框**。⚠️ **T015 / T016 两个框故意不勾、交付后仍是未勾**——它们被 C3 裁为**作废**（零授予 ⇒ 无 `V92`），**作废 ≠ 完成**，勾上会读成"做过"（逐条理由在该两行内）。**Phase 5+（T050–T074）的 15 个框一律不勾**：P2/P3/P4 **未交付**。⇒ **交付态的框面 = 36 个一级框 + 2 个 D 子框已勾 / 17 个未勾**（2 个作废 + 15 个未开工）。

⚠️ **不跑任何 `/speckit-*`**（`.specify/feature.json` 是共享单槽指针、gitignored、会被并行会话覆盖）。

---

## Phase 0：开工前置（**先做，不做完不开工**）

⚠️ **2026-09-27 记：T000–T004 已执行，但下方复选框仍不勾**——本文件的规矩是「**只在本项交付时才勾**」（见文首），**已做的事≠交付**。四条读数分别落在 `research.md` 的 **§7.1**（ErrorCode 分布，**首测即订正**）、**§6.1**（门禁基线，零漂移）、**§11**（SDK 版本与方法名，**两条标红的未核实项就此结清**），**不在本文件复制数字**。

> ✅ **2026-09-27 C5 交付：上句「仍不勾」的条件已解除**——交付已发生，故 T000–T004 **五个框此刻全部勾上**（原文逐字保留在上：它描述的是**交付前**的状态，且「既执行又不勾」这条中间态是被本文件明文允许的）。

- [x] T000 **实跑 `ErrCode` 分布**：确认 `ErrorCode` 的 (status) 分布，据以**最终确定三个新码的状态码**——`plan.md` 表里的是**建议值，不得直接采用**（本仓规矩：不沿用历史数字）
- [x] T001 **实测 base URL 的 SDK 写法**：读 SDK 的 client config，或直接写最小文件让**编译器**指出正确方法名（`research.md` §10.1）。**确认后再往下**
- [x] T002 **锁定 SDK 版本**并与 JDK 21 / Spring Boot 版本做兼容性确认（`research.md` §10.2）
- [x] T003 `ListAgents` 确认无并行会话写同一批文件；核对 `.specify/feature.json` 当前指向（**只读，不改**）
- [x] T004 记录**开工基线读数**：`i18n:check` 键数、`ui:check` 冻结债、`zh:check` 台账、迁移数与最高版本、后端/前端测试计数——全部**实跑**，作为交付时的对照基线

---

## Phase 1：基础设施（C2，**无端点、无 UI**）

⚠️ **2026-09-27（C2 实做）改判：本相位只到 T014 + T018**。原计划的 T015 / T016 / T017（权限码三件套）**不在 C2 做，移到 C3 与第一个端点同批**——理由不是排期，是三道守卫**合起来使 C2 阶段的权限码无法存在**：`UnwiredPermissionCodeTest`（冻结台账 22 条，双向）、`RequirePermissionCatalogTest`（注解 ⊆ 字典）、`PermissionMatrixIT` 的 FR-G14 判例（字典里有、注解有、**无任何迁移授予**，零授予是**可执行断言**）⇒ 一个"进了字典但无端点消费"的码，要么让台账变红，要么必须靠迁移去授予它，而授予一个没人校验的码正是 FR-G14 判例禁止的那种补授。**实测已证**（`research.md` §12.3：把 `ai:generate` 加进 `RoleConstants.PERMISSION_DEFS` 后，`UnwiredPermissionCodeTest` 以 `Expecting empty but was: ["ai:generate"]` 变红，探针随即删除、行数归零）。
⚠️ **T010–T014 与 T018 已执行，但下方复选框一律不勾**（同 Phase 0 的规矩：**只在本项交付时才勾**）。阅读位置：`AiStatus` 的四态表与"启动即失败"理由在类 javadoc；`AiClientFactory` 是**实现期新增件**（原结构树里没有）——把客户端构造留在 `AiContentService` 会与 `quickstart.md` §6 那条 `grep apiKey` 判据直接冲突，故拆出一个只此一处持有密钥的类。

> ✅ **2026-09-27 C5 交付：上句「一律不勾」的条件已解除**——交付已发生，故 **T010–T014 / T017 / T018 七个框此刻勾上**；**T015 / T016 仍不勾**（C3 裁为作废，见该两行内的 ✅）。**T017 的第三项（前端注册表）由 T023 落地**（宿主裁决 A 之后），故该框的勾选前提是 T023 也勾——两者同批，本条即兑现。

- [x] T010 [P] `config/AiStatus.java`：唯一判据源；`@Value` 读 `crm.ai.enabled`（默认 `false`）/ `base-url` / `api-key` / `model` / `max-tokens` / `timeout-seconds`；javadoc **自称唯一判据源**（照 `MailInboundStatus`）；含启动期白名单校验（plan D1）
- [x] T011 [P] `common/AiNotConfiguredException.java` + `common/AiGenerationException.java`：**继承 `BusinessException`**，与 `MailInboundNotConfiguredException` 同包同形
- [x] T012 `common/ErrorCode.java`：**只增不改**三个码（状态码按 T000 的实测结果定）
- [x] T013 `service/AiContentService.java`：**唯一出网点**——SDK 客户端持有、超时、`usage` 取数、错误映射（`RateLimitException` → 可重试码）、审计写入（**detail 不含提示词与客户数据**）。**不加 `@Async`**（plan D3）
- [x] T014 `resources/application.yml`：新增 `crm.ai.*` 六项，**默认值与 `@Value` 兜底逐字一致**（FR-002）；`.env.example` 增占位（**空值，不是真密钥**）
  - ✅ **2026-09-27 C4：实为**七**项**——第 7 项 `crm.ai.daily-token-budget`（默认 `100000`）随 T029 的预算闸同批落地。⚠️ "六项"这个计数写在三处（本行、`application.yml` 的注释、`spec.md` FR-001），**三处已同批改为七项**；只改一处就是用一次订正造出两处新矛盾。原文逐字保留。
- [ ] T015 `db/migration/V92__ai_generate_permission.sql`：**仅权限授予**（无 DDL）
  - ⚠️ **2026-09-27 C2：移到 C3，且「授给谁」本身是待裁项**——按本仓权限授予判据③（改造前无粗粒度门、也无菜单承诺 ⇒ **一个都不补**，只接码），本码很可能**一个角色都不授予**；那样就**没有 `V92` 这个文件**（`plan.md` 顶部 ⚠️ ①②）。**在 C3 裁决前不要创建此文件。** ✅ **2026-09-27 C3 已裁：零授予成立 ⇒ 本任务整条作废（文件不创建）**。上句"在 C3 裁决前不要创建此文件"已被遵守（`git status` 里没有 `db/migration/` 任何新文件）。⚠️ **此复选框故意保持未勾**——作废 ≠ 完成，勾上会读成"做过"。权威落点 `research.md` §13.6；零授予的可执行痕迹 = `com.crm.integration.AiPermissionGrantIT#aiGenerateIsGrantedToNoPresetRole`。
- [ ] T016 同步 `schema-h2.sql`（行尾 `-- V92` 标记）+ `SchemaParityIT` 镜像清单；确认 `SchemaIdempotencyIT` 仍可重跑
  - ⚠️ **2026-09-27 C2：随 T015 一起移 C3；若 T015 裁为「无迁移」则本项整条不存在**（原文逐字保留）。当前实测基线：迁移 90 个文件、最高 `V91`（仅 `V72` 缺号），`schema-h2.sql` 有 65 个 `-- V<n>` 标记。 ✅ **2026-09-27 C3：T015 已裁为「无迁移」⇒ 本项整条不存在**，`schema-h2.sql` / `SchemaParityIT` / `SchemaIdempotencyIT` 三处一字未动。上面这组基线读数（**90 / `V91` / 65 个标记**）交付时**原样复用为「未变」的对照**（判据见 `quickstart.md` §6 的 `ls … | wc -l`）。
- [x] T017 `RoleConstants.PERMISSION_DEFS` 增 `ai:generate`；前端 `constants/permissions.ts` 同步
  - ⚠️ **2026-09-27 C2：移到 C3，且三项（字典 + 端点注解 + 前端注册表）必须同批落地**。理由是实测出来的：`FrontendPermissionCodeAlignmentTest` 是**单向**的（前端 ⊆ 字典），字典领先前端**没有任何门禁看得见**（`research.md` §12.3 的预测落空就落在这里）；反过来，字典里的码若无人消费，则 `UnwiredPermissionCodeTest` 立即变红。
  - ⚠️ **2026-09-27 C3：三项里落了**两项**（字典 + 端点注解），第三项（前端注册表）随宿主裁决整批走**（见本相位 T023 上方的 ⚠️）。这**不是**把上面那条"必须同批"的规矩违背了，而是它的**两半成立性不同**：
    - "字典里的码必须被 `@RequirePermission` 真的引用"——**C3 已满足**（端点落地即消费），故 `UnwiredPermissionCodeTest` **绿**（C2 那次实测的 `Expecting empty but was: ["ai:generate"]` 已不再出现）。这条是**自动判据**。
    - "前端注册表必须跟上字典"——**仍然无人看着**（同一道单向护栏），故它是**人工判据**。⇒ 交付相位必须显式核对 `permissions.ts` 里有 `ai:generate`、且 `perms:check` 读数是 **69**（不是 68）。
  - ⚠️ **同批订正一个已作废的前提**：本项原计划带**迁移 `V92`**（仅授权）。按判据③ 裁为**零授予** ⇒ **本项零迁移，`V92` 不存在**（`research.md` §13.6）。`V92` 的存废在 C2 期是"待裁"，现在是**已裁：不做**。
- [x] T018 门禁子集自证：`mvn -B -q compile` + `pnpm run perms:check`
  - ⚠️ **2026-09-27 C2 实测**：两条都过——`mvn -B -q compile` 退出 0；`perms:check` 读数 **68 码 / 8 文件 / 9 处**，与 T004 基线**零漂移**。子集到此为止，**不冒充全量**（全量门禁留到交付相位，见 `research.md` §12.6）。

---

## Phase 2：P1 邮件草稿（C3 —— **建议的首期交付点**）

- [x] T020 `service/AiPromptCatalog.java`：P1 组提示词（system 稳定 / user 含数据）+ **P1 字段白名单**（`contracts/` §5.2 的落地）
  - ⚠️ **2026-09-27 C3 已执行（不勾选，同 Phase 1 的规矩）**。两处与任务描述不同：① 白名单**删掉了 `industry` / `level`**——契约 §5.2 原列这两个字段，而它们在本仓**不存在**（`industry` 全仓零命中；无自定义字段种子），清单里不列取不到的字段（`research.md` §13.4）；② **预算算式订正过一次**：初稿只算"跟进 + 固定字段 ≈ 1.5k"，把 `instruction`（上限 4000、**唯一由调用方直接控制且最大的一段**）漏在算式外 ⇒ 标称上界改为 **≈ 5.3k 字符**（`research.md` §13.2）。**"上界由构造给出"在漏掉最大项时是假命题，且朝"看起来更安全"的方向失真**。
- [x] T021 `controller/AiContentController.java`：`POST /api/v1/ai/email-draft`，**带 `@RequirePermission("ai:generate")` + `@RateLimit`**
  - ⚠️ **2026-09-27 C3 已执行（不勾选）**。限流参数**定稿**：`scope="ai-generate" / limit=10 / windowSeconds=60 / by=USER`（理由见契约 §4 的 C3 块——原任务只写"带 `@RateLimit`"，**没写取值**，等于把"配多少"留给实现随手定）。⚠️ **同一个 ⚠️ 里有一条坑**：本端点是 POST 且权限码不以 `export:` 开头 ⇒ **本来就会落进 `RateLimitCoverageTest` 的具名规则「已认证常规写接口」，一个字都不加注解台账也绿** ⇒ FR-014 的"必须带注解"是**人工判据、不是自动判据**，C4 必须自己断言四个端点都带注解。
- [x] T022 输入校验（`contracts/` §3）：长度/枚举/可达性，**判门在出站之前**
  - ⚠️ **2026-09-27 C3 已执行（不勾选），但裁决与原任务不同**：① 校验在**服务层**（`AiEmailDraftService.validate`）而非 Bean Validation；② 失败状态码是 **400 + 既有 `BAD_REQUEST`**，**不是契约 §3 原表的 422**，且**不新增 `ErrorCode`**（全仓无通用 422 校验码；`@Valid` 那条路在本仓一律 400 且注释明禁扩张）；③ "实体不存在 / 不可见"改走房规 **404 / 403**（原表"不区分"的括号是假前提）。逐条见契约 §3 的两段 ⚠️ 与 `research.md` §13.5。**判门在出站之前**这一条不变：校验与可见性判定都在 `AiContentService.generate` 之前。

- [x] T027 **（C3 实做新增，原任务表没有这一条）** `service/AiEmailDraftService.java`：P1 上下文装配器——取数**只经既有服务方法**（不注入 Mapper）、可见性在读取之前判、102 FLS 在装配时应用、然后调 `AiContentService.generate`
  - 为什么必须单独一件：出网点（`AiContentService`）不认识客户；常量与纯函数件（`AiPromptCatalog`）刻意不认识 Spring/数据库/当前用户。塞进前者会让 `quickstart.md` §6 那条 `grep -rl "apiKey()"` **读数应为 1** 的判据失去区分力（同一个类会同时持有密钥消费方与客户数据装配方两种身份）。**分层是那条判据能成立的前提**。同 C2 的 `AiClientFactory`（`research.md` §13.1）。
- [x] T028 **（C3 实做新增）** `integration/AiPermissionGrantIT.java`：`ai:generate` **零授予**的可执行痕迹（正对照 + 前提 + 断言），照 `PermissionMatrixIT` 的 FR-G14 判例
  - **只做数据面**：`ADMIN` 在 `PermissionAspect` 里恒放行 ⇒ 零授予的后果是"ADMIN 照旧、其余角色一律 403"。**行为面那一半（预置角色真打端点 → 403）归 C4 的 I 系列**，此处刻意不写（C3 处于显式红窗）。
- [x] T029 ⚠️ **（缺口登记：原任务表与 plan 都没有这一条）** FR-015 / FR-017 的**每日预算闸**：Redis `ai:gen:budget:{scope}:{id}:{yyyyMMdd}`（plan D8 已定键形），耗尽 → 429（复用 `RATE_LIMITED`，不新增码）
  - **缺口是查出来的**：`grep -n "预算\|budget\|ai:gen:" tasks.md` → **零命中**（C3 开工前）。plan 只有设计（D8）、C4 有断言（U7 的键形），**中间没有实现任务**。
  - ⚠️ **这导致一个顺序约束**：**U7 断言的是这个键的形状，而它在 C3 未实现** ⇒ 若不在 C4 之前补上实现，U7 就是一条"断言一个不存在的东西"的用例。裁决（补实现 / 改 U7 / 推到后续批次）**须在 C4 开工前定**。
  - 本批未实现的是**成本闸**那一半；已落地的 `@RateLimit` 只管**突发**（契约 §4 的 C3 块写了这个分工）。
  - ✅ **2026-09-27 C4 已裁并已落地（顺序约束解除）**：**先落门、再写 U7**（用户裁定）。落地形状——
    - `service/AiTokenBudget.java`【新】：`check(userId)` 判在**出站之前**、`charge(userId, tokens)` 记在**成功之后**；耗尽抛 `RateLimitExceededException`（429 + `Retry-After` = **到明天零点**的秒数）。
    - 计数原语**不新开一处**：`AiTokenBudget` 只在 `security/RateLimitStore` 之上定义配额语义，后者新增 `usage(key)` / `charge(key, amount, window)` 两个方法——`RateLimitStore` 的 javadoc 里"全仓唯一碰计数原语的地方"那句因此仍然成立（否则它会被第二个计数实现悄悄架空）。
    - 键形按 plan D8 落地为 **`ai:gen:budget:user:{userId}:{yyyyMMdd}`**（`{scope}` 那一段是**身份种类**，照 `RateLimitKeys` 的 `rl:{scope}:user:{id}` 形状；将来加全局闸应新增同族的 `…:global:all:…`，**不改这一段**）。U7 断言的 `ai:gen:budget:*` 与 `≠ ai:ignore:` 都成立。
    - 配置项 **`crm.ai.daily-token-budget`（默认 `100000`，`<=0` = 闸关闭/不限）**：`AiStatus` 第 7 个 `@Value` + `application.yml` + `.env.example` 三处同批（FR-001/FR-002 的"六项"→"七项"同步订正，见该处 ✅）。
    - 两处**已知边界**（写出来而不是等它成为事故）：① **check-then-charge 的窗口**——并发 N 次可各自过检，当日用量最多超出 (N−1) 次调用的成本，N 的上界就是 `@RateLimit` 的 10 次/60 秒；② **fail open**——Redis 不可用时放行（同 `RateLimitStore` 的立场），故成本上界仍应由服务商侧的用量上限兜底。
    - 裁决与算式见 `research.md` §14；验证（U 系列 + I 系列）在 C4 的用例批。
> ⚠️ **2026-09-27 C3：本相位的前端半边（T023–T026）整批未动 —— 不是排期原因，是"接入点根本不存在"**（`research.md` §13.7 有实测证据）。
>
> `plan.md:186` 把 P1 的落点写成"**邮件编辑/活动页**（P1 草稿）"。**实测：本仓前端没有"带客户上下文的邮件编辑器"这个页面。** 唯一的"邮件主题 + 正文"编辑器是 `pages/marketing/EmailTemplatePage.tsx`——**模板 CRUD 弹窗**，字段名 `content`，走 `createEmailTemplate` / `updateEmailTemplate`，**与客户无关**（没有客户选择器，也没有客户上下文）。契约 §2.5 要求的"与 022 的规则式建议在同一页面上做视觉区分"同样没有落点：`playbookService.fetchOpportunityActions` **全仓无引用** ⇒ 022 那条建议当前没有任何页面在渲染它。
>
> ⇒ **须用户裁决宿主**（三种都在改产品可见形态，我不替用户选）：
>
> | 选项 | 形态 | 代价 / 需要改的既定文本 |
> |---|---|---|
> | **A. 客户详情页 + 复制到剪贴板** | `CustomerDetailPage` 上加按钮 → 弹窗展示草稿 → 一键复制 | 需订正 `spec.md` FR-019 的"**插入既有编辑区、经既有保存路径持久化**"——P1 没有既有编辑区可插，草稿不落库（本项**零新增表**，落库是另一项）；"复制"是新的交互形态 |
> | **B. 邮件模板页弹窗** | 在 `EmailTemplatePage` 的弹窗里加生成按钮 | **字面满足 FR-019**，但该表单**客户无关** ⇒ 必须给它加一个客户选择器，即"把模板页改成依赖客户"，影响的是**既有模板 CRUD**；且生成的正文与模板变量混在一起，语义可疑 |
> | **C. 新建一个小页/抽屉** | 独立的"AI 邮件草稿"入口 + 客户选择器 | 最干净，但引入新导航目标 ⇒ `menu:check` / `perms:check` / i18n / `ui:check` **四道门禁全都要动**，且要新授一个菜单（与 FR-020 的"零授予"直接冲突：菜单承诺一旦存在，判据② 就要求按菜单持有者补授）|
>
> **我的建议是 A**：它是唯一不需要改既有页面语义、也不新增菜单/权限面的选项，且"生成 → 复制 → 粘到任何地方"对用户是可解释的；代价集中在一处（订正 FR-019 的持久化那半句）。**但这条建议只在用户认可"P1 不落库"时成立**，故须先问。
>
> ✅ **2026-09-27 用户裁定：选 A（客户详情页 + 复制到剪贴板）**。⇒ 随之生效的三条：
> 1. **FR-019 的"插入既有编辑区、经既有保存路径持久化"订正为"展示 + 复制"**（P1 不落库；「零新增表」那半句不变）。`spec.md` FR-019 处已就地补 ✅。
> 2. **T023–T026 解卡**，可按 A 的形态落地；**不新增导航目标、不新增菜单、不新增权限授予**（⇒ FR-020 的零授予不受影响，判据② 不被激活）。
> 3. **`perms:check` 读数 68 → 69 随本批一起发生**（T023 的 `constants/permissions.ts`），故交付读数里那个 69 有来历。
> ⚠️ 仍**未验证**的是 P2/P3/P4 的接入点是否存在（只有 P1 这一处实测过）；不要以为它们已经被检查过。

- [x] T023 `frontend/src/types/aiContent.ts` + `services/aiContentService.ts`（生成类调用**显式 `timeout: 0`**，不动全局 30s）
  - ⚠️ 随宿主裁决整批走。**另有一处归属提醒**：`constants/permissions.ts` 加 `aiGenerate: 'ai:generate'` 会让 `perms:check` 读数 **68 → 69**；「登记在先、接线在后」在分批交付时是**被明文许可**的中间态（该脚本的 unused 提示是**非失败**项），但**别只加注册表不接页面**——那会让 69 这个数在交付读数里说不清来历。
- [x] T024 `frontend/src/components/AiGenerateButton.tsx`：四态（未配置 / 生成中 / 成功 / 截断）
  - ⚠️ 随宿主裁决整批走。**视觉必须与 022 区分**：022 的既有约定是琥珀金 `#faad14` + `BulbOutlined` + `ai-icon-pulse`，故本项**不得**直接复用那套（否则两个"AI"在界面上无法分辨）；同时新增组件要过 `ui:check` R1–R8（品牌色字面量 / Modal 固定宽 / 裸 `<Col span>` / `required:true` 须带 message+label / 裸 placeholder 与 aria-label / **R7 孤儿组件** / `Descriptions column`）。
- [x] T025 邮件编辑侧接入点 + **i18n 双语键同数新增**（`zh-CN` 与 `en`）
  - ⚠️ **本项是整批被卡住的那一条**（宿主不存在，见上）。键的命名空间尚未定（本仓无 `ai.` 前缀，键按 `pages.<area>.<feature>` 分层）⇒ 键名随宿主一起定，**不要先造键**。
- [x] T026 自证不违反 `ui:check` R1–R8；**冻结台账 54 处不增长**
  - ⚠️ 随宿主裁决整批走。台账是**双向**的（条目过期也红）。

---

## Phase 3：P1 用例与定向破坏（C4）

- [x] T030 [P] `AiStatusTest`：三种构造（U1）——**这是唯一能抓"Java 兜底值被改"的判据**
- [x] T031 [P] `AiContentServiceTest`：U2–U7（未配置零副作用 / 错误映射 / 截断 / usage 取数 / 键形）
- [x] T032 [P] `AiPromptCatalogTest`：U8（白名单不含标识符、含必需字段）
- [x] T033 `AiContentIT`：I1–I6（**I2 与 I3 是最值钱的两条**：字段级权限一致性、跨 owner 不泄漏）
- [x] T034 `AiGenerateButton.test.tsx`：F1–F4
- [x] T035 **定向破坏 D1–D10**：逐条先写"该改变哪条可观察行为"→ 跑 → **观测到转红** → `cp` 回写还原（**禁用 `git checkout`**）→ 探针标记 `留痕后还原` 零命中
  - [x] D3（**复现 022 的错法**：直查裸 Mapper）**必须实测 I3 是否变红**——若不变红，说明 I3 没有判据看着，**逐字记录该证伪结果**
  - [x] D1 **必须实测 I 系列是否照旧全绿**（若绿 ⇒ 实证了假绿通道 2，**逐字记录**）
- [x] T036 写 `falsification-evidence.md`（**实测读数**，不得预先编造）

---

## Phase 4：P1 交付（C5）

- [x] T040 `mvn -B spotless:apply && mvn -B verify`（**不传 `-DargLine`**）；若 spotless 报缓存命中，移走 `target/spotless-index` 复跑到 `skipped 0`
- [x] T041 前端八道 + `build` 全跑
- [x] T042 **落点表全部落笔**：`specs/README.md`（6 列行 + `:3` 版本行 + 编号说明段 + **迁移表加 V92**）、`specs/roadmap.md`（进度行 + 计数 + 债务 blockquote + `:4` 日期）、`README.md`（目录树计数）、`PROJECT_FEATURES.md`（只写真移动的行）、`INSTALL.md`（迁移列表）
  - ⚠️ **2026-09-27 C3 订正：本条里有四处随"零迁移"裁决**（`research.md` §13.6）**而变成"不动"**：`specs/README.md` 的**迁移表不加行**、`INSTALL.md` **不动**、`PROJECT_FEATURES.md` 的 Flyway 计数**不移动**（**90 → 90**，不是 91）、`specs/README.md:3` 版本行与编号说明段**照常动**（那是"新增一个 spec 编号"的行，与本项的迁移数无关）。**"哪些行真移动"必须以交付时的实测为准**，不要照本行的字面清单逐条改——本行是 C1 立项期写的，那时 `V92` 还被认为是必有的。
- [x] T043 ⚠️ **`CRM_FEATURE_COMPARISON.md` 的三条要求**（`plan.md` 落点表）：① 只动"生成式 AI"那一格；② 等权算术平均随 `AI 能力` 行重算并声明取整口径；③ **写明"本项属能力增量、不是订正"**，以免读者误以为有人违反了"判定列与分值一律不动"
  - [ ] §2.10 的「总闸门实测」零命中段落：**原文逐字保留 + 追加带日期 ⚠️ 块**（不是重写）
- [x] T044 `DELIVERY_SCOPE.md`：记本项给部署包新增的外部依赖（出网 + API key + 网关要求）
- [x] T045 `tasks.md` **此刻才勾**；`## 实做订正` 按**三列**填
- [x] T046 `ListAgents` → **逐路径 `git add`**（禁 `git add -A`）→ 提交（`Co-Authored-By: Claude Code <noreply@anthropic.com>`）

---

## Phase 5+：P2 / P3 / P4（C6+，**每组一个独立可交付阶段**）

**每组自成一个可停点**（plan D0）：完成后各跑一次门禁子集（`i18n:check` / `ui:check` / `zh:check` / 定向 vitest + 后端 `-Dtest`）。

### P2 客户 360 摘要
- [ ] T050 `AiPromptCatalog` P2 组 + 白名单（`contracts/` §5.3）
- [ ] T051 `POST /api/v1/ai/customer-summary` 端点（权限 + 限流）
- [ ] T052 客户详情页接入 + i18n 键
- [ ] T053 用例：空数据客户不编造（spec US2-AS2）；**HIDDEN 字段零出现（I2 的能力版）**
- [ ] T054 定向破坏：白名单加入一个 HIDDEN 字段 ⇒ 该红

### P3 跟进记录润色 / 总结
- [ ] T060 `AiPromptCatalog` P3 组 + 白名单（`contracts/` §5.4）
- [ ] T061 `POST /api/v1/ai/followup-polish` 端点（权限 + 限流）
- [ ] T062 跟进表单接入 + i18n 键
- [ ] T063 用例：**关键要素逐项保留**（日期、客户名）；空/超长 ⇒ 422 且**出站 0**
- [ ] T064 定向破坏：让"润色"丢掉日期 ⇒ 该红

### P4 商机下一步建议
- [ ] T070 `AiPromptCatalog` P4 组 + 白名单（`contracts/` §5.5）
- [ ] T071 `POST /api/v1/ai/opportunity-advice` 端点（权限 + 限流）
- [ ] T072 商机详情页接入 + i18n 键 + ⚠️ **与 022 阶段模板建议的视觉区分**（spec US4-AS1）
- [ ] T073 用例：不同阶段得到不同建议；建议语气（非事实断言）
- [ ] T074 定向破坏：让两套建议在同一页面无区分 ⇒ 该红

---

## Dependencies & Execution Order

- **T000–T004（Phase 0）必须先做**：T000/T001 的结论直接决定代码怎么写；跳过就是照着一份未验证的预测写实现。
- T010 → T011/T012/T013 → T014 → T015/T016/T017 → T018
  - ⚠️ **2026-09-27 C2 改判**：实做链条是 **T010 → T011/T012/T013 → T014 → T018**（C2 到 T018 收尾），**T015/T016/T017 移到 C3**，与新链条 **C3 = T015/T016/T017 → T020–T026（首个端点）** 合并同批。原链条「→ T015/T016/T017 → T018」**逐字保留**如上：它写的是"权限码先于门禁自证"，而实做把门禁自证提前、权限码推后——**两条都不是错的顺序，区别在于权限码能不能独立于端点存在**，实测答案是不能（见 Phase 1 的 ⚠️）。
- **Phase 2 依赖 Phase 1 全部**（客户端与配置门是 P1 的前提）
- Phase 3 依赖 Phase 2；T035 **必须**在 T030–T034 全绿之后做（否则破坏打在未绿的用例上，读数无意义）
- Phase 4 依赖 Phase 3；**Phase 5+ 依赖 Phase 4 的门禁通过**
- **P2/P3/P4 之间无相互依赖**，可任意顺序、可任意截断

## Notes

- **本批唯一的迁移是 V92**（仅权限码，无 DDL）；**零新增表、零新增实体**（plan D5/D7）
  - ⚠️ **2026-09-27 C2 改判**：本句**以「存在 `V92`」为前提**，而该前提**尚未成立**——按判据③ 本项很可能**一个角色都不授予**，那样**本批迁移数为 0**（连"唯一"的那个也没有）。原句逐字保留如上。**零新增表、零新增实体**这半句不受影响，仍然成立。 ✅ **2026-09-27 C3 已裁：那个"尚未成立"的前提**被判为永远不成立** ⇒ 本句两半都作废，正确表述是**本批零迁移**。原句逐字保留如上；权威落点 `research.md` §13.6**
- **零 `@Async`、零流式、零 markdown 渲染**（plan D3 + spec 非目标）
- 中文断言须显式 `StandardCharsets.UTF_8`（本仓有 ISO-8859-1 假红先例）；能断 `error.code` 优先断 code
- IT **不得**加 `@Transactional` / `@TestMethodOrder`
- 前端八道门禁 + **冻结债 54 处 / 4 条 266 处** 不得增长

---

## 实做订正

> 交付时填写。**三列**：原计划 / 实做 / 理由。原文逐字保留，订正**不静默**。
>
> ⚠️ **2026-09-27 C2：本表仍按上句「交付时填写」，暂不预填**（C2 不是交付）。但 C2 期间已发生的改判**不得悬空**，故就地记在**改判发生的那一段**，交付时逐条搬进本表。当前待搬的**六**条（C2 三条 + C3 三条）：
> 1. **C2 的相位边界**：原计划 Phase 1 含 T015/T016/T017（权限码三件套）⇒ 实做 C2 只到 T014 + T018，权限码三件套移 C3 与首个端点同批。理由：三道守卫使"无端点消费的权限码"无法存在（详见 Phase 1 的 ⚠️ 与 `research.md` §12.3）。
> 2. **`AiClientFactory` 是实现期新增件**：原结构树无此类 ⇒ 新增一个只此一处持有 api-key 的 `@Component`。理由：密钥构造若留在 `AiContentService`，`quickstart.md` §6 的 `grep apiKey` 判据会连带命中服务本身，判据失去分辨力。
> 3. **`V92` 的存废**：原计划"本批唯一的迁移是 V92" ⇒ 待 C3 按判据③ 裁；若零授予则**无迁移**，连带 `schema-h2.sql` / 迁移表 / `INSTALL.md` / Flyway 计数四处不动（详见 `plan.md` 顶部 ⚠️ ①②）。 ✅ **2026-09-27 C3 已裁：零授予 ⇒ 无迁移**（T015/T016 已就地标为"作废"，复选框**故意不勾**）。
> 4. **`AiEmailDraftService` 是实现期新增件**（C3）：原结构树 P1 侧只有 `AiContentService` ⇒ 新增一个**装配客户数据**的服务类，与**唯一出网点**分开。理由：`quickstart.md` §6 的肯定式判据要求"取用密钥的文件数 = 1"，若装配与取密钥同居一类，那条判据虽然仍读 1，含义却退化成"这一类既送数据又拿密钥"，安全面收窄不了。
> 5. **`AiPermissionGrantIT` 是实现期新增件**（C3）：零授予这个裁决此前只活在注释与文档里，没有任何可执行痕迹——而 `PermissionMatrixIT` 的 FR-G14 javadoc 明写"零授予**必须**留下一条可执行的痕迹"。
> 6. **FR-015/FR-017 的每日预算门在计划里没有任务**（C3 测出）：`grep -n "预算\|budget" tasks.md` 只命中 FR 引用、没有实现任务；而 C4 的 U7 断言其 Redis 键形状 ⇒ **必须在 C4 之前裁**（已新增 T029 占位，含该排序约束）。

> ✅ **2026-09-27 C5 交付：上段六条已逐条搬进下表第 1–6 行**（上段**原文保留、一字未改**；下表另增 C4/C5 期间新发生的第 **7–12** 行）。

| # | 原计划 | 实做 | 理由 |
|---|---|---|---|
| 1 | Phase 1（C2）含 T015/T016/T017 权限码三件套 | C2 只到 T014 + T018，权限码三件套移到 C3 与首个端点同批 | 三道守卫使「无端点消费的权限码」无法存在（Phase 1 的 ⚠️ 与 `research.md` §12.3） |
| 2 | 结构树里没有 `AiClientFactory` | 新增一个**只此一处**持有 api-key 的 `@Component` | 密钥构造若留在 `AiContentService`，`quickstart.md` §6 的 `grep apiKey` 判据会连带命中服务本身 ⇒ 判据失去分辨力 |
| 3 | 「本批唯一的迁移是 `V92`」 | **零迁移**：`V92` 不存在，`schema-h2.sql` / 迁移表 / `INSTALL.md` / Flyway 计数四处**都不动** | C3 按判据③ 裁为 `ai:generate` **零授予** ⇒ 授权迁移**无事可做**（`research.md` §13.6；T015/T016 已就地标「作废」、复选框**故意不勾**） |
| 4 | 结构树 P1 侧只有 `AiContentService` | 新增 `AiEmailDraftService`（装配客户数据），与**唯一出网点**分开 | 「取用密钥的文件数 = 1」这条肯定式判据若让装配与取密钥同居一类，读数仍是 1、含义却退化成「这一类既送数据又拿密钥」 |
| 5 | 零授予只活在注释与文档里 | 新增 `AiPermissionGrantIT`（可执行痕迹） | `PermissionMatrixIT` 的 FR-G14 javadoc 明写「零授予**必须**留下一条可执行的痕迹」 |
| 6 | `tasks.md` 里没有预算门的实现任务 | 新增 T029 占位（含「必须在 C4 之前裁」的排序约束） | `grep -n "预算\|budget" tasks.md` 当时只命中 FR 引用；而 C4 的 U7 断言其 Redis 键形状 ⇒ 不先落门，U7 无法写 |
| 7 | `plan.md` 把 I5 的见证指为 `verifyNoInteractions`（零出站） | **把该断言挪到状态码断言之前** | 原顺序下它**不可能开口**——状态码断言排在它前面就先红了（D5 实测；改后 D5-b 才如期变红） |
| 8 | 4 处文档写着 SC-004「以 grep 断言」 | **该命令从未登记过**；C4 现定、C5 登记进 `quickstart.md` §6，并**补第二条锚在落点上的判据**（`auditDetail` 形参表 = 4 个标量、无 String 载荷位） | 一条 0 命中的否定判据单独存在时**自证不了**；且 `record(...)` 是调用者、detail 的构造者在 `auditDetail` ⇒ 口径须锚在落点 |
| 9 | `AiPromptCatalog.toneLabel` 不在改动清单里 | **改为 `switch(tone == null ? DEFAULT_TONE : tone)`**（本批**唯一**的 main 代码改动） | 用例反查出：`tone` 为 null 时抛 NPE，且该字段是本类**唯一**对缺值不宽容的一处 ⇒ 会以 500 落在一个 AI 端点上 |
| 10 | T041 记「前端八道 + `build` 全跑」，即默认预期九道**全绿** | **八道 + `build` 绿；第九道分两个口径**：`test:coverage` 在**仓库默认口径（A）下未建立绿灯**（13 文件/21 用例失败，全是超时），在**诊断口径（D，2 worker）下全量全绿**（96 文件/487 用例、退出码 0）⇒ **两个口径都写出来，不许只留一个** | 见 §交付块 §1.3 的四态读数与四层归因。本仓 `vite.config.ts:87-92` 自己立的判据是「**机器空闲时默认池既不假红也不飘**、4 worker 只是机器被外部抢占时的诊断手段」⇒ **A 态那次红仍需空转机器复核，本次没有**（全程 CPU 90–100%，含一个跑飞的 VS Code `rg.exe`）；而 **D 态把"套件本身无红"这个读数独立建立起来了**（降并发即全绿，墙钟 7.4 倍） |
| 11 | `PROJECT_FEATURES.md` 的「前端页面组件」行按「非测试 tsx」理解 | 该行的口径是 **`frontend/src/pages` 限定**（`find frontend/src/pages -name '*.tsx' ! -name '*.test.tsx'`） | 起草第八次重测时**踩过一次**：按全 `src/` 口径会读成 127，凭空造出 27 的假缺口（本仓「grep 模式的边界要自证」的又一例） |
| 12 | 立项期预期 104 会像 102 那样**移动迁移数**（并同步 6 处落点） | 104 **零迁移** ⇒ 与 **103 同形制**，迁移数那一组落点**一处都不动** | 第 3 行的连带结论；立项期那句「与 103 形制不同，不得套模板」**已被证伪**（`specs/README.md` 与 `specs/roadmap.md` 均已加带日期 ⚠️） |

---

## 交付块（C5 落笔；**本表是本批读数的权威住处**，别处只留指针）

> ⚠️ **一句话口径**：下面每一格都取自 **那一次**运行，不沿用任何历史数字；**引用时不得脱离产生它的那次运行**。
> 与 103 的差别：103 的读数里**没有**前端全量 vitest（103 门禁是那五道），**104 有**——**它的绿灯只在诊断口径（2 worker）下建立、在仓库默认口径下未建立**，两个口径都如实记在 §1.3。

### §1.1 后端门禁（T040）

- **命令**：`cd backend && mvn -B spotless:apply && mvn -B verify`（**未传 `-DargLine`**——传了会静默废掉 JaCoCo）。
- **结果**：退出码 **0** / **BUILD SUCCESS** / 总耗时 **03:14 min**。
- **用例**：surefire **828 例**、failsafe **362 例**，Failures · Errors · Skipped **全 0**。
- **覆盖率门禁**：`jacoco:check` 打印结论行 **「All coverage checks have been met.」**（报告 **273 个类**）。
- **覆盖率读数**：INSTRUCTION **82.3625%**（51 255/62 231）· BRANCH **64.7293%**（3 408/5 265）· LINE **83.8143%**（11 589/13 827）· METHOD **86.6303%**（1 905/2 199）。
- **`jacoco.exec`**：**147 887 781 字节 / mtime 2026-09-27T15:28:23**（此后**未再跑过任何 Maven**，交付时复核仍逐字相同）。
- **spotless**：verify 相位那次的输出是**增量缓存命中**形式（「N were skipped because caching determined…」）⇒ **不足以证明真解析过**；C4 已单独把 `target/spotless-index` 移走复跑取到真读数（**829 clean / 1 changed / 0 skipped**，`check` 为 **0 needs changes**），逐字记在 `falsification-evidence.md`。**本块不重复那个数字**。
- **构建卫生**：跑前已核（a）WSL 里 MySQL 有 keepalive、（b）无进程占用胖 jar、（c）`grep "Unresolved compilation"` **0 命中**（IDE 的「error class」先例）。

### §1.2 前端八道中的七道 + `build`（T041，**全部退出码 0**）

> ⚠️ **第八道（`test:coverage`）不在本表**——它有两个口径、见 §1.3；**本表是"已建立绿灯"的那八项**，不要把它读成"前端只有八道"。

| 门禁 | 读数 |
|---|---|
| `typecheck` | 通过（无输出） |
| `lint` | 通过（无输出） |
| `i18n:check` | **2984/2984 键**（路由 **58** / 清单 **56** / 别名 **3**）——**开工基线 2966/2966，本项 +18**，与 `PROJECT_FEATURES.md` 第八次重测的「键 2966 → 2984」**两处独立读数一致** |
| `menu:check` | **56**（P1 **不新增菜单**） |
| `perms:check` | **69 个权限码**（**68 → 69**，+`ai:generate`）——与立项期预测**逐字一致**；⚠️ **这是「登记了码」，不是「授予了角色」**：本条与 C3 的**零授予**裁决**不矛盾**（`AiPermissionGrantIT` 是零授予的可执行痕迹） |
| `ui:check` | **274** 个产品文件 / **126** tsx / **303** 个 `Form.Item`；白名单内冻结既存债 **54** 处 **未新增** |
| `zh:check` | **271** 个产品文件 / 候选点 **9206**；台账内冻结 **266** 处 + 4 条；**未登记命中 0** |
| `build` | 退出码 **0**，`✓ built in 22.39s`（`vite build` 的 chunk 体积告警为**既存**，非本批引入） |

### §1.3 ⚠️ `test:coverage`：**仓库默认口径（A）未建立绿灯；诊断口径（D）全绿**——两个读数必须一起读

**四态读数**（前三态的日志 `/tmp/fe-cov.log` · `/tmp/fe-cov2.log` · `/tmp/fe-cov3.log`；**第四态是本节末段补跑的**，日志 `/tmp/fe-cov4.log`）：

| 态 | 命令 | 结果 |
|---|---|---|
| **A 仓库口径** | `npm run test:coverage`（默认池） | 退出码 **1**：**13 文件失败 / 83 通过（96）**、**21 用例失败 / 466 通过（487）**，墙钟 **444.81s** |
| **B 本仓记的诊断口径** | `npx vitest run --coverage --minWorkers=1 --maxWorkers=4` | 退出码 **1**：**4 文件失败 / 92 通过（96）**、**4 用例失败 / 483 通过（487）** |
| **C 把那 4 个失败文件单独跑** | `npx vitest run --minWorkers=1 --maxWorkers=2 <4 文件>` | 退出码 **0**：**4 文件 / 14 用例全过** |
| **D 全量降到 2 worker** | `npx vitest run --coverage --minWorkers=1 --maxWorkers=2` | 退出码 **0**：**96 文件 / 487 用例全过**，墙钟 **1080.15s**（`15:50:22` 起，18.0 min），**无一条 `testTimeout`** |

> ⚠️ **D 态是在本表写完之后才跑出来的，它改变了结论**——故本节的标题与末段已按它订正；**A / B / C 三态原样保留**（它们是同一台机器上的真实读数，删掉就等于把"默认口径红了"这件事藏起来）。**D 态的并发设置与 A 态只差 worker 数**（`--minWorkers=1 --maxWorkers=2`，其余同 `vite.config.ts` 的默认），**降并发即全绿**，这是"红来自资源抢占"最直接的一次对照。⚠️ **但 D 态不是仓库口径**：`vite.config.ts:87-92` 自己记的诊断手段是 **4 worker**（B 态），**2 worker 这一档是本次会话新增的**，且代价是墙钟 **7.4 倍**（1080.15s 对空转基线 141–146s）⇒ **不得把它读成"默认口径也是绿的"**。

**归因（分三层，逐层给判据）**：

1. **A 态的 21 条失败全部是超时**：逐条读到的错误是 `Error: Test timed out in 40000ms.` 与 `in 20000ms.`——**不是断言失败**；墙钟 **444.81s** 对 `vite.config.ts:87-92` 记的空转基线 **141–146s** 是 **3 倍**。
2. **失败文件与 104 无关**：21 条分布在 13 个文件（`App.render` · `LoginPage.mfa` · `PersonalCenterPage(.mfa)` · `ChangePasswordPage` · `CustomerListPage(.form)` · `ProductListPage.form` · `DepartmentListPage` · `QuotaListPage.form` · `AnnouncementPage.perm` · `LandingPageListPage.perm` · `OpenPlatformPage.perm`），**104 触碰的文件一个都不在其中**；104 自己的 `AiGenerateButton.test.tsx` 在 A 态 **6 passed**。
3. **C 态是决定性的**：B 态与 A 态**唯一交集性质**是「机器被抢占时资源不够」——把 B 的 4 个失败文件**单独跑就全过**。⇒ 红**来自资源抢占**。
4. **环境证据**：全程 CPU **90–100%**；其中 `rg.exe`（**VS Code 自己的文件索引**，`--files --hidden --follow`，**09:40:18 启动**，至 16:2x 累计约 **237 000 CPU 秒**）是一个**跑飞的**进程，另有 **15 个** codegraph node 进程与 `QQLive` / `TMPThumbHD` 等桌面程序。

⚠️ **结论的口径必须写死（四态合起来才是完整读数，只引其中一态都会被读偏）**：

- **套件本身没有红**：**D 态给出了这个读数**——全量 96 文件 / 487 用例全过、退出码 0、**一条 `testTimeout` 都没有**。这是本批**唯一**同时满足「全量」与「绿」的读数。
- **仓库默认口径（A）仍然红**，**B 也红**；两态的红**全部是超时**、**没有一个落在 104 触碰的文件里** ⇒ **不能说「本仓库的 `test:coverage` 门禁在本次交付中通过了」**。
- **C 与 D 的关系**：C 只证明「那 4 条红不是 104 引入的回归」；**D 更进一步**——它把**整个**套件在低并发下跑通，⇒ 「红来自资源抢占」这个归因**由 D 态独立成立**，不必再等"一台空转机器"才敢下判。
- ⚠️ **但 D 是诊断口径、不是仓库口径**：`vite.config.ts:87-92` 立的判据是「**机器空闲时默认池既不假红也不飘**」，而本次机器**始终不空闲**（全程 CPU 90–100%）。**A 态那次红因此仍未被"空转机器"复核过**。

⇒ **T041 如实记为「八道门禁 + `build` 绿；`test:coverage` 在诊断口径（2 worker）下全绿、在仓库默认口径下未建立」**——**两个口径都要写出来**，**不许只留一个**（只留 A 会读成"套件有病"，只留 D 会读成"门禁通过"，两种都是失真）；C6+ 开工前仍应在一台空转机器上复跑 A 态。

### §1.4 本批新增用例读数（**取自那一次 verify 的 surefire / failsafe 报告文件**，不是从别处抄的）

| 类 | 条数 | 报告 |
|---|---|---|
| `AiStatusTest` | **8** | surefire |
| `AiPromptCatalogTest` | **9** | surefire |
| `AiContentServiceTest` | **15** | surefire |
| `AiTokenBudgetTest` | **11** | surefire |
| **后端单测小计** | **43** | 四份报告 `Tests run` 之和，Failures·Errors·Skipped **全 0** |
| `AiContentIT` | **6** | failsafe |
| `AiContentUnconfiguredIT` | **1** | failsafe |
| `AiPermissionGrantIT` | **2** | failsafe（C3 落地，零授予的可执行痕迹） |
| **IT 小计** | **9** | 三份报告，Failures·Errors·Skipped **全 0** |

- 前端：`AiGenerateButton.test.tsx` **6 passed**（F1–F4；在 §1.3 的 A 态里也是 6 passed）。
- ⚠️ **与 C4 提交信息的差异（如实记，不静默）**：那个提交信息写的是「后端用例（**45 单测 + 7 IT** 全绿）」并附「`AiStatusTest` / `AiPromptCatalogTest` / `AiContentServiceTest` / `AiTokenBudgetTest`（**8 / 15 / 9+11 / 2**）」——**四个类名配五个数，位置整体错了一位**：按报告文件的真值是 **8 / 9 / 15 / 11**（合计 **43**，不是 45）。**IT 的 7 与 9 不是矛盾而是口径不同**：C4 的「7」只数 C4 新增（6+1），本表的「9」把 **C3 落地的 `AiPermissionGrantIT`（2 条）**一并计入。**提交信息不可改，故此处为本批的权威读数**；这正是「一个数字住在好几个地方」那条纪律的又一例。
- ⚠️ **报告文件的归属**：上表七份报告的 mtime 全部落在 **15:22–15:26**（那一次 verify 的运行窗口内）⇒ 可以归因。⚠️ 但 `target/surefire-reports/com.crm.integration.AiPermissionGrantIT.txt`（**15:22**）是**早一次定向运行**留下的**陈旧副本**（它是 IT，真报告在 failsafe）⇒ **读 surefire 目录时必须按类名认口径，别把 IT 的陈旧 surefire 报告当成本次读数**。

### §1.5 定向破坏（C4，读数在 `falsification-evidence.md`）

- **D1–D10 逐条实测、逐条还原**；**D1–D9 观测到转红，D10 有判据**（后端 U/F 与 IT 各有读数）。
- **三处与计划预测不符**，逐字记录在 `falsification-evidence.md`：**D2**（预判 I1 红 ⇒ 实际 I1 仍绿，判门有两道，须 D2-b 补刀）、**D3**（预判 I3 红 ⇒ 单刀不红，真正的见证在 `FollowUpService.page` 里 063 的复查）、**D6/D9 的靶面比预测宽**（D6：2 后端 + 1 前端；D9：3 单测 + 2 IT）。
- **D5 带出一处判据排列缺陷**（已改：先断出站计数、再断状态码）⇒ 实做订正第 7 行。
- **D7 带出「SC-004 的 grep 命令从未登记」**⇒ 现定并登记进 `quickstart.md` §6，实做订正第 8 行。
- **还原自查**：六个被破坏文件 **md5 全部回到破坏前值**（`cp` 备份回写，**不用 `git checkout`**——本批有 8 个文件是刻意未提交的）；`grep "留痕后还原"` = **0**。
