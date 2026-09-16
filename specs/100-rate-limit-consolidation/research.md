# 100-rate-limit-consolidation：技术决策记录

每条按 Decision / Rationale / Alternatives 写。**行号只作定位辅助，权威锚点是符号名**
（本仓 099 已实测过「引用行号会腐坏」，见其 `falsification-evidence.md`）。

---

## §1 与 `CRM_FEATURE_COMPARISON.md` 那两行的关系

**Decision**：**颠覆其事实、采纳其意图、不采纳其字面机制**。两处按「订正不静默」改写
（原文逐字保留 + 带日期 ⚠️，粒度到每一列），判定列与分值**照旧不动**。

**Rationale**：
- 事实层被推翻两处（`spec.md` §1.1）：① 「仅 1 处」实为 4 宿主类 / 6 处计数点；
  ② 「登录…均无限流」为假——登录是**三层**防护（017 验证码**始终要求** + 用户名 5 次 + IP 10 次，各 15 分钟）。
- 意图层成立且已被用户裁决采纳：「统一收口 + 补空档」。
- 字面机制「过滤器层」**不采纳**，理由见 §2。
- 判定列与分值按仓规**照旧**：097 已确立「带快照日期的论断过期 ⇒ 写『快照后即失效』而非『报告写错』、
  判定列照旧、分值不动」的处置（该文档的判定列本身就是历史断言，改它等于静默改写历史）。

**Alternatives**：
- *直接改数字、删掉原文*：违反「订正不静默」，且会让 `grep 旧值` 判据失效。
- *只补 ⚠️ 不改列*：会让「动作」列继续把人指向过滤器方案，下一个接手的人会照着做。

---

## §2 机制选型：`@RateLimit` 注解 + `@Aspect`，**不上 Security 链过滤器**

**Decision**：用注解 + AOP 切面 + 一个共享限流件；**不**新增任何 `Filter` / `HandlerInterceptor`。

**Rationale**：
1. **本仓唯一既有的「逐端点声明横切策略」范式就是注解 + AOP**：`security/PermissionAspect` 的
   `@Before("@annotation(requirePermission)")` + `security/RequirePermission`（`@EnableMethodSecurity` 打开），
   该注解的类 javadoc 明写「方向是让本注解成为**唯一闸门**」。新增第二种机制会制造一个
   **没有判据**的问题：「导出用注解、公开端点为什么用过滤器？」——而答案只能是风格偏好。
   `spring-boot-starter-aop` 已在 `pom.xml`；全仓 `@Order`/`FilterRegistrationBean`/
   `HandlerInterceptor`/`WebMvcConfigurer` **零命中**。
2. **配额是逐端点的数字**（导出 10/60s vs 表单 3/60s 差 20 倍）。过滤器的自然形态是
   「URI 前缀 → 配额」的集中表，而**前缀在这里根本不成立**：`/api/v1/public/**` 内部风险差一个量级
   （`/public/track/**` 已有限流、`/public/portal/tickets/status` 是匿名凭证校验、
   `/public/portal/articles` 只是公开只读）。
3. **过滤器方案有一条**没有任何护栏**的顺序契约**：`config/LoggingFilter` 是裸 `@Component`、
   注册在 Security 链**之外**，`SecurityContextHolder` 为空。要靠过滤器按登录用户分桶，
   **必须**插进链内且排在鉴权**之后**，而 `config/SecurityConfig` 里的既有形状是
   `addFilterBefore(..., UsernamePasswordAuthenticationFilter.class)`。照抄这个形状再加一个，
   会落在两个鉴权过滤器**之前** ⇒ `currentUserId()` 恒 `null` ⇒ **全体已认证用户共用一个匿名桶**
   ⇒ 限流被静默降级成无效，而 78 个 IT **照样全绿**（它们全用 admin 令牌）。
   这是一条「写错了看不出来」的契约，注解方案**从结构上没有它**。
4. **注解在 `DispatcherServlet` 之内抛异常 ⇒ 走 `GlobalExceptionHandler` 拿到统一 `ApiResponse` 信封。**
   过滤器要么手写 JSON（`LoggingFilter` 连 `ApiResponse` 都用不上），要么走 Security 的空体 401 那一套 ——
   两者都会让本仓**唯一的 429** 与其它错误**不同形**。
5. **过滤器唯一的结构性优势在本批不存在**：它能在「响应已提交」之前拦截，而**全部导出都是非流式**
   （`SXSSF`/`EasyExcel` **零命中**，全 `XSSFWorkbook` + `ByteArrayOutputStream` /
   `Files.readAllBytes` 先物化进堆）⇒ `@Before` 在方法体之前抛异常对 13 个导出面全都有效。

**注解的弱点与补救**：注解是 **opt-in** ⇒「忘了标注 = 无限流，且**没有用例会红**」。
这是它相对过滤器的**唯一**结构性弱点，补法是本仓已成熟的**字节码扫描台账**
（`RequirePermissionScanTestSupport` 那套）——用台账换 default-deny，见 §13。

**Alternatives**：
- *`SecurityFilterChain` 内加一个按前缀配额的过滤器*：正确性由「你有没有记得把它加在鉴权之后」决定，
  且**没有任何测试能发现加错了位置**（见 Rationale 3）。
- *`OncePerRequestFilter` 注册为 `@Component`*：**结构上拿不到登录用户**（`SecurityContextHolder` 为空），
  只能做 IP 维度 ⇒ 覆盖不了导出与开放 API 这两个最大的空档。
- *`HandlerInterceptor`*：全仓零先例，且同样有顺序/身份问题的另一套写法。
- *在 `SecurityConfig` 里用 `authorizeHttpRequests` 的 `access()` 表达式*：拿不到「窗口内计数」这种带状态的判定。

---

## §3 存储选型：**Redis 固定窗口**

**Decision**：`RedisTemplate.opsForValue().increment(key)`，窗口用 `expire(key, window)`；键族 `rl:<scope>:<identity>`。

**Rationale**：
- 本项要消灭的**就是**「进程内 `ConcurrentHashMap` ⇒ 多实例下真实阈值 = 配置值 × 实例数」。
  继续用进程内等于没收口。
- Redis 已在链路上（`AuthService` 的失败计数、`MfaStateStore` 全族、refresh token 登记、验证码），
  **不引入任何新组件**，也不新增迁移。
- 键族与 `auth:*` **同级前缀**（不是嵌在 `auth:` 下）：`rl:` 是「请求配额」，与认证语义无关；
  同级的另一个好处是运维可按前缀注入故障与监控，而测试替身的 `failOnKeyPrefix("rl:")` 正好可用
  （`AuthMfaIT` 已有用 `"auth:2fa-"` 前缀做隔离断言的同款先例）。

**Alternatives**：
- *进程内 `ConcurrentHashMap`（即今天两处内存桶的形态）*：多实例失效；且它是**跨用例共享状态**
  （`FormService.rateBuckets` 是单例 bean 的实例字段，`clearInProcessCaches()` **不清它**），
  已经在制造与真因无关的假红。
- *滑动窗口 / 令牌桶*：更平滑，但需要 `ZSET` 或 Lua。`InMemoryRedisTestSupport` 的
  `opsForZSet` **刻意未实现**（`:69-71` 明列），而扩替身等于给未来开一条静默 no-op 通道。
  固定窗口的 2× 边界突发**登记不修**（`spec.md` FR-038 ②）。
- *Bucket4j / Resilience4j 等库*：引入新依赖，且它们的语义（`refill`/`greedy`）会把
  「窗口内计数」这个本仓既有的心智模型换成另一套；本项的价值在**收口**不在**升级算法**。

---

## §4 TTL 语义：照 `MfaStateStore`（**仅 `count == 1` 时设**），**不照** `AuthService`（无条件续窗）

**Decision**：`Long n = opsForValue().increment(key); if (n != null && n == 1) expire(key, window);`

**Rationale**：仓内有两个**体例相反**的既有范式，必须显式选一个并写明为什么。

| | 写法 | 窗口起点 | 对「请求限流」是否正确 |
|---|---|---|---|
| `AuthService.recordFailure` | `increment` 后**无条件** `expire` | 每次失败**重新**起算 | ❌ |
| `MfaStateStore.recordFailure` | `count == maxAttempts` 那次才 `expire` | 达阈值那次起算 | ✅（本项照它） |

`AuthService` 那种写法对**登录失败计数**是**有意**的（`MfaStateStore` 的 javadoc 明说
「每一次失败都续一整窗，于是『一直失败』就『一直锁着』。**对登录限流那是有意的**」）。
但把同一写法搬到**请求限流**上会得到一个荒谬的结果：一个稳定 4 次/分钟的客户端
会让键**永远续不上前** ⇒ 配置的「3 次 / 60 秒」事实上变成「**每 60 秒只准错一次，错一次就再也不许过**」——
症状是「这个客户端怎么都不行」，而单看代码每一行都对。

**Alternatives**：
- *无条件续窗（照 `AuthService`）*：语义错误，见上。
- *`>=` 阈值时 `expire`*：接近但不精确。`MfaStateStore` 明确用 `==`（「**恰好**达到，不是 `>=`」）：
  阈值之后即便因并发多记了一次，也不会把窗口再续一窗。本项照它的 `==`。

---

## §5 「读时补窗」在本项**放行**，与 `MfaStateStore` **刻意相反**

**Decision**：`count >= limit` 时先读 `getExpire(key, MILLISECONDS)`；若为 `-1`（有键无 TTL）
或 `-2`（键不存在），则 `expire(key, window)` 补回整窗、`log.warn`、**放行**。

**Rationale**：`MfaStateStore.lockRemainingSeconds` 的 javadoc 已经为 MFA 写透了这个洞：
「仓内既有的 `increment` + `expire` 是**两次**调用，中间断掉（或键被别的路径覆盖）就会留下一个
『计数是满的、但没有过期时间』的键——而 `INCR` 会一直让它满着……那个人的 2FA 将**永久**不可用
（重启进程也没用，键在 Redis 里），且症状是『这个人怎么试都不行』，排查方向会整个跑偏。」
它当时的处置是**补回整窗并继续锁**（返回 `lockWindow.toSeconds()`）。

**本项必须用相反的处置**，两者的差别是**被锁对象的性质**：
- MFA 锁的是**一个人**的**一个流程**（他连试都不能试），而漏放一次 = **跳过二次验证** ⇒ 宁可多锁，fail-close。
- 限流锁的是**一个主体的一个窗口的请求配额**，而漏放一次 = **多过一个请求** ⇒ 宁可多放，fail-open。
  限流是**可用性敏感的旁路控制**，一个永久 429 的客户端比多放一个请求严重得多。

⇒ 代码注释必须**显式写出这个对比**（引 `MfaStateStore.lockRemainingSeconds` 的类名与方法名），
否则下一个看代码的人会以为这里有 bug 而去「修正」成继续锁。

**Alternatives**：
- *补窗并继续拒绝（照 MfaStateStore）*：会把一个临时的 TTL 缺失放大成永久拒服务。
- *不补窗，只读成「没锁」并放行*：那一瞬间是放行了，但键仍然无 TTL 且计数仍满
  ⇒ **下一个请求继续走这条分支**，等价于**限流对这个人永久失效**（方向安全的错，但是**静默的**）。
  补窗同时解决「永久 429」与「永久失效」两个方向，代价只是一次 `EXPIRE`。

---

## §6 fail-open 的**边界**：catch 什么、不 catch 什么

**Decision**：只 `catch (RedisConnectionFailureException | DataAccessException ex)` ⇒ `log.warn` ⇒ 放行。
**不** catch 裸 `Exception`。

**Rationale**：
- **立场有明文依据**：`MfaStateStore` 的类 javadoc 是全仓对 fail-open/fail-close 的论述，
  它说 fail-open 「**对限流是合理的**——它的代价是『限流暂时失效』，而不放行会让整个系统在
  Redis 抖动时无法登录」，并明说它自己的 fail-close 是**为 2FA 语义**（`catch { return null; }`
  = **跳过二次验证**）而立的。⇒ 那句论述**不可移植**到限流，本项在 javadoc 里引它并说明这一点。
- **不 catch 裸 `Exception`** 是本项的**收紧**：仓内既有的 catch（`AuthService` 三处）都是裸 `Exception`，
  对**失败计数**那样写代价小；但对**新件**它会把「键构造写错」「类型不对」「`scope` 拼错」
  这类**真 bug** 也降级成静默放行，症状变成「限流时有时无」——极难归因。
  收窄到这两个类型后，真 bug 会以 500 的形式**响亮地**暴露（并且有 `GlobalExceptionHandler` 的
  catch-all 日志兜底）。

**Alternatives**：
- *catch 裸 `Exception`（照 `AuthService`）*：一致性更好，但把真 bug 静默化，见上。
- *fail-close*：与 §5 的可用性理由矛盾；且 Redis 抖动时会让**全站导出 503**。
- *提供 `fail-open` 开关*：**刻意不做**。那等于给运维一个「关掉开关就全站不可用」的自伤旋钮，
  而没有任何 ops 场景需要它（真正需要的是把 Redis 修好）。

---

## §7 **不用 Lua**、不扩 `InMemoryRedisTestSupport`

**Decision**：`INCR` + 条件 `EXPIRE` + 读时补窗；**不**用 `execute(script)` 做原子计数。

**Rationale**：
1. `InMemoryRedisTestSupport` 的类 javadoc 明列**刻意未实现**：`delete(Collection)`、`keys()`、
   **管道/事务/Lua**、`opsForZSet`/`opsForHash`…… 并说明理由——**未实现的方法按 Mockito 默认答案
   返回 `null`/`false`，而这是静默的**，足以让一个用例**因为错误的原因而变绿**。
2. 在**默认基类**下（裸 mock），`execute(script)` 会返回 `null` ⇒ 又是一条**静默 no-op 的假绿通道**，
   而且它比 `increment` 那条更隐蔽（`increment` 至少还有 FR-033 的正对照能抓）。
3. **本项的原子性需求远弱于 MFA**：`MfaStateStore` 的两个原语（`GETDEL`、`SET NX`）非原子就会
   **跳过二次验证**或**同一码用两次**——是安全语义。限流这里非原子最坏是**多放一次请求**
   （fail-open 方向）或留下无 TTL 的键（由 §5 补窗兜住）。**收益与代价不匹配。**
4. 若要 Lua，必须**同步扩替身**（否则测试是假的），而扩替身正是 §7 Rationale 1 里那条
   「给未来开一条静默 no-op 通道」——本项**刻意不做**。

**Alternatives**：
- *Lua 脚本*：见上。
- *`MULTI`/`EXEC` 事务*：替身未实现，且 `RedisTemplate` 的 `SessionCallback` 在本仓零先例。

---

## §8 身份维度：`AUTO` 把「开放 API 按 keyId」变成默认行为

**Decision**：`AUTO` 顺序 = 机器主体 ⇒ `key:<keyId>` → 已认证 ⇒ `user:<userId>` → 否则 ⇒ `ip:<resolved>`；
另提供显式 `IP` / `USER` / `API_KEY`。

**Rationale**：
- `ApiKeyAuthFilter` 注入的 `CrmPrincipal` 是 `new CrmPrincipal(key.getCreatedBy(), "open-api", "OPEN_API", true)`，
  即**机器主体的 `userId` 是密钥的创建者**；密钥 id 另放在 `authentication.getDetails()` 的
  `ApiKeyPrincipal(keyId, name)` 里。⇒ 按 `currentUserId()` 分桶会让**同一个管理员建的多个密钥共用一个桶**：
  一个密钥打满，其余密钥**全被拒**。这个缺陷在功能上是**静默的**（阈值看起来生效了），只有按密钥分桶才对。
- 把它放进 `AUTO` 的默认判定（而不是让每个端点写 `by = API_KEY`）是因为：**默认值必须是对的**，
  否则「3 个开放端点漏写一个」就会静默退化，而**没有任何测试会红**。
- `SecurityUtil` 已有 `isMachineSubject()`（为行级数据权限而加，`FR-G11`）⇒ 复用，不新增判定。

**Alternatives**：
- *只用 `userId`*：见上（密钥共用桶）。
- *只用 IP*：开放 API 的调用方常在同一台机器上（同一个 IP），会把不同密钥的配额合并。

---

## §9 客户端 IP 收敛：3 份 → 1 份，取**更严**的那一份；XFF 的残余风险**如实登记 + 口径降级**

**Decision**：新增 `com.crm.security.ClientIpResolver`，语义**逐字取** `AuthService.resolveClientIp` 那一份；
`AuthService.resolveClientIp` 保留签名、方法体改 1 行委托；删 `EmailTrackController` 与 `FormService` 的私有副本。
XFF 信任由 `crm.rate-limit.trust-forwarded-for`（默认 `true`）显式化。**不修 XFF 首值信任问题。**

**Rationale**：
- **三份副本在退化输入上行为不一致**（实测）：
  - `AuthService.resolveClientIp`：`forwarded != null && !isBlank()` **且** `first` 非空 ⇒ 返回 `first`，否则 fallback。
  - `EmailTrackController.clientIp` / `FormService.clientIp`：只判 `StringUtils.hasText(forwarded)` ⇒
    XFF 为 `","` 时 `split(",")[0].trim()` = **空串** ⇒ **所有这类请求共用一个键为 `""` 的桶**。
  ⇒ 收敛取**更严**的那一份（首段为空则回退）。这**是一处对外可观测的行为变更**（`spec.md` §5 第 ④ 条），
  须写进提交信息。

  > ⚠️ **2026-09-16 实测订正（`",` 那一格的结论被推翻，上面的原文逐字保留）**：上面这条对
  > `XFF = ","` 的描述**是错的** —— 它写「`split(",")[0].trim()` = **空串** ⇒ 共用一个键为 `""` 的桶」，
  > 而**实测抛的是 `ArrayIndexOutOfBoundsException: Index 0 out of bounds for length 0`**
  > （暴露它的用例：`ClientIpResolverTest.degenerateHeadersFallBackToTheFallback`，实做期由红转绿）。
  > **真因**：Java 的 `split` **丢弃末尾空段**，故 `",".split(",")` 是**长度 0 的数组**，取 `[0]` 抛异常；
  > 产生**空串桶**的是 `", 1.2.3.4"` 这一类（整串非空、首段为空白）。⇒ 三份副本在公开端点上是
  > **一条潜伏的 500**，比「共用空串桶」更严重一个量级（请求头由调用方任意构造）。
  > **实做**：`ClientIpResolver` 用 `indexOf(',')` + `substring`，三种退化输入**一律回退 fallback**。
  > **本节的 Decision 与「取更严那份」的结论不变**，只是理由从「另两份更松」升级为「**另两份会抛异常**」；
  > `EmailTrackController` / `FormService` 两份私有副本仍按本节处置（C3/C4 删除）。
  > 逐条偏差与落点见 `tasks.md` §实做订正 第 9 条。
- **放 `com.crm.security` 而不是 `com.crm.config`**：`service → security` 是本仓**既有**方向
  （`FormService` 已 import `SecurityUtil`）；放 `config` 会让 `service → config` 成为一条**新**方向，
  而 `config` 是装配层不是工具层。
- **`AuthService.resolveClientIp` 保留签名**（`public static`，`AuthController` 与
  `AuthServiceTest` 都在用）⇒ 只换方法体 ⇒ **那两个调用点零改动继续绿**。

**⚠️ XFF 残余风险的口径（这一条最初推理错了，务必按下面写）**：
本项新增的限流面**大部分**在已认证路径（导出按 `userId`、`/open/**` 按 `keyId`），伪造 XFF 打不穿它们；
**但本项同时给 `/api/v1/public/**` 的多个匿名端点加了 IP 桶，而那些端点只能按 IP 分桶**
⇒ 在这些端点上，伪造 XFF 就是**完整绕过**。
⇒ 准确表述是：**这个洞在本项被「扩大」而不是被「绕开」**（新增的匿名 IP 桶提高了它的利用价值，
且容易被误当成安全能力）。**结论仍取「不修」**，但理由换成：
① 修复需要「可信代理网段白名单 + 从右往左取 XFF」，而本仓**没有任何可信代理配置**
（`server.forward-headers-strategy` 也不解决——它同样要求可信边界配置）；
② 生产形态下 nginx 是唯一入口，要伪造得先能直连后端端口，那是网络层的事。
**本项的动作**：提供 `trust-forwarded-for` 配置（默认保行为）+ javadoc 写明前提 + 登记债务台账 +
**在验收口径里降级**：公开端点限流的定位是**误用与意外的阻尼**（防一个死循环的前端把工单表灌满），
**不是抗敌手**。**不得在任何文档里把它宣传成攻击防护。**

**Alternatives**：
- *顺手修 XFF（加可信代理配置）*：超出本项射程，且需要一份**运维才知道**的可信网段清单；
  没有它就等于用一个猜测替换另一个猜测。
- *用 `request.getRemoteAddr()` 完全忽略 XFF*：**更坏**——生产上 nginx 是唯一入口，
  所有请求的 `remoteAddr` 会变成 nginx 自己 ⇒ **全站共用一个桶**。

---

## §10 包选型与 jacoco 分母

**Decision**：新件全部放 `com.crm.security`（`RateLimit` / `RateLimitDimension` / `RateLimitKeys` /
`RateLimitIdentity` / `ClientIpResolver` / `RateLimiter` / `RateLimitAspect`）；
`RateLimitExceededException` 放 `com.crm.common`。

**Rationale**：
- `com.crm.security` 已有 `RequirePermission` / `PermissionAspect` / `SecurityUtil` /
  `JwtAuthFilter` / `ApiKeyAuthFilter` —— 本项是同一族（逐端点横切策略 + 身份判定）。
- `pom.xml` 的 jacoco 排除项是 `CrmApplication` / `dto/**` / `entity/**` / **`common/**`**
  ⇒ `com.crm.security/**` **在覆盖率分母里**（新增类要付覆盖成本），`com.crm.common/**` **不在**。
  异常类放 `common` 与 `ErrorCode` / `BusinessException` 同址是**功能上**的理由（同族），
  顺带不产生覆盖义务。
- 已知代价：新类进分母、BUNDLE 阈值 `INSTRUCTION COVEREDRATIO ≥ 0.73`。⇒ 新组件必须配齐单测与 IT
  （T1–T13 必须真覆盖 `RateLimiter` / `RateLimitAspect` / `RateLimitIdentity` / `ClientIpResolver` 的分支），
  否则挤压既有余量。

**Alternatives**：
- *放 `com.crm.common`*：会让 `common` 依赖 `SecurityContextHolder`（`RateLimitIdentity`）——
  `common` 目前不依赖 Spring Security，那是一条不该开的方向。
- *放 `com.crm.config`*：`config` 是装配层（`RedisConfig` / `SecurityConfig` / `MailConfig`…），
  放业务逻辑进去会让它变成杂物间；且 `service → config` 是新方向（§9）。

---

## §11 错误码：新增 `RATE_LIMITED`，**刻意不合并** `MFA_LOCKED`

**Decision**：`RATE_LIMITED(429, "RATE_LIMITED", "请求过于频繁，请稍后再试")`，紧邻 `MFA_LOCKED` 放置。

**Rationale**：
- `MFA_LOCKED` **不能动**：`AuthMfaIT` **逐字断言** `error.code == "MFA_LOCKED"`。
- **语义相反，合并会让前端无法区分**：
  `MFA_LOCKED` 是「失败计数达阈值后锁定，窗口从达阈值那次起算，且
  `MfaStateStore.lockRemainingSeconds` 会**补窗续锁**」；
  `RATE_LIMITED` 是「固定窗口内的请求配额，窗口走完即**自动放行**，且补窗后**放行**」。
  对用户是两句不同的话：「你的账号被锁了」vs「你请求太快了，等一下」。
- `ErrorCode` 里那句「全仓首个 429（本项引入；此前本枚举里没有任何限流码）」**描述的仍是 082 当时的事实**
  ⇒ 按「订正不静默」**原文逐字保留** + 追加带日期 ⚠️（说明此后新增了通用限流码、『首个』照旧指 082）。

**Alternatives**：
- *复用 `MFA_LOCKED`*：断言会红，且语义混淆。
- *沿用 `GlobalExceptionHandler` 里的裸字符串 `"TOO_MANY_REQUESTS"`*：它今天硬编码在处理器里、
  不在枚举中，与「错误码由 `ErrorCode` 统一」的体例不符；且**没有 message**（用的是异常的 `getMessage()`）。

---

## §12 对外可观测的行为变更（**共五处，逐条写进提交信息**）

| # | 变更 | 性质 |
|---|---|---|
| ① | 邮件追踪 429 的 `error.code`：`TOO_MANY_REQUESTS` → **`RATE_LIMITED`** | **破坏性**（字符串变更）。判据：全仓 `src/test` 对该串**零命中**；前端对 `429`/`TOO_MANY` **零命中**且只读 `error.message` |
| ② | 表单提交超限：**400 → 429** | **订正**（实现向冻结契约靠拢，`036/contracts/online-forms.md:44` 承诺 429） |
| ③ | 两处限流 message 文案统一为 `ErrorCode.RATE_LIMITED` 的那一条 | 破坏性（文案） |
| ④ | XFF 退化输入（首段为空）行为统一：**空串桶 → 回退 fallback** | **订正**（三份副本里两份的行为被收敛到更严的那份） |
| ⑤ | 限流响应新增 **`Retry-After`** 头 | 新增（向后兼容） |

> ⚠️ **2026-09-16 实测订正第 ④ 行（原文逐字保留）**：第 ④ 行对**变更内容**的描述只覆盖了一半 ——
> 它只说「空串桶 → 回退 fallback」，而实做期实测发现三份副本在 `XFF = ","` 上**抛
> `ArrayIndexOutOfBoundsException`**（Java 的 `split` 丢弃末尾空段 ⇒ 长度 0 的数组），
> 即**另有一处「潜伏 500 → 回退 fallback」的变更**，其对外严重性高于空串桶。
> ⇒ 第 ④ 行的**类**（**订正**、而非破坏性变更）与**方向**（收敛到更严那份）都不变，
> 但**变更集合比本行写的更大**。理由与逐条落点见 §9 的 ⚠️ 块与 `tasks.md` §实做订正 第 9 条。

⚠️ ① 单独暴露在**第 2 次提交**（`EmailTrackController` 的收敛）里，**便于日后二分**。

**Alternatives**：保留 `TOO_MANY_REQUESTS` 的字面量以零破坏 —— 但那样会留下**两个**表示 429 的码，
与本项「收口」的目的一处不符。

---

## §13 台账护栏：字节码扫描 + 「类#方法」粒度的**带理由**白名单 + 自检

**Decision**：`RateLimitCoverageTest` 枚举 `com/crm` 下全部控制器端点方法，
每个必须有 `@RateLimit` 或在白名单里（**条目必须带非空理由**）；并带「扫描确实扫到了 N 个端点」的自检。

**Rationale**：
- 这是 §2 里注解方案**唯一**的结构性弱点的补救。没有它，本项等于把「漏设防」从
  「靠人记得」换成「靠人记得写注解」。
- 手法直接复用 `RequirePermissionScanTestSupport`（`MetadataReader` 遍历 classpath
  `com/crm/**/*.class`，用 `getAnnotatedMethods(...)`）。**测试类不在 `com.crm` 包下，不会被算进来。**
- **粒度必须是「类#方法」**：`/api/v1/public/**` 内部风险差一个量级，且 `/public/track/**` 两条**已限流**
  ⇒ 用 URI 前缀会让白名单变成「一放一大片」，那正是台账要防的东西。
- **条目必须带理由**：防「随手加一行让测试变绿」——一个没有理由的豁免就是一条没被想过的豁免。
- **自检必须有**：`RequirePermissionCatalogTest` 已有同款先例（先断言扫描非空，
  否则扫描 pattern 写错时「零违规」是**假绿**）。这条与本仓 099 的实测教训同族：
  「grep 模式的边界要自证」。

**Alternatives**：
- *反向：只扫「有 `@RateLimit` 的方法」并核对它们合法*：查不出**漏**（正是要防的）。
- *用反射 + `RequestMappingHandlerMapping`*：要启 Spring 上下文，而这是一个纯静态检查；
  且 `RequestMappingHandlerMapping` 会做路径合并，反而拿不到「类#方法」这个锚点。

---

## §14 与 036 / 016 / 017 / 055 的关系（**均不改其工件**）

**Decision**：四个上游工件**一个字符都不改**。

**Rationale**：
- **036-online-forms**：`contracts/online-forms.md:44` 与 `tasks.md:50` 都承诺 **429**，
  实现给 400 ⇒ 本项**把实现改对**（085 已确立判例：**契约是对的，改的是实现**）。
  036 的工件不动，只在实现侧留 javadoc 引那两处行号。
- **055-open-platform**：⚠️ 逐条读了 FR-O01~O10，**从来没有速率限制这条**。
  043 的建议里有「API Key / 权限 / 限流」，而**限流在 055 立项时就没进 FR**
  ⇒ 本项是「**从未规格化**」的补课，**不是**「规格了没做」。故不构成对 055 的违反。
- **016-system-enhancement**：其 `tasks.md` 的 T052 实为**前端表单对齐**（不是限流）、
  T064 是 **IP 维度登录锁定**（本项**不收敛、不改**）。⚠️ 「T052/T064 是限流」是本项立项前
  **容易被误读**的一处：T052 的原文与限流无关。
- **017-login-captcha**：验证码**始终要求**（不是失败触发）——本项在文档订正里**把它算作登录的第一层防护**，
  017 的工件不动。

**Alternatives**：给 055 补一条 FR-O11（速率限制）—— 那会让本项变成「改需求」，
而用户裁决的范围是「补空档 + 收敛」，且补 FR 会牵连 055 的全部工件与判定列。

---

## §15 覆盖缺口与假绿通道（**如实登记**）

**登记如下，本项不修饰、不掩盖**：

1. **默认测试基类下限流是 no-op**（最大的假绿陷阱）：`AbstractIntegrationTest` 的裸 mock
   `opsForValue()` ⇒ `increment` 返回 `null` ⇒ fail-open ⇒ 限流不生效。
   **真正的假绿形态**不是「循环断言第 N+1 次 429」（那种在裸 mock 下**会红**，它看到 200），
   而是 `AuthServiceTest` 那种「**把 Redis 返回值桩成 10、断言拒绝**」——它证明的是
   「**如果** Redis 说 10 我就拒」，**不证明接线**。
   ⇒ 补救是 §FR-033 的三件事硬规则；**缺任一件即视为假绿**。
2. **`FormService` 与 `EmailTrackController` 的限流在今天 `src/test` 里零用例**（实测：
   `rateBuckets` / `RATE_LIMIT` / `提交过于频繁` 在三处**零命中**）。⇒ 本项的 T 系列是这两条路径的
   **第一批**行为层证据，而**删除旧实现本身没有回归证据**（旧实现从来没被用过用例测过）。
3. **`FormIT` / `LandingPageIT` 那颗 3/3 的雷**：实测 `FormIT` 2 次 + `LandingPageIT` 1 次提交，
   全部来自默认 `remoteAddr=127.0.0.1`，而 `RATE_LIMIT = 3` ⇒ **恰好 3/3**，第 4 次就 400。
   切到 Redis 后，在**默认基类**（裸 mock）下这两处限流**变成 no-op**。
   **这不是弄丢护栏**（那颗雷今天**不是护栏而是跨用例共享状态**，且限流零用例），
   但**必须在交付时显式登记**，免得后人以为「IT 里限流一直生效」。
   ⚠️ **附带硬规则**：替身是**类级安装、用例级清空**，而 MockMvc 的默认 `remoteAddr` 是
   **上下文级常量** ⇒ 凡装了替身且会打到限流端点的用例，**必须显式给一个独立的 `X-Forwarded-For`**，
   不得依赖默认 `remoteAddr`（否则会重现同一颗雷）。
4. **原子性不可证**：`InMemoryRedisTestSupport` 明说自己是单线程语义、
   「**不能用来证明任何原子的东西**」⇒ 本项**不声称**任何原子性；T6 的调用形状断言只钉「用了哪个原语」。
5. **多实例行为未验**：本机只跑一个实例 ⇒ 「阈值不再 ×N」这条**只有推理、没有实测**。
6. **`@Order` 的顺序契约无护栏**：T13 钉的是「未授权者不消耗配额」这个**后果**，
   不是「两个切面的相对顺序」本身。定向破坏 D11（摘掉 `PermissionAspect` 的 `@Order`）若**仍绿**，
   须**如实记为已知空档**，不得假装有护栏。
