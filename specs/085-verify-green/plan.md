# 实施计划：验证门禁转绿（陈旧集成测试失败背后的真实缺陷）

**Branch**: `085-verify-green` | **Date**: 2026-09-13 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/085-verify-green/spec.md`

## 摘要

`mvn -B verify` 当前以非零退出码结束，**唯一障碍**是 4 例集成测试失败；而它的连带后果是**覆盖率门槛从未被判定过**（`jacoco:check` 与 `failsafe:verify` 同绑 `verify` 相位且声明其后，失败即中止）。逐条核实后确认其中 **3 例是生产缺陷**、1 例是同一缺陷的外部表现：

1. **登录污染乐观锁令牌** —— `AuthService.java:91` 用 `updateById(user)` 更新 `lastLoginAt`，`@Version` 使该用户版本自增，管理端手中的令牌立即过期（FR-V01/V02）。
2. **关闭端点状态码与契约不符** —— `CloseRequest.closeResult` 的 `@NotBlank` 让 400 抢先于契约约定的 422 `CLOSE_RESULT_REQUIRED`；而同一端点对"结果非法"**已经**返回 422，**实现自相矛盾**（FR-V03/V15）。
3. **投递记录在重试结束后才落库** —— 最长 36 秒在飞窗口内不可见，进程中断则记录彻底丢失（FR-V05/V07/V08/V13）。

技术路线（详见 [research.md](./research.md)）：①把登录的"更新时间"改为**定向单列更新**，不经过实体级 `updateById`，令牌不再被消费，同时消除一类整行回写的丢更新；②在全局异常处理器增加**按 DTO 类型 + 字段限定**的映射分支，使关闭端点回到 422，其余端点维持 400（FR-V15 由构造保证并另加行为测试）；③改为**派发时插入 `PENDING` 记录、结束时原地更新为终态**，并加**启动清扫**处理中断残留，前端补第三态分支与中英文 i18n 键。

**本规格不产 `contracts/`，不引入任何 Flyway 迁移。**

## Technical Context

**Language/Version**: Java 17（LTS）—— 本机只装 JDK 17；`backend/pom.xml` 的 `java.version` 被并行会话改为 25（未提交），故本地构建显式带 `-Djava.version=17`

**Primary Dependencies**: Spring Boot 3.2.x、MyBatis-Plus（含乐观锁插件 `OptimisticLockerInnerInterceptor`）、Flyway、Jakarta Bean Validation、JUnit 5 与 MockMvc、React 18 + TypeScript（前端第三态）

**Storage**: MySQL 8（开发/生产，Flyway 管理）；集成测试用 H2（`MODE=MySQL`）且 `flyway.enabled: false`。**本规格不改动任何 schema。**

**Testing**: 后端 surefire（单元）+ failsafe（集成，`*IT`）+ JaCoCo `check`（阈值 0.73）；前端 Vitest 覆盖率阈值 + `eslint` + `i18n:check` + `menu:check`

**Target Platform**: 后端服务（Windows 开发 / Linux 容器）；前端浏览器

**Project Type**: web-service（backend + frontend）

**Performance Goals**: 无吞吐/延迟指标。唯一与时效相关的验收点是 **SC-V05**——投递记录的可见性从"最长 36 秒"降到**派发后 1.5 秒内**

**Constraints**:
- **不得引入 Flyway 迁移**（FR-V14 范围守卫；确需迁移时必须停下重新裁决，不得就地扩围）
- **不得修改任何契约**（FR-V12）—— 本规格的立论就是"把实现带到既有契约上"
- 构建校验**必须绕过 Windows 文件占用噪声**：跳过打可执行包（`-Dspring-boot.repackage.skip=true`），否则构建在 `repackage` 一步失败、**根本跑不到集成测试**（research.md R6）
- 不得传 CLI `-DargLine`（会静默废掉 JaCoCo，见 083 quickstart 验证 8 第 3 条）

**Scale/Scope**: 3 个缺陷；4 例失败用例；生产代码约 4–6 个文件、测试约 3–4 个文件、i18n 2 个文件

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| 原则 | 判定 | 依据与说明 |
|---|---|---|
| **一、契约优先**（不可协商） | ✅ **通过** | 本规格**修改零份契约**，而是把**实现**带到既有契约上：`001-crm-core/contracts/sales-opportunities.md:81` 早已写明 422 `CLOSE_RESULT_REQUIRED`，缺陷在于实现返回 400。故不存在"契约被静默修改"。新增的 `PENDING` 状态取值**不构成契约变更**——两份相关契约（`058-integration-hub`、`055-open-platform`）**都未枚举**状态取值（research.md R4）；仍按原则精神**附一个测试**钉住它（FR-V13）。 |
| **二、分层架构** | ✅ **通过** | 三处修复的分层归属逐一定明：①`AuthService`（Service）；②映射置于全局异常处理器——"校验违反应映射为何种 HTTP 状态码"是**纯 HTTP 关注点**，原则二明确将之划给表现层，而业务规则（"结果必填且必须 WON/LOST"）**仍在 Service**，异常处理器只做**映射**不做**判定**；③`WebhookDeliverer`（Service）。前端第三态分支属表现层渲染，不含业务规则。 |
| **三、数据完整性、安全与校验**（不可协商） | ✅ **通过** | **`@NotBlank` 保留**（§澄清 Q1 选 B）——DTO 校验约束一个不少，后端仍是强制执行点。授权未受影响。**事务边界**：修③的插入与更新**必须各自独立、不得包在同一事务里**——两者间隔最长 36 秒的重试窗口，包进一个事务会**持锁 36 秒**，违背原则三"事务必须显式声明并限定在 Service 层"的**限定**要求；此处显式选择**不**加事务并记录理由。修①为单条定向语句，无需事务。**密钥/日志**：无新增。 |
| **四、测试优先与质量门禁**（不可协商） | ✅ **通过（本规格的主旨）** | FR-V11 要求每处修复都有一条**红→绿**的测试；FR-V09/V10 要求 `verify` 退出码 0 **且覆盖率门槛确实被判定**，并以**反向验证**证明该门槛有牙齿（把阈值调到高于实测值后必须失败）——直接回应"门槛从未被判定过"这一发现。测试金字塔：单元→集成→E2E 层级不变。 |
| **五、简洁、可维护与可观测** | ✅ **通过** | 修③把投递记录从"结束时才存在"改为"派发即存在"，是对原则五"每个用户可见的错误都必须在服务端记录"的**直接履行**（原形态下中断即无记录）；前端第三态避免在 UI 上误报失败。无投机性抽象、无新依赖。 |
| **治理：偏差须说明理由并经批准** | ✅ **无偏差** | 本轮**无章程违规**，故 [Complexity Tracking](#complexity-tracking) 为空。三处口径声明（非偏差）已写入 `checklists/requirements.md` 与 `spec.md`「假设」。 |

**Phase 1 设计后的复检**：结论不变。设计过程中新增确认的三点均**未**产生违规——①不产 `contracts/`（R4：契约未变）；②无迁移（§0：`VARCHAR` 非 ENUM）；③异常处理器只做映射（§3.4）。**无新增偏差，Complexity Tracking 保持为空。**

## Project Structure

### Documentation (this feature)

```text
specs/085-verify-green/
├── spec.md                  # 需求（15 条 FR-V、7 条 SC-V、4 个用户故事）
├── plan.md                  # 本文件
├── research.md              # Phase 0：R1–R6，全部 NEEDS CLARIFICATION 已消解
├── data-model.md            # Phase 1：状态机 / 令牌语义 / 状态码映射（无 schema 变更）
├── quickstart.md            # Phase 1：可执行验证指南（每条附"改造前表现"）
├── checklists/
│   └── requirements.md      # 规格质量清单（全通过）
└── tasks.md                 # Phase 2 输出（由 /speckit-tasks 生成，非本命令产物）
```

**无 `contracts/` 目录**——理由见下方「Structure Decision」。

### Source Code (repository root)

```text
backend/src/main/java/com/crm/
├── service/
│   ├── AuthService.java                 # 修①：定向更新 lastLoginAt（原 :89-91）
│   ├── SalesOpportunityService.java     # 修②：领域校验保留（:136/:140 不动）
│   └── WebhookDeliverer.java            # 修③：派发时插入 PENDING、结束时原地更新（原 :103/:164）
├── exception/
│   └── GlobalExceptionHandler.java      # 修②：新增按 DTO+字段限定的 422 分支（:31-38）
├── common/
│   └── ErrorCode.java                   # 复用既有 CLOSE_RESULT_REQUIRED(:29)，不新增
└── entity/
    └── WebhookDelivery.java             # status 取值集合扩大（结构不动）

backend/src/test/java/com/crm/
├── integration/                         # *IT → failsafe 执行
│   ├── UserIT.java                      # 修① 的回归守卫（disabled 场景 + 生命周期场景）
│   ├── OpportunityIT.java               # 修②：close 返回 422 的契约断言
│   └── IntegrationHubIT.java            # 修③：1.5 秒内可见（用例不改，修根因后自然绿）
└── ...                                  # 新增：422/400 边界的行为测试（FR-V15）

frontend/src/
├── pages/settings/IntegrationHubPage.tsx   # 第三态分支（:195）
└── i18n/{zh-CN,en}.ts                      # 新增键（必须两侧同步，否则 i18n:check 转红）
```

**Structure Decision**：沿用仓库既有的 backend / frontend 双模块布局（Option 2），**不新建任何目录层级**。三处修复全部落在既有文件内，无新包、无新类族。修③若需要"启动清扫"组件，其归属需在 `/speckit-tasks` 时确认是放入既有 `WebhookDeliverer` 还是新建一个 Configuration 内的 `ApplicationRunner`——**倾向后者**（职责分离，且不把启动逻辑塞进投递逻辑），但该决策**不影响分层与契约**，故不阻塞本阶段规划。

## Phase 0 / Phase 1 产物摘要

| 产物 | 关键结论 |
|---|---|
| [research.md](./research.md) | R1 令牌污染机制与定向更新方案；R2 三条事实链与"同一端点自相矛盾"的加强证据；R3 记录落地形态与两个配套问题（残留清扫、恰好一条）；R4 不构成契约变更、无需迁移；R5 前端第三态的必要性；R6 环境噪声会伪装成构建失败 |
| [data-model.md](./data-model.md) | 无 schema 变更；投递状态机（含残留 → FAILED）与 4 条不变量；令牌语义边界 5 条规则；状态码映射模型与判定键选择；前端三态展示模型 |
| [quickstart.md](./quickstart.md) | 6 组可执行验证，**每组附"改造前表现"**以证明验证本身有区分度；含反向验证与构建噪声规避说明 |

## Complexity Tracking

> **本节为空**——Constitution Check 无违规，无需登记例外。

| Violation | Why Needed | Simpler Alternative Rejected Because |
|-----------|------------|-------------------------------------|
| —（无） | — | — |

**两处曾评估是否构成偏差、最终判定不构成的取舍，留痕备查：**

1. **不产 `contracts/`** —— 003（系统加固）与本规格同形制。003 之所以无契约，是因为它既无新端点也无端点语义变更；本规格更进一步：**连"端点语义变更"都没有**——契约早已规定 422，本规格只是让实现回到契约。按"无端点变更则无契约"的一致解释，此处应当没有契约。若强行产出，反而会与 `specs/001-crm-core/contracts/sales-opportunities.md` 构成**两份描述同一端点的契约**，正是原则一要避免的"多事实来源"。
2. **`PENDING` 取值进前端而不进契约** —— 状态取值集合的扩大确实改变了客户端的可观测面。判定不构成契约变更的理由是**两份契约都未枚举状态取值**（research.md R4）；作为补偿，FR-V13 用一个测试把新取值钉住，满足原则一"每次契约变更都必须附带一个测试"的精神。
