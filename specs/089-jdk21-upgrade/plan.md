# 实施计划：把项目从 JDK 17 升级到 JDK 21（基线升级）

**Branch**: `089-jdk21-upgrade` | **Date**: 2026-09-13 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/089-jdk21-upgrade/spec.md`

## 摘要

把项目的 Java 基线从 **17 提到 21**。技术路线只有一句话：**只改具有执行效力的版本声明点，零依赖升级、零源码改动**。

本计划之所以敢这么短，是因为本轮的**隔离实测**已经证明这条路线成立：同一份源码、同一份 pom（结构一字未改，只把 `java.version` 从 17 改成 21），编译 `565 source files with javac [debug release 21]` BUILD SUCCESS、格式门禁 `732 files clean`、单元测试 `555 run / 0 failures / 0 errors`、`mvn -B verify` **退出码 0**（555 单元 + 284 集成全绿，日志出现 `jacoco:0.8.11:check (coverage-check)` 与 `All coverage checks have been met.`，75 份 failsafe XML）。

**可行性的唯一技术依据**：`spring-core 6.1.1`（随 Boot 3.2.0）内置重打包的 ASM 只读到 class **major 66（Java 22）**。21 产出 **major 65 → 在门槛内**；25 产出 **major 69 → 在门槛外**（实测 67/68/69 全部抛 `Unsupported class file major version`，真实扫描探针报 `BeanDefinitionStoreException`）。这条依据同时是**范围守卫的哨兵**：实施中若发现任何组件需要动，就说明这条依据不再成立，**应当停下重新裁决，而不是就地扩大范围**（FR-J12）。

改动面**实测共 5 处执行性声明点 + 2 处步骤名称 + 3 份文档**，全部为版本字面量替换，无逻辑改动。25 的完整调查（四条阻塞点、版本矩阵、已否决的 `--release 22` 伪解）留档于 [jdk25-investigation.md](./jdk25-investigation.md)，供将来另立新项。

**本规格不产 `contracts/`，不产 `data-model.md`，不引入任何 Flyway 迁移。** 理由见下方「Structure Decision」与 [research.md](./research.md) R4/R5。

## Technical Context

**Language/Version**: Java **17 → 21**（二者均为 LTS）。本机 JDK 21 已装于 `C:\Users\Administrator\.jdks\jdk-21.0.12.1+1`（用户目录、不在 PATH、sha256 已校验）；系统 `JAVA_HOME` **仍指向 `C:\Program Files\Java\jdk-17`**，故本地构建需显式指定新基线才能复现（见 quickstart 前置条件）

**Primary Dependencies**: **全部不变** —— Spring Boot 3.2.0、MyBatis-Plus 3.5.5、springdoc 2.3.0、POI 5.2.5、OpenPDF 1.3.30、jjwt 0.12.5、Lombok（继承 1.18.30）、spotless 2.43.0 + google-java-format 1.19.2（pom 内钉死）、JaCoCo 0.8.11（`<properties>` 自管）。**零升级是本规格的核心约束，不是巧合**

**Storage**: MySQL 8（开发/生产，Flyway 管理）；集成测试用 H2（`MODE=MySQL`，`flyway.enabled: false`）。**本规格不改动任何 schema，无迁移**

**Testing**: 后端 surefire（单元）+ failsafe（集成，`*IT`）+ JaCoCo `check`（阈值 **0.73**，不得下调）；前端不涉及

**Target Platform**: 后端服务（Windows 开发 / Linux 容器）；镜像为基础运行时的唯一承载点

**Project Type**: web-service（backend + frontend）—— 本规格只触及 backend 与其构建/部署面

**Performance Goals**: 无。基线升级不引入吞吐或延迟指标

**Constraints**:
- **零依赖升级**（FR-J12）。这是本规格与"上 25"的分界；一旦"不升某依赖就上不去"，说明前提变了 —— **停止并报告**，不得就地扩围
- **可行性的唯一依据是 ASM 版本上限**（class major ≤66）。实施前应把"新基线的 class major 落在此上限内"作为一条**前置自检**，而不是假定它成立（见 quickstart 第 0 组）
- **"能启动"必须被覆盖**：编译通过**不得**单独作为完成判据（FR-J03）。这是上一批改动被漏掉的缺口——它的编译错误修好后，540 例里 482 例 Error、且应用根本起不来
- **不得传 CLI `-DargLine`**（会静默挤掉 JaCoCo 代理，覆盖率门禁空过而构建全程无报错，见 083 quickstart 验证 8）
- **镜像基础标签必须真实存在**：改 `FROM` 时若用了不存在的标签，失败会推迟到很晚的阶段（Dockerfile 两处，见 research.md R5）
- **本地 `JAVA_HOME` 仍是 17**：不改动它（那会影响同机其它会话）；用显式路径或临时环境变量跑新基线构建

**Scale/Scope**: 5 处执行性声明点 + 2 处步骤名称 + 3 份文档；**预期零源码改动、零依赖升级**

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| 原则 | 判定 | 依据与说明 |
|---|---|---|
| **一、契约优先**（不可协商） | ✅ **通过** | 本规格**修改零份契约**：无新端点、无端点语义变更、无请求/响应结构变更。基线升级改变的是"代码跑在哪个运行时上"，不改变接口。故不产 `contracts/`（§澄清 Q3 已裁）。若强行产出，会与既有契约构成"两份描述同一端点"的多事实来源，正是原则一要避免的。 |
| **二、分层架构** | ✅ **通过** | 本规格**不改动任何源码文件**（预期），故分层结构不受影响。声明点全部位于构建与部署面（pom / Dockerfile / CI 配置），不属于 Controller/Service/Repository 任一层，不构成"业务逻辑外溢"。若实施中确需改源码，则属 FR-J10 的适用范围，须逐例留痕。 |
| **三、数据完整性、安全与校验**（不可协商） | ✅ **通过** | 无 schema 变更、无迁移、无认证/授权改动、无新增信任边界。唯一需要论证的是"换运行时是否改变校验与授权语义"——不改变：Jakarta Bean Validation、Spring Security、JWT 的行为由**依赖版本**决定，而依赖版本零变化。该结论由 **SC-J01 的 0 失败 0 错误**直接覆盖（授权用例在被删减的前提下也覆盖不到——故 FR-J09 同时禁止删减用例）。 |
| **四、测试优先与质量门禁**（不可协商） | ✅ **通过（本规格的主旨之一）** | 章程要求"每次合并必须通过构建、单元测试、集成测试、Lint、类型检查以及已配置的覆盖率门槛"。本规格把这句话原样当成验收判据：FR-J05 要求完整生命周期退出码 0，FR-J06 要求覆盖率门禁**被实际判定**（以结论行为证，非"文件存在"或"没有报错"），FR-J07 禁止下调阈值，FR-J09 禁止削减用例。**"门禁名义存在、实际失效"正是本项目已多次踩到的形态**，故此处不满足于"构建成功"。 |
| **五、简洁、可维护与可观测** | ✅ **通过** | **零新依赖、零升级**（YAGNI 的直接履行——本规格不引入任何为"将来上 25"预置的改动）。FR-J13 刷新文档中的前置条件，使新基线对后来者**可发现**；FR-J14 要求留痕，使"为什么不是 25"**可追溯**。 |
| **技术与架构约束**：后端 Java（LTS）+ Spring Boot | ✅ **通过** | **21 是 LTS**，满足约束。此点必须在实施中持续成立：若有人提议非 LTS 版本，须先走章程修正案流程。 |
| **治理：偏差须说明理由并经批准** | ✅ **无偏差** | 本轮无章程违规，故 [Complexity Tracking](#complexity-tracking) 为空。 |

**Phase 1 设计后的复检**：结论不变。设计过程中新确认的三点均**未**产生违规——①不产 `contracts/`（R4：零契约变更）；②不产 `data-model.md`（R5：无数据实体，见 Structure Decision）；③声明点清单由实测扩到 5+2+3（R3），仍全部落在构建/部署/文档面，不触及分层与契约。**无新增偏差，Complexity Tracking 保持为空。**

## Project Structure

### Documentation (this feature)

```text
specs/089-jdk21-upgrade/
├── spec.md                    # 需求（15 条 FR-J、6 条 SC-J、4 个用户故事）
├── plan.md                    # 本文件
├── research.md                # Phase 0：R1–R7，全部 NEEDS CLARIFICATION 已消解
├── quickstart.md              # Phase 1：可执行验证指南（每组附"改造前表现"）
├── jdk25-investigation.md     # 附加留痕：25 的四条阻塞点 + 版本矩阵（FR-J14 的落点）
├── parked-jdk25.diff          # 附加留痕：被退回那批改动的原文（FR-J15 的落点）
├── checklists/
│   └── requirements.md        # 规格质量清单
└── tasks.md                   # Phase 2 输出（由 /speckit-tasks 生成，非本命令产物）
```

**无 `contracts/`、无 `data-model.md`** —— 理由见下方「Structure Decision」。

### Source Code (repository root)

```text
backend/pom.xml                    # :21  <java.version>17</java.version> → 21
Dockerfile                         # :2   FROM maven:3.9-eclipse-temurin-17 → -21
                                   # :15  FROM eclipse-temurin:17-jre-alpine → 21-jre-alpine
.github/workflows/ci.yml           # :14 步骤名 "Set up JDK 17"（订正）
                                   # :18 backend 作业 java-version: '17' → '21'
                                   # :125 步骤名 "Set up JDK 17"（订正）
                                   # :129 e2e 作业 java-version: '17' → '21'
README.md                          # :11 技术栈行；:74 本地前置条件行（"JDK 17、Maven 3.8+…"）
INSTALL.md                         # :28 技术栈行
PROJECT_FEATURES.md                # :25 技术栈行

（预期无源码改动）backend/src/main/java/**、backend/src/test/java/**
```

**不修改**：`Spring-Boot-React-CRM/01-项目背景与技术选型.md`（其中含 `JDK 17 安装` 段落）与 `specs/` 下的既有规格 —— 它们是**记述当时决策的历史记录**，按 FR-J13 的边界保留快照。此判断显式记录，以免被误读为遗漏。

**Structure Decision**：沿用仓库既有的 backend / frontend 双模块布局（Option 2），**不新建任何目录层级**。改动面全部是既有文件里的**版本字面量**，无新文件、无新包、无新类族。

**两点「不产出」的取舍，留痕备查**：

1. **不产 `contracts/`** —— 本规格无新端点、无端点语义变更（§澄清 Q3）。与 085 的判定一致：按"无端点变更则无契约"的统一解释，此处应当没有契约。
2. **不产 `data-model.md`** —— 模板要求从 spec 抽取实体建模，而本规格**无数据实体**（spec「关键实体」已明写：无 schema 变更、无迁移、无新端点）。此处唯一可建模的对象是"基线声明面"（5 处执行性声明点各控制哪条路径、以及 class major 66 的上限模型），但它既不是持久化结构、也不参与运行时状态转换——**它属于计划的技术上下文，不属于数据模型**。故建模内容并入本文件的 Project Structure 与 research.md R1/R3，不另立 `data-model.md` 以免产出空壳文档。

## Phase 0 / Phase 1 产物摘要

| 产物 | 关键结论 |
|---|---|
| [research.md](./research.md) | R1 ASM 上限是可行性的唯一依据（major 65 vs 69）；R2 零依赖升级的隔离实测证据；R3 声明点的实测清点（5 执行性 + 2 步骤名 + 3 文档，为何步骤名也要改）；R4 不产 contracts 的理由；R5 不产 data-model 的理由；R6 镜像标签可得性（需验证）；R7 本规格要防的四种"假绿"形态 |
| [jdk25-investigation.md](./jdk25-investigation.md) | 25 的四条阻塞点（编译 200 错误 / 格式门禁 0.97 秒失败 / 应用起不来 major 69 / 482 例测试错误）；版本矩阵（Boot ≥3.5.6、ASM 9.8 边界 = Boot 3.4.4、gjf ≥1.27.0、jacoco ≥0.8.14、byte-buddy ≥1.16.1、Mockito 无需动、Lombok ≥1.18.40）；两条订正（jacoco 不受 Boot 管理、Mockito 无需升级）；已否决的 `--release 22` 伪解 |
| [quickstart.md](./quickstart.md) | 7 组可执行验证，**每组附"改造前表现"**以证明验证本身有区分度；第 0 组是"先自检可行性依据"（在动手之前确认新基线的 class major 落在 ASM 上限内） |

## Complexity Tracking

> **本节为空**——Constitution Check 无违规，无需登记例外。

| Violation | Why Needed | Simpler Alternative Rejected Because |
|-----------|------------|-------------------------------------|
| —（无） | — | — |

**一处曾评估是否构成"范围外改动"、最终判定不构成的取舍，留痕备查**：

**刷新 3 份文档（README / INSTALL / PROJECT_FEATURES）是否越出"基线升级"的范围？** —— 判定**不越出**。理由：FR-J13 的目的是让新基线**对后来者可发现**；文档里"前置条件：JDK 17"若留不改，后来者按文档装环境会得到一串与文档不符的失败，而这类失败**最容易归因到代码上**（本仓已有类似前科：`pom` 要 25 而本机只有 17 时，源码没动则 Maven 跳过编译、测试照常绿，一动代码才报错，极易误判）。改动仅是版本字面量，零风险。**边界**：只改"技术栈"与"前置条件"这两类**活描述**，历史性记述文档（项目背景与选型、既有 specs）**一律不追改**。
