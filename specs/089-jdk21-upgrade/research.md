# Phase 0 研究：把项目从 JDK 17 升级到 JDK 21

**创建**：2026-09-13 · **归属**：`specs/089-jdk21-upgrade`
**状态**：全部 NEEDS CLARIFICATION 已消解

---

## R1：可行性的唯一依据是框架内置 ASM 的版本读取上限

**Decision**：把"新基线的 class 文件版本落在 `spring-core` 内置 ASM 的读取上限之内"确立为本规格**可行性的单一技术依据**，并在实施前作为**前置自检**执行（quickstart 第 0 组），而不是假定它成立。

**Rationale**：这条依据把"能不能升"从一个主观判断变成了一个可测量的字节值。

| 基线 | class major | 落在上限（66）内？ |
|---|---|---|
| Java 17（现状） | 61 | ✅ |
| **Java 21（本项目标）** | **65** | ✅ |
| Java 22 | 66 | ✅（恰在上限） |
| Java 23 | 67 | ❌ |
| Java 24 | 68 | ❌ |
| Java 25 | 69 | ❌ |

**实测方法（逐字节对照，不是推测）**：把 class 文件的版本字节改成目标值后调用 `org.springframework.asm.ClassReader`。结果：**61–66 全部通过；67/68/69 全部抛** `IllegalArgumentException: Unsupported class file major version`。

**真实扫描探针**（不满足于人造 class，而是让框架扫自己编译出来的产物）：

```
BeanDefinitionStoreException: Failed to read candidate component class ...
  caused by: ASM ClassReader failed to parse class file ...
  Unsupported class file major version 69
```

**所以**：本项的可行性不依赖"Boot 3.2.0 官方是否支持 21"这类文档问题，而依赖一个**本机可复现的字节值比较**。这也解释了为什么 21 与 25 的代价差了一个数量级——它们是同一个阻塞点的两侧。

**Alternatives considered**：
- **升到 25 并把框架线一起升**（Boot 3.2.0 → ≥3.5.6）：技术上成立，但连带 springdoc、MyBatis-Plus、Flyway 9→11、Connector/J 8→9 等多条依赖线，是**另一个量级的独立工程**，已另立为将来项；完整记录见 [jdk25-investigation.md](./jdk25-investigation.md)。
- **`--release 22` 中间路**（编译到 major 66、运行时用 JDK 25）：**已评估、已否决**。它技术上确实能绕开 ASM 上限与大部分测试失败，但（a）不减少任何必需工作——编译插件与 google-java-format 照样要改；（b）把项目置于官方不支持的组合上；（c）回避了真正的目标；（d）会让"我们在 JDK 25 上"变成不准确的自我描述。记为**伪解**，理由留档于 `jdk25-investigation.md` §2.3，避免后人重新"发现"它。
- **停在 17**：17 已进入维护末期，且不解决本次的立项动因。

---

## R2：零依赖升级成立 —— 隔离实测证据与方法学

**Decision**：本项**不改动任何依赖版本**，只改编译目标与承载它的声明。

**Rationale**：这不是"估计应该没问题"，而是在**隔离副本**里量过的。

**方法学（重要——结论的全部强度来自这里）**：

- 在 `/tmp/jdk21-verify/` 建副本，结构与仓库一致：`backend/{pom.xml, src}`，并把 `frontend` 做成**指向真实 `frontend/` 的 Windows junction**（只读使用；真实工作区一行未动）。
- **控制变量**：同一份源码、同一份 pom，**只有 `java.version` 从 17 改成 21**。pom 结构一字未动。
- 全部 maven 运行都通过 `-f` 指向副本，**真实工作区的任何文件都未被测量动作修改**。

**实测结果**：

| 环节 | 结果 |
|---|---|
| 编译 | `565 source files with javac [debug release 21]` → **BUILD SUCCESS** |
| 格式门禁 | `732 files clean` |
| 单元测试 | `555 run / 0 failures / 0 errors` |
| 完整验证 | `mvn -B verify` → **退出码 0** |
| 集成测试 | **284 例全绿**，产出 **75 份** failsafe XML 报告 |
| 覆盖率门禁 | 日志出现 `jacoco:0.8.11:check (coverage-check)` 与 **`All coverage checks have been met.`** |
| **需要升级的组件** | **0** |

**一次被正确归因的假红（留痕）**：首次在副本中跑单测时出现 **16 个 Error**。我没有把它归因给 JDK 21，而是去查根因——`com.crm.support.MenuReadPermissionTestSupport` 会从 `user.dir` 向上寻找**同时含 `frontend/` 与 `backend/` 的目录**，而我当时的扁平副本两者都没有。把副本重建成 `backend/{...}` + `frontend` junction 后 → **555/0/0**。

> **这条对实施有直接价值**：任何"隔离副本跑测试"的做法都**必须**保持 `frontend/` 与 `backend/` 同级，否则会得到 16 个与本项毫无关系的假红。已写入 quickstart。

**Alternatives considered**：
- **顺手把依赖也升到"最新稳定"**：已被排除。用户裁决为"最小可支持集"——只升到能跑新基线所需的最低线，而本轮实测表明**所需的最低线是零**。
- **先升依赖"以防万一"**：违反 FR-J12，且会让归因变难（多个变量同时变化）。

---

## R3：声明点的实测清点 —— 是 5 + 2 + 3，不是"三处"

**Decision**：把声明点按**是否具有执行效力**分为三类，并分别处理。

**Rationale**：原先的说法是"pom + 镜像两个 `FROM` + CI 两个作业 = 4 处"（那一批 diff 正是这 4 处 + pom 三行）。**实测清点后是 5 处执行性声明点**，且另有 2 处步骤名称与 3 份文档。

| # | 类别 | 位置 | 现状 | 为何要改 |
|---|---|---|---|---|
| 1 | **执行性** | `backend/pom.xml:21` | `<java.version>17</java.version>` | 编译目标本身 |
| 2 | **执行性** | `Dockerfile:2` | `FROM maven:3.9-eclipse-temurin-17 AS builder` | 镜像构建阶段 |
| 3 | **执行性** | `Dockerfile:15` | `FROM eclipse-temurin:17-jre-alpine` | 镜像运行阶段——**只有这一处决定容器里跑什么** |
| 4 | **执行性** | `.github/workflows/ci.yml:18` | `java-version: '17'`（backend 作业） | 构建 + 单测 + 集成 + 覆盖率门禁都在此作业 |
| 5 | **执行性** | `.github/workflows/ci.yml:129` | `java-version: '17'`（e2e 作业） | 该作业用 `mvn spring-boot:run` 起后端，**漏改则 e2e 跑在旧基线上** |
| 6 | 步骤名称 | `ci.yml:14` | `- name: Set up JDK 17` | 不具执行效力，但会误导（日志与 UI 显示的名字与实际不符） |
| 7 | 步骤名称 | `ci.yml:125` | `- name: Set up JDK 17` | 同上 |
| 8 | 描述性 | `README.md:11` | 技术栈表 "Java 17 + Spring Boot 3.2…" | FR-J13：新基线须对后来者可发现 |
| 9 | 描述性 | `README.md:74` | 前置条件 "JDK 17、Maven 3.8+…" | 同上——**这一行直接决定后来者装什么** |
| 10 | 描述性 | `INSTALL.md:28` | 技术栈表 | 同上 |
| 11 | 描述性 | `PROJECT_FEATURES.md:25` | 技术栈表 | 同上 |

**已确认无需改动的**：
- `frontend/Dockerfile` —— 无 Java 相关声明。
- 仓库**只有一个** `pom.xml`（`backend/pom.xml`），**无** `.mvn/` 目录（故不存在 `jvm.config` 或 `maven.config` 里的隐藏声明）。

**明确不追改的**：`Spring-Boot-React-CRM/01-项目背景与技术选型.md`（含"JDK 17 安装"段落）与 `specs/` 下的既有规格 —— 它们是**记述当时决策的历史记录**。本项目的既定纪律：活文档刷新为实测值，历史记录保留快照。

**Alternatives considered**：
- **只改 4 处**（即那批 diff 的范围）：会留下 `README.md:74` 的前置条件仍是 17。后来者照文档装环境，会得到一串与文档不符的失败，而这类失败**最容易被归因到代码上**——本仓已有前科：`pom` 要 25 而本机只有 17 时，源码没动则 Maven 跳过编译、测试照常绿；一动代码才报 `release version 25 not supported`，极易误判成自己改坏了。
- **连历史文档一起追改**：会让"当时的决策是什么"变得不可查，与 FR-J14 的留痕目的相反。

---

## R4：不产 `contracts/`

**Decision**：本规格不产出 `contracts/` 目录。

**Rationale**：章程原则一（契约优先）要求"任何端点在契约被定义、评审并版本化之前不得实现或调用"，其反面推论是：**没有端点变更，就没有契约变更**。本规格无新端点、无端点语义变更、无请求/响应结构变更——它改变的是"代码跑在哪个运行时上"，接口一个字节没动。

与本仓先例一致：003（系统加固）之所以无契约，是因为它既无新端点也无端点语义变更。085 更进一步（它让实现回到既有契约），同样未产契约。本项比两者**更彻底**：连实现都不动。

**Alternatives considered**：产出一份"基线升级契约"说明。**已否决**——它不描述任何端点，会与既有契约构成"多事实来源"，正是原则一要避免的。

---

## R5：不产 `data-model.md`

**Decision**：本规格不产出 `data-model.md`。

**Rationale**：模板要求从规格抽取实体并建模，而本规格**无数据实体**（spec「关键实体」已明写：无 schema 变更、无迁移、无新端点）。

唯一可建模的对象是"基线声明面"——11 个声明点各控制哪条路径、以及 class major 的读取上限模型。但它**既不是持久化结构，也不参与运行时状态转换**：它属于**计划的技术上下文**，不属于数据模型。故已并入本文件 R1/R3 与 `plan.md` 的 Project Structure，**不另立空壳文档**。

**Alternatives considered**：产出一份只有说明、没有实体的 `data-model.md`。**已否决**——空壳文档会让人以为"本项有数据模型要读"，与"文档应传达意图"（原则五）相悖。

---

## R6：镜像基础标签的可得性 —— 需在实施时验证

**Decision**：改 `FROM` 之前，**先验证目标标签真实存在**，再改文件。

**Rationale**：`Dockerfile` 的两处 `FROM` 指向两个不同的上游镜像仓库：

| 现状 | 意图 |
|---|---|
| `maven:3.9-eclipse-temurin-17` | 构建阶段（含 Maven + JDK） |
| `eclipse-temurin:17-jre-alpine` | 运行阶段（仅 JRE） |

若目标标签不存在（或已被上游下线），镜像构建会**在很晚的阶段**才失败——本地若无 Docker，更会推迟到流水线才发现。

> **诚实标注**：`maven:3.9-eclipse-temurin-21` 与 `eclipse-temurin:21-jre-alpine` 是上游的常规命名，但**本轮未在本机验证**（本机无 Docker 可用性确认）。**故不写成"已确认存在"**，而写成"实施时先验证"。

**验证方法**（任一）：`docker manifest inspect <tag>` · 或抓取上游仓库的 tags 列表。验证结果应记入 `quickstart.md` 或任务记录。

**Alternatives considered**：直接改、靠流水线发现。**已否决**——本项目已多次因"门禁名义存在、实际失效"付出代价，把一个可在 5 秒内验证的前置条件推给流水线，是同一形态的复发。

---

## R7：本规格要防的四种"假绿"形态

**Decision**：把本项目已经踩过的四种失败形态**显式写成验收要防的对象**，而不是假定"测试绿了就没事"。

**Rationale**：基线升级最容易的失败形态**不是红，而是假绿**。以下四种在本仓都有前科，逐条对应到本规格的验收：

| 形态 | 本仓前科 | 本规格的防线 |
|---|---|---|
| **"编译过"被当成"升级完成"** | 那批改动的编译错误修好后（565 文件 BUILD SUCCESS），540 例测试里 **482 例 Error**，且应用**根本起不来**（major 69 超出 ASM 上限） | FR-J03 明写"编译通过不得单独作为完成判据"；SC-J01 同时覆盖"应用能启动"——上下文加载失败会以**错误**的形式出现在测试计数里 |
| **"没报错"被当成"门禁判定过"** | 覆盖率门禁曾**从未被判定过**（`jacoco:check` 与 `failsafe:verify` 同绑 `verify` 且声明其后，失败即中止） | FR-J06 要求以**日志结论行**为证（`All coverage checks have been met.`），"文件存在"或"没有报错"不构成通过 |
| **"门禁配了但不管"** | 覆盖率阈值曾设 0.30 而实测 0.7516，等于不设防 | FR-J07 禁止下调阈值；FR-J08 禁止放宽既有门禁 |
| **"绿了但什么都没跑"** | Maven 在源码未变时会打印 `Nothing to compile` 并跳过；e2e 打的是**已在跑**的后端，不一定是本次产物 | 要求从**干净构建目录**执行（FR-J02）；SC-J01 要求以**产出的报告份数**为证（本轮实测 75 份 failsafe XML） |

**另有一条与"假红"有关的**：R2 里记录的 16 例假红（隔离副本缺 `frontend/` 同级目录）。**假红与假绿一样危险**——它会让人去修根本不存在的问题，或误判方案不可行。

**Alternatives considered**：只按"退出码 0"验收。**已否决**——本仓的前科表明，退出码 0 与"真的验证过"是两件事。
