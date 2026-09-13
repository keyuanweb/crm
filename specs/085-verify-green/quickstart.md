# 快速验证指南：验证门禁转绿（085）

**Branch**: `085-verify-green` | **Date**: 2026-09-13

本文件给出**证明本规格确实生效**的可执行步骤。每组验证都包含**"改造前表现"**——用于确认验证本身有区分度：若改造前后都通过，说明该验证无效，不能作为证据。

## 前置条件与环境纪律

- JDK 17（本机唯一已装版本）、Maven、Node（版本与 CI 对齐）、Docker（仅验证 5 需要）
- 后端集成测试**不需要**真实 MySQL / Redis：`AbstractIntegrationTest` 使用 H2（`MODE=MySQL`）+ MockMvc，`application-test.yml` 中 `flyway.enabled: false`
- **构建校验必须带 `-Djava.version=17`**（`pom.xml` 的 `java.version` 被并行会话改为 25，尚未提交）

### ⚠️ 两条必须遵守的取证纪律（否则会得到假证据）

**纪律 1 —— 绕过 Windows 文件占用噪声。** 若开发后端（`java -jar target/crm-backend-*.jar`）正在运行，构建会在 `spring-boot:repackage` 一步失败：

```
Unable to rename 'target\crm-backend-0.1.0-SNAPSHOT.jar' to '...jar.original'
```

因为该进程**持有此文件**。**后果**：构建在 **failsafe 之前**中止，**根本没跑到集成测试**。若不加辨别，会把它误读成"代码缺陷"，或把"没跑到集成测试"误读成"集成测试通过"。

```bash
cd backend
mvn -B -Djava.version=17 -Dspring-boot.repackage.skip=true verify
```

**纪律 2 —— 不要传 CLI `-DargLine`。** 它是**用户属性**，优先级高于插件属性，会把 `jacoco:prepare-agent` 追加的代理参数挤掉。后果链**全程无报错**：测试照跑 → 构建成功 → `jacoco:report` 只打印 `Skipping JaCoCo execution due to missing execution data file` → `target/jacoco.exec` **根本不生成** → `jacoco:check` **空过**。**判据**：跑完 `ls backend/target/jacoco.exec` 必须存在，且 report 那行应为 `Loading execution data file` 而非 `Skipping`。

---

## 验证 0 —— 基线（改造前，用于对照）

```bash
cd backend
mvn -B -Djava.version=17 -Dspring-boot.repackage.skip=true verify
```

**改造前实测**（2026-09-13）：

| 项 | 实测值 |
|---|---|
| spotless | 通过（727 文件 0 需改） |
| surefire | **551 例 / 0 失败 / 0 错误**（全绿） |
| failsafe | **275 例 / 4 失败 / 0 错误** |
| 退出码 | **1**（中止在 `failsafe:verify`） |
| `jacoco:check` | **零命中 —— 从未被判定** |

**4 例失败集（恰好 4 例，无第 5 例）**：`IntegrationHubIT.integrationFlow`、`OpportunityIT.closeWithoutResultReturns422`、`UserIT.disableUserRevokesAccess`、`UserIT.userLifecycle`。

> **因果要点**：`jacoco:check` 与 `failsafe:verify` **同绑 `verify` 相位且声明其后**，Maven 失败即中止 → 只要存在**任何一例** IT 失败，覆盖率就**永远判不出来**。故"让 4 例转绿"与"让覆盖率门槛真正生效"是**同一件事**，不是两件。

**【对照实测登记，2026-09-13：改造后同一命令】** 退出码 **0**；surefire **555 / 0 / 0**（基线 551 + `WebhookDelivererTest` 4 例）；failsafe **282 / 0 / 0**（基线 275 + 新增 7 例）；failsafe 报告 **73 份 XML**；`jacoco:check` **已执行并判定通过**。

**用例数对账（曾登记为"对不上"，现已对平）**：275 → 282 的 **+7** 逐类实测为 `UserIT` 4→6、`OpportunityIT` 4→7、`IntegrationHubIT` 2→3、`ExceptionHandlerIT` 4→5、`WebhookRedirectIT` 4→4，即 **+2+3+1+1 = 7**，**275 + 7 = 282 ✓**。（先前误按"新增 5 例"记，漏算了 `ExceptionHandlerIT` 的 T012 用例与 T014 探测项。）
> 另一处**不作对平**的差异如实保留：083 的记录把同一基线写作 **274**，与本文件 T001 的 **275** 差 1。那是两个时点的采样，与本次改动无关，此处不追平。

**改造过程中出现过第 5 例失败**（不在上表 4 例之内，也不在 083/T068 批准范围内）：`WebhookRedirectIT.legitimateRedirectIsFollowedHopByHop`，`expected: "SUCCESS" but was: "PENDING"`。它**由本次对缺陷③的修复直接引起**——该类 `awaitDelivery` 的判据是"投递记录**已出现**"，而记录现在在**派发那一刻**就出现，于是轮询当场读到在途态。**这是同一修复的镜像面**：`IntegrationHubIT` 因"记录**不存在**"而红，它因"记录出现**得太早**"而红。已把判据同步收窄为"**等到终态**"（并声明**不是放宽断言**），复跑后 5 类 25 例全绿。详见 tasks.md 同名小节。

---

## 验证 1 —— FR-V01/V02：登录不再消费乐观锁令牌

**步骤（集成测试，自动化）**：

```bash
cd backend
mvn -B -Djava.version=17 -Dspring-boot.repackage.skip=true verify -Dit.test=UserIT
```

对应的**红→绿**用例（FR-V11）——
- **`UserIT.disableUserRevokesAccess`** / **`UserIT.userLifecycle`**：改造前**红**，改造后**绿**。
- 新增（或使既有断言显式化）一条**照 FR-V01 直述场景的用例**：读用户 → 该用户登录 → 携带读到的版本提交编辑 → **必须成功**。

**改造前表现**：上述两条用例失败。根因链：`AuthService.java:89-91` 的 `userMapper.updateById(user)` 在 `BaseEntity.java:23` 的 `@Version` 作用下生成 `SET version = version + 1 WHERE id=? AND version=?`，登录**消费**了令牌 → 管理端提交时 `WHERE version=?` 不匹配 → 409 `VERSION_CONFLICT`（误报）。

**改造后表现**：登录后该用户的版本标识**不变**；`lastLoginAt` **已更新**（两者须同时成立——只验其一不足以证明修复）。

**同时必须验证的 FR-V02（防"修过头"）**：真正的并发编辑**仍须**返回 409 —— 令牌不能因此被架空。既有并发编辑用例即此项的回归守卫，**必须仍然绿**。

**附带验证的 R-3（丢更新）**：定向更新不再整行回写，故"读后到写前"的他人改动不会被静默覆盖。

**【实测登记，2026-09-13】** `UserIT` **6 例 / 0 失败 / 0 错误**（`mvn ... -Djacoco.skip=true -Dit.test=UserIT`）。
- FR-V01/V02 两条一对，**同一次运行内同时成立**：`loginDoesNotBumpVersion`（登录后**版本标识不变**，且 `lastLoginAt` **确已更新**——两条断言同在一个用例内，避免"只验其一"）+ `staleVersionStillRejected`（**陈旧版本仍然 409**，即令牌未被架空）。
- 改造前的两条失败（`disableUserRevokesAccess`、`userLifecycle`）确已转绿；它们的失败**不是**本次新写的，是基线里就有的。

---

## 验证 2 —— FR-V03/V15：关闭端点回到契约的 422，其他端点仍是 400

**步骤（对运行中的后端）**：

```bash
# 取令牌
TOKEN=$(curl -s -X POST http://localhost:8081/api/v1/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"username":"admin","password":"admin123"}' | jq -r '.data.accessToken')

# ① 结果缺失 —— 契约要求 422
curl -s -o /dev/null -w '%{http_code}\n' -X POST http://localhost:8081/api/v1/sales-opportunities/1/close \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"closeResult":""}'

# ② 结果非法（非空但不是 WON/LOST）—— 应当也是 422
curl -s -o /dev/null -w '%{http_code}\n' -X POST http://localhost:8081/api/v1/sales-opportunities/1/close \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"closeResult":"MAYBE"}'

# ③ FR-V15：其他端点的校验失败必须仍是 400（不得被这次的改动带跑）
curl -s -o /dev/null -w '%{http_code}\n' -X POST http://localhost:8081/api/v1/sales-opportunities \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{}'
```

**期望**：① **422** 且响应体错误码为 `CLOSE_RESULT_REQUIRED`；② **422**；③ **400**。

**改造前表现**：① 返回 **400**（且错误码是 `BAD_REQUEST`）—— Bean Validation 的 `@NotBlank` 抢先；② 返回 **422**；③ 400。

> **这正是缺陷的要害**：改造前 **① 与 ② 状态码不同**，而它们表达的是**同一语义**（结果没给对）。契约只写了 422。故这不只是"实现与契约不符"，而是**实现自身自相矛盾**。

**为什么必须同时验 ③**：本次改的是**全局**异常处理器，风险面天然大于"只管一个端点"。FR-V15 就是为这条风险设的，**不可省略**。

**【实测登记，2026-09-13：本节的三条 curl **未**对运行中的后端执行，如实声明】** 原因与 T031 相同：8081 上跑着的那个后端（PID 7680，启动于 08:40:17）**不含**本规格的改动，拿它打必然得到改造前的旧行为；起第二个后端会与它共抢同一个开发库并重复触发调度器。**故本节改以自动化等价物取证，且等价物在两处都更强**（走完整 Spring 栈：真 Bean Validation、真 `GlobalExceptionHandler`、真库）：

| 本节的 curl | 自动化等价物 | 实测 |
|---|---|---|
| ① `{"closeResult":""}` → 期望 422 `CLOSE_RESULT_REQUIRED` | `OpportunityIT#bothCloseFailurePathsAgree` 第 ① 段（`{"version": 0}`，缺结果） | **422 `CLOSE_RESULT_REQUIRED`** ✓ |
| ② `{"closeResult":"MAYBE"}` → 期望 422 | 同用例第 ② 段（非空非法结果，**不经** Bean Validation，由域层判定） | **422 `CLOSE_RESULT_REQUIRED`** ✓ |
| ③ 其他端点校验失败 → 期望 **400** | `ExceptionHandlerIT#otherEndpointValidationStillReturns400`（`displayName` 60 字超长） | **400，`参数校验失败`，`fieldErrors[0].field=displayName`，且响应体**不含** `CLOSE_RESULT_REQUIRED`** ✓ |

**①② 同值这一点是关键**：它们表达同一语义，实测同码；这正是"改造前 ① 与 ② 自相矛盾"的对照面（见上方"改造前表现"）。③ 的断言**额外**要求响应体**不得**出现 `CLOSE_RESULT_REQUIRED` ——否则"全局处理器改过头、把别的端点也吞了"会被漏掉。
`OpportunityIT` 实测 **7 例 / 0 失败 / 0 错误**，`ExceptionHandlerIT` 实测 **5 例 / 0 失败 / 0 错误**。

> **残留偏差（如实登记）**：本节为**手工 curl** 而写，本次以**自动化**等价物兑现，两者在"同一份代码路径"上等价，但**手工形态本身未被执行**。若将来端口 8081 上跑的是含本规格改动的后端，本节可直接照执行。

---

## 验证 3 —— FR-V05/V07/V08/V13：投递记录在派发时即存在

**步骤（手工，含时序）**：

1. 在集成中心配置一个**必定失败**的回调地址（如 `http://127.0.0.1:9/` —— 该端口必然拒连，触发完整重试链）
2. 触发一次投递
3. **派发后 1.5 秒内**刷新投递记录页

**期望**：
- 记录**已经出现**，状态显示为**"投递中"**（中性色），**不是**红色"失败"；
- 约 36 秒后（退避 1s + 5s + 30s 用尽）再刷新，该条记录**原地变为**"失败"（红），且**仍然只有一条**记录（FR-V08：不因"先插后更"变成两条）。

**改造前表现**：派发后 **36 秒内页面上完全没有这条记录**（`WebhookDeliverer.java:103` 的 `record(...)` 在重试循环**之后**才执行）；`IntegrationHubIT` 等 1.5 秒而记录要 36 秒才落库，**故该用例失败**——**是用例对了、实现错了**，不是相反。

**FR-V07（中断残留）的验证**：投递进行中 kill 掉后端，重启。**期望**：该条悬空记录被启动清扫判定为**终态**（带明确原因），**不会永远停在"投递中"**。

**FR-V13（前端第三态）**：只改后端不改前端时，`PENDING` 会被 `IntegrationHubPage.tsx:195` 的**二元**渲染（`status === 'SUCCESS' ? 绿 : 红`）显示为红色**"失败"**——把"记录不反映真实"原样搬到界面上。**故三态分支与中英文 i18n 键必须同时交付**，否则 `pnpm run i18n:check` 转红。

**【实测登记，2026-09-13：本节的**手工时序观察未执行**，如实声明】** 原因同 T031（8081 上的后端不含本次改动；起第二个会共抢开发库并重复触发调度器）。**逐项对账如下**：

| 本节要求手工观察的 | 自动化等价物 | 覆盖程度 |
|---|---|---|
| 1.5 秒内记录已出现且为"投递中"（中性色，不是红） | `IntegrationHubIT#integrationFlow`：真 HTTP 建通道 → 真事件 → **`Thread.sleep(1500)`** → 查交付记录，断言 `data.items[0].status == "PENDING"` **且 `items.length == 1`** | **时序与状态完整覆盖**（三态中的"中"态已由接口层断言） |
| 约 36 秒后**原地**变失败，且**仍然只有一条** | `WebhookDelivererTest#exhaustedRetriesMarkFailedWithRetryCount`（`insert` ×1、`updateById` ×1、**`isSameAs`** 证明更新的是**派发时那一行**，并断言 `error` 含 `connection refused`、`retryCount > 0`）+ `#rejectedUrlStillProducesExactlyOneRecord` | **"原地且只有一条"完整覆盖**；**"真等满 36 秒"未覆盖**（单元用例以中断标志让首次退避立即抛出，**这是有意的取舍**——否则每条用例要跑满 36 秒） |
| 投递中 kill 后端 → 重启后悬空记录被清扫为终态 | `IntegrationHubIT#stalePendingDeliveriesAreSweptToFailed`：**真库**插入 1 条 10 分钟前的 `PENDING` + 1 条**刚派发**的 `PENDING` → 调 `sweepStalePending()` → 断言前者变 `FAILED` 且 `error` 含"中断"、后者**仍为 `PENDING`** | **清扫的库级效果与"阈值不被误伤"完整覆盖**；**"真 kill -9 再重启"未覆盖**（周期性触发本身无测试，只依赖 Spring `@Scheduled` 的既有契约——**残留项已登记**） |
| FR-V13 前端第三态配色 + i18n 两侧同步 | 代码层：`IntegrationHubPage.tsx` 三态分支 + `zh-CN.ts`/`en.ts` 各加 `pending`（`投递中`/`Delivering`）；门禁层：`pnpm run i18n:check` **退出码 0**，实测 **zh-CN 2884 键 / en 2884 键**相等 | **键的同步与门禁完整覆盖**；**"中性色的视觉观感"未做人工目视**（如实声明） |

**已实测的接口层证据**：`IntegrationHubIT` **3 例 / 0 失败 / 0 错误**（含上述库级清扫验证）；`WebhookDelivererTest` **4 例 / 0 失败 / 0 错误**。

> **本节的要害在"改造前表现"那段，它已被实测证实**：改造前 `IntegrationHubIT#integrationFlow` 等 1.5 秒而记录要 36 秒后才落库，故 `total == 0` 失败——**是用例对了、实现错了**。改造后同一用例（**用例本身未改一字**）读出 `PENDING` 且 `total == 1` 而转绿。

---

## 验证 4 —— FR-V09/V10：`verify` 转绿，且覆盖率门槛**确实被判定**

```bash
cd backend
mvn -B -Djava.version=17 -Dspring-boot.repackage.skip=true verify
echo "exit=$?"
```

**期望**：**退出码 0**；`mvn` 日志中 `jacoco:check (coverage-check)` **有实际判定输出**（含 `INSTRUCTION covered ... / ...` 与阈值比较），而非零命中。

**改造前表现**：退出码 1，`jacoco:check` **零命中**——覆盖率门槛从未被判定过。

**【实测登记，2026-09-13】实测：退出码 **0**，`BUILD SUCCESS`（Total time **02:11**），surefire **555 例/0/0**，failsafe **282 例/0/0**。**

> **【订正，2026-09-13，上文"期望"原文保留】上句"日志中 `jacoco:check` **有实际判定输出**（含 `INSTRUCTION covered ... / ...` 与阈值比较）"**在通过的那一轮里不可能出现**——实测发现 `jacoco:check` **通过时只打印一行** `Loading execution data file .../target/jacoco.exec`，**不打印任何比值与阈值比较**；那行只在**判定失败**时才出现。故这个"期望"若被当作判据，会把**门禁正常**误读成"门禁零命中"。**改判据为三条**（互不依赖）：① `target/jacoco.exec` **存在且非空**，实测 **7 830 477 字节**（零命中时该文件根本不生成）；② 相位正确——`jacoco:report (report)` 出现在 failsafe 汇总**之后**（FR-V09 的相位修复生效），故覆盖率**含集成测试**；③ **反向验证**（见下）给出了那行被引用的比较文本。
>
> **本节"改造前表现"须加一句限定**：改造前的 `jacoco:check` 并非"执行了但零命中"，而是**根本没被执行**（`failsafe:verify` 先失败即中止）。二者都导致"门槛从未判定"，但成因不同。

### FR-V10 反向验证（**证明门槛有牙齿**，不可省略）

只看"转绿"**不足以**证明门槛生效 —— 一个被删掉的门槛也会让构建绿。必须做反向验证：

1. 把 `pom.xml` 的覆盖率阈值临时调到**高于当前实测值**（如实测 0.80 → 临时设 0.99）；
2. 跑同一条命令；
3. **期望：构建必须失败**，且失败点正是 `jacoco:check`。

**做完必须还原阈值**，并把两次实测值（原值、临时值、当前 covered 比值）记入 tasks.md 的实施记录。

> **基线参考**（083 实测，阈值 0.73）：INSTRUCTION covered **45 035 / 56 169 = 0.8018**。请注意本规格修复后该比值**会变**（新增代码行会进分母），故**必须重新实测**，不得沿用此数。

**【反向验证实测，2026-09-13：三轮，只动 `pom.xml:302` 一个数字】**

| 轮次 | `<minimum>` | 实测 covered 比值 | 退出码 | 失败点 |
|---|---|---|---|---|
| ① 原阈值 | **0.73** | **0.8023**（45 171 / 56 302，取自本次 `jacoco.csv` 汇总） | **0** | —— |
| ② 临时调高 | **0.90** | 0.80（日志四舍五入；CSV 精确值 0.8023） | **1** | **正是 `jacoco:check (coverage-check)`** |
| ③ 还原后重跑 | **0.73** | 0.8023 | **0** | —— |

第 ② 轮的关键原文（**这就是上面订正里说"只在失败时才出现"的那行**）：

```
[WARNING] Rule violated for bundle crm-backend: instructions covered ratio is 0.80, but expected minimum is 0.90
[ERROR] Failed to execute goal org.jacoco:jacoco-maven-plugin:0.8.11:check (coverage-check)
        on project crm-backend: Coverage checks have not been met.
```

**第 ② 轮的信息量不止"失败了"**：那一轮里 surefire 555/0/0 与 failsafe 282/0/0 **全绿，构建仍然失败** —— **证明覆盖率门槛是决定性的，不是装饰**。这正是"一个被删掉的门槛也会让构建绿"的反证。

**还原的可信度**：改前与还原后 `pom.xml` 的 MD5 **同为 `6058490cee6fa19a9bbbce96036ad20f`**，`diff` 无输出。即只动了 `<minimum>` 一处并**逐字节还原**，**并行会话在该文件里的未提交改动未受扰动**。

---

## 验证 5 —— 范围守卫与登记一致性（FR-V12/V14）

```bash
cd E:/code/crm
git status --porcelain backend/src/main/resources/db/migration/    # 期望：无输出
git diff --stat specs/001-crm-core/contracts/ specs/058-*/contracts/ specs/055-*/contracts/   # 期望：无输出
ls specs/085-verify-green/    # 期望：spec plan research data-model quickstart checklists tasks
```

**期望**：**无任何 Flyway 迁移新增或修改**（FR-V14）；**无任何契约文件被改动**（FR-V12）。

**若确需迁移**（例如修③发现重试次数/HTTP 状态/错误摘要**没有可用的列**）：**必须停下**，回到项目负责人处重新裁决是否扩围，**不得就地扩围后补记**——这是 FR-V14 作为"范围守卫"而非"技术约束"的本意。

> **已核实无需迁移**：`webhook_delivery.status` 是 `VARCHAR(20)`（`V63__open_platform.sql:35-49`），**不是 ENUM**，加取值不需迁移。

**【实测登记，2026-09-13】前两条守卫命令**均**无输出**（即 `git status --porcelain backend/src/main/resources/db/migration/` 与 `git diff --stat specs/001-crm-core/contracts/ specs/058-*/contracts/ specs/055-*/contracts/` 都空）——**无新增/修改的迁移，无被改动的契约文件**。

**登记面已核对**：`specs/085-verify-green/` 含 `spec.md`、`plan.md`、`research.md`、`data-model.md`、`quickstart.md`、`checklists/`、`tasks.md`，**无 `contracts/`**（加固类形制，与 003/083 一致）。`specs/README.md` 的改动**经 `git diff` 逐行核对只有 3 行**（版本行、085 表格行、编号说明），**迁移对照表未被触及**；`specs/roadmap.md` 改了 `**最后更新**`、`**整体覆盖度**`、`## 当前进度` 追加 085 行共 3 处——**两文件合计 6 处，与"加固类登记面"惯例一致**。

---

## 验证 6 —— 前端门禁未被本次改动弄红

```bash
cd frontend
pnpm run typecheck
npx eslint .            # 期望 0 problems
pnpm run i18n:check     # 期望两侧键数相等（新增键必须中英文同步）
pnpm run menu:check
pnpm run test:coverage
```

**期望**：全部退出码 0。**重点看 `i18n:check`** —— 新增第三态文案时必须两侧同步，否则该门禁转红，等于用一处新缺陷换掉一处旧缺陷。

**【实测登记，2026-09-13】五道门禁**全部退出码 0**：

| 门禁 | 退出码 | 实测摘要 |
|---|---|---|
| `pnpm run typecheck` | **0** | —— |
| `npx eslint .` | **0** | 0 problems |
| `pnpm run i18n:check` | **0** | **zh-CN 2884 键 / en 2884 键**，两侧相等；菜单路由与清单双向对齐（路由 58 条 / 清单 56 项，粗粒度别名 3 条） |
| `pnpm run menu:check` | **0** | —— |
| `pnpm run test:coverage` | **0** | **24 个测试文件 / 100 例全部通过**；`All files` 语句覆盖 47.7% |

**重点项已兑现**：新增的第三态文案（`pending` = `投递中` / `Delivering`）**中英文两侧同步**，`i18n:check` 未因此转红——即本次**没有**用一处新缺陷换掉一处旧缺陷。

> **口径澄清（避免被误读为"前端门禁是本次新加的"）**：覆盖率**阈值**（`vite.config.ts` 的 `thresholds`）与 E2E 进 CI 是 **083** 的交付物，**不属本规格**。本节只证明"本次改动没有把它们弄红"。

---

## 口径声明（避免本文件被误读）

1. **本机 e2e 不是本规格的验收手段**：`e2e/` 打的是**已在监听**的后端（playwright 只自起前端），跑绿不等于本次后端改动被端到端验证。故本文件的验收以**集成测试 + 手工时序观察**为准。若执行 e2e，须先比对后端进程启动时间与 `.class` 文件时间，确认打到的确实包含本次改动。
2. **CI 在本仓库永不执行**（无远端、无 `gh`），门禁口径以**本地命令**为准（083 T069 的声明继续适用）。
3. **本规格产出的"绿"限于本地环境**；本机与 CI 的 JDK 版本不一致（本机 17、CI 的后端作业 25）这一偏差**未被本规格消除**，如实声明。
