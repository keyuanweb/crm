# Tasks: AI 文本生成（104）

**Input**: Design documents from `/specs/104-ai-content-generation/`

**Prerequisites**: [plan.md](./plan.md)（必需）、[spec.md](./spec.md)（必需）、[research.md](./research.md)、[contracts/ai-content-generation.md](./contracts/ai-content-generation.md)

**Tests**: `AiStatusTest`（新）· `AiContentServiceTest`（新）· `AiPromptCatalogTest`（新）· `AiContentIT`（新）· `AiGenerateButton.test.tsx`（新）

⚠️ **复选框一律不预勾**——本仓规矩：**只在本项交付时**才勾（103 批曾明文"不预勾"，此处兑现）。开工时**不得**回填。

⚠️ **不跑任何 `/speckit-*`**（`.specify/feature.json` 是共享单槽指针、gitignored、会被并行会话覆盖）。

---

## Phase 0：开工前置（**先做，不做完不开工**）

⚠️ **2026-09-27 记：T000–T004 已执行，但下方复选框仍不勾**——本文件的规矩是「**只在本项交付时才勾**」（见文首），**已做的事≠交付**。四条读数分别落在 `research.md` 的 **§7.1**（ErrorCode 分布，**首测即订正**）、**§6.1**（门禁基线，零漂移）、**§11**（SDK 版本与方法名，**两条标红的未核实项就此结清**），**不在本文件复制数字**。

- [ ] T000 **实跑 `ErrCode` 分布**：确认 `ErrorCode` 的 (status) 分布，据以**最终确定三个新码的状态码**——`plan.md` 表里的是**建议值，不得直接采用**（本仓规矩：不沿用历史数字）
- [ ] T001 **实测 base URL 的 SDK 写法**：读 SDK 的 client config，或直接写最小文件让**编译器**指出正确方法名（`research.md` §10.1）。**确认后再往下**
- [ ] T002 **锁定 SDK 版本**并与 JDK 21 / Spring Boot 版本做兼容性确认（`research.md` §10.2）
- [ ] T003 `ListAgents` 确认无并行会话写同一批文件；核对 `.specify/feature.json` 当前指向（**只读，不改**）
- [ ] T004 记录**开工基线读数**：`i18n:check` 键数、`ui:check` 冻结债、`zh:check` 台账、迁移数与最高版本、后端/前端测试计数——全部**实跑**，作为交付时的对照基线

---

## Phase 1：基础设施（C2，**无端点、无 UI**）

- [ ] T010 [P] `config/AiStatus.java`：唯一判据源；`@Value` 读 `crm.ai.enabled`（默认 `false`）/ `base-url` / `api-key` / `model` / `max-tokens` / `timeout-seconds`；javadoc **自称唯一判据源**（照 `MailInboundStatus`）；含启动期白名单校验（plan D1）
- [ ] T011 [P] `common/AiNotConfiguredException.java` + `common/AiGenerationException.java`：**继承 `BusinessException`**，与 `MailInboundNotConfiguredException` 同包同形
- [ ] T012 `common/ErrorCode.java`：**只增不改**三个码（状态码按 T000 的实测结果定）
- [ ] T013 `service/AiContentService.java`：**唯一出网点**——SDK 客户端持有、超时、`usage` 取数、错误映射（`RateLimitException` → 可重试码）、审计写入（**detail 不含提示词与客户数据**）。**不加 `@Async`**（plan D3）
- [ ] T014 `resources/application.yml`：新增 `crm.ai.*` 六项，**默认值与 `@Value` 兜底逐字一致**（FR-002）；`.env.example` 增占位（**空值，不是真密钥**）
- [ ] T015 `db/migration/V92__ai_generate_permission.sql`：**仅权限授予**（无 DDL）
- [ ] T016 同步 `schema-h2.sql`（行尾 `-- V92` 标记）+ `SchemaParityIT` 镜像清单；确认 `SchemaIdempotencyIT` 仍可重跑
- [ ] T017 `RoleConstants.PERMISSION_DEFS` 增 `ai:generate`；前端 `constants/permissions.ts` 同步
- [ ] T018 门禁子集自证：`mvn -B -q compile` + `pnpm run perms:check`

---

## Phase 2：P1 邮件草稿（C3 —— **建议的首期交付点**）

- [ ] T020 `service/AiPromptCatalog.java`：P1 组提示词（system 稳定 / user 含数据）+ **P1 字段白名单**（`contracts/` §5.2 的落地）
- [ ] T021 `controller/AiContentController.java`：`POST /api/v1/ai/email-draft`，**带 `@RequirePermission("ai:generate")` + `@RateLimit`**
- [ ] T022 输入校验（`contracts/` §3）：长度/枚举/可达性，**判门在出站之前**
- [ ] T023 `frontend/src/types/aiContent.ts` + `services/aiContentService.ts`（生成类调用**显式 `timeout: 0`**，不动全局 30s）
- [ ] T024 `frontend/src/components/AiGenerateButton.tsx`：四态（未配置 / 生成中 / 成功 / 截断）
- [ ] T025 邮件编辑侧接入点 + **i18n 双语键同数新增**（`zh-CN` 与 `en`）
- [ ] T026 自证不违反 `ui:check` R1–R8；**冻结台账 54 处不增长**

---

## Phase 3：P1 用例与定向破坏（C4）

- [ ] T030 [P] `AiStatusTest`：三种构造（U1）——**这是唯一能抓"Java 兜底值被改"的判据**
- [ ] T031 [P] `AiContentServiceTest`：U2–U7（未配置零副作用 / 错误映射 / 截断 / usage 取数 / 键形）
- [ ] T032 [P] `AiPromptCatalogTest`：U8（白名单不含标识符、含必需字段）
- [ ] T033 `AiContentIT`：I1–I6（**I2 与 I3 是最值钱的两条**：字段级权限一致性、跨 owner 不泄漏）
- [ ] T034 `AiGenerateButton.test.tsx`：F1–F4
- [ ] T035 **定向破坏 D1–D10**：逐条先写"该改变哪条可观察行为"→ 跑 → **观测到转红** → `cp` 回写还原（**禁用 `git checkout`**）→ 探针标记 `留痕后还原` 零命中
  - [ ] D3（**复现 022 的错法**：直查裸 Mapper）**必须实测 I3 是否变红**——若不变红，说明 I3 没有判据看着，**逐字记录该证伪结果**
  - [ ] D1 **必须实测 I 系列是否照旧全绿**（若绿 ⇒ 实证了假绿通道 2，**逐字记录**）
- [ ] T036 写 `falsification-evidence.md`（**实测读数**，不得预先编造）

---

## Phase 4：P1 交付（C5）

- [ ] T040 `mvn -B spotless:apply && mvn -B verify`（**不传 `-DargLine`**）；若 spotless 报缓存命中，移走 `target/spotless-index` 复跑到 `skipped 0`
- [ ] T041 前端八道 + `build` 全跑
- [ ] T042 **落点表全部落笔**：`specs/README.md`（6 列行 + `:3` 版本行 + 编号说明段 + **迁移表加 V92**）、`specs/roadmap.md`（进度行 + 计数 + 债务 blockquote + `:4` 日期）、`README.md`（目录树计数）、`PROJECT_FEATURES.md`（只写真移动的行）、`INSTALL.md`（迁移列表）
- [ ] T043 ⚠️ **`CRM_FEATURE_COMPARISON.md` 的三条要求**（`plan.md` 落点表）：① 只动"生成式 AI"那一格；② 等权算术平均随 `AI 能力` 行重算并声明取整口径；③ **写明"本项属能力增量、不是订正"**，以免读者误以为有人违反了"判定列与分值一律不动"
  - [ ] §2.10 的「总闸门实测」零命中段落：**原文逐字保留 + 追加带日期 ⚠️ 块**（不是重写）
- [ ] T044 `DELIVERY_SCOPE.md`：记本项给部署包新增的外部依赖（出网 + API key + 网关要求）
- [ ] T045 `tasks.md` **此刻才勾**；`## 实做订正` 按**三列**填
- [ ] T046 `ListAgents` → **逐路径 `git add`**（禁 `git add -A`）→ 提交（`Co-Authored-By: Claude Code <noreply@anthropic.com>`）

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
- **Phase 2 依赖 Phase 1 全部**（客户端与配置门是 P1 的前提）
- Phase 3 依赖 Phase 2；T035 **必须**在 T030–T034 全绿之后做（否则破坏打在未绿的用例上，读数无意义）
- Phase 4 依赖 Phase 3；**Phase 5+ 依赖 Phase 4 的门禁通过**
- **P2/P3/P4 之间无相互依赖**，可任意顺序、可任意截断

## Notes

- **本批唯一的迁移是 V92**（仅权限码，无 DDL）；**零新增表、零新增实体**（plan D5/D7）
- **零 `@Async`、零流式、零 markdown 渲染**（plan D3 + spec 非目标）
- 中文断言须显式 `StandardCharsets.UTF_8`（本仓有 ISO-8859-1 假红先例）；能断 `error.code` 优先断 code
- IT **不得**加 `@Transactional` / `@TestMethodOrder`
- 前端八道门禁 + **冻结债 54 处 / 4 条 266 处** 不得增长

---

## 实做订正

> 交付时填写。**三列**：原计划 / 实做 / 理由。原文逐字保留，订正**不静默**。

| # | 原计划 | 实做 | 理由 |
|---|---|---|---|
| | | | |
