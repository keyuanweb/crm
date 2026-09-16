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
      ⑫ **六处对外可观测的行为变更**（立项时是**五处**，⑥ 由 C3 实做期补登，见 §实做订正 14）；
      ⑬ 台账护栏；⑭ 与 036/016/017/055 的关系；⑮ **覆盖缺口与假绿通道**（含事实 ⑬ 那颗雷）
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

**⚠️ 本阶段是「纯新增、零行为变更」**：不删旧处理器、不动任何 Controller/Service、
不改 `SecurityUtil` 的**既有成员**（只**新增** `currentApiKeyId()` —— 它在 C2 就要用上，见 §实做订正 5）。
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
      （候选取舍见 `research.md` §8）；机器主体判定**复用** `SecurityUtil.isMachineSubject()`；
      机器主体取 `SecurityUtil.currentApiKeyId()` —— **该方法在本次一并新增**（从
      `authentication.getDetails()` 的 `ApiKeyAuthFilter.ApiKeyPrincipal.keyId()` 取，⚠️ **密钥 id 不在 principal 里**，
      与 `currentUserId()` 并列、同 javadoc 体例）；**纯新增成员、零行为变更**，故不破本阶段的「纯新增」性质
- [ ] T013 新建 `com/crm/security/ClientIpResolver.java`：语义**逐字取** `AuthService.resolveClientIp` 那一份
      （**更严**的那份：XFF 首段为空则回退 fallback）；配置项 `crm.rate-limit.trust-forwarded-for`；
      ⚠️ javadoc **必须**写明三件事：① 只在反向代理是唯一入口时成立；② 直连可伪造（**已知并登记为债务**）；
      ③ **它不是抗敌手措施**，公开端点上的定位是**误用与意外的阻尼**
- [ ] T014 新建 `com/crm/security/RateLimitStore.java`（**立项期这一件写成 `RateLimiter`，实做拆成两个类**，见 §实做订正 4）：
      依赖**只有** `RedisTemplate`（**不需要 `Clock`** —— 窗口是 Redis 自己的 TTL）；
      `increment` → **仅 `n == 1` 时 `expire`**（照 `MfaStateStore.recordFailure`，**不照** `AuthService.recordFailure`）；
      `count >= limit` 时读 `getExpire(key, MILLISECONDS)`，为 `-1`/`-2` 则**补回整窗 + `log.warn` + 放行**；
      返回值 = **剩余秒数**（`0` = 放行）；**fail-open 只 catch `RedisConnectionFailureException | DataAccessException`**、
      每条 `log.warn`、**不 catch 裸 `Exception`**、**不提供 `fail-open` 开关**；
      ⚠️ javadoc **必须**写明与 `MfaStateStore.lockRemainingSeconds` **刻意相反**的理由（引其类名与方法名）
- [ ] T014a 新建 `com/crm/security/RateLimiter.java`（**组合层**，与 T014 的 store 分开）：依赖 `RateLimitStore` + `ClientIpResolver`；
      身份 → 键 → 交给 store → **剩余秒数 > 0 时抛** `RateLimitExceededException`；
      **自己不碰 `SecurityContextHolder`**（那在 `RateLimitIdentity` 里），使两层各自可单测
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
      ——⚠️ **实做改为「删掉该方法 + 两个端点各挂 `@RateLimit`」**，不是「保留签名换方法体」（见 §实做订正 11；
      **配额三元组与共用 scope 逐字不变**，`clientIp` 的删除因此也随本次落地，见 §实做订正 12）
- [ ] T022 `GlobalExceptionHandler`：**删**旧 `RateLimitedException` 处理器（连带消失的是 `exception → controller` 的 **FQN 反向依赖**）
      ——⚠️ **必须与 T021 同批**（否则中间态编译不过）
- [ ] T023 `integration/RateLimitIT.java`【新】：T1（超限 429 + code + `Retry-After`）· T2（**正对照：计数真写进存储**）·
      T3（`advanceSeconds(61)` 后恢复放行）· T4（`redis.failOnKeyPrefix("rl:")` ⇒ fail-open 且 200）·
      T12（**登录未被重复限流**：连败得 401 而非 429）· T14（`Retry-After` ≤ 窗口）
      ——⚠️ **本行原有 T13，实做时移出本阶段**（**落点后移到 C5**，见 §实做订正 10 与 `plan.md` 的 T13 订正块；
      C3 里带限流的只有两个**公开**端点 ⇒ 写「未授权者 403 且无配额键」是**空断言**）。**原描述（留痕）**：
      「· T13（未授权者 403 且**不消耗配额**）」
      ——⚠️ **每条必须做满三件事**（装替身 + 正对照 + 负对照），且**必须显式给独立的 `X-Forwarded-For`**
- [ ] T024 门禁：`mvn -B verify`；⚠️ **单独复跑** `mvn -B verify -Dit.test='FormIT,LandingPageIT'` 确认**仍绿**（零改动是验收的一部分）
- [ ] T025 `quickstart.md` §5 的判据 ①②③ 实跑：`rateBuckets` / `RateLimitedException` / `TOO_MANY_REQUESTS` 零命中
      —— ⚠️ **实做：口径加了「排除注释行」，且只有 ③ 能在本阶段满足、①② 跨到 C4**（见 §实做订正 15、16）

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
      —— ⚠️ **实做：前半（`EmailTrackController#clientIp`）已随 C3 落地**（C3 删掉 `checkRateLimit` 后它已无调用点），
      **本行只剩 `FormService#clientIp`**；T029 要防的「三份并存」中间态并未出现（见 §实做订正 12）
- [ ] T030 门禁：`mvn -B verify`；⚠️ 再次**单独复跑** `FormIT` 与 `LandingPageIT`；
      ⚠️ 若这两条变红**不许改断言** —— 要么是接线错了、要么是限流真在拦，必须**查清原因**
- [ ] T031 `quickstart.md` §5 的判据 ④ 实跑：`X-Forwarded-For` 在 `ClientIpResolver` 之外**零命中**
      —— ⚠️ **本行还兼 ①② 的完整判据**（它们跨到 C4，见 §实做订正 16）；「零命中」一律**排除注释行**

## 阶段 E 零限流路径接入与覆盖台账（提交 C5 = `feat(100): 零限流路径接入与覆盖台账`）

**⚠️ 本阶段的 P0 标注与台账测试必须同批**（否则中间提交是一堆**没有护栏的标注**）。

- [ ] T032 ⚠️ **已前移到 T012（C2）**：`SecurityUtil.currentApiKeyId()` 是 `RateLimitIdentity` 在
      C2 的必要依赖（机器主体按 `keyId` 分桶），故与共享件同批交付、**不在本阶段**。此处保留行号占位，
      内容见 T012。**原描述（留痕）**：`SecurityUtil` 新增 `currentApiKeyId()`：从 `authentication.getDetails()` 取
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

> 立项期已登记的**五**条（**不是事后补记**）：

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
4. **新件 7 → 8 个**：`RateLimiter` 拆成 `RateLimitStore`（Redis 计数协议）+ `RateLimiter`（组合层）。
   理由：让 `RateLimitStoreTest`（T5 补窗协议）与 `RateLimiterShapeTest`（T6 调用形状）**各对着一个类**——
   混在一个类里时，这两个测试的文件名与实际结构对不上，而「对着什么测」正是它们唯一的可读线索。
   `plan.md` 的组件表与 C2 行已同步按实做改写。
5. **`SecurityUtil.currentApiKeyId()` 由 C5 前移到 C2**（T032 → T012）：`RateLimitIdentity` 在 C2 就需要它，
   而它是**纯新增成员、零行为变更** ⇒ C2 的「纯新增」性质不变。C5 少一件，其余 P0/P1 标注计划不变。

> **C2 实做期登记的偏差（4 条，随 C2 提交一并入库）**：

6. **T014 的「多 catch」是编译错误 ⇒ 只 catch `DataAccessException`**：原文写「fail-open 只 catch
   `RedisConnectionFailureException | DataAccessException`」，但 `RedisConnectionFailureException`
   **是** `DataAccessException` 的**子类** ⇒ 多 catch 形式直接被 javac 判「已由备选 `DataAccessException` 捕获」。
   实做为**单个 `DataAccessException` catch**（两处：计数路径与读窗口路径）。**判据覆盖面不变** ——
   子类照旧被捕获，`RateLimitStoreTest.everyStoreFailureIsFailOpen` 注入的正是
   `RedisConnectionFailureException` ⇒ 「Redis 挂 ⇒ 放行」仍被钉住；「**不 catch 裸 `Exception`**」逐字成立。
   `RateLimitStore` 的 javadoc 把这条**编译错误**明白写出来（防后人「顺手补回多 catch」）。
7. **`RATE_LIMITED` 落在 082 MFA 组**之后**，不是字面的「紧邻 `MFA_LOCKED`」**：T017 写「**紧邻
   `MFA_LOCKED` 放置**」。实做放在 `MFA_STORE_UNAVAILABLE` **之后**（整个 MFA 组之后），
   因为插进组中间会把 082 的 MFA 块**劈成两半**、注释块与枚举项对不上。
   `:158` 那句「全仓首个 429（本项引入…）」**原文逐字保留 + 追加带日期 ⚠️**（照 T017 要求），
   并在该 ⚠️ 里点明新码的位置是「**下方 MFA 组之后的 429 段**」。其余判据逐字成立：
   **不合并 `MFA_LOCKED`**、两码并存（① `AuthMfaIT` 逐字断言 `error.code == "MFA_LOCKED"`；
   ② 两者窗口语义相反）。
8. **T6「用 `increment` 而非 `get`+`set`」的判据落在 `RateLimitStoreTest`**（不是字面的
   `RateLimiterShapeTest`）：判据本身（断言 `increment` 被调用、`get` / `set` **零调用**）**逐字成立**，
   但落点是 `RateLimitStoreTest` —— T014/T014a 把新件拆成 store 与组合层后，**原语住在 store 里**，
   `get`+`set` 的劣解只可能在那里发生；`RateLimiterShapeTest` 钉的是**组合层**的调用形状
   （键名逐字 + 配额三元组 + `verifyNoMoreInteractions`）。两者失效方式不同（「用了哪个原语」vs
   「键长什么样」），故分在两类里并互相在 javadoc 里指路。T020a 的行文已按实做列出四个文件。
9. **`ClientIpResolver` 用 `indexOf(',')` 而非 `split(",")[0]` —— 立项期对退化输入的事实描述被实测推翻
   （同时堵掉一处潜伏 500）**：`plan.md`「五处对外可观测变更」第 ④ 条与 `research.md` §9 都写
   「XFF 为 `","` 时 `split(",")[0].trim()` = **空串** ⇒ **所有这类请求共用一个键为 `""` 的桶**」。
   ⚠️ **实测该表述是错的**（由 `ClientIpResolverTest.degenerateHeadersFallBackToTheFallback` 实测转红暴露，
   红的是 `ArrayIndexOutOfBoundsException: Index 0 out of bounds for length 0`）：Java 的 `split`
   **丢弃末尾空段**，故 `",".split(",")` 切出的是**长度 0 的数组**，取 `[0]` **抛异常**。
   ⇒ 三份副本在**公开端点**上是**一条潜伏的 500**（请求头由调用方任意构造），而不是共用一个空串桶。
   真正产生**空串桶**的输入是 `", 1.2.3.4"` 这一类（整串非空、首段为空白），此时另两份
   （只判「整头非空」）返回空串，`AuthService` 那份（多一层「首段非空」判定）正确回退。
   **实做**：改用 `indexOf(',')` + `substring`，三种退化输入**一律回退 fallback —— 既不返回空串、也不抛异常**。
   **结论不变**（收敛取更严那份、「统一为 fallback」），但**理由升级**：不再是「另两份更松」，
   而是「**另两份会抛异常**」；这同时是「把三份改成一份」这个动作的**净收益** —— 照抄 `split`
   只是把三份的同一个洞搬进一份。`research.md` §9 与「五处变更」第 ④ 行已按实测订正
   （**原文逐字保留 + 带日期 ⚠️**，旧值仍可 grep）。

> **C3 实做期登记的偏差（6 条，随 C3 提交一并入库）**：

10. **T13（未授权者得 403 且不消耗配额）的落点由 C3 后移到 C5**：T023 把它列在
    `integration/RateLimitIT.java`，`plan.md` 的 C3 行与文件树也写「T1–T4、T12–T14」。
    实做时**该用例在 C3 写不出来**：它要的端点必须**同时**带 `@RequirePermission` 与 `@RateLimit`，
    而 C3 里带限流的只有两个**公开**邮件追踪端点（它们本来就不需要权限）⇒「未授权者得 403」与
    「没有配额键」在这个端点上**无论如何都成立**，与「权限先于限流」这个待测命题之间**没有因果链**，
    写出来是**空断言**。**判据本身一字不变**（仍须全 403 且 `redis.snapshot()` 无该键），
    只是落点跟着「本批第一个同时带两种注解的端点」走 —— 即 **C5 的 13 个导出端点**。
    已同步订正 `plan.md` 的 **T13 行**（**原文逐字保留 + 带日期 ⚠️ 块**，点名被改的是「**文件**」列）、
    **C3 行**、**C5 行**与**文件树**；`RateLimitIT` 的类 javadoc 就地写明，免得读者以为漏了。
    ⇒ C3 的 `RateLimitIT` 实为 **6 例**（T1–T4、T12、T14）。
11. **T021 的形式改为「删掉该方法 + 两个端点各挂注解」，不是字面的「保留签名、只换方法体为委托」**：
    原文要求 `checkRateLimit(HttpServletRequest)` **保留签名、只换方法体**。实做删掉了这个方法，
    改为在两个端点上各挂 `@RateLimit(scope = "public-email-track", limit = 60, windowSeconds = 60, by = IP)`。
    理由：该私有方法的**唯一调用点就是它自己的两个端点**（EmailTrackController 之外无调用者，
    也没有子类或测试直接调它）⇒ 保留一个「只被自己调一次」的委托壳**没有第二读取者**，
    反而成了本批唯一机制（注解 + 切面）的反例。**可观测行为逐字不变**：配额 **60/60s**、按 **IP**、
    **两个端点共用同一个 scope**（改造前它们本来就共用同一个 `rateBuckets`，像素与点击合起来数 60）；
    `HttpServletRequest` 形参随之从两个端点消失（不再需要它来手工解析 IP）。
    ⚠️ **与 `FormService`（C4）刻意不同**：那里**保留** `checkRateLimit(String ip)` 的签名与调用点，
    因为它的调用点在**服务内部**、入参是**已解析好的 IP 字符串**，换成注解会把这层
    「服务自持配额」的语义挪到 controller。**两种形态并存是有意的**，不是没统一 —— 判据是
    「端点自己就是被限流单元」还是「服务方法才是」。
12. **T029 的前半（删 `EmailTrackController#clientIp`）随 C3 落地，不在 C4**：T029 要求两份私有副本的删除
    **必须与 T028 同批**。C3 把整个方法体换成注解后，`EmailTrackController#clientIp` **已无调用点**
    （删 `checkRateLimit` 必然连带删它）⇒ 它随 C3 消失。C4 只剩 `FormService#clientIp` 的删除与
    `AuthService.resolveClientIp` 的 1 行委托。T029 想防的「**三份并存**」中间态**并未出现**：
    C3 后是**两份**（`AuthService` + `FormService`），C4 后归**一份**。
    **判据不变**：C4 结束时 `X-Forwarded-For` 在 `ClientIpResolver` 之外**零命中**（T031）。
13. **T12 的判据被**加严**（不是放宽），且该用例**红过一次**，红因是断言自己的解码**：
    T023 的字面判据是「连续失败登录应得 `INVALID_CREDENTIALS`(401) 而非 `RATE_LIMITED`(429)」。实做两条：
    ① 前 5 次失败断言 `401` + `INVALID_CREDENTIALS`，第 6 次断言走进 `AuthService` **自己的**锁定分支
    （`401` + 文案「登录失败次数过多」）⇒ 证明登录的自有防护**仍在岗**、不是被本组件取代；
    ② 断言 `redis.snapshot()` 里**一个 `rl:` 键都没有**，并**同批**断言存在 `auth:fail:` 键作**正对照**。
    **为什么必须加严**：「连打 N 次不得 429」在 N 小于阈值时是**空断言**（首次失败就会触发 `auth:fail:`
    计数，而锁定阈值在 `AuthService` 里）—— 它**无论如何都成立**；而 `rl:` 键的**存在与否对阈值免疫**：
    只要有人给 login 挂上 `@RateLimit`，**第一次请求**就会建键。正对照则防「没有 rl: 键」是因为
    整条 Redis 路径都是死的。
    ⚠️ 该用例首次运行时**失败**，真因是**断言自己的解码**、不是被测行为：`MockHttpServletResponse`
    的默认字符集是 **ISO-8859-1**，而这两个响应**头里没有 charset** ⇒ 无参 `getContentAsString()`
    读中文得到一串乱码，断言以「文案不匹配」的形式红掉、把注意力引向被测代码。
    改用 `getContentAsString(StandardCharsets.UTF_8)` 后 6 例全绿 —— 报错信息里当时那串正确的
    `"登录失败次数过多，请 15 分钟后再试"` 即是「行为自始至终是对的」的证据。**该陷阱已写进用例注释。**

14. **对外可观测变更清单由「五处」补登为「六处」**：第 **⑥** 处 = **窗口算法滑动窗口 → 固定窗口**。
    立项期**只把「固定窗口的 2× 边界突发」写进债务台账**（`plan.md`「明确不做」末条），**没写进
    对外变更清单** —— 而它同时是**变更**：改造前两处内存桶是**滑动窗口**（`Deque<Long>` 时间戳队列，
    逐次淘汰超窗的时间戳 ⇒ **任意** 60 秒跨度内 ≤ 阈值），共享件是**固定窗口**（`INCR` + 首次 `EXPIRE`
    ⇒ 跨窗口边界两次各放满，最坏 2× 突发）。**窗口内部逐字等价**（第 60 次放行、第 61 次拒绝 ——
    T1/T14 钉的正是这个），差异只在**边界突发**。
    ⇒ **判据一个字不改**，但清单是「对外可观测的行为变更」的**全集**，漏掉它会让 javadoc 里
    「阈值语义与改造前一致」被读成「窗口语义也一样」。已按「**一个数字住在好几个地方，要一起改**」
    同步 **6 处落点**：`plan.md` 的清单标题（五处→六处）+ 风险表那一行 + `research.md` §12 标题与表格
    （加第 ⑥ 行）+ `quickstart.md:147` 的引用 + `checklists/requirements.md` 的那条 +
    本文件 T002 的 ⑫ 与交付块那一行。**旧值（「五处」）逐字保留在所改文档里、仍可 grep**。
    另：`research.md` §12 的「① 单独暴露在**第 2 次提交**」按**已批准计划的 5 次编号**；
    实做为 6 次 ⇒ 落点是**第 3 次**，已就地补 ⚠️。

15. **`quickstart.md` §5 的判据 ①②③ 的「零命中」口径必须加「排除注释行」——否则它们与本批自己的
    「订正不静默」规则互斥、在任何正确实现下都不可能满足**：T025 要求实跑这三条。实跑发现 ③ 的
    `grep -rn "TOO_MANY_REQUESTS" backend/src/` 与 ② 的 `rateBuckets` 都**非零命中**，但命中的**全部是
    注释** —— 而且**必然如此**：本批的留痕规则要求「**旧值仍能被 grep 到**」，而这两条要 grep 的
    **正是旧值的名字**（`EmailTrackController` 的类 javadoc 点了 `rateBuckets`；`GlobalExceptionHandler`
    与 `ErrorCode` 的 ⚠️ 块点了 `TOO_MANY_REQUESTS`）。⇒ **两条路都比缺陷本身坏**：判据被无视，
    或后人**删掉留痕**去凑绿。
    **实做口径**：命令末尾加 `| grep -v ':[0-9]*: *[/*]'`（排除行首注释），**实质判据
    「可执行位置零命中」一个字不改**；③ 另加显式许可 —— `src/test` 里的**负断言**
    （`.doesNotContain("TOO_MANY_REQUESTS")`）是「旧串已消失」的**正向证据**、不算残留。
    实跑读数（**本次**）：③ 后端主代码**零命中**（排除注释后；测试里只剩那条负断言）；
    ② 的**测试侧**已零命中（证明这两条路径此前**零用例** —— 事实 ⑬ 的判据），
    **主代码侧尚有 `FormService`**（C4 的活儿）⇒ ② 的**完整**判据在 C4 才能满足，见 16。
    ⚠️ 该 filter **只匹配行首注释**，行尾注释（`foo(); // TOO_MANY_REQUESTS`）**漏得掉** ——
    本批留痕一律是整行注释故够用，属**已知口径缺口**，已就地登记在 `quickstart.md`。
16. **T025 的三条判据只有 ③ 能在 C3 满足，①② 跨到 C4**：判据 ①（3 份 `clientIp` 收敛成 1 份）要等
    `AuthService` 的 1 行委托与 `FormService#clientIp` 的删除（**C4**）；判据 ②（两处内存桶消失）
    要等 `FormService` 的收敛（**C4**）。T025 列在阶段 C 是**行序问题**，不是判据变松：本阶段实跑的是
    **③ 全绿** + ② 的**测试侧**（已零命中）。**①② 的完整判据随 T031 在 C4 复跑**，届时二者都必须
    零命中。此处如实登记，免得交付时把「C3 跑过 ①②」当成既成事实。

> 以下待交付时如实填：

17. **事实 ⑬ 那颗雷的语义变化必须显式登记**：切 Redis 后，`FormIT` / `LandingPageIT` 在**默认基类**下
    限流**变成 no-op**。**这不是弄丢护栏** —— 那个内存桶今天**不是护栏而是跨用例共享状态**，
    且它对限流是**零用例**的（`rateBuckets` / `RATE_LIMIT` / `提交过于频繁` 在 `src/test` **0 命中**）。
    但**必须写进留痕**，免得后人以为「IT 里限流一直生效」。
18. **定向破坏里若出现「预期仍绿」的条目**（D11 最可能），**如实记为已知空档**，**不假装有护栏**。
    ⚠️ 造破坏时先写「它该改变哪条可观察行为」，跑完核对**那条行为确实变了** —— 没变就是**空操作**，
    别把绿记成结论；看到红先读**是不是判据本身**（`TS6133` 一类是**手段**的红，不是**目的**的红）。
19. （预留）其余偏差在交付时逐条补记。

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
| **六处**对外可观测变更的提交落点 | （交付时填：①②③④⑤⑥ 各在哪次提交；⑥ 为 2026-09-16 C3 实做期补登） |
| ⚠️ 本项**不把提交自己的哈希**写进任何被它携带的文件 | 遵守（只引用**提交主题/序号**） |
