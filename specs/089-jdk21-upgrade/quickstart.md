# 快速验证指南：把项目从 JDK 17 升级到 JDK 21

**归属**：`specs/089-jdk21-upgrade` · **创建**：2026-09-13

每组都附 **「改造前表现」**——用来证明**这条验证本身有区分度**。一条在所有情况下都通过的检查，等于没检查。

> **标注约定**（2026-09-13 更新，实施完成后复跑）：标 **✅实测** 的步骤已真实执行并取得结果，括号内注明**在哪跑过**——「隔离副本」= 立项期的 `/tmp/jdk21-verify` 副本；「真实工作区」= 实施完成后在 `E:\code\crm` 上的复跑。标 **⛔** 的是本机不可执行的步骤。

---

## 前置条件

**装新基线**：JDK 21（LTS）。本机已装于 `C:\Users\Administrator\.jdks\jdk-21.0.12.1+1`（用户目录、不在 `PATH`）。

> **⚠️ 订正（2026-09-13）：本节原文写着"不要改系统 `JAVA_HOME`"，该前提已被裁决变更。原文保留如下，订正见后。**
> ~~**不要改系统 `JAVA_HOME`**：它当前指向 `C:\Program Files\Java\jdk-17`，同机还有其它会话在用。用**每次命令临时指定**的方式跑新基线，不要做全局改动。~~
>
> **订正后的现状**：项目负责人于 2026-09-13 裁决把**用户作用域**的 `JAVA_HOME` 切到 `C:\Users\Administrator\.jdks\jdk-21.0.12.1+1`（理由：本地 pom 与运行环境一致；代价"同机其它会话的后台 maven 随之改变"已告知并接受）。**实测现状**：
> - `JAVA_HOME` = JDK 21 → 经它启动的 maven 报 Java 21（`mvn -v` 实测）
> - ⚠️ **裸 `java -version` 仍是 17.0.12** —— Machine PATH 中 `C:\Program Files\Common Files\Oracle\Java\javapath` 排在所有 User PATH 之前，该垫片指向 17。**凡以裸 `java` 为准的检查会被误读成"还是 17"**；应以 `mvn -v` 或 `"$JAVA_HOME/bin/java" -version` 佐证。**此点尚未解决。**

```bash
# 需要显式指定新基线时（仍然可用，且在与他人共用机器时更稳妥）
export JDK21="C:/Users/Administrator/.jdks/jdk-21.0.12.1+1"
"$JDK21/bin/java" -version      # 期望输出含 21.0.12
mvn -v                          # 期望 Java version: 21.0.12
```

**旁路条件**：集成测试需要 MySQL/Redis 可用的既有环境；跑完整 `verify` 时若本机依赖服务未起，先确认后端连接可用。

---

## 第 0 组：先自检"可行性依据"是否成立（动手之前）✅实测（隔离副本）

本项的全部可行性建立在一条**可测量的字节值**上（research.md R1）：新基线的 class 文件版本必须落在框架内置 ASM 的读取上限内。

```bash
# ① 新基线产出的 class 版本号
"$JDK21/bin/javap" -v <某个 .class> | grep 'major version'    # 期望 65

# ② 框架内置 ASM 的读取上限：用业务代码编译出的类做真实扫描探针，
#    而不是只看文档。探针只要能通过组件扫描即可。
```

**判据**：①的结果（65）**小于等于** ②的上限（66）。若这条不成立，**停下**——说明前提已变，本项方案不适用，应重新裁决而不是就地调参。

**改造前表现**：无（这是准备步骤）。但**其价值有反证**：同样的探针在 major 67/68/69 上全部抛 `Unsupported class file major version`，真实扫描报 `BeanDefinitionStoreException` —— 所以这条检查确实能区分。

---

## 第 1 组：干净构建目录，全量编译 0 错误 ✅实测（隔离副本 + 真实工作区各一次）

```bash
cd backend
mvn -B clean compile            # 必须先 clean：增量编译会掩盖问题
```

**期望**：`BUILD SUCCESS`，且日志含 `565 source files with javac [debug release 21]`。

**改造前表现**：同一条命令在 JDK 25 上 → **200 个错误 / 5 个文件**（Lombok 生成物全部消失）。

> ⚠️ 本组通过**不能**作为升级完成的判据（FR-J03）。理由见第 3 组。

---

## 第 2 组：完整验证生命周期 —— 退出码 0 且门禁真的判定过 ✅实测（真实工作区）

```bash
cd backend
mvn -B verify ; echo "EXIT=$?"
```

**期望（三项同时成立，缺一不可）**：

1. **`EXIT=0`**
2. 日志含覆盖率门禁的**结论行**：`All coverage checks have been met.`
   —— 同时应能看到 `jacoco:0.8.11:check (coverage-check)` 这行，证明该目标被**执行**过
3. **单元与集成都真的跑了**：`555 run / 0 failures / 0 errors`（单元）+ `284 例全绿`（集成），且 `target/failsafe-reports/` 下有 **75 份** XML

```bash
ls target/failsafe-reports/*.xml | wc -l     # 期望 75
```

**改造前表现**：该项目曾长期处于"覆盖率门禁**从未被判定**"的状态（`jacoco:check` 与 `failsafe:verify` 同绑 `verify` 且声明其后，前者失败即中止），此时**退出码非 0 且日志中不存在结论行**。

> ⚠️ **不要传 CLI `-DargLine`**：它会挤掉 JaCoCo 代理，`jacoco.exec` 不生成、覆盖率门禁空过，而**构建全程成功、无任何报错**。

**✅ 实测结果（2026-09-13，真实工作区，`mvn -B clean verify` 退出码 0）**：

| 判据 | 实测值 |
|---|---|
| ① 退出码 | **0**；日志 `BUILD SUCCESS` |
| ② 门禁被执行 | `jacoco:0.8.11:check (coverage-check) @ crm-backend` 出现在日志 |
| ② 门禁结论行 | **`All coverage checks have been met.`** |
| ③ 单元测试 | **555 / 0F / 0E** |
| ③ 集成测试 | **284 / 0F / 0E**；`target/failsafe-reports/*.xml` = **75** 份 |
| 附：覆盖率 | **0.8009**（covered 45421 / total 56713，由 `target/site/jacoco/jacoco.csv` 汇总）≥ 0.73 |
| 附：格式门禁 | `spotless-check` → `732 files clean - 0 needs changes` |
| 附：JaCoCo 代理确实挂上 | `argLine set to -javaagent:...org.jacoco.agent-0.8.11-runtime.jar=destfile=...jacoco.exec...`；`jacoco.exec` **1 407 346** 字节 |
| 假绿反查 | `Nothing to compile` **0** 次；`Unsupported class file major version` **0** 次 |

> **覆盖率由隔离副本实测的 0.7516 / 0.7818 升至 0.8009，不是本规格带来的行为变化**——是同期其它会话新增用例所致（分子随用例增加而增长、分母基本不变），即 pom 注释里已预言的单向漂移。**门禁阈值仍取 0.73 未动。**

---

## 第 3 组：应用在新基线上**能启动** ✅实测（真实工作区，H2 隔离实例）

**这是本规格最关键的一组**——它覆盖的是"编译过 ≠ 能起"这个缺口。

```bash
cd backend
# 推荐起法：test-run 用「测试 classpath」起应用，从而带进 H2 与 application-test.yml
mvn -B spring-boot:test-run \
  '-Dspring-boot.run.arguments=--server.port=18099 --spring.profiles.active=test \
   --spring.datasource.url=jdbc:h2:mem:crm;MODE=MySQL;NON_KEYWORDS=YEAR,USER;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE \
   --spring.datasource.driver-class-name=org.h2.Driver --spring.datasource.username=sa --spring.datasource.password= \
   --spring.flyway.enabled=false --spring.sql.init.mode=always --spring.sql.init.schema-locations=classpath:schema-h2.sql \
   --spring.data.redis.database=15 --jwt.secret=<≥32 字符的强密钥>'
# 另一个终端：
curl -fsS http://localhost:18099/actuator/health
```

**为什么不用 `spring-boot:run`**：`-Dspring-boot.run.useTestClasspath=true` **不会**把 `target/test-classes` 放进运行 classpath，`application-test.yml` 因此不生效（只有 profile 的**名字**生效）→ 实例会连上**共享开发库**。`test-run` 是 Boot 3.2 起新增的 goal，专为"用测试 classpath 起应用"而设。**本指南把这条写下来，就是因为它已经被踩过一次**（见下）。

**期望**：应用完成组件扫描并正常提供既有端点；健康检查返回成功。

**✅ 实测结果（2026-09-13，真实工作区，端口 18099）**：

| 探针 | 结果 |
|---|---|
| 启动 | `Tomcat started on port 18099 (http)` + `Started CrmApplication in 3.795 seconds` |
| 组件扫描 | **无** `Unsupported class file major version` / `BeanDefinitionStoreException` |
| `GET /actuator/health` | **HTTP 200** `{"status":"UP","groups":["liveness","readiness"]}` |
| `POST /api/v1/auth/login`（admin/admin123） | **HTTP 200**，返回真实 `accessToken` + `refreshToken` |
| `GET /api/v1/users` 带该 token | **HTTP 200**，返回真实数据（`admin` / 系统管理员）——证明 `DataInitializer` 这条 `ApplicationRunner` 链路在新基线上完整可用 |
| 数据源 | `jdbc:h2:mem:crm`；**全日志中 MySQL 出现 0 次** |

> **⚠️ 一次失败的尝试与它暴露的问题（如实留痕）**：首次尝试用 `spring-boot:run` + `-Dspring-boot.run.useTestClasspath=true` + `SPRING_PROFILES_ACTIVE=test`。该参数**未**把 `target/test-classes` 放进运行 classpath，`application-test.yml` 从未加载。后果：实例连上了**共享开发库 `jdbc:mysql://localhost:3306/crm_db`**。
> **写入面已逐条排查，未产生任何写**：Flyway 输出 `Current version of schema crm_db: 88` / `No migration necessary`；`DataInitializer` 仅在 `user` 表为空时 insert，日志中无 `Seeded default admin user` 行；`WebhookDeliverySweepScheduler` 全程 0 条日志且其 cron（`0 */5 * * * ?`）不在实例存活窗口内。
> **但"隔离"确实没做到，这一点不因"没写"而抵消。** 正确起法已固化为上面的 `test-run` + 显式 H2 参数（任何情况下都不会落到 MySQL）。
> **同一次事故的第二项副作用**：期间执行的 `mvn -B clean` 删除了 `backend/target/classes`，而 8081 上原有一个 java 实例（pid 5288，21:49:03 启动）**随之消失**。该实例归属无法确认。**未**重启它。

**改造前表现（两种，都要知道）**：

- 在 JDK 25 上：**根本起不来**，扫描阶段即报 `BeanDefinitionStoreException: Failed to read candidate component class ... Unsupported class file major version 69`。**而它的编译是成功的**（565 文件 BUILD SUCCESS）——这正是那批改动被漏掉的原因。
- 在 JDK 21 上：正常启动。原因是 major 65 落在 ASM 上限（66）之内。

---

## 第 4 组：逐处声明一致（5 执行性 + 2 步骤名 + 3 文档）✅实测（真实工作区）

```bash
cd <repo root>

# 执行性声明点：期望只剩 21
grep -n 'java.version' backend/pom.xml
grep -n 'FROM' Dockerfile
grep -n 'java-version' .github/workflows/ci.yml

# 步骤名称：不应再有 "JDK 17"
grep -n 'Set up JDK' .github/workflows/ci.yml

# 描述性声明：技术栈与前置条件行
grep -n 'JDK 17\|Java 17' README.md INSTALL.md PROJECT_FEATURES.md

# 反查：全仓还有哪些地方写着 17（应只剩历史记录）
git grep -nI 'temurin-17\|java-version.*17\|java\.version>17' -- . \
  | grep -v '^specs/' | grep -v 'Spring-Boot-React-CRM/'
```

**期望**：前四条**零命中旧值**；最后一条的反查结果为空（`specs/` 与 `Spring-Boot-React-CRM/` 是**刻意保留的历史记录**，见 FR-J13 边界）。

**改造前表现**：当前这 5 处执行性声明点**全部**是 17（`backend/pom.xml:21`、`Dockerfile:2`、`Dockerfile:15`、`ci.yml:18`、`ci.yml:129`），步骤名两处写死 "Set up JDK 17"，三份文档均为 "Java 17"。

> **为什么 e2e 作业那一处不能漏**：它用 `mvn spring-boot:run` 起后端，漏改则 e2e 整体跑在旧基线上——而 e2e 是唯一覆盖真实浏览器链路的门禁。
> **这一前提已在改文件前核对为真**：`ci.yml:157` 原文即 `nohup mvn -B -DskipTests spring-boot:run > /tmp/backend.log 2>&1 &`。

**✅ 实测结果（2026-09-13，真实工作区）**：前四条**零命中旧值**；最后一条反查**非空但全部落在边界内**。

| 检查 | 实测 |
|---|---|
| `grep -n 'java.version' backend/pom.xml` | `21: <java.version>21</java.version>` |
| `grep -n 'FROM' Dockerfile` | `2: FROM maven:3.9-eclipse-temurin-21 AS builder` / `15: FROM eclipse-temurin:21-jre-alpine` |
| `grep -n 'java-version\|Set up JDK' ci.yml` | `14: Set up JDK 21` / `18: java-version: '21'` / `125: Set up JDK 21` / `129: java-version: '21'` |
| `grep -n 'JDK 17\|Java 17' README.md INSTALL.md PROJECT_FEATURES.md` | **零命中** |
| 全仓反查 | **67 行残留，全部在 `specs/`(66) 与 `Spring-Boot-React-CRM/01-项目背景与技术选型.md`(1) 之内 → 边界成立**；两处之外**零残留** |

> **残留性质**（抽样确认）：**61/67 是各 spec `plan.md` 的 `Language/Version` 声明行**（历史记录），其余为 017 的 `research.md`、3 份 `quickstart.md`、1 份项目背景文档。**`specs/089-jdk21-upgrade/` 自身零残留。**

> **⚠️ 一次被自己的量具骗到的假通过（留痕，因为它正是第 7 组要防的形态）**：首次反查我写的过滤器是 `grep -v '^Spring-Boot-React-CRM/'`，**它没滤掉该文件**——因为 `git grep` 对非 ASCII 路径会输出**带前导双引号**的路径（`"Spring-Boot-React-CRM/01-…"`），锚点 `^Spring-…` 因此失配。当时屏幕打出的「反查为空 ✅」是**量具坏了**，不是真的空（真值 67 行）。**教训：过滤器也要自证——先用一条必然命中的探针确认它真的能滤掉该滤的东西。**

---

## 第 5 组：镜像两个阶段一致且标签真实存在 ✅实测（2026-09-14 补做容器构建；原文的 ⛔ 判词已不成立）

```bash
# ① 先验证目标标签存在（改文件之前！）
docker manifest inspect maven:3.9-eclipse-temurin-21      >/dev/null && echo OK-builder
docker manifest inspect eclipse-temurin:21-jre-alpine     >/dev/null && echo OK-runtime

# ② 再构建
docker build -t crm-backend:jdk21-check .
docker run --rm crm-backend:jdk21-check java -version     # 期望 21  ← ⚠️ 这行是错的，见文末订正
```

**期望**：两个标签都存在；镜像构建成功；容器内 `java -version` 为 21。

> **⚠️ 订正（2026-09-14，原文保留在上不静默改写）**：上面第 ② 步的最后一行
> **测不到它声称的东西**。`Dockerfile:40` 的 ENTRYPOINT 是 **exec 形式**的
> `["sh","-c","java $JAVA_OPTS -jar app.jar"]`，镜像名之后附加的参数会被 `sh -c`
> 当成 `$0`/`$1`，**不会被执行** —— 于是这行命令**不是打印版本，而是把应用整个启起来**。
> 正确的写法要覆盖 entrypoint：
> ```bash
> docker run --rm --entrypoint java crm-backend:jdk21-check -version
> ```

**改造前表现**：`Dockerfile:2` 与 `:15` 分别为 `...temurin-17` 与 `eclipse-temurin:17-jre-alpine`，容器内 `java -version` 为 17。**注意**：运行阶段的 `FROM` 是**唯一**决定容器里跑什么的那一处——只改构建阶段会让本地/CI 与容器分叉。

> ⚠️ 标签存在性**本轮未验证**（research.md R6 已如实标注）。若本地无 Docker，这一步必须在流水线之前完成，不要留给流水线发现。
>
> **〔2026-09-14 补记：本条已不成立，原文保留〕** 标签存在性当日即用另一条通路验过（见下），
> 2026-09-14 更由 `docker build` **真的拉取**证实；「本地无 Docker」也只对 Windows 侧 shell 成立——
> WSL 里 Docker 是装着的。**该注意点的来源是「本 shell 不可执行」被写成了「本机不可执行」。**

**实测结果（2026-09-13，第一轮；容器构建部分见其后 2026-09-14 补做一节）**：

- **① 标签存在性 —— 已验，但换了通路**。本机**无 Docker**（`docker: command not found`），且 `registry-1.docker.io` / `hub.docker.com` **直连超时**（HTTP 000；同机 `repo.maven.apache.org` 与 `registry.npmjs.org` 均 200，故是这两个域被挡，非整体断网）。`docker manifest inspect` 在本机走不通。改用可达的镜像 registry v2 接口 `GET https://docker.1panel.live/v2/library/{maven,eclipse-temurin}/tags/list`：
  - `maven:3.9-eclipse-temurin-21` → **命中**
  - `eclipse-temurin:21-jre-alpine` → **命中**
  - **阴性对照**：`...temurin-99` 与 `99-jre-alpine` 在同一份响应里均 **0 命中** —— 证明检索方法有区分度
  - **列表真伪**：不是缓存残片 —— `maven` 返回 **1892** 个标签、`eclipse-temurin` 返回 **3373** 个，且 `3.9-eclipse-temurin-` 系列呈完整递进（`-8 -11 -17 -19 -20 -21 -22 -23 -24 -25 -26`）
  - **口径限制**：这是**第三方镜像**的标签列表，与上游同步存在时延可能。它足以否掉"标签不存在"这一风险，**不构成对上游字节级同一性**的证明
- **② 容器构建 —— ⛔ 未执行，本指南不主张已验**：`docker build` 与容器内 `java -version` 必须在有 Docker 的机器或 CI 上补做。

**实测结果（2026-09-14 补做）✅ 已验**：

> 上面那条 ⛔ 的判词**已不成立**，原文保留。它把「**本 shell** 不可执行」写成了「**本机**不可执行」——
> `docker: command not found` 是 **Windows 侧** shell 的结论，而**同一台机的 WSL 里 Docker 是装着的**
> （`docker --version` → **29.1.3**，Ubuntu-22.04）。这是一处**口径扩大**，与 089 自己反复强调的
> 「量具要先自证」同源。动手前核过共享环境未被扰：`mysqld`/`redis-server` 已跑 1 天 6 小时、
> 后端 `:8081` 返回 **HTTP 200**；构建**在镜像内**编译 `backend/src` 的副本，**不碰宿主的 `backend/target/`**。

- **① 标签存在性 —— 由「列表里存在」升级为「真的拉下来了」**：`docker build` 成功（**exit 0**，镜像 **370MB**），
  两个 `FROM` 均实拉。**证据强度比 2026-09-13 那次高一个量级**——那次只证明标签在第三方镜像站的**列表**里，
  这次是构建器真的解析并下载了它们。
- **② 容器内为 21 —— 已验，且分四条独立证据**：

  | 被测对象 | 命令 | 实测 |
  |---|---|---|
  | 构建阶段镜像的 java（**jar 就是在这一层编译的**） | `docker run --rm maven:3.9-eclipse-temurin-21 java -version` | `openjdk version "21.0.12" 2026-07-21 LTS` / `Temurin-21.0.12+8` |
  | 运行阶段镜像的 java | `docker run --rm --entrypoint java crm-backend:jdk21-check -version` | **同上 21.0.12 LTS** |
  | 镜像内 jar 的**字节码主版本** | `unzip -p /app/app.jar <class> \| od -An -tu1 -j6 -N2` | 抽样 3 份**均 `0 65`** |
  | **应用进程自己报的运行时** | `docker run --rm crm-backend:jdk21-check` | `using Java 21.0.12 with PID 1 (/app/app.jar started by crm in /app)` |

  - **主版本的取法经过两点校准**（否则 `65` 只是我认得的一个常数）：同一个 `od` 取法读同机 `javac` 编出的样本
    → `--release 17` ⇒ **`0 61`**、`--release 21` ⇒ **`0 65`**。故 jar 内的 `65` 是**自证的**。
  - **「运行时是 21」与「字节码目标是 21」是两件事**：只换运行阶段镜像而编译目标停在 17，容器照样跑得起来。
    上表第 3 行才是「编译目标」那一侧的证据。
  - **原文那条错命令的行为已实测**：`docker run --rm crm-backend:jdk21-check java -version` →
    机制上 `sh -c 'echo argv0=$0 argv1=$1' java -version` 给出 **`argv0=java argv1=-version`**；
    照原文执行则进入完整 Spring Boot 启动，50 秒后被 `timeout` 截断（**退出码 141**），**没有一个字**是版本号。
  - **一个反讽的收获**：照那条**错**命令跑出的启动日志，反而是本次**最强**的一条证据——
    `using Java 21.0.12` 是**应用进程自己**在 `main` 里报出的运行时。

---

## 第 6 组：反向验证 —— 证明这些门禁真的有牙齿 ✅实测（真实工作区）

一条只在成功时"看起来通过"的检查是不可信的。用**故意破坏**证明它能红：

```bash
cd backend
mvn -B verify -Djacoco.skip=false -Dmaven.test.failure.ignore=true \
    -Djacoco.threshold.override=0.99 2>&1 | tail -20
```

**期望**：日志中**出现失败判定**（覆盖率未达阈值 → 构建失败）。

**改造前表现**：该门禁的阈值曾设为 0.30，而实测覆盖率 0.7516 —— **距实测 45 个百分点，等于不设防**，怎样都不会红。现阈值为 0.73。本组的目的就是确认它现在**会**红。

> 更简单的等价做法：临时把 `pom.xml` 的 `<minimum>` 调到高于实测值，确认 `verify` 失败，再改回。**改完必须改回**。

**✅ 实测结果（2026-09-13，真实工作区，走上面"更简单的等价做法"：把 `<minimum>` 临时改为 0.99 后跑 `mvn -B verify`）**：

| | 阈值 0.73（正常） | 阈值 0.99（故意破坏） |
|---|---|---|
| 构建结论 | `BUILD SUCCESS` | **`BUILD FAILURE`** |
| 失败点 | — | `Failed to execute goal org.jacoco:jacoco-maven-plugin:0.8.11:check (coverage-check) on project crm-backend: Coverage checks have not been met.` |
| jacoco 判定原文 | `All coverage checks have been met.` | **`[WARNING] Rule violated for bundle crm-backend: instructions covered ratio is 0.80, but expected minimum is 0.99`** |
| 结论行出现次数 | **1** | **0** |

两条额外收益：① 该次失败发生在**同相位的 spotless 与 failsafe 都通过之后**，说明失败**确实由覆盖率判定触发**，不是更早的门禁顺带失败；② `Analyzed bundle 'crm-backend' with 233 classes` 证明 jacoco 真的解析了产物，不是空跑。

**已还原**：`<minimum>` 改回 **0.73**；`git diff backend/pom.xml` 实测 = `1 file changed, 1 insertion(+), 1 deletion(-)`，只余 `java.version` 那一行。

---

## 第 7 组：留痕可读性 —— 后来者能否判断"为什么不是 25" ✅已做（证据强度有限，见下）

**不需要跑命令**，是一次阅读实验：

1. 只读 `spec.md` 的「背景」与「澄清 Q1」，能否说出"为什么不是 25"？
2. 只读 `jdk25-investigation.md`，能否复述 25 的四条阻塞点？
3. 能否指出 `--release 22` 为什么被否决？

**期望**：三个问题都能答出，且不需要翻会话记录或聊天历史。

**改造前表现**：那批改动被退回时，"为什么"只存在于会话上下文里——**没有任何仓库内文档承载它**。本组确保这次的结论不会同样丢失。

**✅ 已做（2026-09-13），三问均可答出，出处如下**：

1. `spec.md:8-35`（背景）与 `spec.md:172-182`（澄清 Q1）→ 可说出"为什么不是 25"：25 须跳框架大版本线（四条阻塞点），21 零依赖升级；**分界点是一条可测量的字节值**——class major 65 落在 `spring-core 6.1.1` 内置 ASM 的读取上限 66 内，25 的 69 在外
2. `jdk25-investigation.md` §1.1–§1.4 → 可复述四条阻塞点（编译 200 错误 / 5 文件；格式门禁 0.97 秒失败；`major 69` 起不来；`540 例 / 482 错误`）
3. `jdk25-investigation.md` §2.3 → 可指出 `--release 22` 为什么被否决：它**技术上确实能绕开** ASM 上限与大部分测试失败（因 byte-buddy 判定的是**被 mock 类的字节码版本**、不是运行 JVM 版本），但**不减少任何必需工作**、把项目置于官方不支持的组合上、回避真正的目标、并让"我们在 JDK 25 上"成为不准确的自我描述 → 记为**伪解**

> ⚠️ **本项证据强度的诚实标注**：这三个答案是我作为**这些文档的作者**给出的，因此只证明"文档里确实写了这些内容、且能从中检索到"，**不构成"另一个读者也能读懂"的证明**。本组主张的"后来者能否判断为什么不是 25"，严格验证需要一个未参与本次工作的读者做一次**盲读**。**此处不作更强主张。**

---

## 附：一条会伪装成失败的**假红**（务必先看这条）✅实测

首次在**隔离副本**中跑单测时出现 **16 个 Error**。它**与 JDK 21 无关**。

- **根因**：`com.crm.support.MenuReadPermissionTestSupport` 会从 `user.dir` 向上寻找一个**同时含 `frontend/` 与 `backend/` 的目录**。
- **触发条件**：把副本建成了扁平结构（既无 `frontend/` 也无 `backend/`）。
- **修法**：副本必须保持 `backend/{pom.xml, src}` + `frontend/` **同级**（可用 Windows junction 指向真实 `frontend/`，只读使用）。
- **修好后**：**555 / 0 / 0**。

> **教训**：假红与假绿一样危险——它会让人去修不存在的问题，或误判方案不可行。**遇到失败先确认测量工具本身**，再谈归因。
