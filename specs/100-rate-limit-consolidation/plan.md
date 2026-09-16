# 实施计划：全局限流收口（100）

**上游**：[spec.md](./spec.md)（US1–US3 / FR-001–FR-040 / SC-100-001–010）
**用户裁决（2026-09-16）**：「收口」范围 = **补空档 + 收敛既有 2 处重复实现** ｜ 登录的 2 层锁定**不动** ｜ 登录侧**不加限流**，只订正文档

**⚠️ 行号口径**：本文件里的行号是**立项时的实测值**，只作定位辅助。**权威锚点是符号名**
（类名 / 方法名 / 字段名 / 字符串字面量）。本仓已实测过「行号引用会腐坏」（见 `research.md` 开头）。

---

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

### 一、契约优先的 API 设计（不可协商）
- [x] **不新增端点、不改任何 DTO、不动任何 OpenAPI 契约**：本项加的是**横切行为**（频率上限），不是接口形状。
- [x] **不产出 `contracts/`**（无端点/无实体/无迁移）。唯一的契约交互是**向既有契约靠拢**：
      `specs/036-online-forms/contracts/online-forms.md:44` 与 `tasks.md:50` 承诺 **429** 而实现给 400
      ⇒ **本项改实现，契约一个字符不改**（判例已在 `GlobalExceptionHandler` 的注释里，085 确立）。
- [x] ⚠️ **一处对外**已有**行为的变更**（照章程「契约不得被静默修改」的精神**显式披露**）：
      邮件追踪 429 的 `error.code` 由 `TOO_MANY_REQUESTS`（**硬编码在处理器里的裸字符串**）改为 `RATE_LIMITED`（枚举）。
      **但**：该字符串**不在任何冻结契约里**（`036` 只承诺状态码 429），且全仓 `src/test` 对它 **0 命中**、
      前端对它 **0 命中**（`apiClient.ts` 只读 `error.message`）⇒ 判定为**未被契约化的实现细节**，
      按仓规「订正不静默」**在提交信息里单独点出**（见 §分步与提交 C3）。

### 二、分层架构与关注点分离（不可协商）
- [x] **业务逻辑只在 Service 层** ⇒ 限流判定收进 `com.crm.security.RateLimiter`（**新件**），
      Controller **只加一个注解**（HTTP 关注点之外的横切声明），**不在任何 Controller 里写计数逻辑**。
- [x] **切面属横切关注点**，与既有 `PermissionAspect` 同址同形（`@Aspect @Component` + `@Before("@annotation(...)")`）⇒ **不引入新范式**。
- [x] **前端零改动**（`apiClient.ts` 只读 `error.message`，429 文案天然可展示）⇒ 不新增 i18n 键、不动任何 `.tsx`。
- [x] **收敛方向正确**：删掉的是**重复实现**（`EmailTrackController` 与 `FormService` 各自的私有 `checkRateLimit`/`clientIp`），
      留下的是**共享件 + 1 行委托** ⇒ 净删多于净增（前端 0 行，后端 3 份 IP 工具 → 1 份）。

### 三、数据完整性、安全与校验（不可协商）
- [x] **安全边界在服务端**：限流是本项新增的**服务端**控制（不依赖前端）。
- [x] ⚠️ **诚实标注本项**不是**的东西**：公开端点上的 IP 限流**不是抗敌手措施**——`X-Forwarded-For` 首值被无条件信任，
      而本项新增的匿名 IP 桶**扩大了**该洞的利用价值（此前只有 2 个匿名端点按 IP 分桶，现在更多）。
      ⇒ **结论取「不修」**（理由与动作见 `research.md` §9），但**验收口径降级**为「误用与意外的阻尼」，
      并在 `ClientIpResolver` javadoc、债务台账里写明；**不得在任何文档里宣传成攻击防护**。
- [x] **不写库、不动共享开发库**：本项无迁移、无 schema 变更；冒烟只做只读（见 §验证）。
- [x] 无密码/密钥/日志泄漏涉及；`log.warn` 只打**键名与失败原因**，**不打标识符之外的用户数据**
      （键里含 `userId`/`keyId`/IP，属**可观测性所需的最小标识**，与 `AuthService`/`MfaStateStore` 既有做法一致）。

### 四、测试优先与质量门禁（不可协商）
- [x] **新增 14 组用例**（T1–T14），其中 **T1–T4 / T12–T14 是 HTTP 层行为层证据**（本项唯一的真回归证据）。
- [x] ⚠️ **章程说「测试先于实现」（红→绿）**：**如实说明边界**——本项实际顺序仍是**先实现、后补用例**
      （沿用 087/088/092/095–099 的既有做法），**不得**据此声称走过 spec-first；
      定向破坏留痕证明的是**护栏有牙齿**，**不是**「红先出现」。
- [x] 测试金字塔：**新增 1 个 IT 类**（`RateLimitIT`）+ **4 个纯单测类**（不启 Spring 的 3 个 + 1 个装替身的）；
      **不新增 e2e**（本项零前端改动）。
- [x] **覆盖率**：新组件落 `com/crm/security/**`（**在 jacoco 分母里**）⇒ T1–T13 必须真覆盖到各分支，不许挤压既有 `0.73` 余量。

### 五、简洁、可维护与可观测（不可协商）
- [x] **YAGNI**：不引入任何新依赖（**不加** Bucket4j/Resilience4j；**不加** `@ConfigurationProperties`，全仓 0 个）；
      不做滑动窗口/令牌桶；**不提供 `fail-open` 开关**（等于给自己一个自伤旋钮）。
- [x] **可观测**：每条 fail-open 都 `log.warn`；拒绝路径给 `Retry-After` 头；`rl:` 键族可被运维按前缀监控。
- [x] **命名与结构传达意图**：键族 `rl:<scope>:<identity>` 自解释；`scope` 是**显式常量**而非从 URI 推导
      （URI 改名会让配额悄悄换桶且**没有任何测试会红**）。
- [x] **代码首先为人类读者而写**：`RateLimiter` 与 `ClientIpResolver` 的 javadoc **必须**写明三处**反直觉**的决定：
      ① 为什么「读时补窗」是**放行**而不是像 `MfaStateStore` 那样继续锁；② 为什么 fail-open；③ XFF 的前提与**不是抗敌手**。

**Gate 结论**：五项原则**无违规、无需 `Complexity Tracking`**。
唯一需要显式披露的是「一、」里的**一处对外 code 字符串变更**与「三、」里的**口径降级**，两者均已按仓规登记。

---

## 已核实事实（机制级，均已实测/读实现而得）

> 逐条对应 `spec.md` §1.3/§1.4 与 `research.md` 的决策；此处只列**决定形状**的那些。

**① 本仓「按端点声明横切策略」的既有范式是注解 + AOP，不是过滤器**
`security/PermissionAspect` 的 `@Before("@annotation(requirePermission)")` + `security/RequirePermission`
（由 `config/SecurityConfig` 的 `@EnableMethodSecurity` 打开）；`RequirePermission` 的 javadoc 明写
「方向是让本注解成为**唯一闸门**」。`spring-boot-starter-aop` 已在 `pom.xml`。
全仓 `@Order` / `FilterRegistrationBean` / `HandlerInterceptor` / `WebMvcConfigurer` **0 命中**。

**② 裸 `@Component` 过滤器拿不到登录用户；而链内过滤器的顺序契约没有护栏**
`config/LoggingFilter` 是裸 `@Component`、注册在 Security 链**之外**，`SecurityContextHolder` 为空。
要靠过滤器按登录用户分桶，**必须**插进链内且排在鉴权**之后**——而 `config/SecurityConfig` 的既有形状是
`addFilterBefore(..., UsernamePasswordAuthenticationFilter.class)`。照抄这个形状再加一个会落在两个鉴权过滤器**之前**
⇒ `currentUserId()` 恒 `null` ⇒ 全体已认证用户**共用一个匿名桶** ⇒ 限流被静默降级成无效，
而 **78 个 IT 照样全绿**（它们全用 admin 令牌，且 admin 在切面直通）。**这条契约没有任何测试能发现写错。**

**③ 全部导出都是非流式**（`SXSSF` / `EasyExcel` **0 命中**，全 `XSSFWorkbook` + `ByteArrayOutputStream`；
`ExportController#download` 是 `Files.readAllBytes` 先物化进堆）
⇒ **不存在「响应已提交、改不了状态码」的难题** ⇒ `@Before` 在方法体之前抛异常对 13 个导出面**全都有效**。
这是「不必上过滤器」的决定性证据 —— 过滤器唯一的结构性优势在本批**不存在**。

**④ 身份：API Key 主体的 `userId` 是密钥创建者**
`security/ApiKeyAuthFilter` 注入 `new JwtAuthFilter.CrmPrincipal(key.getCreatedBy(), "open-api", "OPEN_API", true)`；
密钥 id 另在 `authentication.getDetails()` 的 `ApiKeyPrincipal(keyId, name)` 里。
⇒ 按 `currentUserId()` 分桶会让**同一管理员建的多个密钥共用一个桶**（一个打满、其余全被拒，**症状是静默的**）。
`security/SecurityUtil` 已有 `isMachineSubject()`（为行级数据权限而加）⇒ 复用，不新增判定。
匿名请求 `currentUserId()` 为 `null`。

**⑤ 客户端 IP 有权威实现但被复制了 3 份，且三份在退化输入上不一致**
权威实现是 `AuthService.resolveClientIp(request, fallback)`（`public static`）；
另有两份私有副本：`EmailTrackController#clientIp` 与 `FormService#clientIp`（**逐字相同**）。
⚠️ `EmailTrackController` / `FormService` 那两份只判整体非空 ⇒ XFF 为 `","` 时 `split(",")[0].trim()` 返回**空串**
⇒ **所有这类请求共用一个键为 `""` 的桶**；`AuthService` 那份多一层「首段非空」判定并回退 fallback。
`nginx.conf` 确实注入 XFF；全仓无 `server.forward-headers-strategy`。

> ⚠️ **2026-09-16 实测订正（上面这条对 `","` 的描述被推翻，原文逐字保留）**：实测
> `",".split(",")` **抛 `ArrayIndexOutOfBoundsException: Index 0 out of bounds for length 0`**
> （Java 的 `split` 丢弃末尾空段 ⇒ 长度 0 的数组），**不是**返回空串 ⇒ 三份副本在公开端点上
> **各有一条潜伏的 500**（请求头由调用方任意构造），严重性高于「共用空串桶」。产生**空串桶**的
> 是 `", 1.2.3.4"` 这类输入（整串非空、首段空白）。**实做**改用 `indexOf(',')` + `substring`，
> 三种退化输入**一律回退 fallback**；「取更严那份」的决定与「统一为 fallback」的判据都不变，
> 理由从「另两份更松」升级为「**另两份会抛异常**」。逐条见 `tasks.md` §实做订正 第 9 条、
> `research.md` §9 与 §12 第 ④ 行的 ⚠️ 块。

**⑥ Redis 计数的两个既有范式，体例相反 —— 新件照 `MfaStateStore`**
`MfaStateStore.recordFailure` 是「**恰好等于阈值**那次才 `expire`」；`AuthService.recordFailure` 是
`increment` 后**无条件** `expire`（每次失败续整窗）。后者对**登录失败计数**是**有意**的
（`MfaStateStore` 的 javadoc 明说「对登录限流那是有意的」），但对**请求限流是错的**：
一个稳定 4 次/分钟的客户端会让「3 次/60 秒」事实上变成「每 60 秒只准错一次，错一次就再也不许过」。
详见 `research.md` §4 的两范式对照表。

**⑦ fail-open 是本仓对限流的明文立场**
`MfaStateStore` 的类 javadoc（`:19-25`）是全仓对 fail-open/fail-close 的论述：它说自己的 fail-close 是
**为 2FA 语义**（`catch { return null; }` = **跳过二次验证**）而立的，并指出 fail-open「**对限流是合理的**
——代价只是限流暂时失效，而不放行会让整个系统在 Redis 抖动时不可用」。
⚠️ 那段论述**不可移植**到 MFA，本项在 javadoc 里引它并说明这一点。

**⑧ `ErrorCode` 里没有通用限流码；`MFA_LOCKED` 不可动**
`:158` 的注释自称 `MFA_LOCKED(429, ...)` 是「全仓首个 429」；
`GlobalExceptionHandler` 用的是**裸字符串** `"TOO_MANY_REQUESTS"`（硬编码在处理器里、不在枚举中）。
⚠️ `AuthMfaIT` **逐字断言** `error.code == "MFA_LOCKED"` ⇒ 必须原样保留、不可被新码合并。

**⑨ `FormService` 违反自己的冻结契约**
`specs/036-online-forms/contracts/online-forms.md:44` 与 `tasks.md:50` 都承诺 **429**，实现抛 `ErrorCode.BAD_REQUEST` ⇒ **400**。
同类缺陷的处置范式已由 085 确立（`GlobalExceptionHandler` 里那段注释即判例：**契约是对的，改的是实现，契约一个字符不改**）。

**⑩ 配置范式：全仓没有任何 `@ConfigurationProperties`（0 命中）**
⇒ 用 `@Value("${crm.rate-limit.*:default}")`，业务配置挂 `crm.*`，
并照 `crm.outbound.allowed-hosts`（`application.yml`）那种「解释为什么是这个默认值」的注释体例。
⚠️ 有「代码默认 `true`、yml 里 `false`、yml 胜出」的前例（captcha）⇒ 默认值只留**一处真源**。

**⑪ jacoco 排除项含 `com/crm/common/**`**
⇒ `com/crm/security/**` **在覆盖率分母里**（新增类要付覆盖成本），`com/crm/common/**` 不在。
唯一规则：**INSTRUCTION COVEREDRATIO ≥ 0.73、粒度 BUNDLE**。

**⑫ 测试环境的 Redis 是假的，且默认那个假货不存任何东西**
`AbstractIntegrationTest` 的 `@MockBean RedisTemplate` + 裸 `mock(ValueOperations.class)`
⇒ `get` 恒 `null`、`increment` 返回 `null` ⇒ **fail-open ⇒ 限流是 no-op**。
功能型替身 `support/InMemoryRedisTestSupport` 由 IT 自己 `redis.install(redisTemplate, clock)` 安装；
该替身的类 javadoc 明文警告「主代码新增任何 Redis 调用都必须同步补实现，否则默认答案 `null/false`
**看起来像正常结果**」，并**刻意不实现**管道/事务/Lua。

**⑬ 一颗已实测的雷：`FormService.rateBuckets` 是跨用例共享状态**
它是单例 bean 的**实例字段**，而 `AbstractIntegrationTest.clearInProcessCaches()` **只清 `CacheManager`**、不清它。
实测 `FormIT` 2 次 + `LandingPageIT` 1 次提交，全部来自 MockMvc 默认 `remoteAddr=127.0.0.1`，
而 `RATE_LIMIT = 3` ⇒ **恰好 3/3**，第 4 次就 400。
⇒ 它今天**不是护栏而是跨用例共享状态**，且对限流是**零用例**的
（`rateBuckets` / `RATE_LIMIT` / `提交过于频繁` 在 `src/test` **0 命中**）。

**⑭ 门禁的相位实序**
`test → integration-test → post-integration-test → verify`；同相位内 **`spotless:check` 比用例失败更早中止**；
`jacoco:check` 在 `failsafe:verify` **之后**。⚠️ **4 例 IT 失败属已批准偏差**
（`IntegrationHubIT.integrationFlow`、`OpportunityIT.closeWithoutResultReturns422`、
`UserIT.disableUserRevokesAccess`、`UserIT.userLifecycle`）⇒ 判据是「**失败集合 ⊆ 这 4 例 + 本批新增用例全绿**」，
**不是 exit 0**。⚠️ **不传 `-DargLine`**（会静默废掉 jacoco）。

**⑮ 骨架已具备，无需新建**
`support/RequirePermissionScanTestSupport` 的 `MetadataReader` 字节码扫描（`usages()` 返回「码 → 类名#方法名」）
正是台账测试要复用的手法；`support/FixedClockTestSupport` + `support/MutableClock` 提供冻结/推进时间；
`support/InMemoryRedisTestSupport` 提供 `increment` / `expire` / `getExpire` / `snapshot` / `failOnKeyPrefix`。

---

## 结构决策

### 机制：`@RateLimit` 注解 + `@Aspect` 切面 + 一个共享限流件（**不上过滤器**）

五条理由（详证见 `research.md` §2）：① 本仓唯一的逐端点声明策略范式就是注解 + AOP，新增第二种机制会制造一个
**没有判据**的问题；② **配额是逐端点的数字**（导出 10/60s vs 表单 3/60s 差 20 倍），而过滤器的自然形态是
「URI 前缀 → 配额」的集中表，**前缀在这里根本不成立**（`/public/**` 内风险差一个量级）；
③ 注解在 `DispatcherServlet` 之内抛异常 ⇒ 走 `GlobalExceptionHandler` 拿到**统一 `ApiResponse` 信封**，
而过滤器要么手写 JSON、要么走空体 401 那一套，会让全仓唯一的 429 与其他错误**不同形**；
④ 身份维度是过滤器方案的硬伤（事实 ②）；⑤ 过滤器唯一的结构性优势在本批不存在（事实 ③）。

**注解的弱点必须补**：注解是 **opt-in** ⇒「忘了标注 = 无限流，**且没有用例会红**」。
补法是本仓已成熟的**字节码扫描台账测试**（事实 ⑮）⇒ 用台账换 default-deny，见 T10/T11。

**⚠️ 与对比文档 P0 第 5 条的不一致（不静默改）**：该行原文**逐字保留 + 带日期 ⚠️**，
写明**采纳其意图**（单一实现 + 全端点覆盖 + 台账护栏）、**不采纳其字面机制**（Security 链过滤器），
理由取事实 ①/②/③；**判定列与分值按仓规照旧不动**。

### 组件设计（新件统一放 `com.crm.security`，与 `PermissionAspect`/`RequirePermission`/`SecurityUtil` 同包）

| 新件 | 职责 | 关键约束 |
|---|---|---|
| `@RateLimit(scope, limit, windowSeconds, by)` | 逐端点声明 | `scope` 是**显式字符串**而非从 URI 推导（URI 改名会悄悄换桶，**没有测试会红**）；`@Target(METHOD)` + `@Retention(RUNTIME)`，形状照 `@RequirePermission` |
| `enum RateLimitDimension { AUTO, IP, USER, API_KEY }` | 维度 | `AUTO` = 机器主体→`keyId` → 已认证→`userId` → 否则→IP。**把「开放 API 必须按 keyId」变成默认行为**，靠人写对的属性 |
| `RateLimitKeys` | 键构造 | 具名静态方法，**供调用形状断言逐字核对键名**（照 `MfaStateStore` 的 `failKey` 那组先例）；键族 `rl:<scope>:<identity>` |
| `RateLimitIdentity` | 解析身份 | 纯静态函数，**可脱 Spring 单测**（手工塞 `SecurityContextHolder`）；显式 `USER`/`API_KEY` 而主体缺失时**回退 IP 并 `log.warn`**（候选取舍见 `research.md` §8） |
| `ClientIpResolver` | 解析客户端 IP | 语义**逐字取** `AuthService.resolveClientIp` 那一份（**更严**的那份） |
| `RateLimitStore` | Redis 计数协议：`increment` + **仅首次 `expire`** + **读时补窗** + fail-open | 依赖**只有** `RedisTemplate`（**不需要 `Clock`** —— 窗口是 Redis 自己的 TTL）；**全仓唯一**碰计数原语的地方 |
| `RateLimiter` | 组合：身份 → 键 → 交给 store → 超限抛异常 | 依赖 `RateLimitStore` + `ClientIpResolver`；**自己不碰 `SecurityContextHolder`**（那在 `RateLimitIdentity` 里） |
| `RateLimitAspect` | `@Before("@annotation(rateLimit)")` | `@Order` **显式声明**，排在 `PermissionAspect` **之后**（权限先于限流） |
| `RateLimitExceededException`（放 `com.crm.common`） | 拒绝信号 | **`extends BusinessException`**（多重保障：万一没走到新处理器，父类的 `handleBusiness` 仍给正确 429，不会掉进 `Exception` catch-all 变 500） |

⚠️ **两处实做调整（立项期写的是 7 个新件，实做 8 个；均在 C2 内）**：
① **`RateLimiter` 拆成 `RateLimitStore` + `RateLimiter`**（上表已按实做写）——把「Redis 计数协议」
与「身份→键→拒绝」分成两层，是为了让本批两个关键测试各对着**一个**类：`RateLimitStoreTest` 钉住
**补窗协议**（T5），`RateLimiterShapeTest` 钉住**调用形状**（T6，`increment` 而非 `get`+`set`）。
混在一个类里时，两个测试类名与实际结构对不上，而「对着什么测」正是这两条用例唯一的可读线索。
② **`SecurityUtil.currentApiKeyId()` 由 C5 提前到 C2**：`RateLimitIdentity` 在 C2 就要用它
（机器主体必须按 `keyId` 分桶，见事实 ④）。它是**纯新增成员、零行为变更** ⇒ C2 的「纯新增」
性质不变；其余 P0/P1 标注仍在 C5/C6。

**`@Order` 必须先定**：`PermissionAspect` 今天**没有 `@Order`**。本项起仓里就有了第二个切面，
两者同用默认序 = 并列，谁先谁后取决于排序实现 ⇒ **在第一次提交里就给定**（权限先于限流，
使**未授权者不消耗配额**）。T13 钉住这个后果；⚠️ 它**不是**「顺序本身」的护栏（见 §风险）。

### 存储与时间语义

- **Redis 固定窗口**：`opsForValue().increment(key)`；**仅 `n == 1` 时 `expire(key, window)`**（事实 ⑥ 的正确范式）。
- **「读时补窗并放行」必须写**：`INCR` 与 `EXPIRE` 是两次调用 ⇒ 可能留下「计数 ≥ 阈值但无 TTL」的键
  ⇒ 该主体**永久 429**（**重启无效**，键在 Redis 里），症状是「这个人怎么都不行」。
  处置 = **补回整窗并放行** —— **与 `MfaStateStore`（补窗并继续锁）刻意相反**，
  因为限流锁的是「一个窗口的请求配额」而 MFA 锁的是「一个人的一个流程」；代码注释**必须**显式写出这个对比。
- **不用 Lua**：替身刻意不实现管道/事务/Lua，而裸 mock 下 `execute(script)` 返回 `null`
  ⇒ 又开一条**静默 no-op 的假绿通道**；本项的原子性需求远比 MFA 的 `GETDEL`/`SETNX` 弱（最坏是多放一次）。
- **fail-open**（事实 ⑦），且**只 catch `RedisConnectionFailureException | DataAccessException`**、
  **不 catch 裸 `Exception`**（那会把「键构造写错」这类**真 bug** 也降级成静默放行）；每条 `log.warn`；
  **不提供 `fail-open=false` 旋钮**。

  > ⚠️ **2026-09-16 实做订正（上面的多 catch 写法是编译错误，原文逐字保留）**：
  > `RedisConnectionFailureException` **是** `DataAccessException` 的**子类** ⇒ javac 判「已由备选
  > `DataAccessException` 捕获」，多 catch 形式**过不了编译**。实做为**单个 `DataAccessException` catch**。
  > **判据覆盖面不变**（子类照旧被捕获；「不 catch 裸 `Exception`」逐字成立），
  > `RateLimitStoreTest.everyStoreFailureIsFailOpen` 注入的正是 `RedisConnectionFailureException`
  > ⇒ 「Redis 挂 ⇒ 放行」仍被钉住。见 `tasks.md` §实做订正 第 6 条。

### 配置两层

- **逐端点数字写在注解上**（与端点同址，照 `@RequirePermission` 的体例）—— **不集中配置**，
  因为「配额」是端点属性而不是部署属性。
- 全局只有**两个** `@Value` 键（事实 ⑩）：
  - `crm.rate-limit.enabled`（默认 `true`）：唯一的总开关，供联调/压测临时关闭。
  - `crm.rate-limit.trust-forwarded-for`（默认 `true` = 与今天三处 `clientIp` 行为**逐字相同**）：
    把 XFF 信任**显式化**，并附注释说明「只在反向代理是唯一入口时成立；直连可伪造（**已知并登记**）」。

### 错误码与异常

- 新增 `ErrorCode.RATE_LIMITED(429, "RATE_LIMITED", "请求过于频繁，请稍后再试")`，紧邻 `MFA_LOCKED` 放置。
  **不合并 `MFA_LOCKED`**（事实 ⑧：断言会红，且两者窗口语义**相反** —— 一个是「账号被锁」，一个是「等一下」）。

  > ⚠️ **2026-09-16 实做订正（「紧邻 `MFA_LOCKED`」未逐字照做，原文逐字保留）**：新码实做放在
  > **`MFA_STORE_UNAVAILABLE` 之后**（即 082 的整个 MFA 组**之后**），因为插进组中间会把该 MFA 块
  > **劈成两半**、注释块与枚举项对不上。**判据逐字成立**：**不合并 `MFA_LOCKED`**、两码并存
  > （① `AuthMfaIT` 逐字断言 `error.code == "MFA_LOCKED"`；② 两者窗口语义相反）。
  > `:158` 那句「首个 429」的订正 ⚠️ 里已点明新码在「**下方 MFA 组之后的 429 段**」。
  > 见 `tasks.md` §实做订正 第 7 条。
- `ErrorCode` 里那句「全仓首个 429（本项引入；此前本枚举里没有任何限流码）」**描述的仍是 082 当时的事实**
  ⇒ 按「订正不静默」**原文逐字保留 + 追加带日期 ⚠️**（说明此后新增了通用限流码、「首个」照旧指 082）。
- `GlobalExceptionHandler`：**删掉** `EmailTrackController.RateLimitedException` 的处理器
  （连带消失的是 `exception → controller` 的 **FQN 反向依赖**），换成 `RateLimitExceededException` → 429 + `Retry-After`。
- `EmailTrackController.RateLimitedException`（控制器私有内部类）**删除、不留兼容壳**（它已无 throw 点），
  且**必须与处理器删除同一次提交**（否则中间态编译不过）。
- **`Retry-After` 要给**：判据是本仓先例 —— `AuthMfaIT` 断言 429 的 message 里要有**剩余锁定秒数**（契约明确要求），
  头是它的机器可读版本；**只在拒绝路径读一次 TTL**，不额外增加正常路径的 Redis 往返。

### 收敛 2 处重复 + 3 份 IP 工具收敛成 1 份

**实现形态：保留调用点与方法签名，只换方法体为委托。**
- `EmailTrackController#checkRateLimit(HttpServletRequest)`：**60/60s 逐字不变**；
  删 `rateBuckets` / 两个常量 / `clientIp` / `RateLimitedException`；订正 `:72` 那句**与实现相反**的注释
  （注释说「降级为 400」，实际是 429）。
- `FormService#checkRateLimit(String ip)`：**3/60s 逐字不变**；保留 `request == null → "unknown"` 字面量
  （**逐字保留**）；删 `rateBuckets` / `cleanupRateBuckets` / `clientIp`。

**为什么不改成「在 Controller 上标注」**：`FormService` 的 `ip` **必须先解析一次**
（`submission.setClientIp(ip)` 要写进库），若改在 `FormController` 上标注，IP 会被解析**两次**、
多一处分叉机会；且最小 diff 让「阈值逐字不变」**可核**。
**代价（必须登记）**：这 3 个端点（`EmailTrackController` 两个 + `FormController#submit`）**不带 `@RateLimit`**
⇒ 必须进台账白名单并**带非空理由**。

**两个 scope 分开**（`public-form-submit` / `public-email-track`）：合并会让「邮件客户端加载像素」的
自然高频挤掉表单提交的配额。**这不算行为变更** —— 今天本来就是两个独立内存桶。

**`ClientIpResolver` 放 `com.crm.security`**（**不放 `config`**）：`service → security` 是本仓**既有**方向；
放 `config` 会让 `service → config` 成为一条**新**方向，而 `config` 是装配层不是工具层。
`AuthService.resolveClientIp` **保留签名**、方法体改成 1 行委托 ⇒ `AuthController` 与 `AuthServiceTest`
**零改动继续绿**；另两份私有副本删除。统一取**更严**的语义（事实 ⑤ 那个 `","` 退化输入）。

### 前端：**零改动**

`apiClient.ts` 的 `extractErrorMessage` 只读 `error.message` ⇒ 429 的新 message 会被正常展示；
`ErrorCode` 里的中文消息与本仓既有体例一致（`MFA_LOCKED` 就是中文）。
给导出页做专门文案不在任何冻结契约里 ⇒ 本批不加，**因此也不为它新增 i18n 键**
（避免键数 2962→2963 连带一串数字落点）。

### 补哪些空档（排序依据：匿名可达 + 写副作用/可枚举 + 单次成本高，三者齐备者最优先）

**P0（本批必做）**

| 端点（按符号锚定） | 维度 | scope / 阈值 | 理由 |
|---|---|---|---|
| `CustomerPortalController#ticketStatus` | IP | `public-ticket-status` **10/60s** | **全仓唯一的匿名凭证校验端点**（工单号 + 手机/邮箱双验证）⇒ 枚举/爆破面 |
| `CustomerPortalController#submitTicket` | IP | `public-ticket-submit` **5/60s** | 匿名建单，**有写副作用** |
| `EmailUnsubscribeController#unsubscribe` | IP | `public-unsubscribe` **10/60s** | 匿名写 + 以邮箱为入参（可批量退订 / 探测邮箱存在性） |
| **导出 generate 类 8 个** | **USER** | `export-generate` **10/60s** | 导出是「谁在导」不是「从哪导」，且都要登录（IP 维度在移动网络/多分支办公会**误伤**） |
| **导出 download 类 5 个** | **USER** | `export-download` **30/60s** | 见下「判定口径」 |
| `OpenPlatformController` 两个 GET | **API_KEY** | `open-api-read` **60/60s** | 机器客户端、此前零限制。为此给 `SecurityUtil` 增 `currentApiKeyId()` |
| `OpenPlatformController#openCreateLead` | **API_KEY** | `open-api-write` **30/60s** | 同上 + 有写副作用 |

**⚠️ 导出面的判定口径（本项最容易自相矛盾的一处）**：13 = **8 个 generate + 5 个 download**，
判定依据是「**响应类型 + 是否走 Excel/PDF 引擎**」，**不是 URI 前缀、也不是方法名里有没有 `export`**。
- **generate（8）**：`CustomerController#exportExcel`、`LeadController#exportExcel`、`QuoteController#exportPdf`、
  `ReportController#exportReport`、`ComplianceExportController#export`、`ExportController#create`、
  `ScheduledExportController#create`、`ScheduledExportController#executeNow`。
- **download（5）**：`CustomerController#importTemplate`、`LeadController#importTemplate`、
  `ContactController#importTemplate`、`ExportController#download`、`ContractAttachmentController#download`。
- **不纳入**（逐条列出以免自相矛盾）：`ExportController#page`、`ScheduledExportController` 的 5 个读/状态端点、
  `ReportController#query`、`CustomerPortalController#articles` / `#article`（属 P1 的 `public-read`）。
  ⚠️ 注意 `LeadController#importTemplate` 与 `ContactController#importTemplate` **今天没有 `@RequirePermission`**
  ⇒ 它们仍会被 `AUTO` 归到 `user:<id>`（登录用户），**不因缺权限码而旁路**。

**P1（同批做，边际成本近零、形态完全一样）**
- **匿名只读四个**共用 `scope=public-read`，**60/60s**：`FormController#meta`、`LandingPageController#publicView`、
  `CustomerPortalController#articles`、`CustomerPortalController#article`。
  非 P0（无写副作用、非凭证端点），但「匿名 + 打 DB」是**唯一**能被单机打穿的方向，
  且落地页/表单 meta 是任何爬虫与预取器都会打的路径。
- **三个导入端点** `scope=import-excel`，**USER**，**5/60s**：`CustomerController#importExcel`、
  `LeadController#importExcel`、`ContactController#importContacts`。
  multipart + Excel 解析，成本与导出同级**还带写**；**漏掉它会形成明显的不对称**（能导出受限、能导入不受限）。

**故意不纳入（逐条有理由，进台账白名单）**
`/actuator/health*`（**必须不限流** —— 存活/就绪探针被限流会让编排系统**判死并重启**，是把护栏变成故障源）、
`/error`、`OPTIONS /**`（CORS 预检，限流会让浏览器端**整体不可用**且症状极难归因）、swagger 三路径（联调噪音）、
**登录的 2 层锁定**（用户裁决；且它们是**失败计数**不是请求速率，无条件续窗是**有意**的）、
**2FA 两处**（`MfaStateStore`，全仓唯一 fail-close 边界，语义**不可移植**）、
`/ws/**`（握手限流会误伤长连接重连，滥用面在消息层）、
**已认证的常规读接口**（业务主干，滥用已被数据权限与分页约束）——
⚠️ 这一条与 P1 的「匿名只读四个」**不矛盾**：区别是**匿名**（无身份可归责、无配额可谈）vs **已认证**（有主体、有审计）。

### 一个数字住在好几个地方（落点清单）

| 数字 | 落点 | 本批动作 |
|---|---|---|
| Spec 模块数 | `specs/README.md` 编号说明（**C1**）、`specs/roadmap.md` 覆盖度行（**C1**）、`README.md:163`（**C6**）、`PROJECT_FEATURES.md` Spec 模块行（**C6**） | `98 / 001~099` → **`99 / 001~100`**（**旧值逐字保留**）；判据 `ls -d specs/[0-9]* \| wc -l` = **99**。⚠️ **C1 只动前两处**（它们说的是「本批立项了」）；后两处是**对外规模数字**，与后端规模行同批在 **C6** 改齐（见 §分步与提交「偏离二」） |
| `roadmap.md` 勾选数 | `## 当前进度` 列表 | **98 勾 / 0 未勾** → **98 勾 / 1 未勾**（**方框留空，绝不预勾**） |
| 后端 java 文件数、测试数等 | `PROJECT_FEATURES.md` 后端规模行 | **交付时实跑取值**，不推算 |
| jacoco 覆盖率四项 | `tasks.md` 交付块、`specs/README.md` 100 行 | **交付时实跑取值** |

⚠️ 订正一律**原文逐字保留 + 带日期 ⚠️ 块**，粒度到**每一列**；自查判据是「**旧值仍能被 grep 到**」。

---

## Project Structure

### Documentation (this feature)

```text
specs/100-rate-limit-consolidation/
├── spec.md                    # 用户故事与验收（SC-100-001–010）
├── plan.md                    # 本文件
├── research.md                # 15 条 Decision/Rationale/Alternatives
├── quickstart.md              # 门禁命令 + 只读冒烟配方 + 隔离实例配方
├── falsification-evidence.md  # 定向破坏的逐字留痕（含还原判据）—— 交付时产出
├── tasks.md                   # 任务分解（按 US 分组，**勾选框留空**）
└── checklists/requirements.md # 规格质量自查
```

**不产出** `data-model.md` / `contracts/`：本项**无实体、无字段、无端点、无迁移**
（与 092/095–099 的先例一致；为凑齐工件而生成空壳正是「为了流程而流程」）。

### Source Code

```text
backend/src/main/java/com/crm/security/           # 【新】7 个文件
├── RateLimit.java                                # @interface（scope/limit/windowSeconds/by）
├── RateLimitDimension.java                       # enum AUTO/IP/USER/API_KEY
├── RateLimitKeys.java                            # 键构造（具名静态方法，供调用形状断言核对）
├── RateLimitIdentity.java                        # 身份解析（纯静态，可脱 Spring 单测）
├── ClientIpResolver.java                         # 3 份 clientIp 收敛于此
├── RateLimiter.java                              # 判定 + 读时补窗 + fail-open
└── RateLimitAspect.java                          # @Before("@annotation(rateLimit)") + @Order

backend/src/main/java/com/crm/common/
├── ErrorCode.java                                # 改：加 RATE_LIMITED；「首个 429」注释原文保留 + ⚠️
└── RateLimitExceededException.java               # 【新】extends BusinessException

backend/src/main/java/com/crm/
├── exception/GlobalExceptionHandler.java         # 改：删旧处理器，加 RateLimitExceededException → 429 + Retry-After
├── security/SecurityUtil.java                    # 改：加 currentApiKeyId()（从 authentication.getDetails() 取）
├── security/PermissionAspect.java                # 改：加 @Order（本项起仓里有第二个切面）
├── controller/EmailTrackController.java          # 改：委托共享件；删桶/常量/clientIp/RateLimitedException；订正 :72 注释
├── service/FormService.java                      # 改：委托共享件；删桶/cleanup/clientIp；400 → 429 + javadoc 引 036 契约
├── service/AuthService.java                      # 改：resolveClientIp 方法体改 1 行委托（签名不变）
├── controller/CustomerPortalController.java      # 改：标注（P0×2 + P1×2）
├── controller/EmailUnsubscribeController.java    # 改：标注（P0）
├── controller/OpenPlatformController.java        # 改：标注（P0×3，**by = API_KEY**）
├── controller/CustomerController.java            # 改：标注（exportExcel / importTemplate / importExcel）
├── controller/LeadController.java                # 改：标注（exportExcel / importTemplate / importExcel）
├── controller/ContactController.java             # 改：标注（importTemplate / importContacts）
├── controller/QuoteController.java               # 改：标注（exportPdf）
├── controller/ReportController.java              # 改：标注（exportReport）
├── controller/ComplianceExportController.java    # 改：标注（export）
├── controller/ExportController.java              # 改：标注（create / download）
├── controller/ScheduledExportController.java     # 改：标注（create / executeNow）
├── controller/ContractAttachmentController.java  # 改：标注（download）
├── controller/FormController.java                # 改：标注 #meta（P1）；#submit 走服务内委托**不标注**
├── controller/LandingPageController.java         # 改：标注（P1）
└── config/SecurityConfig.java                    # 改：仅注释（说明本项为何不走过滤器）

backend/src/main/resources/application.yml        # 改：crm.rate-limit.enabled / trust-forwarded-for（含「为什么是这个默认值」注释）

backend/src/test/java/com/crm/
├── integration/RateLimitIT.java                  # 【新】T1–T4、T12–T14
├── security/RateLimitStoreTest.java              # 【新】T5（读时补窗）
├── security/RateLimiterShapeTest.java            # 【新】T6（调用形状，裸 mock）
├── security/RateLimitIdentityTest.java           # 【新】T7、T8
├── security/ClientIpResolverTest.java            # 【新】T9
└── security/RateLimitCoverageTest.java           # 【新】T10、T11（字节码台账 + 自检）

CRM_FEATURE_COMPARISON.md                         # 改：两处订正（原文逐字保留 + 带日期 ⚠️，判定列与分值照旧）
specs/README.md · README.md · specs/roadmap.md · PROJECT_FEATURES.md   # 立项登记与交付态
```

⚠️ **`specs/036-online-forms/` 与 `specs/055-open-platform/` 的工件一字不动**（理由见 `research.md` §14）。
⚠️ **`specs/088/090/098/099` 的历史实测读数一字不动。**
⚠️ **`CRM_FEATURE_COMPARISON.md` 只改那两行的文字，判定列与分值照旧。**

---

## 分步与提交

> ⚠️ **相对已批准计划的两处偏离（已在立项提交时向用户说明）**：
>
> **偏离一：5 次 → 6 次提交。** 计划里是 **5 次**，但仓库惯例是**立项独自一次**
> （099 有 `5e9ffbb docs(099): 立项` 为先例）。故拆成 **6 次** —— 多出的这一次是纯文档、**零代码**，
> 且让 `docs` 与 `feat` 的边界与仓规一致。其余 5 次的内容与顺序**与已批准计划逐字相同**，只是编号后移。
>
> **偏离二：`README.md:163` 与 `PROJECT_FEATURES.md` 的 Spec 模块数**由 **C1 前移到 C6**。
> 计划把 `README.md:163` 的 `98 → 99` 放在 C1。**照 099 的先例改到交付提交**，理由是这两处与
> `PROJECT_FEATURES.md` 的后端规模行是**同一类对外规模数字**，而后者必须**实跑取值**（新增类会动 java 文件数）
> ⇒ 「一个数字住在好几个地方，要一起改」：只改 `README.md:163` 而留 `PROJECT_FEATURES.md` 到交付，
> 等于用一次订正**造出两处新矛盾**。C1 只登记「**本批自己的状态**」（`specs/README.md` 模块表 + 编号说明 + `roadmap.md` 三处），
> 这三处说的是「**立项了**」，与「模块总数」不是同一个数字。

| # | 提交 | 内容 |
|---|---|---|
| C1 | `docs(100): 立项` | 本目录 7 件工件（`falsification-evidence.md` 除外）+ `specs/README.md` 模块表 100 行（状态「⏳ 进行中」）+ 编号说明 + `roadmap.md` 的 100 行（**勾选框留空**）与两条聚合数。**零 Java 改动。⚠️ `README.md:163` 与 `PROJECT_FEATURES.md` 的模块数不在本次**（见上「偏离二」）。 |
| C2 | `feat(100): 共享限流件与通用 429 错误码` | **纯新增、零行为变更**：**8 个**新类（`RateLimit`/`RateLimitDimension`/`RateLimitKeys`/`RateLimitIdentity`/`ClientIpResolver`/`RateLimitStore`/`RateLimiter`/`RateLimitAspect`）+ `RateLimitExceededException` + `ErrorCode.RATE_LIMITED` + `GlobalExceptionHandler` **新**处理器 + `application.yml` 配置段 + **`PermissionAspect` 的 `@Order`** + `SecurityUtil.currentApiKeyId()`（**纯新增成员**）+ 单测（T5–T9）。**不删旧处理器、不动任何 Controller/Service、不改 `SecurityUtil` 的既有成员。** |
| C3 | `refactor(100): 邮件追踪改用共享限流件` | 收敛 `EmailTrackController`（60/60s 逐字不变）+ 删私有 `RateLimitedException` **与**其处理器（**同一次提交**，否则编译不过）+ `RateLimitIT`（T1–T4、T12–T14）。**本次单独暴露对外 code 变更**（`TOO_MANY_REQUESTS` → `RATE_LIMITED`），便于日后二分。 |
| C4 | `refactor(100): 表单提交改用共享限流件并订正 429 契约` | 收敛 `FormService`（3/60s 逐字不变）+ 400→429 + javadoc 引 036 契约行号 + 3 份 `clientIp` 收敛成 `ClientIpResolver`（含 `AuthService` 的 1 行委托）。 |
| C5 | `feat(100): 零限流路径接入与覆盖台账` | P0 清单 5 组标注 + **`RateLimitCoverageTest` + 豁免白名单**（⚠️ **必须同批**，否则 C5 引入的是一堆**没有护栏的标注**）。⚠️ `SecurityUtil.currentApiKeyId()` 已前移到 C2（见上「两处实做调整」②）。 |
| C6 | `docs(100): P1 接入、文档订正与数字收口` | P1 两组标注；`CRM_FEATURE_COMPARISON.md` 两处订正；`SecurityConfig` 注释；债务台账三条；**`README.md:163` 与 `PROJECT_FEATURES.md` 的 Spec 模块数 `98 / 001~099` → `99 / 001~100`（与后端规模行同批，实跑取值）**；交付态登记与 `falsification-evidence.md` 实测输出 + `tasks.md` 勾选。 |

⚠️ **`PermissionAspect` 的 `@Order` 必须落在 C2**：C2 起仓里就有了第二个切面，两者同用默认序 = 并列，
谁先谁后取决于排序实现；不早定则 C2–C5 全程受影响。目标顺序是**权限先于限流**（未授权者不消耗配额）。
⚠️ **`RateLimitCoverageTest` 必须与 P0 标注同批**（C5）：分成两次会让中间提交留下「一堆没有护栏的标注」，
而那正是台账要防的东西。

---

## 验证

### 用例清单（本批唯一的**行为层**证据）

⚠️ **本批最大的假绿陷阱，要说准**：默认基类下 `opsForValue()` 是裸 mock ⇒ `increment` 返回 `null`
⇒ fail-open 生效 ⇒ **限流是 no-op**。而真正的假绿**不是**「for 循环断言第 N+1 次 429」
（那种用例在裸 mock 下**会红**，因为它看到的是 200），而是 `AuthServiceTest` 那种
**「把 Redis 返回值桩成 10、断言拒绝」** —— 它证明的是「**如果** Redis 说 10 我就拒」，**不证明接线**。

⇒ **每条 HTTP 层限流用例必须做满三件事，缺一即视为假绿**：
① 装功能型替身（`redis.install(redisTemplate, clock)`）；
② **正对照**：断言计数真的落到 Redis（`redis.snapshot()` 含该键）—— **没接线时该键不存在 ⇒ 红，这是核心**；
③ **负对照**：未达阈值时断言 200（防「限流恒拒」这种反向劣解）。
组件单测可用裸 mock 验**调用形状**，但**不得**用它验**行为**（形状与行为不可互相替代）。

| # | 用例 | 文件 | 形态 | 会因什么缺陷变红 |
|---|---|---|---|---|
| T1 | 超限即 429（IP） | `integration/RateLimitIT.java` | MockMvc + 替身 + 冻结时钟 | 第 4 次不是 429 / `error.code != "RATE_LIMITED"` / 缺 `Retry-After` |
| T2 | **计数真写进了存储** | 同上 | 同上 | `redis.snapshot()` 无该键 ⇒ **专杀「没接线」** |
| T3 | 窗口过期后恢复放行 | 同上 | 同上 + `advanceSeconds(61)` | 用了无条件续窗（`AuthService` 旧体例）⇒ 仍 429；或键没 TTL ⇒ 永远 429 |
| T4 | 存储故障 ⇒ fail-open 且可观测 | 同上 | `redis.failOnKeyPrefix("rl:")` | 变 500/429（fail-close）⇒ 红；同时断言请求 200 |
| T5 | **无 TTL 的僵死计数被补窗并放行** | `security/RateLimitStoreTest.java` | 单测 + 替身（造 `set(key, 99L)` 不设 TTL） | 不补窗 ⇒ 该主体**永久 429** |
| T6 | 用 `increment` 而非 `get`+`set` | `security/RateLimiterShapeTest.java` | surefire，**裸 mock** 调用形状 | 退化成 `get`+`set`（并发漏计） |
| T7 | 机器主体按 **keyId** 而非创建者 id 分桶 | `security/RateLimitIdentityTest.java` | 纯函数（手工塞 `SecurityContextHolder`） | 用 `userId` ⇒ **同管理员的多个密钥共用一个桶** |
| T8 | AUTO 判定顺序（主体优先于 IP） | 同上 | 纯函数 | 顺序写反 |
| T9 | XFF 畸形输入回退 fallback | `security/ClientIpResolverTest.java` | 纯函数（XFF = `","`） | 返回空串（今天 `EmailTrackController` 的行为） |
| T10 | **每个端点都有限流或显式豁免** | `security/RateLimitCoverageTest.java` | surefire，**字节码扫描、不启 Spring**（复用 `RequirePermissionScanTestSupport`） | 新增端点忘标且不在豁免白名单 ⇒ 红。**这是对「注解 = opt-in」弱点的唯一补救** |
| T11 | 扫描确实扫到了端点 | 同上 | 同上 | 扫描 pattern 写错时「零违规」会**假绿**（照 `RequirePermissionCatalogTest` 的自检） |
| T12 | **登录没有被重复限流** | `integration/RateLimitIT.java` | MockMvc | 连续失败登录应得 `INVALID_CREDENTIALS`(401) 而非 `RATE_LIMITED`(429) ⇒ **钉住用户裁决**，防后人顺手给 login 挂注解叠加 |
| T13 | 未授权者得 403 且不消耗配额 | 同上 | MockMvc + 无 `export:*` 的角色 | 断言全 403 且 `redis.snapshot()` **无**该键 ⇒ 钉住「权限先于限流」 |
| T14 | `Retry-After` 在窗口内 | 同上 | MockMvc | 头缺失、或值 > 窗口秒数 |

⚠️ **豁免白名单的粒度必须是「类#方法」而不是 URI 前缀**：`/public/**` 内部风险差一个量级
（且 `/public/track/**` 两条**已有限流**），用前缀会让白名单变成「一放一大片」—— 那正是台账要防的东西。

> ⚠️ **2026-09-16 实测订正上表 T9 的「会因什么缺陷变红」列**（**判定列本身就是被改写的断言**，
> 故单独点名）：该列原文写「返回空串（今天 `EmailTrackController` 的行为）」——**实测是抛
> `ArrayIndexOutOfBoundsException`**，不是返回空串。**判据本身不变**（本类必须由红转绿地钉住
> `XFF = ","` 的处理），只是那条**劣解的名字**要改对：`split(",")[0]` 在这里是**500**、不是空串桶。
> T9 实做已落到 `security/ClientIpResolverTest.degenerateHeadersFallBackToTheFallback`（5 组退化输入）。
⚠️ **白名单条目必须带非空理由**（无理由判失败），防「随手加一行让测试变绿」。
⚠️ **测试类不在 `com.crm` 包下**，不会被字节码扫描算进来。

⚠️ **不要为事实 ⑬ 那颗雷去改 `FormIT`/`LandingPageIT`**：它们**零改动**是验收的一部分；
若它们变红，要么是接线错了、要么是限流真在拦，必须**查清原因**而**不是**改断言。
换 Redis 后那颗雷在上下文层自动拆除，但会在**装了替身的类里立刻重现**
（替身是**类级安装、用例级清空**，而 MockMvc 默认 `remoteAddr` 是**上下文级常量**）
⇒ **硬规则**：**凡装了替身且会打到限流端点的用例，必须显式给一个独立的 `X-Forwarded-For`**，
不得依赖默认 `remoteAddr`。

### 门禁

```bash
cd backend && mvn -B spotless:apply   # 先修格式（spotless 比用例失败更早中止）
cd backend && mvn -B verify
ls backend/target/jacoco.exec         # 必须存在（不存在 = jacoco 被静默跳过、覆盖率门禁空过）
```

- **判据**：surefire + failsafe 的**失败集合 ⊆ 那 4 例已批准偏差**，且**本批新增用例全部通过**；**不是 exit 0**。
- **分相位读读数**（spotless 在 `verify` 里先跑，会掩盖用例结果）。
- **不许传 `-DargLine`**。
- `jacoco:check` 的成功判据是打印 `All coverage checks have been met.`；**没有这行 = 门禁根本没被判定**。
- 新增类在 `com/crm/security/**`（**在 jacoco 分母里**）⇒ T1–T13 必须真覆盖到
  `RateLimiter`/`RateLimitAspect`/`RateLimitIdentity`/`ClientIpResolver` 的**分支**，
  否则挤压既有 `0.73` 的余量；`RateLimitExceededException` 在 `com/crm/common`（已排除）无覆盖义务。
- **前端零改动 ⇒ 不跑前端门禁**（本批不动 `frontend/` 任何文件）。
- 改完 Java **先 `mvn -B spotless:apply` 再提交**（spotless 是同相位里最先中止的门禁）。

### 定向破坏留痕（每条分支都要**被观测到转红**；逐条做、逐条还原；破坏期间不提交）

**还原判据分两类**：本应等于 HEAD 的文件用 `git hash-object <file>` == `git rev-parse HEAD:<path>`
（**内容级相等，不称逐字节一致**）；本批**有意未提交**的工件用「还原后复跑读数与破坏前**逐字相同**」。
**一律 `cp` 备份回写，禁用 `git checkout`**（它会吞掉同文件里本批有意未提交的订正）。

⚠️ 造破坏时**先写一句「它该改变哪条可观察行为」**，跑完核对**那条行为确实变了** —— 没变就是**空操作**，
别把绿记成结论；看到红先读**是不是判据本身**（`TS6133` 一类是**手段**的红，不是**目的**的红）。

| # | 破坏 | 该红的判据 |
|---|---|---|
| D1 | 存储从 Redis 换成进程内 `ConcurrentHashMap` | T2「计数真写进了存储」⇒ 本批的**核心主张** |
| D2 | 只留注解、让切面不生效 | T1/T2 接线用例 ⇒ 证明注解真被消费，不是装饰 |
| D3 | TTL 改成「每次都续」（照 `AuthService` 的错法） | T3 ⇒ 推 61s 后仍 429 |
| D4 | 删掉「首次才设 TTL」 | T3 的「键没 TTL ⇒ 永远 429」那半 |
| D5 | 删掉「读时补窗」 | T5 ⇒ 永久 429 |
| D6 | 开放 API 分桶改用 `currentUserId()` | T7 ⇒ 两密钥分桶 |
| D7 | `FormService` 的 429 改回 400 | T 系列里的契约用例 |
| D8 | 阈值改成 1 | **负对照**（前 N 次断 200）⇒ 证明「只断 429」是弱断言 |
| D9 | fail-open 改成 fail-close（catch 后 rethrow） | T4 |
| D10 | 给 `login` 挂上 `@RateLimit` | T12 ⇒ 钉住裁决 |
| D11 | 把 `@Order` 从 `PermissionAspect` 摘掉 | T13 ⇒ **若仍绿则如实记为「顺序契约无护栏」的已知空档**（不假装有） |

⚠️ **D3 与 D4 必须分开做**：它们打的是 T3 的**不同两半**（「窗口被不断续」vs「键根本没有 TTL」），
合起来做就分不清是哪一半在起作用（同 099 的 D3/D4 先例）。

### 手工冒烟（**边界要如实写**）

- **只读冒烟（默认做）**：8081 与 5173 本会话已在跑 ⇒ 对一个**只读且零副作用**的受限端点
  连打至超限，观察 429 + `Retry-After`；**不写库**。这条不需要额外同意。
- ⚠️ **冒烟要选对端点**：唯一「只读 + 有限流 + 无副作用」的是 P1 的 `public-read` 组
  （`GET /public/forms/{id}/meta` 一类）—— 但那组在 **C6** 才接。
  在 C6 之前只能对**已接入**的只读端点做（`/api/v1/open/**` 需要 API Key 且是读接口，或导出的 generate 类会**产生文件**）
  ⇒ **按提交阶段选端点，读不到就如实写「本阶段无可只读冒烟的受限端点」**。
- **匿名写端点（`POST /public/portal/tickets`、`unsubscribe`）不冒烟**（会往**共享开发库**写，
  按仓规需用户明确同意）。
- 端到端验证写端点须起**隔离实例**（临时端口 + 独立 schema + 另一个 Redis db），
  收尾 `DROP`/`REVOKE`/`FLUSHDB` 并核对**共享库未动**。配方见 `quickstart.md`。
- **不擅自重启 8081 后端**（可能归并行会话所有）；需要新实例时按隔离实例配方起。

---

## 风险

| 风险 | 缓解 |
|---|---|
| **假绿**：默认基类 Redis 是裸 mock ⇒ 限流 no-op | 三件事硬规则（装替身 + **正对照**断言键在 Redis + 负对照断 200）；把「不装就假绿」写进 `falsification-evidence.md` 与记忆 |
| **切 Redis 后 `FormIT`/`LandingPageIT` 在默认基类下限流变 no-op —— 算不算弄丢护栏？** | **不算**：那个内存桶今天**不是护栏而是跨用例共享状态**（事实 ⑬，已在制造与真因无关的假红），且它对限流是**零用例**的。**净收益为正**，但**必须**在交付留痕里显式登记这个语义变化 |
| 内存桶 → Redis 的**语义变化** | 见「五处对外可观测变更」逐条写进提交信息 |
| 事实 ⑬ 那颗 3/3 的雷 | 换 Redis 后在上下文层自动拆除；新用例一律自带独立 XFF；改完**单独复跑** `FormIT` 与 `LandingPageIT` 确认仍绿（**零改动是验收的一部分**） |
| 切 Redis 引入外部依赖（抖动时限流失效 + 追踪像素多一次 `INCR`） | fail-open 使其**不构成可用性风险**；登记 `rl:` 键族纳入运维监控（`allkeys-lru` 淘汰会让限流时强时弱，方向是 fail-open，**可接受**） |
| `FormService` 400 → 429 是**行为变更** | 它是在**修一个违反冻结契约的偏差**；照 085 判例：契约不动、实现改，并在留痕里写清 |
| 新增类进 jacoco 分母（BUNDLE ≥ 0.73） | 新组件配齐单测与 IT；`ls target/jacoco.exec` + 「`All coverage checks have been met.`」确认门禁**真被判定过** |
| 切面异常掉进 `Exception` catch-all 变 500 | `RateLimitExceededException extends BusinessException`（多重保障）+ T1 直接钉住 429 与 code |
| `PermissionAspect`/`RateLimitAspect` 顺序**不确定** | C2 就加 `@Order`（权限先于限流）；T13 钉住「未授权者不消耗配额」；**D11 若不红则如实记为已知空档**（⚠️ T13 钉的是**后果**，不是「顺序本身」） |
| 4 例已知 IT 失败 | 判据写成「失败集合 ⊆ 这 4 例 + 新增全绿」；`mvn -B verify` 退出码非 0 **是本仓既有状态**，不因此得结论 |
| `spotless` 比用例更早中止，掩盖真实结果 | 改完 Java 先 `mvn -B spotless:apply`；**分相位读读数** |
| **XFF 洞被本项「扩大」而非「绕开」** | 见 `research.md` §9：结论取「不修」+ `trust-forwarded-for` 显式化 + javadoc 写明前提 + 债务台账 + **口径降级为「误用与意外的阻尼」**；**不得宣传成攻击防护** |
| 数字落点漏改（模块数/编号各住 3 处以上） | 交付提交逐落点核对，以「**旧值仍能被 grep 到**」作订正留痕自查 |

**五处对外可观测的行为变更**（逐条写进提交信息）：
① 邮件追踪 429 的 `error.code`：`TOO_MANY_REQUESTS` → `RATE_LIMITED`；
② 表单提交 **400 → 429**（**订正**，非破坏）；③ 两处 message 文案统一为 `ErrorCode.RATE_LIMITED` 的那一条；
④ XFF 空段退化行为统一（`""` → fallback）；⑤ 新增 **`Retry-After`** 头。

---

## 明确不做

- **不给登录加限流**、**不收敛登录 2 层锁定与 2FA 锁定**（用户裁决 + 语义不同）。
- **不修 `X-Forwarded-For` 首值无条件信任** —— **登记**，且理由要写对（见上表最后一行）。
- **不引入 `@ConfigurationProperties`**（全仓 0 个，不为本批开新范式）。
- **不加 `fail-open` 开关**（等于给自己一个自伤旋钮）。
- **不用 Lua / 不扩 `InMemoryRedisTestSupport`**（替身刻意不实现管道与 Lua；扩它等于给未来开一条静默 no-op 通道）。
- **不做滑动窗口/令牌桶**（固定窗口的 **2× 边界突发**登记为债务）。
- **不改 036 / 016 / 017 / 055 的任何 FR 与工件**；**不改对比文档的判定列与分值**。
- **不动前端**（含**不加 i18n 键**）。
- **不给 `/ws/**`、`/actuator/health*`、`/error`、`OPTIONS /**`、swagger 加限流**（逐条理由见「故意不纳入」）。
- **不碰 `.specify/feature.json`**；**不跑任何 `/speckit-*`**。

## 提交纪律

`ListAgents` 确认无并行会话写同一批文件 → **逐路径 `git add`，禁用 `git add -A` / `git commit -a`**
（本仓多会话共用工作区）→ 提交信息末尾带 `Co-Authored-By: Claude Code <noreply@anthropic.com>`。
**不把提交自己的哈希写进它携带的文件**；**复选框绝不回填/预勾**（`tasks.md` 只在交付时勾）；
**已应用的 Flyway 迁移永不编辑**。**订正不静默**：原文逐字保留 + 带日期 ⚠️ 块，粒度到**每一列**，
自查判据是「**旧值仍能被 grep 到**」。若同伴工作被卷入，用 `git reset --soft` 重做，
**绝不修改或丢弃另一会话的未提交工作**。
