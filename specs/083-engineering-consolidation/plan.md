# Implementation Plan: 工程收口与质量门禁修复

**Branch**: `083-engineering-consolidation` | **Date**: 2026-09-12 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/083-engineering-consolidation/spec.md`

## Summary

把三个**名义存在而实际失效**的质量门禁修复为真实门禁，并顺带清零已逐条确认的安全、性能与阻断性缺陷。技术路径分五块：

1. **测试门禁** —— 接入 `maven-failsafe-plugin` 使**存量 60 个** `*IT` 类真正执行（连同本规格新增的 5 个，交付时为 **66** 个——T062 复核更正）；把 JaCoCo `report` 从 `test` 相位移到集成测试之后（否则报告不含集成测试覆盖）；阈值上调至实测值；同步补齐测试库镜像 `schema-h2.sql` 的 V70–V77（**V72 缺号**，实为 7 个迁移），并新增**迁移-镜像一致性守卫**防止再次漂移。
2. **前端门禁** —— Lint 转绿、Vitest 补覆盖率阈值、E2E 进持续集成。
3. **安全** —— 密钥主体去管理员化、实时通道补失效校验与来源白名单、出站地址统一服务端请求伪造防护、补 3 个控制器的既有权限码、修复定时导出越权读取。
4. **性能与一致性** —— 6 处已定位的热路径浪费。
5. **阻断性缺陷** —— 14 个前端页面的凭据失配、容器编排三处。

关键约束：**缓存必须为进程内实现**（集成测试把外部缓存客户端替换为测试替身，用外部缓存会使缓存行为在测试中静默不被验证）；**出站地址默认全拒 + 环境变量白名单**；**实时通道来源复用既有跨域配置**；**不得为本次修复新增任何权限码**。

## Technical Context

**Language/Version**: Java 17 / TypeScript 5（沿用既有技术栈）

**Primary Dependencies**: 新增 `maven-failsafe-plugin`（构建期）、`caffeine`（进程内缓存，Spring Boot 版本管理）；复用既有的 JaCoCo、Spotless、Spring Security、MyBatis-Plus、Vitest（v8 provider）、Playwright

**Storage**: **无生产 schema 变更、无新增 Flyway 迁移**。`backend/src/test/resources/schema-h2.sql`（测试资产）补齐镜像：**8 张新表 + 3 个新增列 + 1 组角色权限种子**，覆盖 V70/V71/V73/V74/V75/V76/V77 共 7 个迁移（V72 不存在）。详见 data-model.md §2

**Testing**: JUnit 5 + Spring Boot Test（单元 `*Test` / 集成 `*IT`）、H2（`MODE=MySQL`）、Vitest + React Testing Library、Playwright

**Target Platform**: Web（前后端分离）+ Docker Compose

**Project Type**: 现有 Web 应用的非功能性收口（加固类，无新端点）

**Performance Goals**:
- 授权校验热路径：同一角色的后续请求命中缓存，不再每次触发两次数据库查询
- 报价单商品查询次数与明细行数**解耦**（当前为 2N）
- 日志过滤器不再为每个请求分配与响应体等量的堆缓冲

**Constraints**:
- 缓存**必须进程内**——集成测试将外部缓存客户端声明为测试替身
- 出站地址防护**默认全拒 + 环境变量白名单**，且必须覆盖重定向
- 实时通道来源**复用既有跨域配置**，不新增第二个来源配置项
- 补权限注解**只能使用既有权限码**；全局搜索**明确不加**注解
- 迁移镜像必须是**机械转译**，不得语义重写，不得整段粘贴
- 不得改动生产数据库结构

**Scale/Scope**: 27 条功能需求 / 5 个用户故事 / 7 条成功标准；约 66 个集成测试首次纳入执行（原估"约 60 个"，T062 按交付实测更正）；14 个前端页面；1 处契约同步（开放平台授权语义）

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| 章程条款 | 门禁要求 | 状态 |
|---|---|---|
| 原则一：契约优先（不可协商） | 端点在契约定义前不得实现；契约不得被**静默**修改；契约变更须附测试 | ⚠️ 满足，**但含一处须显式声明的既有偏差**。本规格无新端点、不改任何契约的请求/响应结构，故不触发契约变更；开放平台授权语义确有变更——按"不静默"要求同步 `specs/055-open-platform/contracts/open-platform.md`（T042）并附回归测试。**偏差**：章程技术要求"前端 API 客户端由 OpenAPI 契约生成"，而本项目前端为 50 个手写 service 模块。本规格 US4 是在**该既有模式下**消除重复，未引入也未加重偏差；契约生成客户端**已由范围决策排除**。按治理节"任何偏差必须明确说明理由"，此处如实声明，**处置该偏差需另立规格** |
| 原则二：分层架构 | 业务逻辑在 Service；前端数据访问必须经专用 API 客户端层 | ✅ 满足（缓存配置在 Config 层 / 失效在 Service 层；三份绕过客户端层的裸 `fetch` 与一处裸 HTTP 调用收口回既有的 API 客户端） |
| 原则三：数据完整性、安全与校验（不可协商） | 授权必须服务端强制；密钥不硬编码 | ✅ 满足（FR-G11–G16；出站地址校验属服务端强制，白名单走环境变量） |
| 原则四：测试优先与质量门禁（不可协商） | 每次合并必须通过构建、单元、集成、Lint、类型检查与覆盖率门槛 | ✅ 满足（**本规格的主题**）。并反向补齐：新增守卫使"新增迁移未同步测试库镜像"成为构建失败 |
| 原则五：简洁、可维护与可观测 | 禁 N+1；结构化日志；YAGNI；列表分页 | ✅ 满足（FR-G24 消除 2N 查询；FR-G22 保留既有日志字段不降低可观测性；不新增权限码；缓存实现选型在 research.md 论证必要性） |
| 技术与架构约束 | 复用既有技术栈；schema 变更须附迁移计划 | ✅ 满足（无 schema 变更。新增 failsafe 属构建期插件、Caffeine 为 Spring Boot 版本管理的必要依赖，均在 research.md 论证） |

**结论**: 无门禁违规，无需豁免。

> 说明：原则四被本规格**加强**而非豁免——本规格正是在补齐该原则长期未被落实的缺口。

### Phase 1 设计完成后的复评

设计阶段（research.md / data-model.md）未引入新的章程冲突，但使原则三与原则四的具体含义更严格，复评结论如下：

- **原则三（安全）复评加严**：设计中发现可见数据范围缓存的失效面**大于**其键面——按用户 id 缓存，但值的正确性取决于部门成员集合，成员变动无法从该用户的写操作推断。若按常规"谁被改就失效谁"实现，会造成**越权读到他人数据**。故不变式与实现方案均收紧为"用户或部门的任何写操作全量失效"（data-model.md §3、§5.2）。**结论：仍满足，且要求高于初评。**
- **原则四（质量门禁）复评加严**：设计确认覆盖率门禁存在"报告相位早于集成测试执行"的假绿路径，故把"报告覆盖数严格高于纯单元测试"与"人为调低阈值必须导致构建失败"列为强制验证点（quickstart.md 验证 2）。同时新增测试库镜像守卫，使"机制不随版本回填"这一根因被长期封堵。**结论：仍满足，且要求高于初评。**
- **原则一（契约）复评**：确认本规格无新端点、不产 `contracts/`，与 `003-system-hardening` 同形制；唯一契约动件为 055 的授权语义澄清。**结论：满足。**
- **原则二、五**：设计未改变初评结论。

## Project Structure

### Documentation (this feature)

```text
specs/083-engineering-consolidation/
├── spec.md            # 功能规格
├── plan.md            # 本文件
├── research.md        # Phase 0：技术决策与取舍
├── data-model.md      # Phase 1：测试库镜像、缓存、配置项、不变式
├── quickstart.md      # Phase 1：端到端验证指南
├── baseline.md        # 实施期产物：T001–T003 的改造前实测证据与事实修正
└── tasks.md           # Phase 2（由 /speckit-tasks 生成）
```

> `baseline.md` 非初始设计的产物，而是实施 T001–T003 时为固定"改造前证据"新增（`/speckit-implement` 期）。quickstart 的每条验证都依赖它证明区分度，故保留为独立文件而非并入 quickstart。

**不产 `contracts/`** —— 与 `003-system-hardening` 同形制（加固类、无新端点）。唯一的契约动件是 `specs/055-open-platform/contracts/open-platform.md` 的授权语义澄清（见 Structure Decision）。

### Source Code Changes

```text
backend/
├── pom.xml                                          # 修改：接入 failsafe；JaCoCo report 相位迁移；阈值上调
└── src/
    ├── test/resources/schema-h2.sql                 # 修改：镜像 7 个迁移（本规格最大工作量项）
    ├── test/java/com/crm/
    │   ├── AbstractIntegrationTest.java             # 修改：每测试前清理进程内缓存
    │   └── integration/
    │       ├── SchemaParityIT.java                  # 新增：迁移-镜像一致性守卫（FR-G07）
    │       ├── SchemaIdempotencyIT.java             # 新增：建库脚本幂等守卫（实施期补充，见 baseline.md §7）
    │       ├── LeadITTest.java → LeadIT.java        # 重命名：归入集成测试执行范围（FR-G06）
    │       ├── OpenPlatformIT.java                  # 修改（**非新增**，T062 更正）：开放平台授权语义回归
    │       ├── SecurityHardeningIT.java             # 修改（**非新增**，T062 更正）：权限门禁与越权回归
    │       ├── WebSocketHandshakeIT.java            # 新增：失效令牌与来源拒绝
    │       ├── WebhookRedirectIT.java               # 新增：不自动跟随 3xx + 合法逐跳重定向
    │       └── PerformanceRegressionIT.java         # 新增：缓存命中 / 操作人填充 / 批量查询
    └── main/java/com/crm/
        ├── common/OutboundUrlValidator.java         # 新增：统一出站地址校验（FR-G13）
        ├── config/
        │   ├── CacheConfig.java                     # 新增：进程内缓存配置
        │   ├── LoggingFilter.java                   # 修改：移除响应体包装（FR-G22）
        │   ├── RestTemplateConfig.java              # 修改（**T062 补入**）：关闭自动跟随 3xx——FR-G13「重定向不可绕过」的另一半，缺它则该校验形同虚设
        │   ├── WebSocketConfig.java                 # 修改：失效校验 + 来源复用既有跨域配置（FR-G12）
        │   └── MybatisPlusConfig.java               # 修改：补操作人自动填充（**仅新增路径**，FR-G26 经 T062 收窄）
        ├── entity/*.java                            # 修改（**T062 补入**，40 个实体；含 BaseEntity 共 41 个文件带标注）：为 createdBy 补 @TableField(fill = INSERT)——
        │                                            #   不带该标注时填充**静默不生效**，故这 40 个文件是 FR-G26 机制的一部分，不是附属改动
        ├── security/
        │   ├── ApiKeyAuthFilter.java                # 修改：去管理员化（FR-G11）
        │   └── VisibleOwnerIdsCache.java            # 新增（**T062 补入**）：可见范围缓存的访问包装，兼作依赖环断点（见 research.md §4）
        ├── service/
        │   ├── EntityAccessService.java             # 修改：无限制判定改显式三分支
        │   ├── RoleService.java                     # 修改：权限缓存 + 写操作失效（FR-G23）
        │   ├── DataPermissionService.java           # 修改：可见范围缓存 + 失效（FR-G27）
        │   ├── UserService.java                     # 修改（**T062 补入**，5 处 evictAll）：可见范围缓存的**失效点**之一
        │   ├── DepartmentService.java               # 修改（**T062 补入**，3 处 evictAll）：同上，部门侧失效点
        │   ├── QuoteService.java                    # 修改：共享批量产品查询（FR-G24）
        │   ├── ApiKeyService.java                   # 修改：写回收窄为单语句原子更新（**不加事务**，FR-G25 经 T062 修正）+ 删死代码
        │   ├── IntegrationChannelService.java       # 修改：接入统一出站校验
        │   └── WebhookService.java                  # 修改：接入统一出站校验
        └── controller/
            ├── ComplianceExportController.java      # 修改：补既有权限码
            ├── DataRetentionPolicyController.java   # 修改：补既有权限码
            └── ScheduledExportController.java       # 修改：补权限码 + 修越权读取（FR-G16）

frontend/
├── Dockerfile                                         # 新增（**T062 补入**）：多阶段构建，产出在镜像内——FR-G20 的落点
├── vite.config.ts                                   # 修改：coverage thresholds（FR-G09）
├── e2e/
│   ├── helpers/login.ts                             # 新增（**T062 补入**）：登录辅助
│   └── module-page-auth.spec.ts                     # 新增（**T062 补入**）：SC-G04 的浏览器级验收
└── src/
    ├── services/api/
    │   ├── quotaApi.ts                              # 修改：收口 apiClient、保形（FR-G17）
    │   ├── dataRetentionApi.ts                      # 修改：同上
    │   ├── scheduledExportApi.ts                    # 修改：同上
    │   └── moduleApiClients.test.ts                 # 新增（**T062 补入**）：用自定义 adapter 钉死"发什么头"
    └── pages/
        ├── data-retention/ComplianceExportPage.tsx  # 修改：改用既有 API 客户端（FR-G18）
        ├── map/UsageMapPage.tsx                     # 修改：Lint
        ├── quotas/QuotaListPage.tsx                 # 修改：Lint
        └── stats/DashboardPage.test.tsx             # 修改：Lint

.github/workflows/ci.yml                             # 修改：E2E 入 CI；运行时版本对齐
docker-compose.yml                                   # 修改：三处（FR-G19–G21）
INSTALL.md                                           # **未实施**（T062 复核发现）：原计划补"首次启动流程说明"，
                                                     #   实际未改动该文件——FR-G19–G21 的说明目前只存在于 docker-compose.yml
                                                     #   的注释里。已列为转交 `/speckit-converge` 的缺口，不当作已完成。
```

> **本清单是"改动要点"而非穷举**（T062 结论）。实施中还有一批文件被触及而未列于此，分两类：
> **①机制必需但原清单遗漏的**——已在上方补入（`RestTemplateConfig`、`VisibleOwnerIdsCache`、`UserService`／`DepartmentService` 的失效点、`entity/*.java` 的 40 个标注、`frontend/Dockerfile`、两个守卫 IT）；
> **②顺带产物**——Lint 修复与 i18n 引动的若干页面／组件（`LocaleProvider.tsx`、`useFullscreen.ts`、`dataVision/components/*`、`leads/LeadDetailPage.tsx` 等）。后者不承载需求，故不逐条罗列。
> **不要用本清单反推改动范围**：请以 `git diff` 与 `tasks.md` 的任务记录为准。
specs/055-open-platform/contracts/open-platform.md    # 修改：开放端点授权语义澄清（不静默变更）
specs/README.md / specs/roadmap.md                    # 修改：083 登记
```

**Structure Decision**:

- **测试库镜像**是单一文件，所有镜像任务天然串行，不可并行编辑。镜像按"新表 → 既有表加列 → 角色种子"的顺序落位，且授权语句必须位于角色种子**之后**（否则静默插入 0 行、导致测试假绿）。
- **出站地址校验抽为独立类** `OutboundUrlValidator`，被通知通道与回调用统一复用，避免两处各写一套（当前正是"一处只判协议、一处完全不判"的状态）。
- **进程内缓存**集中由 `CacheConfig` 提供，Service 层只声明失效点，避免缓存逻辑散落到业务代码。
- **不新增 `contracts/`**：本规格无新端点，与 003 同形制。开放平台的授权语义变更**不新建契约文件**，而是更新该契约的既有文件——因为契约必须在其所在地被修改，静默修改与另起文件都会破坏"唯一事实来源"。
- **前端三客户端收口时必须保形**：这三个模块的后端返回**裸响应体而非全站错误信封**，收口后不得按信封解包，否则 14 个页面会全部解析失败。

## Complexity Tracking

| 复杂度项 | 说明 | 缓解措施 |
|---|---|---|
| JaCoCo 相位与阈值的耦合 | `report` 当前绑在 `test` 相位、`check` 在 `verify`。只加 failsafe 不动相位会得到"报告不含集成测试覆盖但门禁通过"的**假绿**。**实测加剧**：构建在 surefire 相位即失败（24 失败 / 1 错误），`check` 所在的 `verify` 相位从未到达——门禁不可达，而非仅宽松；配置注释自述的 0.36 与实测 0.5093 不符 | 相位迁移与阈值上调必须同批完成，并以"报告覆盖数严格大于仅单元测试时的数值"作为强制验证点；人为降低覆盖率验证门禁确实会失败。改造前基线见 baseline.md |
| 密钥主体角色语义变更 | 该变更同时触及行级权限的"无限制"判定与权限切面的管理员短路，且该判定还有"无主体"分支服务于系统内部调用 | 判定改为显式三分支并**保留无主体分支**；变更前先用测试固定开放端点的当前行为，使回归可见；目标语义写进 055 契约而非靠推断 |
| 出站地址白名单与合法内网集成的冲突 | 企业内网回调地址常落在私有网段，一刀切会误伤 | 默认全拒 + 环境变量白名单显式出口；该冲突为已知取舍，记录于规格边界情况 |
| 新增 Caffeine 依赖 | 章程原则五要求每个依赖证明自身价值 | 进程内缓存需要 TTL 兜底与容量上限；手写会引入更多代码（违反简洁）。该依赖由 Spring Boot 统一管理版本。备选方案见 research.md |
| 测试库镜像为机械转译 | 生产迁移含大量该数据库特有语法，需转译为目标内存库既有形制 | 不做语义重写；新增列落回对应建表语句内；授权语句置于角色种子之后；由守卫测试（FR-G07）长期保证一致性 |
| 覆盖率数据未写入的静默失败 | 两个测试插件分属不同 JVM，均靠 `argLine` 属性接收覆盖率 agent；若构建配置自定义了该属性而未保留 `@{argLine}` 占位，集成测试数据不会写入，门禁再次假绿 | 作为实施必查项；以"报告覆盖数严格高于纯单元测试"作为验证点（quickstart.md 验证 2），而非仅看构建是否通过 |
| 进程内缓存跨测试类泄漏 | 测试上下文跨测试类复用，缓存 Bean 随之存活 → 用例结果依赖执行顺序，产生难归因的偶发失败，污染本规格新建的集成测试基线 | 基类每个用例前清空全部缓存（research.md §5）；这是选择进程内缓存时**必须**同时承担的成本 |
| 可见范围缓存的安全敏感性 | 该缓存按用户缓存、按部门失效，两者不同维；漏失效即越权读数据 | 全量失效 + 30 秒 TTL 兜底（data-model.md §3）；已写入不变式 |
| 手写 API 客户端（章程偏差，本规格不消除） | 章程要求前端 API 客户端由 OpenAPI 契约生成，实际为 50 个手写模块。US4 是在该模式下消除重复，不消除偏差本身 | 已在章程核对表中显式声明理由（治理节要求）；本规格不改契约结构，故偏差不扩大。**处置该偏差需另立规格**，不得静默延续 |

以上为已知风险与缓解措施，无章程门禁违规。
