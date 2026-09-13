---

description: "Task list for 089-jdk21-upgrade"
---

# Tasks: 把项目从 JDK 17 升级到 JDK 21（基线升级）

**Input**: Design documents from `/specs/089-jdk21-upgrade/`

**Prerequisites**: [plan.md](./plan.md)（必需）、[spec.md](./spec.md)（必需）、[research.md](./research.md)、[quickstart.md](./quickstart.md)

**本规格无 `data-model.md`、无 `contracts/`** —— 理由见 plan.md「Structure Decision」与 research.md R4/R5。

**Tests**: 本规格**不新写测试**。它要的"测试"是**既有的全套用例在新基线上仍然全绿**，因此测试相关任务是**执行与判定**，不是编写。

**组织方式**：按用户故事分组，每个故事可独立实现与验证。

## Format: `[ID] [P?] [Story] Description`

- **[P]**: 可并行（不同文件、无未完成依赖）
- **[Story]**: 所属用户故事（US1…US4）
- 每条任务都带确切文件路径

## ⚠️ 三条贯穿全程的约束（每条任务都受其约束）

1. ~~**本机 `JAVA_HOME` 仍指向 JDK 17**，且**不得改动它**（同机有其它会话在用）。所有需要新基线的命令都必须**逐次临时指定**~~
   **【2026-09-13 由项目负责人裁决变更；原文保留在上，订正不静默】** 裁决：把**用户作用域**的 `JAVA_HOME` 切到 `C:\Users\Administrator\.jdks\jdk-21.0.12.1+1`。理由：让本机 pom 与运行环境一致，消除"本地绿、别人红"的分叉；代价（同机其它会话的后台 maven 随之改变）已告知并接受。
   **订正后的实测现状**：
   - `JAVA_HOME` = JDK 21 → 经它启动的 maven 报 Java 21（实测 `mvn -v`）
   - ⚠️ **裸 `java -version` 仍是 17.0.12** —— Machine PATH 中 `C:\Program Files\Common Files\Oracle\Java\javapath` 排在所有 User PATH 之前，这个 Oracle 垫片指向 17。**本条尚未解决**：凡以裸 `java` 为准的检查会被误读成"还是 17"，必须用 `$JAVA_HOME/bin/java` 或 `mvn -v` 佐证
   - 需要显式指定新基线时仍可用：`export JDK21="C:/Users/Administrator/.jdks/jdk-21.0.12.1+1"`
2. **不得传 CLI `-DargLine`**：它会静默挤掉 JaCoCo 代理，`jacoco.exec` 不生成、覆盖率门禁空过，而构建全程成功无报错。
3. **不得 `git add -A` / `git commit -a`**：工作区里有并行会话（`crm-gap-remediation`）的未提交改动（`frontend/vite.config.ts`、`specs/083-engineering-consolidation/data-model.md`）。提交必须用**显式路径**。提交前先跑 `ListAgents`。

---

## Phase 1: Setup（共享前置）

**目的**：把"方案可行"这件事在动手之前钉死。

- [ ] T001 确认 JDK 21 可用并记录其路径：`"$JDK21/bin/java" -version` 应输出 `21.0.12`。**不得**修改系统 `JAVA_HOME`（它当前是 `C:\Program Files\Java\jdk-17`）。同时记录 `mvn -v` 在指定该 JDK 后报出的 Java 版本，作为后续每条验证任务的前置确认

- [ ] T002 **可行性哨兵**：执行 quickstart.md 第 0 组 —— 取一个新基线产出的 class 文件，用 `javap -v` 读出 `major version`（期望 **65**），并与框架内置 ASM 的读取上限（**66**）比较。**判据：65 ≤ 66**。
  **⚠️ 本任务不通过即停止整个实施**——它意味着 plan.md 与 research.md R1 所依赖的唯一技术依据已失效，此时正确的动作是**重新裁决目标基线**，而不是就地调参或改方案。测试用例：用 `major version` 为 67/68/69 的 class 走一次真实组件扫描，应抛 `BeanDefinitionStoreException`（证明这条检查确实有区分度）

**Checkpoint**：确认新基线可用、且可行性依据成立 → 可以开始改动

> **✅ T001 / T002 已完成（2026-09-13）**
> - **T001**：`C:\Users\Administrator\.jdks\jdk-21.0.12.1+1` 可用；`$JAVA_HOME/bin/java -version` → `21.0.12`；指定该 JDK 后 `mvn -v` 报 Java 21。**注**：T001 原文写的"不得修改系统 `JAVA_HOME`"已被后续裁决取代，见文首约束 1 的订正留痕。
> - **T002（可行性哨兵，已通过）**：新基线产出的 class `major version` = **65**；框架内置 ASM 读取上限 = **66** → **65 ≤ 66 成立**。区分度反证同时取得：`major` 为 67/68/69 的 class 走真实组件扫描全部抛 `Unsupported class file major version`，扫描层报 `BeanDefinitionStoreException`。**闸门放行，实施继续。**

---

## Phase 2: Foundational（阻塞性前置）

**目的**：编译目标是所有用户故事共同的根。没有它，US1 与 US2 都无从验证。

- [x] T003 改 `backend/pom.xml:21`：`<java.version>17</java.version>` → `<java.version>21</java.version>`。
  **只改这一行**——pom 的结构、插件配置、依赖、`<argLine>`、jacoco 阈值 0.73 **一律不动**（FR-J01 明确禁止引入与本升级无关的构建配置结构变更；实测 21 上无需新增编译插件配置）。
  验证：`git diff backend/pom.xml` 应**只有一行**变更
  → **✅ 实测**：`git diff --stat backend/pom.xml` = `1 file changed, 1 insertion(+), 1 deletion(-)`。仅 `java.version` 一行；`<argLine>-Dfile.encoding=UTF-8</argLine>`、jacoco `<minimum>0.73`、google-java-format 1.19.2 / spotless 2.43.0 均未触碰

**Checkpoint**：编译目标已是 21 → 所有用户故事可开始

> **为什么这一条在 Foundational 而不在 US1 里**：US1 与 US2 都要在"编译目标是 21"的前提下才能验证；把它放在 US1 会让 US2 依赖 US1，破坏 US2 的独立可验证性。

---

## Phase 3: User Story 1 - 项目跑在一个仍被支持的 LTS 基线上（P1）🎯 MVP

**Goal**：在新基线上能编译、**能启动**。

**Independent Test**：干净构建目录全量编译 0 错误；应用在新基线上完成组件扫描并正常服务既有端点。

- [x] T004 [US1] 干净构建目录全量编译：`cd backend && mvn -B clean compile`，**必须先 clean**（增量编译会掩盖问题）。
  **期望**：`BUILD SUCCESS`，日志含 `565 source files with javac [debug release 21]`。
  **改造前表现**（证明本检查有区分度）：同一条命令在 JDK 25 上是 **200 个错误 / 5 个文件**（Lombok 生成物全部消失）。对应 FR-J02
  → **✅ 实测**：`BUILD SUCCESS`（exit 0），日志 `Compiling 565 source files with javac [debug release 21]`。**0 错误**。日志存 `/tmp/089-t004.log`

- [x] T005 [US1] **启动验证（本规格最关键的一条）**：`cd backend && mvn -B -DskipTests spring-boot:run`（或起 jar），另开终端 `curl -fsS http://localhost:8081/actuator/health`。
  **期望**：应用完成组件扫描、健康检查成功、调用少量既有业务端点行为与升级前一致。
  **⚠️ T004 通过不得单独作为 US1 完成的判据**（FR-J03）：在 JDK 25 上编译是成功的（565 文件 BUILD SUCCESS），而应用**根本起不来**——扫描阶段即报 `BeanDefinitionStoreException ... Unsupported class file major version 69`。这正是上一批改动被漏掉的原因。对应 FR-J03、FR-J04
  → **✅ 实测（2026-09-13 23:11，真隔离 + 真 HTTP）**。起法改用 `mvn -B spring-boot:test-run`（Boot 3.2 起新增该 goal，**用测试 classpath 起应用**，从而带进 H2 与 `application-test.yml`），显式参数兜底指向 H2：

  | 探针 | 结果 |
  |---|---|
  | 启动 | `Tomcat started on port 18099 (http)` + `Started CrmApplication in 3.795 seconds` |
  | 组件扫描 | **无** `Unsupported class file major version` / `BeanDefinitionStoreException`（扫描在 JDK 21 上干净通过） |
  | `GET /actuator/health` | **HTTP 200** `{"status":"UP","groups":["liveness","readiness"]}` |
  | `POST /api/v1/auth/login` (admin/admin123) | **HTTP 200**，返回真实 `accessToken` + `refreshToken` |
  | `GET /api/v1/users?page=1&size=1` 带该 token | **HTTP 200**，返回真实数据（`admin` / 系统管理员）——证明 `DataInitializer` 这条 `ApplicationRunner` 链路在新基线上完整可用 |
  | 数据源 | `jdbc:h2:mem:crm`；全日志中 MySQL 出现 **0 次** |

  > **⚠️ 一次失败的尝试与它暴露的问题（如实留痕）**：首次尝试用 `spring-boot:run` + `-Dspring-boot.run.useTestClasspath=true` + `SPRING_PROFILES_ACTIVE=test`。**该参数并未把 `target/test-classes` 放进运行 classpath**，`application-test.yml` 因此从未加载——只有 profile 的**名字**生效。后果：实例连上了**共享开发库 `jdbc:mysql://localhost:3306/crm_db`**。
  > 写入面已逐条排查，**未产生任何写**：Flyway 输出 `Current version of schema crm_db: 88` / `No migration necessary`；`DataInitializer` 仅在 `user` 表为空时 insert，日志中无 `Seeded default admin user` 行；`WebhookDeliverySweepScheduler` 全程 0 条日志且其 cron（`0 */5 * * * ?`）不在实例存活窗口内。
  > **但隔离确实没做到**，这一点不因"没写"而抵消。**正确起法已固化为上面的 `test-run` + 显式 H2 参数**（任何情况下都不会落到 MySQL）。
  > **同一次事故的第二项副作用**：期间执行的 `mvn -B clean` 删除了 `backend/target/classes`，而 8081 上原有一个 java 实例（pid 5288，21:49:03 启动）**随之消失**。该实例归属无法确认（进程已退出，查不到命令行）。**未**重启它（按既定约束不碰可能属于他人的 8081）。

> **端口冲突注意**：本机 8081 上可能已有**别的会话**起着的后端。**不得**重启一个可能属于他人的实例；若端口被占，改用临时端口起本实例（可参考 083 quickstart 的隔离实例配方），并在证据里写明端口与进程启动时间

**Checkpoint**：US1 独立可验证 —— 编译过 + 应用起得来

---

## Phase 4: User Story 2 - 在新基线上，全套测试与门禁仍然真的绿（P1）

**Goal**：确认"绿"是真绿——门禁被**实际判定**，用例**真的跑了**。

**Independent Test**：完整验证生命周期退出码 0；日志出现覆盖率门禁结论行；用例计数不低于升级前。

- [x] T006 [US2] 执行完整验证生命周期：`cd backend && mvn -B verify ; echo "EXIT=$?"`。
  **三项同时成立，缺一不可**（FR-J05、FR-J06）：
  1. `EXIT=0`
  2. 日志含 `jacoco:0.8.11:check (coverage-check)`（证明该目标被**执行**过）**与**结论行 `All coverage checks have been met.`
  3. 单测 `555 run / 0 failures / 0 errors`、集成 284 例全绿，且 `ls target/failsafe-reports/*.xml | wc -l` 为 **75**
  **改造前表现**：该项目曾长期处于覆盖率门禁**从未被判定**的状态（`jacoco:check` 与 `failsafe:verify` 同绑 `verify` 且声明其后，前者失败即中止），此时退出码非 0 且日志中**不存在结论行**。对应 FR-J05、FR-J06、SC-J01、SC-J02
  → **✅ 实测（2026-09-13 23:11–23:13，`mvn -B clean verify` 退出码 0）**：

  | 判据 | 实测值 |
  |---|---|
  | ① 退出码 | **0**；日志 `BUILD SUCCESS` |
  | ② 门禁被执行 | `jacoco:0.8.11:check (coverage-check) @ crm-backend` 出现在日志 |
  | ② 门禁结论行 | **`All coverage checks have been met.`** |
  | ③ 单元测试 | **555 / 0F / 0E**（surefire 汇总行） |
  | ③ 集成测试 | **284 / 0F / 0E**（failsafe 汇总行） |
  | ③ failsafe XML | **75** 份（`target/failsafe-reports/*.xml`） |
  | 附：覆盖率实测 | **0.8009**（covered 45421 / total 56713，由 `target/site/jacoco/jacoco.csv` 汇总）；阈值 0.73 |
  | 附：格式门禁 | `spotless-check` → `732 files clean - 0 needs changes` |
  | 附：JaCoCo 代理确实挂上 | `argLine set to -javaagent:...org.jacoco.agent-0.8.11-runtime.jar=destfile=...jacoco.exec...`；`jacoco.exec` **1 407 346** 字节 |
  | 假绿反查 | `Nothing to compile` **0** 次；`Unsupported class file major version` **0** 次 |

  > **与基线的一致性说明**：覆盖率由隔离副本实测的 0.7516 / 0.7818 升至本轮 **0.8009**。这不是本规格带来的行为变化，而是**同一段时期内其它会话新增用例**所致（分子随用例增加而增长、分母基本不变）——正是 pom 注释里已预言的那条单向漂移。**门禁仍取 0.73 未动**（FR-J07）。

- [x] T007 [US2] **反向验证：证明覆盖率门禁有牙齿**（FR-J07）。临时把 `backend/pom.xml` 的 `<minimum>0.73</minimum>` 调到**高于实测值**（如 0.99），跑 `mvn -B verify`，**期望构建失败、且失败点正是 `jacoco:check`**；随后**必须改回 0.73** 并用 `git diff` 确认 `pom.xml` 只余 T003 那一行变更。
  **改造前表现**：该阈值曾设为 0.30 而实测覆盖率 0.7516 —— 距实测 45 个百分点，**等于不设防**，怎样都不会红。对应 quickstart 第 6 组
  → **✅ 已证明会红，且给出了一对干净对照**（`mvn -B verify` 实跑，把 `<minimum>` 临时改为 0.99）：

  | | T006（阈值 0.73） | T007（阈值 0.99） |
  |---|---|---|
  | 构建结论 | `BUILD SUCCESS` | **`BUILD FAILURE`** |
  | 失败点 | — | `Failed to execute goal org.jacoco:jacoco-maven-plugin:0.8.11:check (coverage-check) on project crm-backend: Coverage checks have not been met.` |
  | jacoco 判定原文 | `All coverage checks have been met.` | **`[WARNING] Rule violated for bundle crm-backend: instructions covered ratio is 0.80, but expected minimum is 0.99`** |
  | 结论行出现次数 | **1** | **0** |

  两条额外收益：① 该次失败发生在**同一相位的 spotless 与 failsafe 都通过之后**（日志中有集成测试输出），说明失败**确实由覆盖率判定触发**，不是更早的门禁顺带失败；② `Analyzed bundle 'crm-backend' with 233 classes` 证明 jacoco 真的解析了产物，不是空跑。
  → **已还原**：`<minimum>` 改回 **0.73**；`git diff backend/pom.xml` 实测 = `1 file changed, 1 insertion(+), 1 deletion(-)`，**只余 T003 那一行**（`java.version`）

- [x] T008 [P] [US2] 确认**依赖版本零变化**（FR-J12、SC-J05）：`git diff` 全仓查看，除 11 处版本声明外**不应有任何依赖坐标或版本号变更**。
  **若发现"不升某依赖就上不去 21"**：**停止并报告**，不得就地扩大范围——那说明 plan.md 的前提已变，应当重新裁决
  → **✅ 实测通过**：`git diff` 全仓中检索 `dependency|artifactId|groupId|<version>|spring-boot-starter-parent` 的增删行 → **零命中**。全仓被改的构建文件只有 `backend/pom.xml`（另一处 `frontend/vite.config.ts` 属并行会话，非本规格）。**"零依赖升级"这条范围守卫成立。**

- [x] T009 [US2] 确认**用例集未被削减**（FR-J09）：比对 T006 的计数与升级前基线，确认用例总数**不低于**升级前，且**无删除、无 `@Disabled`/`@Ignore`、无断言放宽**。
  核对方式：`git diff` 中不应出现 `backend/src/test/**` 的任何改动（本规格预期**零测试代码改动**）
  → **✅ 实测通过**：`git status --porcelain -- backend/src/test/` 与 `git diff --stat -- backend/src/test/` **均为空**；全仓 diff 中**无**新增 `@Disabled`/`@Ignore`。用例数 555（单元）+ 284（集成）**均不低于**隔离副本基线（同为 555 / 284）。
  → **附加强于预期的证据**：`backend/src/**` **整个源码树零改动**（不只是测试代码）——正是 plan.md「预期零源码改动」的落实，说明 T002 的可行性依据未被证伪。

**Checkpoint**：US2 独立可验证 —— 全绿且门禁真的判定过

---

## Phase 5: User Story 3 - 各处声明的基线一致（P2）

**Goal**：每一处具有执行效力的声明点都是 21，本地与流水线不分叉。

**Independent Test**：逐处读取声明点全部为 21；反查无残留旧值；镜像两阶段一致。

> **顺序很重要**：T010 必须**在** T011 之前——先确认目标标签存在，再改文件（research.md R6）。

- [x] T010 [US3] **改文件之前**先验证镜像目标标签真实存在：
  `docker manifest inspect maven:3.9-eclipse-temurin-21` 与 `docker manifest inspect eclipse-temurin:21-jre-alpine`。
  **若本机无 Docker**：改为抓取上游仓库 tags 列表验证；**不得**把这个前置条件推给流水线去发现（标签不存在会让失败推迟到很晚的阶段）。**验证结果必须记入记录**
  → **✅ 已验证（2026-09-13），但换了方法与通路，如实记明**：
  - **本机无 Docker**（`docker: command not found`），且 **`registry-1.docker.io` / `hub.docker.com` 直连超时**（HTTP 000；同机 `repo.maven.apache.org` 与 `registry.npmjs.org` 均 200，故是这两个域被挡，不是整体断网）。`docker manifest inspect` 这条路在本机走不通。
  - **改用可达的镜像 registry 的 v2 标签列表接口**：`GET https://docker.1panel.live/v2/library/{maven,eclipse-temurin}/tags/list`
  - **结果**：`maven:3.9-eclipse-temurin-21` **命中**（1 次）；`eclipse-temurin:21-jre-alpine` **命中**（1 次）
  - **阴性对照（证明检索方法有区分度，不是"什么都找得到"）**：`...temurin-99` 与 `99-jre-alpine` 在同一份响应里均 **0 命中**
  - **列表真伪核验**：不是缓存残片——`maven` 返回 **1892** 个标签、`eclipse-temurin` 返回 **3373** 个，且 `3.9-eclipse-temurin-` 系列呈完整递进（`-8 -11 -17 -19 -20 -21 -22 -23 -24 -25 -26`）
  - ⚠️ **口径限制**：这是**第三方镜像**的标签列表，与 Docker Hub 官方源的同步存在时延可能。它足以否掉"标签不存在"这一风险（若上游无此标签，镜像侧不会有），但**不构成对上游字节级同一性**的证明。**故 T014 的容器内 `java -version` 一步不在本机执行，本节不主张已验**

- [x] T011 [US3] 改根 `Dockerfile` 两处：`:2` `maven:3.9-eclipse-temurin-17` → `-21`；`:15` `eclipse-temurin:17-jre-alpine` → `21-jre-alpine`。
  **注意 `:15` 是运行阶段**——它是**唯一**决定容器里跑什么的那一处，只改构建阶段会让本地/CI 与容器分叉。对应 FR-J11、SC-J03
  → **✅ 已改**，实测：`Dockerfile:2` = `FROM maven:3.9-eclipse-temurin-21 AS builder`；`Dockerfile:15` = `FROM eclipse-temurin:21-jre-alpine`。两处即全部 `FROM`（本文件仅此两个）

- [x] T012 [P] [US3] 改 `.github/workflows/ci.yml` 四处：`:18`（backend 作业）与 `:129`（e2e 作业）的 `java-version: '17'` → `'21'`；`:14` 与 `:125` 的步骤名 `Set up JDK 17` → `Set up JDK 21`。
  **e2e 那一处不能漏**：该作业用 `mvn spring-boot:run` 起后端，漏改则 e2e 整体跑在旧基线上——而它是唯一覆盖真实浏览器链路的门禁。对应 FR-J11
  → **✅ 已改（4 处全中）**，实测 `grep -n 'java-version\|Set up JDK'`：`:14`/`:125` = `Set up JDK 21`，`:18`/`:129` = `java-version: '21'`。
  **"e2e 用 spring-boot:run 起后端"这一前提已在改文件前核对为真**：`ci.yml:157` 原文即 `nohup mvn -B -DskipTests spring-boot:run > /tmp/backend.log 2>&1 &`，故漏改该处确实会让整条 e2e 跑在旧基线上

- [x] T013 [P] [US3] 刷新三份**描述性**声明（FR-J13）：`README.md:11`（技术栈行）、`README.md:74`（前置条件行 "JDK 17、Maven 3.8+…"）、`INSTALL.md:28`、`PROJECT_FEATURES.md:25`。
  **`README.md:74` 最要紧**——它直接决定后来者装什么。**边界**：`specs/` 下的既有规格与 `Spring-Boot-React-CRM/01-项目背景与技术选型.md` 是**历史记录，一律不追改**
  → **✅ 已改 4 行**：`README.md:11` 与 `INSTALL.md:28` 的技术栈行 → `Java 21 + Spring Boot 3.2 …`；`README.md:74` 前置条件行 → `- JDK 21、Maven 3.8+、MySQL 8.0、Redis 7.0、Node.js 18+`；`PROJECT_FEATURES.md:25` → `Java 21、Spring Boot 3.2、…`

- [ ] T014 [US3] 逐处反查 + 镜像构建（quickstart 第 4、5 组）：
  **〔部分完成：反查已完成，容器构建部分本机不可执行，未主张已验〕**
  ```bash
  grep -n 'java.version' backend/pom.xml
  grep -n 'FROM' Dockerfile
  grep -n 'java-version\|Set up JDK' .github/workflows/ci.yml
  grep -n 'JDK 17\|Java 17' README.md INSTALL.md PROJECT_FEATURES.md
  git grep -nI 'temurin-17\|java-version.*17\|java\.version>17' -- . \
    | grep -v '^specs/' | grep -v 'Spring-Boot-React-CRM/'
  ```
  **期望**：前四条**零命中旧值**；最后一条反查结果**为空**。再执行 `docker build -t crm-backend:jdk21-check . && docker run --rm crm-backend:jdk21-check java -version`，期望容器内为 21
  → **✅ 反查部分通过；⛔ 容器构建部分本机不可执行**
  - 前四条反查实测：`java.version` = **21**；`Dockerfile` 两个 `FROM` = **temurin-21 / 21-jre-alpine**；`ci.yml` 四个位置 = **21 / Set up JDK 21**；三份文档检索 `JDK 17\|Java 17` = **零命中**
  - 全仓反查：共 **67** 行残留，**全部**落在 `specs/`（66 行）与 `Spring-Boot-React-CRM/01-项目背景与技术选型.md`（1 行）之内 —— **边界成立**，`specs/` 与历史文档之外**零残留**。残留性质经抽样确认：**61/67 是各 spec `plan.md` 的 `Language/Version` 声明行**，其余为 017 的 research、3 份 quickstart、1 份历史文档，均为历史记录。**`specs/089-jdk21-upgrade/` 自身零残留**
  - ⛔ **容器构建未执行，且本项不主张已验**：本机无 Docker（`docker: command not found`），且 `registry-1.docker.io` / `hub.docker.com` 直连超时。**`docker build` 与容器内 `java -version` 必须在有 Docker 的机器或 CI 上补做**
  - ⚠️ **一次被我自己的量具骗到的假通过（留痕）**：首次反查时我写的过滤器是 `grep -v '^Spring-Boot-React-CRM/'`，它**没滤掉**该文件——因为 `git grep` 对非 ASCII 路径会输出**带前导双引号**的路径（`"Spring-Boot-React-CRM/01-…"`），锚点 `^Spring-…` 因此失配。当时屏幕上打出的「反查为空 ✅」是**量具坏了**，不是真的空（真值 67 行）。已先自证引号存在、再重做反查

**Checkpoint**：US3 独立可验证 —— 5 处执行性 + 2 处步骤名 + 3 份文档全部一致

---

## Phase 6: User Story 4 - 后续者能读到"为什么是 21，而不是 25"（P3）

**Goal**：本次基线选择的证据留在仓库里，不留在会话上下文里。

**Independent Test**：仅读本规格与调查记录，即可复述 25 的阻塞点。

- [x] T015 [US4] 核对留痕完整性（FR-J14、FR-J15）：`specs/089-jdk21-upgrade/jdk25-investigation.md` 是否**四条阻塞点齐备**（编译 200 错误 / 格式门禁 0.97 秒失败 / major 69 起不来 / 482 例测试错误）、**版本矩阵齐备**、**已否决的 `--release 22` 伪解已记**；`parked-jdk25.diff` 是否**可读且未被覆盖**（81 行，含两个 CI 作业 + 两个 `FROM` + pom 三项）
  同时核对 research.md R2 中**两条订正**（jacoco 不受 Boot 管理、Mockito 无需升级）与 R6 的如实标注（镜像标签未验证）均在案
  → **✅ 逐项核对通过**：
  - **四条阻塞点齐备**：§1.1 编译 200 错误 / 5 文件（`SlaPolicyService 112 · SlaEscalationService 64 · NotificationService 16 · ApiResponse 6 · RoleConstants 3 · PageResult 2`，含最小实验"不加参数生成 0 个 `getPriority()`，加 `-proc:full` 生成 1 个"）；§1.2 格式门禁 0.97 秒失败；§1.3 `major 69` 起不来（对照 `spring-core 6.1.1` 的 ASM 上限 major 66）；§1.4 `Tests run: 540, Failures: 0, Errors: 482`
  - **版本矩阵齐备**：Boot 3.5.7（最低 3.5.6）· ASM 界限 Spring Framework 6.2.5 = Boot 3.4.4 · google-java-format ≥1.27.0（spotless 2.43.0 不动）· Lombok ≥1.18.40 · JaCoCo ≥0.8.14 · byte-buddy ≥1.16.1 · Mockito 5.7.0 无需升级
  - **`--release 22` 伪解已记**：§2.3 记为**伪解**，并写明它技术上确实能绕开 ASM 与大部分测试失败（因 byte-buddy 判定的是**被 mock 类的字节码版本**而非运行 JVM 版本），但仍是伪解
  - **两条订正均在案**：§2.4.1 jacoco **不受 Boot 管理**（pom 里 `<jacoco.version>` 自管）；§2.4.2 **Mockito 无需升级**（门槛在 byte-buddy）
  - **research.md R6 的如实标注在案**：明写目标标签"**本轮未在本机验证**"，并给出"实施时先验证"的处方
  - **`parked-jdk25.diff` 可读、未被覆盖**：**81 行**，改动 3 个文件（`.github/workflows/ci.yml` / `Dockerfile` / `backend/pom.xml`）；内容 = **2 个 CI 作业**（`Set up JDK 25` + `java-version: '25'` 各两处）+ **2 个 `FROM`**（`maven:3.9-eclipse-temurin-25` / `eclipse-temurin:25-jre-alpine`）+ **pom 三项**（`<java.version>25</java.version>`、新增 `<lombok.version>1.18.42</lombok.version>`、lombok 依赖上新增 `<version>${lombok.version}</version>`）
  > **一处必须点明的分工，否则会误导后人**：`parked-jdk25.diff` **不包含** §1.1 里那个已验证的修法（`maven-compiler-plugin` + `annotationProcessorPaths`）——它记录的是**当时被退回的那批改动原文**，而修法是后来实验得出的，写在 `jdk25-investigation.md` §1.1。**把这份 diff 当作"上 25 的完整配方"会漏掉编译插件这一步。**

- [x] T016 [US4] 留痕可读性实验（quickstart 第 7 组，SC-J06）——**不跑命令**，做一次阅读实验，三个问题都要答得出：
  1. 只读 `spec.md` 的「背景」与「澄清 Q1」，能否说出"为什么不是 25"？
  2. 只读 `jdk25-investigation.md`，能否复述 25 的四条阻塞点？
  3. 能否指出 `--release 22` 为什么被否决？
  **改造前表现**：那批改动被退回时，"为什么"只存在于会话上下文里，**没有任何仓库内文档承载它**
  → **✅ 三问均可答出（逐条原文出处如下）**：
  1. **可答**。`spec.md:8-35`：「升到 25 = 跳框架大版本线」；四条阻塞以表格列出，起点是「本仓 pom 没有任何 `maven-compiler-plugin` 配置 + JDK 23 起隐式注解处理默认关闭 → Lombok 不运行」，终点是「class major 69 超出 `spring-core 6.1.1` 内置 ASM 的 major 66 上限——**故"只改几处版本、不动框架线"在构造上就不可能**」。`澄清 Q1`（`spec.md:172-182`）给出裁决与分界点：「class major 65 落在框架内置 ASM 的读取上限（66）之内，25 的 69 在之外」
  2. **可答**。`jdk25-investigation.md` §1.1 编译 200 错误 / 5 文件（并给出最小实验：不加参数生成 0 个 `getPriority()`、加 `-proc:full` 生成 1 个）· §1.2 格式门禁 0.97 秒失败（`NoSuchMethodError`，并写明 `--add-exports`/`--add-opens` 修不了）· §1.3 `major 69` 起不来 · §1.4 `540 例 / 482 错误`
  3. **可答**。§2.3：它**技术上确实能绕开** ASM 上限与大部分测试失败（因为 byte-buddy 判定的是**被 mock 类的字节码版本**、不是运行 JVM 的版本），但仍是**伪解**——不减少任何必需工作、把项目置于官方不支持的组合上、回避真正的目标、并让"我们在 JDK 25 上"成为不准确的自我描述

  > ⚠️ **本项证据强度的诚实标注**：这三个答案是我作为**这些文档的作者**给出的，因此只证明"文档里确实写了这些内容、且能从中检索到"，**不构成"另一个读者也能读懂"的证明**。SC-J06 主张的"后来者能否判断为什么不是 25"，严格意义上的验证需要一个未参与本次工作的读者做一次盲读。**此处不作更强主张。**

**Checkpoint**：US4 独立可验证 —— 留痕齐备且可读

---

## Phase 7: Polish & Cross-Cutting Concerns

- [x] T017 统一分支名。**当前所在分支是 `appmod/java-upgrade-20260912235322`** —— 它是那批被放弃的改动留下的（工具自动创建），而 spec.md 声明的是 `089-jdk21-upgrade`。
  **⚠️ 本任务需要判断，不是机械操作**：工作区里有并行会话未提交的改动，切分支有把它们卷走或阻断的风险（本仓有前科）。**动手前先 `ListAgents` 确认对方空闲，并先确认自己的 089 产物已提交**。若判断有风险，保留现分支并在 spec.md 中订正 Feature Branch 字段（订正须留痕）
  → **裁决：不重命名，改走 T017 授权的备选路径。** 依据是实测而非偏好：
  - `ListAgents`：并行会话 `crm-gap-remediation` 处于 **waiting（空闲）**，其在飞改动为 `frontend/vite.config.ts` 与 `specs/083-engineering-consolidation/data-model.md`（与本规格无交集）
  - `git worktree list`：**单工作树**；本地分支只有 `master` 与工作分支，**无任何 worktree 占用别的分支**
  - **决定性证据**：**083–088 的提交全部落在 `appmod/java-upgrade-20260912235322` 上**（抽验 `6bb5119`(085) / `c3256a1`(085) / `048a884`(087) 均只在此分支；`b939ef7`(088) 同时在其与 `master`）；而 085 的 `spec.md:3` 写 `085-verify-green`、086 写 `086-frontend-button-gating`、083 的 `plan.md` 写 `083-engineering-consolidation` —— **这三条分支全都不存在**。即本仓的既有做法是：SDD 模板的 `Feature Branch` 字段按规格名填写，但**从不真的建分支**，产出统一落在那条集成分支上
  - **因此把该分支改名为 `089-jdk21-upgrade` 会让它更误导**（一条承载 083–088 历史的分支挂了单一规格的名字），且与全部既有规格的做法相悖。**改名也不解决任何真实问题**——没有第二条分支需要区分
  - **已做**：在 `spec.md:3` 的 `Feature Branch` 下方加订正块（原文保留、未删除），写明实际落入的分支与上述依据
  > **一处留给用户裁决的点**：那条分支名 `appmod/java-upgrade-20260912235322` 本身也是误导的——它来自一个自动升级工具，而那批改动已被放弃。把它改成一个中性名字（例如 `integration` 或 `master`）是**项目级决定**，超出本规格范围，故未做。

- [x] T018 登记 `specs/README.md`（4 处）与 `specs/roadmap.md`（3 处）：模块清单表追加一行（6 列）、版本行、编号说明、迁移对照表（**本项无迁移，故不动**）；roadmap 的 `**最后更新**`、`**整体覆盖度**`、`## 当前进度` 追加一行。
  **⚠️ 仅在实施完成、验证通过后才做**——这两处实测都是**已交付清单**（README 86 行全 ✅、roadmap 84 项全勾、零未勾选），提前登记会造出表里唯一的非 ✅ 项
  → **✅ 已登记（前置条件已满足：T001–T016 验证通过，仅 T014 的容器构建部分本机不可执行）**：
  - `specs/README.md`：① 第 3 行**版本行**追加 089 子句；② **模块清单表**在 087 行后追加 089 行（6 列齐）；③ **编号说明**追加 089 段（含"下方迁移对照表没有 089 行"的理由）；④ **迁移对照表不动**（无迁移）
  - `specs/roadmap.md`：① `**最后更新**` 追加 089 交付记录；② `**整体覆盖度**` 订正（见下）；③ `## 当前进度` 追加 `- [x] 089-jdk21-upgrade（…）`
  > **⚠️ 登记时发现三处既有漂移，如实报告、未替他人认领**（不静默修，也不静默略过）：

  | 规格 | 已提交 | `tasks.md` | README 模块表 | roadmap 当前进度 |
  |---|---|---|---|---|
  | 086 | ✅ `b7ede96` | **0 未勾选** | 有行 | **缺行** |
  | 087 | ✅ `048a884` | **0 未勾选** | 有行 | **缺行** |
  | 088 | ✅ `048a884` | **13 项未勾选** | **缺行** | **缺行** |
  | 089 | 本次 | 本次 | **本次已加** | **本次已加** |

  - 086/087 **已交付且已进 README 模块表，但从未进 roadmap 的 `## 当前进度`** —— 属既有登记遗漏
  - 088 **已提交但 `tasks.md` 尚有 13 项未勾选**，故按"登记 = 已交付清单"的口径**不登记**；它是**并行会话的在飞工作**，登记与否应由其自身收口时决定
  - **我未代改 086/087/088 的任何一行**：本规格的范围是 089；跨规格补登记会与那两个规格的收口过程互相干扰。**此项留给用户裁决**
  - 连带订正：`**整体覆盖度**` 原写「001–085 全部交付」，而 086/087 实为已交付 → 改为「**001–087 与 089 已交付；069 编号空缺未创建；088 仍在实施中（`tasks.md` 尚有 13 项未勾选），故未登记**」

- [x] T019 把 quickstart.md 的 7 组验证**在改造后的真实工作区完整复跑一遍**，把 `📋待执行` 标记逐条改为实测结果（含时间、命令、原始输出），与 `✅实测` 的条目区分来源——**订正不静默、原文留痕**

  **✅ 完成。`📋待执行` 标记已归零**（全文件反查 0 处）。8 个分组的最终标注：

  | 组 | 标注 | 证据来源 |
  |---|---|---|
  | 0 可行性自检 | ✅实测（隔离副本） | 立项期 `/tmp/jdk21-verify` |
  | 1 全量编译 | ✅实测（隔离副本 + 真实工作区各一次） | T004 |
  | 2 验证生命周期 + 门禁判定 | ✅实测（真实工作区） | T006 判据表 |
  | 3 **应用能启动** | ✅实测（真实工作区，H2 隔离实例） | T005：端口 18099 + `test-run` |
  | 4 逐处声明一致 | ✅实测（真实工作区） | T012/T013 + 反查 |
  | 5 镜像两阶段与标签 | ⚠️部分实测（标签已验；容器构建 ⛔） | T010 镜像源 tags/list |
  | 6 反向验证（门禁有牙齿） | ✅实测（真实工作区） | T007 对拍 |
  | 7 留痕可读性 | ✅已做（证据强度有限，见下） | T016 三问答 |
  | 附：一条假红 | ✅实测 | 编译产物被 IDE 覆盖的假红形态 |

  **本轮修改的关键一处是第 3 组**：原文写的是 `mvn -B -DskipTests spring-boot:run` + `curl :8081/actuator/health`，而**实际做的是** `spring-boot:test-run` + 显式 H2 参数 + 端口 18099。**原文保留在上，未删除**——因为这条错误方法正是 T005 首次尝试踩坑的原因，把它从文档里抹掉等于让后来者再踩一次。第 3 组现同时载有「正确起法」「为什么不用 `run`」「实测结果表」「那次失败尝试的完整留痕（含未写入共享库的逐条排查与 pid 5288 消失）」。

  **口径披露**：第 5 组的「⚠️部分实测」是**真·部分**，不是措辞保守——本机无 Docker（`docker: command not found`），容器构建那一半**确实没做**，`docker build/run` 已列为 T014 的悬置半项。


- [x] T020 提交。**先 `ListAgents`**；**只用显式路径** `git add specs/089-jdk21-upgrade/ backend/pom.xml Dockerfile .github/workflows/ci.yml README.md INSTALL.md PROJECT_FEATURES.md`；**绝不** `git add -A` / `git commit -a`。提交信息遵循 Conventional Commits，结尾附 `Co-Authored-By: Claude Code <noreply@anthropic.com>`

  **✅ 完成，提交 `5252e20`**（16 files changed, 1491 insertions(+), 15 deletions(-)）。实际暂存路径比本条多两处：`specs/README.md` 与 `specs/roadmap.md`（T018 的登记面，本条的清单里漏列了，已补）。

  **`ListAgents` 结果与隔离确认**：当时有 1 个并行会话 `crm-gap-remediation`（waiting）。它与本规格无关的两处改动 `frontend/vite.config.ts`、`specs/083-engineering-consolidation/data-model.md` **未被暂存**——提交后 `git status --porcelain | grep -v '^[AM]'` 仍原样显示为 ` M`，**逐条核对过**。


---

## Dependencies & Execution Order

### 阶段依赖

- **Setup (Phase 1)**：无依赖，可立即开始。**T002 是闸门**——不通过则整个实施停止
- **Foundational (Phase 2)**：依赖 Setup。**阻塞所有用户故事**
- **User Stories (Phase 3–6)**：均依赖 Foundational。US1 与 US2 是 P1 且都必须先做；US3、US4 相对独立
- **Polish (Phase 7)**：依赖所有用户故事完成。**T018 必须最后做**（它的前提是"已交付"）

### 用户故事之间的依赖

- **US1 (P1)**：Foundational 之后即可，无其它依赖
- **US2 (P1)**：Foundational 之后即可。**它与 US1 共享同一个前提**（编译目标为 21），但验证目标不同——US1 问"能不能起"，US2 问"绿得真不真"
- **US3 (P2)**：改动**独立于** US1/US2（不同文件），可与它们并行；但它的**验证**（T014 反查）在任何时候都成立
- **US4 (P3)**：**不依赖任何其它故事**——留痕文件在立项阶段就已产出，T015/T016 是核对与阅读实验，随时可做

### 关键顺序约束

1. **T002 先于一切**（可行性哨兵）
2. **T003 先于 T004–T009**（编译目标是验证前提）
3. **T010 先于 T011**（先验标签存在，再改 Dockerfile）
4. **T004 先于 T005 但不足以替代它**（编译过 ≠ 能起，FR-J03）
5. **T007 做完必须改回 0.73** 并用 `git diff` 确认
6. **T008/T009 若发现依赖变更或测试改动 → 停止并报告**（FR-J12 范围守卫）

### 并行机会

- **T008** 与 T006/T007 可并行（只查 `git diff`，不依赖构建输出）
- **T011 / T012 / T013** 三者**文件互不重叠**，可并行
- **US3 与 US4 全部**可与 US1/US2 并行（不同文件、无共享状态）

---

## Parallel Example: US3 的三处改动

```bash
# 三者改的是不同文件，可同时进行：
Task: "改根 Dockerfile 两处 FROM → 21"
Task: "改 .github/workflows/ci.yml 四处（两个 java-version + 两个步骤名）"
Task: "刷新 README.md / INSTALL.md / PROJECT_FEATURES.md 的技术栈与前置条件行"
```

---

## Implementation Strategy

### MVP（US1 + US2）

本规格的 MVP **必须包含两个 P1**——只做 US1 会漏掉整个门禁维度，而"假绿"正是本项目反复踩到的形态。

1. Phase 1（T001–T002）：确认基线可用、可行性依据成立
2. Phase 2（T003）：改编译目标
3. Phase 3（T004–T005）：编译 + **启动**
4. Phase 4（T006–T009）：完整验证 + 门禁判定 + 依赖零变化
5. **STOP and VALIDATE**：此时后端已达到可交付状态

### 增量交付

1. Setup + Foundational → 基线就位
2. US1 + US2 → **后端在新基线上全绿**（这是本规格的实质交付）
3. US3 → 声明一致、镜像与 CI 同步
4. US4 → 留痕核对
5. Polish → 分支、登记、复跑、提交

### 一条预先声明的停止条件

实施中若出现下列任一情形，**停止并报告，不得就地扩围**：

- T002 的可行性依据不成立（class major > 66）
- 需要升级任何依赖版本才能通过（FR-J12）
- 出现失败用例且根因是"实现依赖了旧基线的行为"——此时按 spec「澄清 Q2」处置（改实现回到契约、逐例留痕），**不得**删用例/放宽断言/标记跳过（FR-J09、FR-J10）

---

## Notes

- **[P] 任务 = 不同文件、无未完成依赖**
- **每个用户故事都可独立验证**——但 US1 与 US2 共享同一个 Foundational 前提
- **预期零源码改动**：`backend/src/**` 应完全不动。若动了，说明 T002 的可行性依据被证伪，回看停止条件
- **本规格不产 `contracts/`、不产 `data-model.md`**（plan.md「Structure Decision」已记录理由）
- **每条验证任务的「改造前表现」不是装饰**：它是"这条检查有没有区分度"的证明。本项目已有多次"门禁名义存在、实际失效"的前科（见 research.md R7 的四种形态）
