# 任务：全局限流收口（100）

**Created**: 2026-09-16
**图例**：`[ ]` 未完成 / `[x]` 已完成。**立项阶段一律不预勾。**
**⚠️ 本项的实际编写顺序是「先实现、后补用例」**（沿用 087/088/092/095–099 的既有做法）——
**不得**据此声称走过 spec-first；定向破坏留痕证明的是**护栏有牙齿**，不是「红先出现」。
**⚠️ 本项的活动半径**：**纯后端 + 文档**。**零端点、零 DTO、零迁移、零权限码、零前端改动、无 `contracts/`**。

**⚠️ 行号口径**：本文件里的行号是**立项时的实测值**，只作定位辅助。**权威锚点是符号名**
（类名 / 方法名 / 字段名 / 字符串字面量）。本仓已实测过「行号引用会腐坏」。

**⚠️ 三处「必须同批」（分成两次就是错的）**：
1. **T021 与 T022 同批**：删 `EmailTrackController.RateLimitedException` 与删它的 `GlobalExceptionHandler` 处理器
   **必须在同一次提交** —— 否则中间态**编译不过**。
2. **T036 与 T037 同批**：P0 标注与 `RateLimitCoverageTest` **必须在同一次提交** ——
   否则中间提交留下「一堆**没有护栏的标注**」，而那正是台账要防的东西。
3. **T028 与 T029 同批**：`AuthService.resolveClientIp` 的委托与两处私有副本的删除**必须在同一次提交** ——
   否则中间态**三份并存**（虽然能跑，但「收敛」这件事没发生）。

---

## 阶段 A 工件与登记（提交 C1 = `docs(100): 立项`）

- [ ] T001 写 `spec.md` / `plan.md` / `research.md` / `quickstart.md` / `tasks.md` / `checklists/requirements.md`
      （**6 件**；`falsification-evidence.md` 在 T048 交付时产出）
- [ ] T002 `research.md` 必须装齐：① 与 `CRM_FEATURE_COMPARISON.md` 两行的关系；② 机制选型（注解 vs 过滤器的**顺序契约无护栏**证据）；
      ③ 存储选型；④ **TTL 语义两范式对照表**（照 `MfaStateStore` 而非 `AuthService`）；⑤ **「读时补窗放行」与 `MfaStateStore` 刻意相反**的理由；
      ⑥ fail-open 的**边界**（catch 什么、**不** catch 什么）；⑦ **不用 Lua** 与不扩替身；⑧ 身份维度与 `keyId`；
      ⑨ 客户端 IP 收敛 + **XFF 残余风险的口径降级**；⑩ 包选型与 jacoco 分母；⑪ 错误码与 `MFA_LOCKED` 的分工；
      ⑫ **五处对外可观测的行为变更**；⑬ 台账护栏；⑭ 与 036/016/017/055 的关系；⑮ **覆盖缺口与假绿通道**（含事实 ⑬ 那颗雷）
- [ ] T003 `specs/README.md` 模块表加 **100 行**（**状态列如实写「⏳ 进行中」**，**不预勾**）
- [ ] T004 `specs/README.md` 的**编号说明**纳入 100（**按文本锚定位，不按行号**）
- [ ] T005 `specs/roadmap.md` 加 100 行（**勾选框留空、不预勾**）；`最后更新` 前置 100 立项条目
      （旧值降级为「**上一条（原文保留）**」，逐字不改）；`## 当前进度` 由 **98 勾 / 0 未勾** 变 **98 勾 / 1 未勾**
      （实测复核：`grep -c '^- \[x\]' specs/roadmap.md` 与 `'- \[ \]'` 各计一次）
- [ ] T006 ⚠️ **立项阶段不改 `README.md:163`**：照 099 先例（其 T007 实做订正）**前移到交付提交 T049**。
      **理由**：它与 `PROJECT_FEATURES.md` 的 **Spec 模块行**是**同一类对外规模数字**，而后者必须**实跑取值**
      （新增类会动 java 文件数）⇒ 「一个数字住在好几个地方，要一起改」：只改 `README.md:163` 而把
      `PROJECT_FEATURES.md` 留到交付，等于用一次订正**造出两处新矛盾**。C1 只登记**本批自己的状态**
      （T003/T004/T005 三处，它们说的是「**立项了**」，与「模块总数」不是同一个数字）。
      ⚠️ 该前移**已在 `plan.md` §分步与提交「偏离二」登记**，并在**立项提交时向用户说明**
- [ ] T007 ⚠️ **立项阶段不改 `roadmap.md` 第 6 行的「交付态断言」**：该句说的是**已交付**的编号面，
      而 100 **尚未交付** —— 改成 `001–100` 等于把在办项写成已交付（**假话**）。
      照 **097/099 立项时**的同一处置：只前置一段带日期的 ⚠️ 块登记**随入列漂移的计数**
      （编号面判据 **98 → 99**；`## 当前进度` **98 勾 / 0 未勾 → 98 勾 / 1 未勾**），
      **旧值逐字保留**（`001–099` 仍在句中，可 grep 到）；交付时（T050）再改那句。**该偏离已在 `plan.md` 与 T045 登记。**
- [ ] T008 `PROJECT_FEATURES.md` 的 **Spec 模块行**：立项阶段**不动**（它与后端规模行、i18n 行同属**对外规模数字**，
      必须与 T049 的实测读数**同批改齐**；只改一处等于用一次订正造出两处新矛盾）—— **该前移已在 T049 登记**

## 阶段 B 共享限流件与通用 429 错误码（提交 C2 = `feat(100): 共享限流件与通用 429 错误码`）

**⚠️ 本阶段是「纯新增、零行为变更」**：不删旧处理器、不动任何 Controller/Service、不改 `SecurityUtil`。
**本阶段结束时仓里同时存在两套限流**（旧的 2 处内存桶 + 新的共享件），**这是有意的**（可回退的中间态）。

- [ ] T009 新建 `com/crm/security/RateLimit.java`：`@Target(METHOD)` + `@Retention(RUNTIME)`；
      `String scope(); int limit(); long windowSeconds(); RateLimitDimension by() default AUTO;`
      —— 形状与 javadoc 体例照 `security/RequirePermission.java`；**`scope` 必须是显式字符串**（不从 URI 推导，理由写进 javadoc）
- [ ] T010 新建 `com/crm/security/RateLimitDimension.java`：`enum { AUTO, IP, USER, API_KEY }`
      + javadoc 写明 `AUTO` 的判定顺序（机器主体→`keyId` → 已认证→`userId` → 否则→IP）
- [ ] T011 新建 `com/crm/security/RateLimitKeys.java`：键族 `rl:<scope>:<identity>`；
      **具名静态方法**（`user(scope, id)` / `apiKey(scope, keyId)` / `ip(scope, ip)`）**供调用形状断言逐字核对**
      （照 `MfaStateStore` 的 `failKey` 那组先例）；javadoc 写明「`rl:` 与 `auth:` **同级**，便于运维按前缀监控」
- [ ] T012 新建 `com/crm/security/RateLimitIdentity.java`：**纯静态函数**（可脱 Spring 单测）；
      `AUTO` 判定顺序按 T010；显式 `USER`/`API_KEY` 而**主体缺失时回退 IP 并 `log.warn`**
      （候选取舍见 `research.md` §8）；机器主体判定**复用** `SecurityUtil.isMachineSubject()`
- [ ] T013 新建 `com/crm/security/ClientIpResolver.java`：语义**逐字取** `AuthService.resolveClientIp` 那一份
      （**更严**的那份：XFF 首段为空则回退 fallback）；配置项 `crm.rate-limit.trust-forwarded-for`；
      ⚠️ javadoc **必须**写明三件事：① 只在反向代理是唯一入口时成立；② 直连可伪造（**已知并登记为债务**）；
      ③ **它不是抗敌手措施**，公开端点上的定位是**误用与意外的阻尼**
- [ ] T014 新建 `com/crm/security/RateLimiter.java`：依赖**只有** `RedisTemplate`（**不需要 `Clock`** —— 窗口是 Redis 自己的 TTL）；
      `increment` → **仅 `n == 1` 时 `expire`**（照 `MfaStateStore.recordFailure`，**不照** `AuthService.recordFailure`）；
      `count >= limit` 时读 `getExpire(key, MILLISECONDS)`，为 `-1`/`-2` 则**补回整窗 + `log.warn` + 放行**；
      拒绝时抛 `RateLimitExceededException`（带剩余秒数）；**fail-open 只 catch `RedisConnectionFailureException | DataAccessException`**、
      每条 `log.warn`、**不 catch 裸 `Exception`**、**不提供 `fail-open` 开关**；
      ⚠️ javadoc **必须**写明与 `MfaStateStore.lockRemainingSeconds` **刻意相反**的理由（引其类名与方法名）
- [ ] T015 新建 `com/crm/security/RateLimitAspect.java`：`@Aspect @Component` + `@Before("@annotation(rateLimit)")`；
      **显式 `@Order`**，排在 `PermissionAspect` **之后**（权限先于限流 ⇒ 未授权者不消耗配额）；
      `crm.rate-limit.enabled=false` 时直接放行
- [ ] T016 新建 `com/crm/common/RateLimitExceededException.java`：**`extends BusinessException`**
      （多重保障：万一没走到新处理器，父类的 `handleBusiness` 仍给正确 429，不会掉进 `Exception` catch-all 变 500）；
      携带**剩余秒数**供 `Retry-After` 用
- [ ] T017 `com/crm/common/ErrorCode.java`：新增 `RATE_LIMITED(429, "RATE_LIMITED", "请求过于频繁，请稍后再试")`，
      **紧邻 `MFA_LOCKED` 放置**；⚠️ **不合并 `MFA_LOCKED`**（`AuthMfaIT` 逐字断言它，且两者窗口语义**相反**）；
      ⚠️ `:158` 那句「全仓首个 429（本项引入；此前本枚举里没有任何限流码）」**原文逐字保留 + 追加带日期 ⚠️**
      （说明此后新增了通用限流码、「首个」照旧指 082）
- [ ] T018 `com/crm/exception/GlobalExceptionHandler.java`：**新增** `RateLimitExceededException` 处理器 → 429 +
      `Retry-After` 头；⚠️ **本次不删**旧的 `RateLimitedException` 处理器（C3 才删，见 T022）
- [ ] T019 `com/crm/security/PermissionAspect.java`：**加 `@Order`**（本项起仓里有第二个切面，不早定则 C2–C6 全程受影响）；
      附注释写明「权限先于限流」及其后果
- [ ] T020 `application.yml`：新增 `crm.rate-limit.enabled`（默认 `true`）与 `crm.rate-limit.trust-forwarded-for`（默认 `true`），
      照 `crm.outbound.allowed-hosts` 那种「**解释为什么是这个默认值**」的注释体例；
      ⚠️ **默认值只留一处真源**（有「代码默认 `true`、yml 里 `false`、yml 胜出」的前例）
- [ ] T020a 单测 T5–T9 五组：`security/RateLimitStoreTest.java`（T5 读时补窗）·
      `security/RateLimiterShapeTest.java`（T6 调用形状，**裸 mock**）· `security/RateLimitIdentityTest.java`（T7/T8）·
      `security/ClientIpResolverTest.java`（T9）· 复跑 T5 时造 `set(key, 99L)` 不设 TTL 的僵死键
- [ ] T020b 门禁：`mvn -B spotless:apply` + `mvn -B verify`（**本阶段应零行为变更** ⇒ 既有用例集**与基线逐例相同**）

## 阶段 C 邮件追踪改用共享限流件（提交 C3 = `refactor(100): 邮件追踪改用共享限流件`）

**⚠️ 本次单独暴露唯一的对外 code 字符串变更**（`TOO_MANY_REQUESTS` → `RATE_LIMITED`），**便于日后二分**。

- [ ] T021 `EmailTrackController`：`checkRateLimit(HttpServletRequest)` **保留签名、只换方法体为委托**；
      **60 / 60s 逐字不变**；删 `rateBuckets` / `RATE_WINDOW_MS` / `RATE_LIMIT` / `clientIp` /
      `RateLimitedException`（**不留兼容壳**，它已无 throw 点）；
      订正 `:72` 那句**与实现相反**的注释（注释说「降级为 400」，实际是 429）——**原文逐字保留 + 带日期 ⚠️**
- [ ] T022 `GlobalExceptionHandler`：**删**旧 `RateLimitedException` 处理器（连带消失的是 `exception → controller` 的 **FQN 反向依赖**）
      ——⚠️ **必须与 T021 同批**（否则中间态编译不过）
- [ ] T023 `integration/RateLimitIT.java`【新】：T1（超限 429 + code + `Retry-After`）· T2（**正对照：计数真写进存储**）·
      T3（`advanceSeconds(61)` 后恢复放行）· T4（`redis.failOnKeyPrefix("rl:")` ⇒ fail-open 且 200）·
      T12（**登录未被重复限流**：连败得 401 而非 429）· T13（未授权者 403 且**不消耗配额**）· T14（`Retry-After` ≤ 窗口）
      ——⚠️ **每条必须做满三件事**（装替身 + 正对照 + 负对照），且**必须显式给独立的 `X-Forwarded-For`**
- [ ] T024 门禁：`mvn -B verify`；⚠️ **单独复跑** `mvn -B verify -Dit.test='FormIT,LandingPageIT'` 确认**仍绿**（零改动是验收的一部分）
- [ ] T025 `quickstart.md` §5 的判据 ①②③ 实跑：`rateBuckets` / `RateLimitedException` / `TOO_MANY_REQUESTS` 零命中

## 阶段 D 表单提交改用共享限流件并订正 429 契约（提交 C4 = `refactor(100): 表单提交改用共享限流件并订正 429 契约`）

- [ ] T026 `FormService`：`checkRateLimit(String ip)` **保留签名与调用点、只换方法体为委托**；
      **3 / 60s 逐字不变**；`request == null → "unknown"` 字面量**逐字保留**；
      删 `rateBuckets` / `cleanupRateBuckets` / `clientIp`
- [ ] T027 **400 → 429**：`checkRateLimit` 的拒绝路径改抛 `RateLimitExceededException`；
      ⚠️ 附 javadoc **引 `specs/036-online-forms/contracts/online-forms.md:44` 与 `tasks.md:50`**
      说明「**契约是对的，改的是实现**」（085 判例）；**036 的工件一个字符不改**
- [ ] T028 `AuthService.resolveClientIp`：**保留签名**（`public static`）、方法体改 **1 行委托** 给 `ClientIpResolver`
      ⇒ `AuthController` 与 `AuthServiceTest` **零改动继续绿**
- [ ] T029 **删** `EmailTrackController#clientIp` 与 `FormService#clientIp` 两份私有副本
      —— ⚠️ **必须与 T028 同批**（否则三份并存，「收敛」没发生）
- [ ] T030 门禁：`mvn -B verify`；⚠️ 再次**单独复跑** `FormIT` 与 `LandingPageIT`；
      ⚠️ 若这两条变红**不许改断言** —— 要么是接线错了、要么是限流真在拦，必须**查清原因**
- [ ] T031 `quickstart.md` §5 的判据 ④ 实跑：`X-Forwarded-For` 在 `ClientIpResolver` 之外**零命中**

## 阶段 E 零限流路径接入与覆盖台账（提交 C5 = `feat(100): 零限流路径接入与覆盖台账`）

**⚠️ 本阶段的 P0 标注与台账测试必须同批**（否则中间提交是一堆**没有护栏的标注**）。

- [ ] T032 `SecurityUtil` 新增 `currentApiKeyId()`：从 `authentication.getDetails()` 取
      `ApiKeyAuthFilter.ApiKeyPrincipal.keyId()`（⚠️ **密钥 id 不在 principal 里**）；与 `currentUserId()` 并列，同 javadoc 体例
- [ ] T033 **P0 匿名 IP 三处**标注：`CustomerPortalController#ticketStatus`（`public-ticket-status` 10/60s）·
      `CustomerPortalController#submitTicket`（`public-ticket-submit` 5/60s）·
      `EmailUnsubscribeController#unsubscribe`（`public-unsubscribe` 10/60s）—— 均 `by = IP`
- [ ] T034 **P0 导出 13 处**标注：**generate 8**（`CustomerController#exportExcel` · `LeadController#exportExcel` ·
      `QuoteController#exportPdf` · `ReportController#exportReport` · `ComplianceExportController#export` ·
      `ExportController#create` · `ScheduledExportController#create` · `ScheduledExportController#executeNow`）
      → `export-generate` **10/60s**；**download 5**（`CustomerController#importTemplate` · `LeadController#importTemplate` ·
      `ContactController#importTemplate` · `ExportController#download` · `ContractAttachmentController#download`）
      → `export-download` **30/60s**；**均 `by = USER`**
      ⚠️ 判定依据是「**响应类型 + 是否走 Excel/PDF 引擎**」，**不是 URI 前缀、也不是方法名里有没有 `export`**
- [ ] T035 **P0 开放 API 三处**标注：`OpenPlatformController` 两个 GET → `open-api-read` **60/60s**；
      `#openCreateLead` → `open-api-write` **30/60s**；**均 `by = API_KEY`**
      ⚠️ 该类的 javadoc 明写三个 `/open/**` **刻意不加 `@RequirePermission`**（挂码会把全部 API Key 调用方打成 403）
      ⇒ 附注释说明「限流挂在这里是安全的，因为它**不依赖**权限码」
- [ ] T036 新建 `backend/src/test/java/com/crm/security/RateLimitCoverageTest.java`：
      **字节码扫描、不启 Spring**（复用 `support/RequirePermissionScanTestSupport` 的 `MetadataReader` 手法）；
      枚举 `com/crm` 下全部**控制器端点方法**，每个必须有 `@RateLimit` 或在**豁免白名单**里
- [ ] T037 豁免白名单：**粒度必须是「类#方法」**（**不是 URI 前缀**）；**每条必须带非空理由**（**无理由判失败**）；
      初始四类 = ① 服务内限流的 3 处（`EmailTrackController` 两个 + `FormController#submit`）·
      ② FR-029 的逐条豁免（`/actuator/health*` / `/error` / `OPTIONS /**` / swagger 三路径 / `/ws/**` / 已认证的常规读接口）·
      ③ 登录的 2 层锁定（`AuthService` 两个 record*）· ④ 2FA 两处（`MfaStateStore`）
- [ ] T038 T11 **自检**必须带：先断言「扫描确实扫到了 ≥ N 个端点」
      （照 `RequirePermissionCatalogTest` 的先例）—— 否则扫描 pattern 写错时「零违规」是**假绿**
- [ ] T039 门禁：`mvn -B verify`；⚠️ **单独复跑 `FormIT` / `LandingPageIT`**；⚠️ 注意 T033 起
      `LandingPageIT` 打的 `GET /public/lp/{id}` 会走到**有 IP 限流**的端点 ⇒ 若变红先查是否**漏了独立 XFF**
- [ ] T040 冒烟（**只读**）：对 `/api/v1/open/**` 的 GET（**只读、需 API Key**）连打至超限，观察 429 + `Retry-After` +
      统一 `ApiResponse` 信封；**不写库**；⚠️ 8081 上若跑的是**改动前的旧实例**则**看不到限流** ⇒ 按 `quickstart.md` §4 起隔离实例并写明端口

## 阶段 F P1 接入、文档订正与数字收口（提交 C6 = `docs(100): P1 接入、文档订正与数字收口` = 交付）

- [ ] T041 **P1 匿名只读四处**共用 `scope=public-read` **60/60s**，`by = IP`：
      `FormController#meta` · `LandingPageController#publicView` · `CustomerPortalController#articles` ·
      `CustomerPortalController#article`
- [ ] T042 **P1 导入三处** `scope=import-excel` **5/60s**，`by = USER`：
      `CustomerController#importExcel` · `LeadController#importExcel` · `ContactController#importContacts`
      （⚠️ 漏掉它会形成明显的不对称：能导出受限、能导入不受限）
- [ ] T043 `CRM_FEATURE_COMPARISON.md` **2.9 速率限制行**订正：**原文逐字保留 + 带日期 ⚠️** ——
      「不是**仅 1 处**，是 4 宿主类 / 6 处计数点、零共享件」；
      「**『登录无限流』为假**：登录是三层（017 验证码**始终要求** + 用户名 5 次 + IP 10 次，各 15 分钟）；
      准确表述是『**缺统一收口件**』」；**判定列与分值照旧不动**（订正留痕不改分）
- [ ] T044 `CRM_FEATURE_COMPARISON.md` **P0 第 5 条**订正：**原文逐字保留 + 带日期 ⚠️** ——
      写明**采纳其意图**（单一实现 + 全端点覆盖 + 台账护栏）、**不采纳其字面机制**（Security 链过滤器），
      理由取事实 ①②③（其中②= **顺序契约无护栏**、③= **全部导出非流式** ⇒ 过滤器的唯一结构性优势不存在）；
      ⚠️ **改完做竖线自证**：该行是表格行，改动后**列数必须与表头一致**
- [ ] T045 `config/SecurityConfig.java` 加注释：说明**为什么本项不走过滤器**
      （一句话给结论 + 指向 `RateLimitAspect`），避免后人「补一个过滤器更统一」的重复讨论
- [ ] T046 债务台账（`specs/roadmap.md` 遗留惯例）加**三条**：① **XFF 首值无条件信任**
      （附理由、修复动作、**口径降级**：公开端点限流是「误用与意外的阻尼」**不是抗敌手**）·
      ② **固定窗口的 2× 边界突发** · ③ **`INCR` + `EXPIRE` 非原子**（修它需 Lua，且**必须同步扩 `InMemoryRedisTestSupport`**）
- [ ] T047 定向破坏 **D1–D11** 逐条做、逐条**观测到转红 /（D11）观测到仍绿**，逐条还原；
      结果填入 `falsification-evidence.md` §A–§K。⚠️ **D3 与 D4 必须分开做**（打的是 T3 的不同两半）
- [ ] T048 写 `falsification-evidence.md`：**定向破坏的逐字留痕**（含还原判据）+ 门禁实跑读数 + 冒烟记录 +
      可核判据实跑 + 订正不静默自查命中数 + **四条如实登记的边界**（见 T051）
- [ ] T049 `PROJECT_FEATURES.md`：**Spec 模块行** 98 → **99（001–100，缺 069）**；
      **后端规模行**（java 文件数等）按**实跑**改写；**与 T006 的欠账 / T007 同批改齐**（**不推算**）；
      **`README.md:163`** 的 `98 个功能模块，001~099，缺 069` → **`99 个功能模块，001~100，缺 069`**
      （**旧值逐字保留 + 带日期 ⚠️**；判据 `ls -d specs/[0-9]* | wc -l` = **99**）—— **T006 前移到此，同批改**
- [ ] T050 `specs/README.md` / `specs/roadmap.md` 的状态列改**交付态**（`✅ 已交付`）（**勾选在交付时**）；
      `roadmap.md` 第 6 行的交付态断言 98 → **99**（T007 的欠账在此还）；100 行补【交付后记】
- [ ] T051 `tasks.md` 全部勾选（**交付时才勾**）+「实做订正」小节（如实记录与本计划的偏差，见下）
- [ ] T052 数字落点一致自查（**逐处点名，不写「若干处」**）：
      **Spec 模块数** = `ls -d specs/[0-9]* | wc -l` = `README.md:163` = `PROJECT_FEATURES.md` 的 Spec 模块行 =
      `specs/roadmap.md` 第 6 行的编号面判据 = `specs/README.md` 编号说明；
      **用例/测试文件数** = `mvn -B verify` 的实跑打印 = `PROJECT_FEATURES.md` 的后端测试行
- [ ] T053 订正不静默自查（`quickstart.md` §6 五条命令）实跑，**逐条确认非零**，命中数填入 `falsification-evidence.md`
- [ ] T054 `git status --porcelain specs/036* specs/055*` 必须为**空**（**契约是对的，改的是实现**；两个上游工件一字不动）

---

## 实做订正（**如实记录与本计划的偏差**，交付时填）

> 立项期已登记的**三**条（**不是事后补记**）：

1. **提交数 5 → 6**：已批准计划写的是 **5 次**提交（C1 共享件起步），实做拆成 **6 次** ——
   多出的第一次是 `docs(100): 立项`（**纯文档、零代码**）。
   **理由**：仓库惯例是**立项独自一次**（099 有 `5e9ffbb docs(099): 立项` 为先例），
   让 `docs` 与 `feat` 的边界与仓规一致；其余 5 次的内容与顺序**与已批准计划逐字相同**，只是编号后移。
   该偏离**已在立项提交时向用户说明**，并写进 `plan.md`。
2. **`README.md:163` 与 `PROJECT_FEATURES.md` 的模块数由 C1 前移到 C6**（T006 → T049）：
   已批准计划把 `README.md:163` 的 `98 → 99` 放在 C1；照 099 的 T007 先例改到交付提交，
   因为它们是**对外规模数字**、且 `PROJECT_FEATURES.md` 的后端规模行必须**实跑取值** ——
   分开改等于用一次订正造出两处新矛盾。该偏离**已在立项提交时向用户说明**，并写进 `plan.md`。
3. **`roadmap.md` 第 6 行的交付态断言在立项阶段不改**（T007）：它是**已交付**的断言，
   立项期改等于把在办项写成已交付；照 097/099 同一处置，只在交付时（T050）改。**旧值逐字保留可 grep**。

> 以下待交付时如实填：

4. **事实 ⑬ 那颗雷的语义变化必须显式登记**：切 Redis 后，`FormIT` / `LandingPageIT` 在**默认基类**下
   限流**变成 no-op**。**这不是弄丢护栏** —— 那个内存桶今天**不是护栏而是跨用例共享状态**，
   且它对限流是**零用例**的（`rateBuckets` / `RATE_LIMIT` / `提交过于频繁` 在 `src/test` **0 命中**）。
   但**必须写进留痕**，免得后人以为「IT 里限流一直生效」。
5. **定向破坏里若出现「预期仍绿」的条目**（D11 最可能），**如实记为已知空档**，**不假装有护栏**。
   ⚠️ 造破坏时先写「它该改变哪条可观察行为」，跑完核对**那条行为确实变了** —— 没变就是**空操作**，
   别把绿记成结论；看到红先读**是不是判据本身**（`TS6133` 一类是**手段**的红，不是**目的**的红）。
6. （预留）其余偏差在交付时逐条补记。

---

## 交付块（**交付时填**，不得预填）

| 项 | 值 |
|---|---|
| 提交数 / 末条提交主题 | （交付时填） |
| `mvn -B verify`（surefire / failsafe） | （交付时填：失败集合 ⊆ 4 例已批准偏差 + 本批新增全绿） |
| `jacoco:check` 结论行 | （交付时填：**必须**有 `All coverage checks have been met.`） |
| 覆盖率（INSTRUCTION / BUNDLE） | （交付时填：≥ **0.73**，阈值未改） |
| `ls backend/target/jacoco.exec` | （交付时填：**必须存在**） |
| 新增测试文件 / 用例数 | （交付时填：5 个单测类 + 1 个 IT 类；T1–T14 全绿） |
| 定向破坏 D1–D11 | （交付时填：逐条红/绿 + 还原判据） |
| 只读冒烟 | （交付时填：端点、端口、读数；⚠️ 写清打的是哪个实例） |
| 可核判据（`quickstart.md` §5） | （交付时填） |
| 订正不静默自查（`quickstart.md` §6） | （交付时填：**逐条非零**） |
| 五处对外可观测变更的提交落点 | （交付时填：①②③④⑤ 各在哪次提交） |
| ⚠️ 本项**不把提交自己的哈希**写进任何被它携带的文件 | 遵守（只引用**提交主题/序号**） |
