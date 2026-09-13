# 快速验证指南：工程收口与质量门禁修复

**Branch**: `083-engineering-consolidation` | **Date**: 2026-09-12

本文档给出**证明本规格确实生效**的可执行步骤。每条验证都包含"改造前的表现"，用于确认验证本身有区分度——若改造前后都通过，说明该验证无效。

## 前置条件

- JDK 17、Maven、Node（版本需与 CI 对齐，见 `.github/workflows/ci.yml`）、Docker（仅验证 6 需要）
- 后端集成测试**不需要**真实的 MySQL / Redis：`AbstractIntegrationTest` 使用 H2（`MODE=MySQL`）+ MockMvc，`application-test.yml` 中 `flyway.enabled: false`

## 验证 1 —— 集成测试真正执行（US1 / SC-G01）

```bash
cd backend
mvn -B verify
ls target/failsafe-reports/*.xml | wc -l
```

**期望**：`verify` 触发集成测试执行；`failsafe-reports` 目录**存在**且含 ≥60 个 XML 报告。
**改造前**（实测，见 [baseline.md](./baseline.md)）：`target/failsafe-reports/` **不存在**——60 个 `*IT.class` 从未被执行。**但需注意两点实测修正**：① `target/surefire-reports/` 实有 **80 份**报告（非 0 份），单元测试确实在执行；② `mvn -B verify` 的退出码已是 **1**，构建在 `test` 相位即以 426 例中 24 失败 / 1 错误中止，**从未到达 `verify` 相位的覆盖率门禁**。故覆盖率门禁不仅"宽松"，而是**不可达**。

**范围裁决的已知后果**：按澄清结论，本规格只要求**因环境原因失败的用例数为 0**；因业务缺陷失败的用例将转入独立规格处理。因此 `mvn verify` **可能仍以非零退出码结束**——且**改造前它就已经是非零**。此时应核对失败清单并逐条归因：凡归因于测试库缺失、镜像错误或配置缺失者**属本规格缺陷**；凡归因于真实业务行为不符者**不属本规格**。

按 baseline.md 的实测归因，改造前 25 例失败中：**22 例为环境类**（登录返回 500，根因为 `Column "email" not found`——`user` 表缺 V76 镜像的列），**属本规格，应由 US1 的镜像工作清零**；**3 例为业务类**（`DataRetentionPolicyServiceTest`、`ScheduledExportServiceTest` 的纯 Mockito 用例，断言服务回填自增主键，与实际实现不符），**不属本规格**，转独立规格。

> 关键陷阱：若只接入测试执行而未迁移覆盖率报告相位，验证 1 会通过而验证 2 会**假绿**。两项必须同时验证。

## 验证 2 —— 覆盖率门禁真实生效（US1 / SC-G02、SC-G07）

```bash
cd backend
mvn -B verify
# 打开 target/site/jacoco/index.html，确认指令覆盖率数值
```

**期望**：① 报告中的覆盖率数值**严格高于**仅执行单元测试时的数值（证明集成测试已计入）；② 人为把阈值调到高于当前实测值后再跑，`verify` **必须失败**（证明门禁有牙齿）。
**改造前**：报告在 `test` 相位生成，早于集成测试执行——覆盖率天然不含集成测试。

## 验证 3 —— 前端三道门禁（US2 / SC-G03）

```bash
cd frontend
npx eslint .                 # 期望 0 errors 0 warnings
pnpm run typecheck
pnpm run test:coverage       # 期望触发覆盖率阈值
pnpm run test:e2e
```

**期望**：Lint 输出 0 problems；覆盖率不足时命令以非零码失败；E2E 在持续集成中也执行（检查 `.github/workflows/ci.yml` 的前端作业含端到端步骤）。

> 【后记，2026-09-13，**订正：末句不再成立，原文保留**】本仓库无远端、无 `gh`，CI **从未运行**，故"E2E 在持续集成中也执行"无法按字面核对。`ci.yml` 的 e2e 作业已存在且完整（自带 MySQL/Redis 服务、等后端就绪、安装 chromium），但**不可执行**；e2e 门禁改以本地执行为准（后端 8081 + `pnpm run test:e2e`），执行记录见「验证 8」。
**改造前**：`npx eslint .` 为 **31 errors / 9 warnings**；`vite.config.ts` 无 `thresholds`；CI 前端作业为 typecheck → lint → test → build，**无**端到端步骤。

## 验证 4 —— 14 个页面恢复正常加载（US4 / SC-G04）

1. 启动后端与 `pnpm dev`，以 `admin` / `admin123` 登录
2. 逐个访问下列 14 个路由：`/quotas`、`/quotas/create`、`/quotas/:id/breakdown`、`/quotas/:id/achievement`、`/quotas/:id/versions`、`/quotas/comparison`、`/data-retention`、`/data-retention/create`、`/data-retention/:id/edit`、`/data-retention/:id/executions`、`/data-retention/compliance-export`、`/exports/scheduled`、`/exports/scheduled/create`、`/exports/scheduled/:id/executions`

**期望**：全部正常加载数据。
**改造前**：全部 401 或空表。打开开发者工具的 Network 面板，确认请求头是 `Authorization: Bearer <真实令牌>` 而非 `Bearer null`。

**因果验证（重要）**：重点确认 `/quotas` 的**空表**是否随本修复消失。最近两次提交（`6f2dd40`、`1f74f4b`）连修同一处配额 SQL，怀疑正是被 401 掩盖的表象。此因果关系一旦成立，应作为本规格修复价值的直接证据记录。

> 14 个页面有**两种**根因：13 个是凭据键失配，1 个（合规导出页）是完全没有携带凭据。修复后两者的表现形式都应是"正常加载"，而非仅其中一类。

## 验证 5 —— 三项安全手工验证（US3 / SC-G05）

| # | 步骤 | 期望（改造后） | 改造前表现 |
|---|---|---|---|
| 1 | 用 scope 受限的 API Key 调用开放平台端点读取**他人**数据 | 返回 403 或空结果 | 返回**全量**数据（密钥主体被注入管理员身份） |
| 2 | 停用某用户后，用其**旧令牌**连接实时通知通道 | 握手被拒绝 | 握手**成功**（不校验用户状态与令牌版本） |
| 3 | 创建指向 `http://169.254.169.254/` 或 `http://127.0.0.1:8081/` 的回调地址 | 被拒绝 | 被接受（一处仅判协议、一处完全无校验） |

**注意**：第 1 项的结果变化是**安全修复的预期效果**，不是回归。原先能读全量的调用方将开始收到 403/空结果——该语义变更已按章程原则一同步记录于 `specs/055-open-platform/contracts/open-platform.md`，核对该文件已更新。

另需确认：三个控制器补的权限注解使用的是**既有**权限码（未新增权限码），且全局搜索控制器**未**被加上注解（加了会使搜索对所有用户失效）。

## 验证 6 —— 干净检出 + 一键启动（US4 / SC-G06）

```bash
git clean -xdf frontend/dist      # 模拟干净检出
docker-compose up -d
curl -I http://localhost          # 期望 200
docker-compose logs crm-backend | head    # 期望迁移按版本序正常执行
```

**期望**：前端首页返回 200 且非空白页；数据库迁移按版本序号执行，无字母序错乱（不应出现 `V1` 之后紧跟 `V10` 这类顺序）。
**改造前**：前端挂载了一个不随仓库交付的宿主机构建产物目录 → 空白页；迁移目录被挂成数据库初始化目录 → 按**字母序**执行；允许来源指向开发端口而非编排实际暴露的端口。

## 验证 7 —— SDD 登记与产物一致性

```bash
ls specs/083-engineering-consolidation/     # spec/plan/research/data-model/quickstart/checklists/tasks
grep -n "083" specs/README.md specs/roadmap.md
```

**期望**：`specs/README.md` 的模块清单、版本行、编号说明已登记 083；`specs/roadmap.md` 的"最后更新"、"整体覆盖度"与"当前进度"已更新。本规格**无 Flyway 迁移**，故迁移对照表不动。

> 【后记，2026-09-13，**订正：末句不再成立，原文保留**】T077 交付了 `V88__quota_child_tables_base_entity_columns.sql`，故迁移对照表**已新增 083 行**（并顺带补上此前漏登的 V87，1.5 批 3）。登记面见 tasks.md T077 实施记录第 11 条。

**约定核对**：本规格与 `003-system-hardening` 同形制，**不产 `contracts/`**（无新端点）。唯一的契约动件是 `specs/055-open-platform/contracts/open-platform.md` 的授权语义澄清——见验证 5。

## 验证 8 —— 门禁口径：以本地命令为准（T069 声明）

**为什么有这一节**：本仓库 `git remote -v` 为空、无 `gh`，故 `.github/workflows/ci.yml` 里的作业**永不执行**。门禁口径因此正式改为**以本地命令为准**（`spec.md` 的 FR-G10 处已就地声明）。下表列出 CI 定义的**每一道**门禁及其本地等效命令，**并附 2026-09-13 的实测执行记录**——"门禁生效"的主张今后必须附这种记录，不得以 `ci.yml` 的存在为证。

| # | CI 中的步骤 | 本地等效命令 | 实测（2026-09-13） |
|---|---|---|---|
| 1 | backend: `mvn -B verify` | `cd backend && mvn -B verify` | **退出码 1**。spotless:check 通过（727 文件 0 需改）；surefire **551 例 / 0 失败 / 0 错误**（全绿）；failsafe **274 例 / 4 失败 / 0 错误**；中止在 `failsafe:verify` |
| 2 | backend: 覆盖率门禁（`jacoco:check`，阈值 0.73） | `cd backend && mvn -B -Dmaven.test.failure.ignore=true verify` | **退出码 0**，`jacoco:check (coverage-check)` **实际执行并判定通过**：INSTRUCTION covered **45 035 / 56 169 = 0.8018** ≥ 0.73；BRANCH 2 809 / 4 626 = 0.6072 |
| 3 | frontend: typecheck | `pnpm run typecheck` | 退出码 0 |
| 4 | frontend: lint | `pnpm run lint` | 退出码 0（0 problems） |
| 5 | frontend: i18n key parity | `pnpm run i18n:check` | 退出码 0（zh-CN 2 883 键 / en 2 883 键；路由 58 / 清单 56 / 粗粒度别名 3） |
| 6 | frontend: menu manifest 陈旧性 | `pnpm run menu:check` | 退出码 0（56 项，来源 `RoleConstants.MENU_TREE`） |
| 7 | frontend: unit + 覆盖率阈值 | `pnpm run test:coverage` | 退出码 0（22 文件 / 96 用例全通过；statements 47.02 / branches 71.35 / functions 22.43） |
| 8 | frontend: build | `pnpm run build` | 退出码 0（11.50s；有一条 >500 kB 分块告警，非失败） |
| 9 | e2e 作业 | 后端 8081 就绪后 `pnpm run test:e2e` | `e2e/module-page-auth.spec.ts` **14 passed**（T073） |

**四条必须知道的前提**（否则上表会被误读）：

1. **第 1 行与第 2 行的关系不是"换个参数"**：`jacoco:check` 与 `failsafe:verify` **同绑 `verify` 相位，且声明在其后**，而 Maven 是失败即中止 —— 故只要存在**任何一例** IT 失败，覆盖率就**永远判不出来**。当前那 4 例失败均为 T068 已批准的业务类失败（`IntegrationHubIT.integrationFlow`、`OpportunityIT.closeWithoutResultReturns422`、`UserIT.disableUserRevokesAccess`、`UserIT.userLifecycle`），**不属** SC-G01 要求清零的"因环境原因失败"。想看到覆盖率判定就必须加 `-Dmaven.test.failure.ignore=true`。**同相位内 `spotless:check` 又声明在两者之前**，故格式违规比用例失败更早中止。
2. **`target/failsafe-reports/` 现有 72 份 XML**——SC-G01 要求的"≥60 份报告"这一条**已满足**（改造前该目录不存在）。
3. **⚠️ 不要传 `-DargLine`（实测陷阱，会静默废掉覆盖率门禁）**：命令行给的 `-DargLine` 是**用户属性**，优先级高于插件属性，会把 `jacoco:prepare-agent` 追加的代理参数挤掉。后果链**全程无报错**：测试照跑（551 例全绿）→ 构建成功 → `jacoco:report` 只打印一行 INFO `Skipping JaCoCo execution due to missing execution data file` → `target/jacoco.exec` **根本不生成** → `jacoco:check`（阈值 0.73）**BUILD SUCCESS 空过**。实测：`mvn -B -Djava.version=17 -DargLine="-Dfile.encoding=UTF-8" test` 后 `target/jacoco.exec` **不存在**；去掉该参数后同样命令生成 681 KB 的 exec。`pom.xml:33` 已用 `<argLine>-Dfile.encoding=UTF-8</argLine>` 设好编码，故该参数**既多余又有害**；要追加 JVM 参数应改 `pom.xml` 的 `<argLine>` 属性（该属性的注释已警告插件级同类陷阱，但未覆盖命令行这条路径）。**判据**：跑完 `ls backend/target/jacoco.exec` 必须存在，且 `jacoco:report` 那行应是 `Loading execution data file` 而非 `Skipping`。
4. **JDK 版本在本机与 CI 不一致**：本机**只装了 JDK 17**；`.github/workflows/ci.yml` 的后端作业（当前工作区版本）为 **25**、e2e 作业仍为 **17**，而 `backend/pom.xml` 的 `java.version` 被并行会话改为 **25**（尚未提交）。故上表第 1、2 行实测时**显式带了 `-Djava.version=17`** 覆盖 pom 的值。该参数在 `pom.xml` 提交为 17 后即不再需要，此处如实记下以免被当作稳定做法。
