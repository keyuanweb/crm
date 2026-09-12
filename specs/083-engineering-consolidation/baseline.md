# 改造前基线证据

**Branch**: `083-engineering-consolidation` | **Date**: 2026-09-12 | **任务**: T001–T003

本文档固定**任何改动之前**的实测证据。quickstart.md 的每条验证都以"改造前表现"证明验证本身有区分度——若不先捕获，改造后将**无法证明**门禁真的从失效变为生效。

原始日志：`/tmp/083-baseline/{mvn-verify-before,eslint-before,coverage-before}.log`

## 1. 后端构建（T001）

```
cd backend && mvn -B verify
```

| 观测项 | 实测值 | 原始预期 | 判定 |
|---|---|---|---|
| 退出码 | **1**（`BUILD FAILURE`） | 未预期 | **新增事实** |
| 中止相位 | **`test`**（surefire） | 未预期 | **新增事实** |
| 用例统计 | Tests run: **426**, Failures: **24**, Errors: **1** | 未预期 | **新增事实** |
| `target/failsafe-reports/` | **不存在** | 不存在 | ✓ 一致 |
| `target/surefire-reports/*.txt` | **80 份** | 0 份 | ✗ **文档有误** |
| `target/test-classes/**/*IT.class` | 60 个（全部位于 `integration/` 包） | 60 个 | ✓ 一致 |
| `target/jacoco.exec` | 存在（619 KB） | — | — |
| `target/site/jacoco/index.html` | **不存在** | — | **新增事实** |

**关键推论**：`jacoco:check` 绑在 `verify` 相位，而构建在更早的 `test` 相位即中止——**覆盖率门禁从未被执行过**。它不只是"阈值宽松"，而是**不可达**。`report` 目标同样从未产出报告，故配置注释所称的 0.36 **从未被任何绿色构建核对过**。

### 1.1 失败用例归因（25 例）

| 类别 | 数量 | 代表 | 根因 | 归属 |
|---|---|---|---|---|
| **环境类** | **22** | `CustomerContractTest`、`LeadContractTest`、`UserContractTest`、`LeadITTest` 全部经由 `AbstractIntegrationTest.loginAndGetToken` | 登录返回 **500**；日志中 `org.h2.jdbc.JdbcSQLSyntaxErrorException: Column "email" not found` —— `user` 表缺 V76 所加的列 | **本规格**（US1 镜像工作清零） |
| **业务类** | **3** | `DataRetentionPolicyServiceTest:70`、`ScheduledExportServiceTest:98`、`:162` | 纯 Mockito 单元测试（`@ExtendWith(MockitoExtension.class)`，不涉数据库）：打桩 `repository.insert(...)→1` 后断言服务回填自增主键，实际未回填（`expected: <1> but was: <null>` / NPE） | **不属本规格**，按澄清裁决转独立规格 |

环境类归因与 spec.md 的判断完全吻合：**22 例全部指向 H2 镜像缺失**，正是 US1 的修复目标。故按裁决，"因环境原因失败的用例数为 0"在本规格内是可达的。

### 1.2 覆盖率基线（仅单元测试口径）

为取得 SC-G02 所需的对照值，以忽略失败的方式强制产出报告：

```
mvn -B test -Dmaven.test.failure.ignore=true org.jacoco:jacoco-maven-plugin:0.8.11:report
```

| 计数器 | 覆盖比 |
|---|---|
| **INSTRUCTION** | **0.5093**（covered 27 920 / total 54 818） |
| BRANCH | 0.3807 |

**此即 SC-G02 所称"改造前仅按单元测试口径统计的数值"。** 集成测试贡献为 **0**（failsafe 从未执行）。改造后该数值必须**严格高于** 0.5093 方证明集成测试覆盖率已计入。

> 方法学说明：该次运行叠加在同日的 `verify` 运行之上（JaCoCo `append` 默认为真），两次均为 surefire-only，故不影响口径。

## 2. 前端（T002）

```
cd frontend && npx eslint .
```

| 观测项 | 实测值 | 原始预期 | 判定 |
|---|---|---|---|
| Lint 结果 | `40 problems (31 errors, 9 warnings)`，退出码 1 | 31 errors / 9 warnings | ✓ 一致 |
| `vite.config.ts` 的 `coverage.thresholds` | **未设置**（仅 `provider: 'v8'` + `reporter`） | 未设置 | ✓ 一致 |
| CI 前端作业步骤 | typecheck → lint → test → build，**无端到端** | 无端到端 | ✓ 一致 |

## 3. 工具链（T003）

| 项 | CI 原值 | 约束 | 判定 |
|---|---|---|---|
| node | `'18'` | `pnpm@11.7.0` 的 `engines` 为 `{ node: '>=22.13' }`（`npm view pnpm@11.7.0 engines` 实测） | ✗ **不兼容** |
| pnpm | `11.7.0` | 与 `frontend/package.json` 的 `packageManager` 一致 | ✓ 一致 |

**处置**：`.github/workflows/ci.yml` 改为 `node-version: '22'`，并加注说明约束出处。原配置下前端作业**整体不可达**——lint 是否为 31 个错误根本无从谈起。

> 修正记录：本任务原描述推测"pnpm 11.x 要求 Node ≥ 20"，实测要求为 **≥ 22.13**，比推测更严格。

## 4. 格式门禁（实施期新发现，原计划未涵盖）

`spotless:check` 绑在 `verify` 相位，实测**失败**：

```
cd backend && mvn -B spotless:check   →  退出码 1
```

**问题不只在失败本身，还在其报告**：`check` 只列出了 `RoleConstants.java` **一个**文件便中止；实际执行 `spotless:apply` 后发现**共 17 个 Java 文件**不合规。即该门禁的失败报告**低估了违规范围**。

| 观测项 | 值 |
|---|---|
| `spotless:check` 退出码 | 1 |
| 报告中列出的违规文件 | **1** 个 |
| `spotless:apply` 实际改动 | **17** 个 Java 文件 |

**为何必须在本规格内处理**：构建当前在 `test` 相位即失败，`verify` 相位从未到达，故该失败长期不可见。但**一旦 US1 补齐镜像使用例转绿，构建就会卡在 `verify` 的 spotless 检查上**——`mvn verify` 仍无法成功。更关键的是 **T020 的让路性验证会因此失效**：T020 要求"人为调高覆盖率阈值 → 构建必须失败"来证明门禁有牙齿；若构建本就因格式违规而失败，该失败**证明不了任何事**，恰是本规格要消灭的假红陷阱。

**处置**：执行 `mvn -B spotless:apply`（google-java-format 1.19.2 / GOOGLE 风格，语义保持），复核 `spotless:check` 退出码为 **0**。改动为纯格式化，17 个文件，无逻辑变更。

> 该工作**不在原 FR 集内**，属实施期发现的构建可达性缺陷，性质与 H2 镜像同族（质量机制名义存在而实际失效）。在此显式记录，不静默扩大范围。

## 5. 事实修正汇总

改造前基线使下列文档表述被实测证伪，已同步修正：

| # | 位置 | 原表述 | 实测 |
|---|---|---|---|
| 1 | `quickstart.md` 验证 1、`tasks.md` T001 | `surefire-reports/*.txt` 为 **0 个** | **80 份** |
| 2 | `spec.md` FR-G03、验收场景 4 | 阈值低于实测值 **6 个百分点** | 约 **21 个百分点**（0.30 对 0.5093） |
| 3 | `spec.md` 优先级理由、`plan.md` 复杂度表 | 覆盖率门禁"阈值宽松" | 门禁**不可达**（构建在更早相位中止） |
| 4 | `pom.xml` 注释（T019 已删除） | 当前套件覆盖 **0.36** | 仅 surefire 口径 **0.5093**（注释自述的数字实测不成立） |
| 5 | `tasks.md` T003 | pnpm 11.x 要求 Node **≥ 20** | **≥ 22.13** |
| 6 | `data-model.md` §2 转译约定表 | 约定表已覆盖类型与子句差异 | **不完整**：漏了"保留字引号"一类（见 §5.1） |

**上述修正均未削弱任何需求**——恰恰相反：第 2、3 项使 FR-G03 与 US1 的严重性高于原判，第 5 项把一项原本不可达的工作变为可达。

### 5.1 H2 保留字：转译约定表的一处缺口（实施期新发现）

**发现**：镜像 V71 的 `sales_quota` 表后，**上下文加载整体失败**，而非单个用例失败：

```
JdbcSQLSyntaxErrorException: Syntax error in SQL statement "CREATE TABLE sales_quota ( ...
quarter SMALLINT, [*]year INT NOT NULL, ...)"; expected "identifier"
```

**根因**：列名 `year` 在 MySQL 8 中**不是**保留字（生产 `V71__sales_quota.sql:8` 就是裸写的 `year INT NOT NULL`），但在 **H2 中是保留字**。`quarter` 不是，故 H2 只报了 `year`。

**为什么影响面远大于一个列**：`DataSourceInitializationConfiguration` 是 `userMapper` 的依赖，脚本执行失败 → `userMapper` 创建失败 → **全部**依赖 Spring 上下文的用例失败。本机表现为「ApplicationContext failure threshold (1) exceeded: skipping repeated attempt to load context」——**26 个"错误"由这 1 条语法错误级联而来**。这正是 FR-G07 所描述的"以误导性的方式失败"，也是本规格要消灭的失败模式。

**处置**：按本文件对保留字的**既定约定**加反引号——镜像中 `user` 表早已如此书写（`CREATE TABLE \`user\``、`CREATE INDEX ... ON \`user\` ...`），因为 `user` 在 MySQL 中同样是保留字。此处生产 DDL 无引号，是 H2 单方面的需要，故属**方言差异，非语义改动**；已在 `sales_quota` 表上方注明理由。

**教训（已回填 `data-model.md` §2）**：原转译约定表只列了**类型**（`DATETIME→TIMESTAMP`、`TEXT/JSON→CLOB`、`TINYINT(1)→INT`）与**子句**（外键/COMMENT/`ON UPDATE`/`ENGINE` 一律省略）两类差异，**没有列第三类：标识符可用性差异**。MySQL 的非保留字可能是 H2 的保留字，此时镜像必须补引号。已把该条补入约定表。

> 该缺口的代价由一条语法错误一次性暴露，属于**幸运**：若该保留字位于只被少数用例触及的表，问题会以"部分用例莫名 500"的形式长期潜伏——与 V70–V77 未镜像的潜伏方式完全同构。

## 6. 对后续任务的影响（2026-09-12 全部实测完毕）

- **failsafe 范围已收口**：failsafe `**241 run / 6F / 0E**`，**环境类失败 = 0**；**66 份** `*IT` 报告 + 1 份 `failsafe-summary.xml`（≥60）。`0E` 是关键机械证据——无上下文加载失败、无 SQL 异常，即①镜像缺失与②方言差异两类已彻底清零。逐条归因见 `tasks.md` T017。
  > **T062 复核更新**：本条原先记录 `186 run` / 64 份报告（T017 阶段快照）。T051–T058 继续新增用例后已失效，按 T061 的实测更正；报告份数与 `*IT` 类数（66）一一对应。
  > **复现命令必须带 `-Dmaven.test.failure.ignore=true`**：surefire 在 `test` 相位有失败即中止 Maven 生命周期，`integration-test`／`verify` 根本不执行；故**字面执行 `mvn -B clean verify` 连 failsafe 的影子都看不到**（这不是配置缺失，是生命周期语义）。当前 3 例单元失败属本规格已裁决转出的范围（见 `spec.md`「实施期实测结果与范围裁决」）。
- **T019 已完成**：覆盖率 **0.7516**（covered `41 544` / total `55 274`），较改造前仅 surefire 口径 **0.5093** 高 **24.23 个百分点**——差值即集成测试的贡献，也正是此前无法度量的部分。阈值定为 `0.73` 并回填 `data-model.md §4`；门禁的牙齿由 T020 反向验证（阈值临时提到 `0.99` → `Rule violated … expected minimum is 0.99` + BUILD FAILURE，随后改回 `0.73`）。
  > **T062 复核更新**：本条原先记录 `0.7376`（covered `40 464` / total `54 856`，T019 阶段快照）。T056–T058 继续改动主代码（分母随之变化）与新增用例（分子随之变化）后已失效，按 T061 实测更正。**该值同时是三处记录的同源数字**（`pom.xml` 注释、`data-model.md §4`、本文件），T062 一并更正以免三处各自陈旧。
- **收缩出本规格的 9 例**（6 集成 + 3 单元）及其各自需要先做的产品决策，见 `spec.md`「实施期实测结果与范围裁决」。其中 **`export_job.export_format` 缺列是阻断性生产缺陷**（实体有字段、V40 建表与全部迁移均无此列 → 导出功能在生产即为 500），应优先于其余项处理。
- **surefire 范围**：`**487 run / 2F / 1E**`，剩余 3 项为纯 Mockito 单测（不接数据库），按 clarify 裁决转独立 spec。
  > **T062 复核更新**：本条原先记录 `419 run / 2F / 1E`（T019 阶段快照）。后续任务新增单元用例后已失效，按 T061 实测更正。**F／E 的构成（2F + 1E = 3 例）未变**——即"转出的失败集合"自始至终未被后续改动动摇，这一点比总数更值得记录。基线中的"22 例环境类失败由 `user.email` 列缺失单一根因导致"已被实测证实，非假定。
- **§5.1 的教训已被二次验证**：镜像的机械转译必须**实际加载一次上下文**才算验证——守卫只比对版本号集合，不解析 SQL 文本（`SchemaParityIT` 的有意设计）。本次正是"跑起来"这一步才暴露出 `export_job.export_format` 这类**实体/迁移漂移**：它与"镜像漏了迁移"是不同病症，**守卫抓不到，只有执行能抓到**。
- **新增一类腐化，值得单独留档**：**时间炸弹式断言**（`CustomReportsIT` 写死 `2026-08-01~08-31`、`FieldVisitIT` 写死 `2026-08-25`）与**写死计数式断言**（`RoleIT` 菜单 28）。二者共同特征是**不报错、只静默失败**（合计变 0、长度不符），是"测试从未执行"这一根因最隐蔽的产物。已分别改为按当天计算、与字典接口做集合断言。

## 7. 测试隔离与建库脚本幂等（实施期新发现，原计划未涵盖）

**起因**：接入 failsafe 后（T015），集成测试**首次真正执行**，暴露出两类此前不可能出现、也不可能被发现的问题。二者都不在原 FR 集内，但都是"机制建了却没跑过"这一根因的直接产物——性质与 §4 的格式门禁同族，在此显式记录，**不静默扩大范围**。

### 7.1 建库脚本不幂等：22 张表只有 CREATE、没有 DROP

**症状**：failsafe 首轮 `167 failures / 6 errors`，报错指向一个与真实调用链相隔很远的建表语句：

```
JdbcSQLSyntaxErrorException: Table "lead" already exists  (脚本第 88 条语句)
```

**根因链**：`schema-h2.sql` 的 DROP 块只覆盖 62/84 张表。而同一个 JVM 内会出现**两套 `ApplicationContext`**——`AuthCaptchaIT` 带 `@TestPropertySource`，其上下文键与 `AbstractIntegrationTest` 不同。测试库是共享的具名内存库（`jdbc:h2:mem:crm;DB_CLOSE_DELAY=-1`），第二套上下文启动时**重跑脚本**：先按 DROP 块清掉 62 张表，再在 `CREATE TABLE lead` 处撞上"已存在"，库因此停在**半清空**状态，其后该 JVM 内**全部**用例级联失败。

**为什么此前不可见**：`AuthCaptchaIT` 是 `*IT`——surefire 从不执行它。接入 failsafe 前该 JVM 内只存在一套上下文，脚本只跑一次。**这是"机制从未被行使"的典型形态：缺陷早已存在，只是触发它的那条路径没有通电。**

**处置**：补 22 条 `DROP TABLE IF EXISTS`，已核对 84/84 一一对应、无多余项。

### 7.2 幂等守卫 `SchemaIdempotencyIT`（新增，不在原 FR 集内）

**为什么需要守卫，而不是指望"以后记得写 DROP"**：7.1 之所以能存活，靠的是"脚本只在上下文首次创建时执行一次"这一**当时成立的前提**。failsafe 一接入，前提就没了。这类"前提消失"无法靠自觉防御，只能靠对不变量的自动化守卫。

**为什么直接执行两次，而不是比对 CREATE/DROP 的名字集合**：名字集合只是**已知根因**的代理指标，只能抓住"漏写 DROP"这一种成因；执行两次检验的是**不变量本身**——任何成因（漏写 DROP、二次执行时违反唯一约束的种子 INSERT、只有二次执行才暴露的语法差异）都会被抓到，代价约几十毫秒。为此该用例自带一套**库名带随机后缀的私有内存库**，不与 `jdbc:h2:mem:crm` 互相干扰。

**与 `SchemaParityIT` 的分工**：后者比对"迁移文件集合 vs 已镜像版本清单"，**刻意不解析 SQL 文本**（脆弱且易误判）；前者检验同一个文件的**内部自洽性**——那里没有比"执行它"更可靠的表示方式。两者是同族但不同轴的不变量。

### 7.3 集成测试之间没有隔离（共享库 + 无重置）

**症状（不加重置时的口径）**：failsafe `186 run / 19F / 3E`，主导根因即"无隔离"。典型断言为 `$.data.total expected:<1> but was:<27>`、`Status expected:<201> but was:<409>`——"本类新建的那条记录"之外，还躺着前面几十个测试类留下的数据。

**处置**：在 `AbstractIntegrationTest` 的 `@BeforeEach` 重建测试库。三点取舍：

1. **复用 `schema-h2.sql`，不另写清理脚本**：该脚本自带完整 DROP 块，本就为"可重复执行"而设计；其幂等性由 7.2 的守卫单独保证（若 DROP 块再被漏维护，守卫会直接失败，而不是让这里以 22 个用例级联失败的形式暴露）。
2. **逐方法而非逐类**：实测全部 **66** 个 IT 类均无 `@TestMethodOrder`／`@Order`，也无静态可变状态，即设计上各方法相互独立；逐方法因此安全，且比逐类省——无需重建 `ApplicationContext`。
   > **T062 复核**：本条的计数在 T017 阶段为 61，现为 66（新增 5 个 IT 类：`SchemaParityIT`、`SchemaIdempotencyIT`、`WebSocketHandshakeIT`、`PerformanceRegressionIT`、`WebhookRedirectIT`）。**该结论不可假定继承**——新增类若声明了方法序或静态可变状态，逐方法重置就不再安全，故 T062 对新增的 5 个类逐一复测，均为无注解、无静态可变状态，结论仍成立。全仓 `@TestMethodOrder` 仅出现在 `AbstractIntegrationTest` 的 javadoc 正文里（`{@code …}` 形式），非真实注解。
3. **只重跑脚本是不够的——必须重放启动期播种（本项最贵的一课）**：第一版只重跑脚本，结果**由 19F/3E 恶化到 178F/0E**（surefire 侧同时由 2F 恶化到 16F），表现为 `178 × Status expected:<200> but was:<401>`，且**零 SQLException**。根因：admin 账号**不在建库脚本里**，而由 `DataInitializer` 这个 `ApplicationRunner` 在上下文启动时写入；脚本重置删掉 admin 且**永不复原**。**症状（全站 401）与"没有清理"同样具有误导性，只是方向相反。** 最终改为重放**全部** `ApplicationRunner` bean（而非直接依赖 `DataInitializer`）：这样以后新增的启动期播种会被自动涵盖，本重置不会悄悄落后于上下文真正的启动序列。

> 7.1–7.3 合计改动：`schema-h2.sql`（DROP 块）、新增 `SchemaIdempotencyIT`、`AbstractIntegrationTest` 新增 `@BeforeEach` 重置与播种重放。**均不在原 FR 集内**，属"让机制真正跑起来"所必需的配套——不补齐它们，T015–T018 无法进行，也无法度量。
