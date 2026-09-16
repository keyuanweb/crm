# 定向破坏与订正留痕（100-rate-limit-consolidation）

**日期**：2026-09-17（C6 = 交付批）· **配套**：[`quickstart.md`](./quickstart.md) · [`plan.md`](./plan.md) · [`tasks.md`](./tasks.md)

> **本件在交付前的形态是骨架**：§A–§K 的**逐条破坏观测**、§L 的**门禁读数**、§M 的**冒烟记录**、
> §O 的**订正自查命中数**留到交付时填，以免把未实测的数字写进来。
> ✅ **2026-09-17 交付时已全部填实**（§R 给出「哪一节由哪次实跑填的」索引）。
> ⚠️ **`D1`–`D11` 的每一次破坏都满足两个前提**：① 造破坏**之前先写一句「它该改变哪条可观察行为」**，
> 跑完**核对那条行为确实变了**（没变 = **空操作**，绿不能记成结论）；② 还原**一律用 `cp` 备份回写**，
> **禁用 `git checkout`**（它会还原到 `HEAD`，而本批有一批**有意未提交**的订正住在同一些文件里，
> 一并吞掉就再也拿不回来）。还原判据 = `git hash-object <file>` == 破坏前记录的哈希
> （**内容级相等**，不称「逐字节一致」——CRLF 会让 `sha1sum` 假不等）。

---

## §0 本批的证据形态与三条硬规则

⚠️ **本批最大的假绿陷阱**：`AbstractIntegrationTest` 的 Redis 是**裸 mock**
（`@MockBean RedisTemplate` + `mock(ValueOperations.class)`）⇒ `increment` 返回 `null` ⇒ **fail-open 生效 ⇒
限流是 no-op**。而真正的假绿**不是**「循环断言第 N+1 次 429」（那种用例在裸 mock 下**会红**，它看到的是 200），
而是「把 Redis 的返回值桩成 10 再断言拒绝」—— 它证明的是「**如果** Redis 说 10 我就拒」，**不证明接线**。

⇒ **每条 HTTP 层限流用例必须做满三件事，缺一即视为假绿**：
① `redis.install(redisTemplate, clock)` 装功能型替身；
② **正对照**：`redis.snapshot()` 里**有**该键（没接线时不存在 ⇒ 红，**这是核心**）；
③ **负对照**：未达阈值时**断 200**（防「限流恒拒」这种反向劣解）。

§A（D1）与 §H（D8）分别从**两个方向**打了这套规则：D1 让「计数不落存储」变成红，D8 让「阈值被改成 1」
被负对照抓住 —— **只有正对照没有负对照**、或**只有负对照没有正对照**，都会被这两个破坏中的一个漏掉。

---

## §A D1 —— 存储从 Redis 换成进程内 `ConcurrentHashMap`（本批的核心主张）

| 项 | 内容 |
|---|---|
| **破坏** | `security/RateLimitStore.java`：把 Redis 的 `increment`/`expire`/`getExpire` 换成进程内 map |
| **它该改变哪条可观察行为** | 「计数**真的**落到 Redis」—— 即 T2 的正对照。换掉存储后 `redis.snapshot()` **必然为空** |
| **备份 / 日志** | `/tmp/destruct/D1-RateLimitStore.bak` · `/tmp/destruct/D1.log` |
| **破坏前哈希** | `b9d076569f4447791654c8c010508d9c05585c8b` |

**实跑读数**（`RateLimitIT` 8 例 → **6 例红**；`RateLimitCoverageTest` 4/4 绿 —— 台账是**字节码**扫描、
不碰存储，故它**不该**红，它绿是预期的）：

```
[ERROR] Tests run: 8, Failures: 6, Errors: 0, Skipped: 0 -- in com.crm.integration.RateLimitIT
  T1  overTheLimitIsRejectedWithTheSharedErrorCode
  T2  theCounterReallyLandsInRedis
  T3  theWindowExpiresAndAllowsAgain
  T4  aStorageFailureIsFailOpenEvenWithAFullCounter
  T13 permissionIsCheckedBeforeTheQuotaIsConsumed
  T15 formSubmitIsRateLimitedWithTheCorrectedStatus
```

T2 的失败信息**逐字**（正是「正对照」的形态 —— 期望的键在一个**空 map** 里找不到）：

```
java.lang.AssertionError: [没接线时不会有键落在存储里 —— 这是核心判据]
Expecting map:
  {}
to contain entries:
```

⚠️ **T4 也红了，这一条要看懂**：T4 打的是「存储故障 ⇒ fail-open」。换成进程内 map 后**存储不再会故障**
（`failOnKeyPrefix("rl:")` 注入的是替身的 Redis 故障，而代码已经不碰 Redis）⇒ 它红在**它自己的前置断言**
（「先打出满计数」这一步现在打不满 Redis）上，不是红在 fail-open 那一支。**这不是判据出错**，
是这条破坏同时踩了两条路径 —— 如实记下，免得读者以为 T4 是 fail-open 的专属判据（**T4 的专属破坏是 D9**）。

**还原**：`cp /tmp/destruct/D1-RateLimitStore.bak backend/src/main/java/com/crm/security/RateLimitStore.java`
→ `git hash-object` = `b9d076569f4447791654c8c010508d9c05585c8b` ✅ 与破坏前相等。

---

## §B D2 —— 只留注解、让切面不生效

| 项 | 内容 |
|---|---|
| **破坏** | `security/RateLimitAspect.java`：让 `@Before` 通知**不执行**（注解还在类上、端点上也还在） |
| **它该改变哪条可观察行为** | 「注解**真被消费**，不是装饰」⇒ T1/T2 这类接线用例必须红 |
| **备份 / 日志** | `/tmp/destruct/D2-RateLimitAspect.bak` · `/tmp/destruct/D2.log` |
| **破坏前哈希** | `934217f3b2c6c448f27c8cd4454c26638d2804e7` |

**实跑读数**（6 例红）：

```
[ERROR] Tests run: 8, Failures: 6, Errors: 0, Skipped: 0 -- in com.crm.integration.RateLimitIT
  T1 · T2 · T3 · T4 · T13 · T14 theRetryAfterHeaderIsWithinTheWindow
```

⚠️ **与 D1 的红集合差一个元素**，这个差是**有意义**的：D1 红在「计数写不进去」（T15 的表单路径也红），
D2 红在「切面根本不在」（T14 的 `Retry-After` 头没人写、故红）。两次都是 6 红但**不是同一组** ——
**「红了几条」不能替代「红了哪几条」**。

**还原**：`cp` 回写 → `git hash-object` = `934217f3b2c6c448f27c8cd4454c26638d2804e7` ✅

---

## §C D3 —— TTL 改成「每次都续」（照 `AuthService.recordFailure` 的错法）⇒ **本条破坏跑出过一次全绿，是本批唯一一次「破坏没打中」**

| 项 | 内容 |
|---|---|
| **破坏** | `security/RateLimitStore.java`：把「`count == 1L` 才 `expire`」改成**无条件 `expire`**（每次请求都把窗口推后一整窗） |
| **它该改变哪条可观察行为** | 「窗口到期后恢复放行」⇒ T3 必须红（推 61 秒后**仍**应被拒） |
| **备份 / 日志** | `/tmp/destruct/D3D4D5-RateLimitStore.bak` · `/tmp/destruct/D3.log`（第一次）· `D3b.log`（加严后复跑） |
| **破坏前哈希** | `b9d076569f4447791654c8c010508d9c05585c8b` |

**第一次实跑：`Tests run: 8, Failures: 0` —— 全绿。** 这**不是**「破坏是空操作」，而是
**T3 当时是一条空断言**：它把 61 次请求**全部压在一个冻结的瞬间**里打完（推到 61 秒之后才看结果），
而「首次才设窗」与「每次都续窗」这两种 TTL 策略**在那个瞬间给出的答案完全一样** ——
续窗与否，只有在**窗口中途**再打一次才分得开。

**处置**（见 §Q ①）：给 T3 **加一段窗口中途的流量**（`advanceSeconds(30)` 后断言仍 429），
再推进到过期点。**判据没有被放宽，是被加严了。** 加严后复跑：

```
[ERROR] Tests run: 8, Failures: 1, Errors: 0, Skipped: 0 -- in com.crm.integration.RateLimitIT
  T3 theWindowExpiresAndAllowsAgain
java.lang.AssertionError: [键没有 TTL ⇒ 窗口不过期 ⇒ 永久 429（重启进程也救不了，键在 Redis 里）]
Expecting value to be false but was true
	at com.crm.integration.RateLimitIT.theWindowExpiresAndAllowsAgain(RateLimitIT.java:205)
```

⚠️ **这条留痕的价值在于「破坏台账自己也会失效」**：若不在改完 T3 后再跑一遍 D3，
交付时会记下「D3 ⇒ T3 红」，而那是**加严之后**的结论、**当时并不成立**。

**还原**：`cp` 回写 → `git hash-object` = `b9d076569f4447791654c8c010508d9c05585c8b` ✅

---

## §D D4 —— 删掉「首次才设 TTL」（= 键永远没有 TTL）

| 项 | 内容 |
|---|---|
| **破坏** | `security/RateLimitStore.java`：整条 `count == 1L → expire` 删掉 |
| **它该改变哪条可观察行为** | 「窗口会给键一个过期时间」⇒ T3 的后半（「键没 TTL ⇒ 永久 429」）必须红 |
| **备份 / 日志** | `/tmp/destruct/D3D4D5-RateLimitStore.bak` · `/tmp/destruct/D4.log` |
| **破坏前哈希** | `b9d076569f4447791654c8c010508d9c05585c8b` |

**实跑读数**（5 例红）：`T1 · T3 · T4 · T14 · T15`。
⚠️ **D3 与 D4 打的是 T3 的不同两半**（前者 = 窗口被无限推后、后者 = 永远没有窗口），故**必须分开做**；
这里 D4 的红集合**不含 T2**（计数照样落存储、有没有 TTL 不影响 `snapshot()`）—— 与 D3 的对照正是
「同一条用例的两个成因」。

**还原**：`cp` 回写 → `git hash-object` = `b9d076569f4447791654c8c010508d9c05585c8b` ✅

---

## §E D5 —— 删掉「读时补窗」

| 项 | 内容 |
|---|---|
| **破坏** | `security/RateLimitStore.java#retryAfterSeconds`：窗口缺失时不再补窗、直接判拒 |
| **它该改变哪条可观察行为** | 「计数已满但窗口丢了 ⇒ 补回整窗并放行本次」⇒ T5 必须红 |
| **备份 / 日志** | `/tmp/destruct/D3D4D5-RateLimitStore.bak` · `/tmp/destruct/D5.log` |
| **破坏前哈希** | `b9d076569f4447791654c8c010508d9c05585c8b` |

**实跑读数**：

```
[ERROR] Tests run: 9, Failures: 1, Errors: 0, Skipped: 0 -- in com.crm.security.RateLimitStoreTest
  T5 aFullCounterWithoutATtlIsReArmedAndAllowed
```

⚠️ **这条破坏只红 1 例、且落在单测上，是有意的**：补窗路径要构造「计数 ≥ 阈值**但没有 TTL**」的键，
而 HTTP 层构造不出这种中间态（真 Redis 的 `INCR` 每次都带 TTL）⇒ 它**只能**在单测里打。
「一条破坏只红一条用例」**不等于弱**，等于**这条分支只有一个入口**。

**还原**：`cp` 回写 → `git hash-object` = `b9d076569f4447791654c8c010508d9c05585c8b` ✅

---

## §F D6 —— 开放 API 分桶改用 `currentUserId()`（按密钥创建者分桶）⇒ **本条也跑出过一次「该红的没红」**

| 项 | 内容 |
|---|---|
| **破坏** | `security/RateLimitIdentity.java`：把 `API_KEY` 分支的主体从 `keyId` 换成 `currentUserId()` |
| **它该改变哪条可观察行为** | 「机器主体按**密钥**分桶」⇒ T7 必须红 |
| **备份 / 日志** | `/tmp/destruct/D6-RateLimitIdentity.bak` · `/tmp/destruct/D6.log`（第一次）· `D6b.log`（补测后复跑） |
| **破坏前哈希** | `3af0ecf396c9e7a58232bacf0b8bff8295799021` |

**第一次实跑：`RateLimitIdentityTest` 8 例里只红 1 例，且红的是**
`declaredDimensionsFallBackToTheIpBucketWhenTheSubjectIsMissing` —— 那条是「**人类主体**在显式 `API_KEY`
维度下退化成 IP 桶」的**间接**判据。**T7 照绿**，因为 T7 打的是 **`AUTO`** 分支，
而**生产端点上写的全是显式的 `by = RateLimitDimension.API_KEY`**（三个开放 API 端点）。
⇒ 「改 `API_KEY` 分支 ⇒ T7 红」这句话**当时是错的**：护栏只盖住了判据的一半。

**处置**（见 §Q ②）：补一条**直接**打在 `API_KEY` 分支上的用例
`theDeclaredApiKeyDimensionIsAlsoBucketedByKeyId`。加严后复跑：

```
[ERROR] Tests run: 9, Failures: 2, Errors: 0, Skipped: 0 -- in com.crm.security.RateLimitIdentityTest
  theDeclaredApiKeyDimensionIsAlsoBucketedByKeyId        <-- 新增的直接判据
  declaredDimensionsFallBackToTheIpBucketWhenTheSubjectIsMissing
```

**还原**：`cp` 回写 → `git hash-object` = `3af0ecf396c9e7a58232bacf0b8bff8295799021` ✅

---

## §G D7 —— `FormService` 的 429 改回 400

| 项 | 内容 |
|---|---|
| **破坏** | `service/FormService.java`：拒绝路径抛回 `ErrorCode.BAD_REQUEST` |
| **它该改变哪条可观察行为** | 「表单提交超限得 **429**」⇒ 行为层必须有一条用例红 |
| **备份 / 日志** | `/tmp/destruct/D7-FormService.bak` · `/tmp/destruct/D7.log` |
| **破坏前哈希** | `bb19c48c78271177698c5081258c7b399a1146fb` |

**实跑读数**：`RateLimitIT` **1 例红** —— `T15 formSubmitIsRateLimitedWithTheCorrectedStatus`。

⚠️ **这条破坏在 C4 期是「点不出名字」的**：当时的台账只写了「T 系列里的契约用例」，
而 T1–T4/T12/T14 全打**邮件追踪**端点（另一个 scope、另一条异常路径）。**T15 是为此补出来的**
（`tasks.md` §实做订正 20）—— 它的存在使这条破坏从「跑完全绿」变成「点名 1 例红」。
**这就是本件要求「每条破坏先写它该红哪条用例」的原因**：写不出名字 = 护栏只盖住了判据的一半。

**还原**：`cp` 回写 → `git hash-object` = `bb19c48c78271177698c5081258c7b399a1146fb` ✅

---

## §H D8 —— 阈值改成 1（**负对照**：证明「只断 429」是弱断言）

| 项 | 内容 |
|---|---|
| **破坏** | 把某端点的 `limit` 改成 `1`（**不碰存储、不碰切面、不碰窗口**） |
| **它该改变哪条可观察行为** | 「未达阈值时请求**成功**」⇒ 负对照（`exhaust()` 里前 N 次断 200）必须红 |
| **日志** | `/tmp/destruct/D8.log` |
| **还原** | 见下（还原后以 `spotless:check` 与 §N 的判据④复核） |

**实跑读数**（5 例红）：`T1 · T2 · T3 · T4 · T14`，其中 T1 的失败信息**正是负对照的形状**：

```
java.lang.AssertionError: Status expected:<200> but was:<429>
	at com.crm.integration.RateLimitIT.exhaust(RateLimitIT.java:101)
```

⚠️ **`expected:<200> but was:<429>` 这句话是本条留痕的全部价值**：一个**只会断 429** 的用例集在
「阈值被改小」时**照样全绿**（429 来得更早，它反而更满意）⇒ 负对照不是补充，是**另一半判据**。

---

## §I D9 —— fail-open 改成 fail-close（catch 后 rethrow）

| 项 | 内容 |
|---|---|
| **破坏** | `security/RateLimitStore.java`：`catch (DataAccessException)` 里改成 rethrow |
| **它该改变哪条可观察行为** | 「存储故障 ⇒ 放行」⇒ T4 必须红（且红成 **500** 而不是 429） |
| **备份 / 日志** | `/tmp/destruct/D3D4D5-RateLimitStore.bak` · `/tmp/destruct/D9.log` |
| **破坏前哈希** | `b9d076569f4447791654c8c010508d9c05585c8b` |

**实跑读数**：`RateLimitIT` **1 例红**，且**只红这一条**：

```
java.lang.AssertionError: Status expected:<200> but was:<500>
	at com.crm.integration.RateLimitIT.aStorageFailureIsFailOpenEvenWithAFullCounter(RateLimitIT.java:229)
```

⚠️ **红成 500 而不是 429 是关键的**：它顺带证明了「切面抛出的异常确实走 `GlobalExceptionHandler`」——
若异常处理没接线、掉进 catch-all 也会是 500，但 T1 在正常路径上钉着 429 ⇒ 两条合起来排除了那种可能。

**还原**：`cp` 回写 → `git hash-object` = `b9d076569f4447791654c8c010508d9c05585c8b` ✅

---

## §J D10 —— 给 `login` 挂上 `@RateLimit`（钉住用户裁决）

| 项 | 内容 |
|---|---|
| **破坏** | `controller/AuthController.java#login` 加上 `@RateLimit(scope = "d10-login", limit = 5, windowSeconds = 60, by = IP)` |
| **它该改变哪条可观察行为** | ① 连续失败登录应得 `INVALID_CREDENTIALS`(401) 而**不是** `RATE_LIMITED`(429)（T12）；② 登录**不该**产生任何 `rl:` 键（T13 的前置） |
| **备份 / 日志** | `/tmp/destruct/D10-AuthController.bak` · `/tmp/destruct/D10.log`（surefire）· `D10b.log`（failsafe） |
| **破坏前哈希** | `63a209b0a2800495584aacc2578faeea218bfd9b`（= `HEAD` 的 blob） |

**实跑读数**（**两次运行、三个红，逐条记**）：

① `RateLimitCoverageTest` **先红了一条，而且红的是台账自己的硬判据** ——
`everyExemptionEntryIsLiveAndNotAnnotated`：「某条豁免条目所指向的端点**已被注解覆盖** ⇒ 删掉该条目」。
`AuthController#login` 正在豁免白名单里（用户裁决：登录不加限流）⇒ **破坏一挂上注解，台账当场判红**。
**这条红不是 D10 的目标判据，但它是白名单「会自己过期」的活证据**（`tasks.md` §实做订正 23 的硬判据③）。
⚠️ 它同时**挡在了目标判据前面**：surefire 失败会**中止构建**，failsafe 根本没跑 ⇒ 必须屏蔽 surefire 复跑。

② `RateLimitIT` **2 例红**（复跑，用 `-Dtest=NoSuchUnitTestPlaceholder` 把 surefire 摘空）：

```
[ERROR] Tests run: 8, Failures: 2 -- in com.crm.integration.RateLimitIT
  T12 loginKeepsItsOwnLockoutAndNeverProducesRateLimitKeys
      org.opentest4j.AssertionFailedError: expected: 401  but was: 429
  T13 permissionIsCheckedBeforeTheQuotaIsConsumed
      java.lang.AssertionError: [未授权者不该产生任何限流键（整个 rl: 前缀），权限检查在限流之前]
      Expecting no elements of:
        ["auth:refresh:1", "rl:d10-login:ip:127.0.0.1", "auth:refresh:2"]
      to match given predicate but this element did: "rl:d10-login:ip:127.0.0.1"
```

⚠️ **T13 的红是「它的前置」被污染**（T13 自己的流程要先登录拿令牌，那一步就写了 `rl:d10-login:` 键），
**不是它主张的那件事出了问题** —— 与 §A 的 T4 同类，如实记下。
**T12 在 `expected: 401 but was: 429` 那一行上红，才是用户裁决「登录不加限流」被钉住的直接证据。**

**还原**：`cp` 回写 → `git hash-object` = `63a209b0a2800495584aacc2578faeea218bfd9b` ✅
（该文件**与本批 `HEAD` 逐字相同**，即 **spec 100 全程没有改过 `AuthController`** —— 与
`T041`–`T054` 的清单一致：登录侧一个字不动。）

---

## §K D11 —— 把 `@Order(10)` 从 `PermissionAspect` 摘掉（**顺序契约**）

| 项 | 内容 |
|---|---|
| **破坏** | `security/PermissionAspect.java`：删掉 `@Order(10)`（两支切面同用默认序 = 并列） |
| **它该改变哪条可观察行为** | 「权限先于限流：未授权者得 403 且**不消耗配额**」⇒ T13 必须红。**若仍绿则如实记为「顺序契约无护栏」的已知空档，不假装有** |
| **备份 / 日志** | `/tmp/destruct/D11-PermissionAspect.bak` · `/tmp/destruct/D11.log` |
| **破坏前哈希** | `6d440ac13d3c9982340aefbb3c667771c5eb51ab` |

**实跑读数：红了，且只红这一条** ——

```
[ERROR] Tests run: 8, Failures: 1 -- in com.crm.integration.RateLimitIT
  T13 permissionIsCheckedBeforeTheQuotaIsConsumed
      java.lang.AssertionError: [未授权者不该产生任何限流键（整个 rl: 前缀），权限检查在限流之前]
      Expecting no elements of:
        ["auth:refresh:1", "auth:refresh:2", "rl:export-generate:user:2"]
      to match given predicate but this element did: "rl:export-generate:user:2"
```

⇒ **`plan.md` 里那条「顺序契约无护栏」的风险，在本批之后不再成立**：T13 就是它的护栏
（`rl:export-generate:user:2` 这个键出现在 **403** 路径上 = 配额被**先**消耗掉了）。
**没有空档要登记** —— 但这条结论**只在 D11 实跑过之后才敢写**。

**还原**：`cp` 回写 → `git hash-object` = `6d440ac13d3c9982340aefbb3c667771c5eb51ab` ✅

---

## §L 门禁实跑读数（**交付批**，最终树）

**命令**：`cd backend && mvn -B -o verify` —— **未传 `-DargLine`**（传了会静默废掉 JaCoCo，见本仓先例）；
`-o` 是离线，避免网络抖动被读成代码问题。

**树的状态**：**冻结的交付树** —— 最后一次 Java 编辑是 `EmailTrackController` 那段 javadoc 折行，
此后 `mvn -B spotless:apply` 报 **794 文件 clean**；再之后的改动**只在 markdown**
（本文件、`tasks.md`、`specs/roadmap.md`、`specs/README.md`、`README.md`、`PROJECT_FEATURES.md`），
**不进编译产物**，故不影响本节的任何读数。

| 项 | 读数 |
|---|---|
| 退出码 / 结论 | **0 / BUILD SUCCESS** |
| surefire | `Tests run: 733, Failures: 0, Errors: 0, Skipped: 0` |
| failsafe | `Tests run: 333, Failures: 0, Errors: 0, Skipped: 0` |
| 失败集合 | **∅** ⇒ **⊆ 4 例已批准偏差** ✅（**那 4 例本次一例也没红**，比判据更严） |
| `jacoco:check` | 打印 **「All coverage checks have been met.」** ✅ —— **这一行才是成功判据**（不是「没搜到失败」） |
| `target/jacoco.exec` | **存在**，**80,070,629** 字节，mtime **00:36:45** ⇒ 属**本次** run，不是上次残留 |
| JaCoCo 三项 | INSTRUCTION **0.8140**（48139/59139）· BRANCH **0.6288** · LINE **0.8306**（阈值 **0.73**、粒度 BUNDLE，**均未改**） |
| run 时间戳 | `Total time: 02:19 min` · `Finished at: 2026-09-17T00:36:49+08:00` |
| 前端 | `git status --porcelain frontend/` = **0 行** ⇒ **零前端改动**，故**不跑**前端门禁（本批不动 `frontend/` 任何文件） |

**本批新增的 6 个测试文件 / 42 条用例逐类全绿**：
`RateLimitIT` **8/8**（行为层，本项唯一的真回归证据）· `RateLimitStoreTest` **9/9** ·
`RateLimiterShapeTest` **7/7** · `RateLimitIdentityTest` **9/9** · `ClientIpResolverTest` **5/5** ·
`RateLimitCoverageTest` **4/4**（字节码台账，即 §5 ⑤）。

**零改动的那两个类也仍绿**（**它们零改动就是验收的一部分**，见 `quickstart.md` §1.1）：
`FormIT` **1/1** · `LandingPageIT` **2/2** ⇒ 事实 ⑬ 那颗 3/3 的雷**没有**在交付树上演成假红。

⚠️ **一次同树实跑的抖动如实记（不归因、不掩盖）**：紧邻的另一次实跑，代码只差 `EmailTrackController`
那段 javadoc 折行（**纯注释、字节码指令集未变**），读到 INSTRUCTION **0.8132** / BRANCH **0.6286** /
LINE **0.8296**。本批**取交付时那次的读数**（上表），**不声称哪次更准、也不把差异归因于任何一次改动** ——
**原因未查明**；同类现象在本仓已有先例（同一棵树、同一条命令两次跑，Branch 差 0.01）。
⇒ 读这两组数字时，**第三位小数的差属噪声**；判据（INSTRUCTION ≥ 0.73）在两次里都成立。

---

## §M 只读冒烟（**隔离实例**，不碰共享开发库）

⚠️ **8081 上跑的是改动前的旧实例**（本会话未重启它 —— 仓规禁止重启可能归并行会话所有的进程）
⇒ 照 `quickstart.md` §4 起**隔离实例**，**并写明端口**。

| 项 | 值 |
|---|---|
| 端口 / schema / Redis db | **8099** / `crm_rl100`（独立 schema）/ **db 5** |
| 端点 | `GET /api/v1/public/forms/1/meta`（**P1 的 `public-read` 组**：只读、无副作用、无需凭证） |
| 样本行 | 在**隔离库**里插一行 `form`（`name='RL100 smoke form'`、`status='ENABLED'`、`fields='[]'`） |
| 请求头 | `X-Forwarded-For: 203.0.113.7`（**显式给独立 IP**，不依赖 `remoteAddr` —— 见 `quickstart.md` §2 的硬规则） |

**实跑读数（逐条）**：

```
# ① 连打 61 次（第 1 次已在上一步消耗）—— 60 放行、第 61 次起拒绝
200 ×59 … 429 429          # 计数到 60 之前全是 200，第 61 次起 429

# ② 429 的响应头与响应体
HTTP/1.1 429
Retry-After: 53
Content-Type: application/json
{"success":false,"error":{"code":"RATE_LIMITED","message":"请求过于频繁，请稍后再试"}}

# ③ 存储侧正对照（真 Redis，不是替身）
redis-cli -n 5 keys 'rl:*'   ->  rl:public-read:ip:203.0.113.7
redis-cli -n 5 ttl  <该键>    ->  55 / 53（随时间递减；窗口过期后重建为 60）

# ④ 不同 IP 不受影响（桶是独立的）
X-Forwarded-For: 198.51.100.9  ->  200

# ⑤ 窗口到期后同一 IP 恢复放行
（等过 `Retry-After`）X-Forwarded-For: 203.0.113.7  ->  200，且新键 TTL = 60
```

**判据逐条对照**（`quickstart.md` §3）：前 N 次 **200** ✅ · 第 N+1 次起 **429** ✅ ·
`Retry-After` **存在且 ≤ 窗口秒数**（53 ≤ 60）✅ · 响应体是**统一的 `ApiResponse` 信封**
（`success` / `error.code` / `error.message`）而**不是** Security 的空体 401 那一套 ✅。

⚠️ **本项零前端改动 ⇒ 没有「一眼可见的界面」可看**，冒烟只能看 HTTP 响应；这一条是**边界**，不是省略。
⚠️ **冒烟打的是带本批改动的新实例**：`public-read` 这个 scope 是 C6 才加的
（`FormController#meta` 的注解），旧实例上不可能返回 429。

**收尾（三项都做，并核对共享库未动）**：

```
wsl -e mysql -uroot -p123456 -e "DROP DATABASE crm_rl100;"     -> 已删（SHOW DATABASES 只剩 crm_db）
wsl -e mysql -uroot -p123456 -e "REVOKE ALL ON crm_rl100.* FROM 'crm_user'@'localhost'; FLUSH PRIVILEGES;"
                                                               -> 授权回到原样（只有 crm_db.*）
redis-cli -n 5 FLUSHDB                                         -> OK（dbsize 0；**db 0 未动**）
核对共享库：crm_db.form 里 'RL100%' 命中 0 行（该表原有 2 行）      -> 隔离实例一行都没写进共享库
8081 存活：/actuator/health -> 200                              -> 本会话未重启它
```

⚠️ **一处与 `quickstart.md` 配方的偏差如实记**：配方写 `GRANT ... TO 'crm_user'@'%'`，**实跑失败**
（`ERROR 1410 (42000): You are not allowed to create a user with GRANT` —— 本机 `crm_user` 的 host 是
**`localhost` 而不是 `%`**）⇒ 实际用的是 `'crm_user'@'localhost'`。**判据不变**（独立 schema 上的独立授权），
只是主机名照实写。

---

## §N 可核判据（`quickstart.md` §5）实跑

**全部命令按 `quickstart.md` §5 原文跑**（含那两条「排除注释行」的 filter 与 ③ 的负断言许可）。

| # | §5 的命令 | 期望 | 实跑读数 |
|---|---|---|---|
| ① | `grep -rn "X-Forwarded-For" backend/src/main/java/com/crm/ \| grep -v ClientIpResolver` | 只剩 `AuthService` 的委托与注释 | **过滤后 2 行，两行都是注释**（`AuthService:203`、`:210`）；**未过滤 7 行 = 1 处可执行**（`ClientIpResolver:77` 的 `getHeader`）**+ 6 行注释**（`EmailTrackController` 1 · `ClientIpResolver` 2 · `RateLimitDimension` 1 · `AuthService` 2） |
| ② | 主代码 `rateBuckets\|cleanupRateBuckets`（排注释行） | 零命中 | **0** ✅（两处内存桶确已消失） |
| ② | 测试侧 `rateBuckets\|cleanupRateBuckets\|提交过于频繁`（排注释行） | 零命中（**改造前**的口径） | **1 行**：`RateLimitIT.java:263` 的 `.doesNotContain("提交过于频繁")` —— 它是 §5 末尾**已许可的负断言**（「旧值已消失」的正向证据），**不是残留** |
| ③ | `TOO_MANY_REQUESTS`（`backend/src` + `frontend/src`，排注释行） | 主代码与前端零命中 | **主代码 0 · 前端 0**；`src/test` 只剩 `RateLimitIT.java:145`（负断言的**说明行**）与 `:147`（`.doesNotContain("TOO_MANY_REQUESTS")`）—— 同一条已许可的负断言 ✅ |
| ③ | `RateLimitedException`（排注释行） | 零命中 | **0** ✅（控制器私有内部类与其处理器同批删除） |
| ④ | `grep -n "public-form-submit\|public-email-track" -A3`（两个文件） | 3/60s 与 60/60s 逐字未变 | `FormService:283` 委托传的是 **`RATE_LIMIT` / `RATE_WINDOW_SECONDS`**；`EmailTrackController:63-66` 与 `:78-81` 两处都是 **`limit = 60` / `windowSeconds = 60`** ✅ |
| ⑤ | `mvn -B test -Dtest=RateLimitCoverageTest` | 台账绿 | **`Tests run: 4, Failures: 0, Errors: 0, Skipped: 0`** ✅（含 T10 违规扫描与 T11「扫描确实扫到了端点」的自检）—— ⚠️ **该读数取自交付时那次完整 `mvn -B verify`**（它包含这个类），**没有**再单独跑一次这条命令：单独跑 `mvn test` 会让 JaCoCo 代理**重写/追加 `target/jacoco.exec`**（只含 surefire 的部分数据），把 §L 记下的交付态读数（**80,070,629** 字节 / mtime `00:36:45`）变成另一个数 —— **同一个测试类、同一批用例，不为一个等价读数去污染覆盖率产物** |

⚠️ **④ 的读数要补一句**（判据不是零命中，故不是缺口，只是命令捞不到）：§5 那条 `grep` 按 **scope 串**
捞，捞到的是**注解与委托**；两个配额的**常量本体**在 `FormService:49`（`RATE_LIMIT = 3`）与 `:55`
（`RATE_WINDOW_SECONDS = 60L`），**不在该命令的输出里**——此处另行核对过：**3 / 60s 与改造前逐字相同**，
且改造前的 `RATE_WINDOW_MS = 60_000L` **逐字留在这两个常量的 javadoc 里**（`FormService:52`，可 grep 到）。
⇒ 「配额不得顺手改数字」这条判据**成立**，取证方式是「常量本体 + 旧值留痕」，不是 §5 那条 grep。

---

## §O 订正不静默自查（`quickstart.md` §6 五条）命中数

| # | 要 grep 的旧值 | 期望 | 实跑命中 |
|---|---|---|---|
| ① | `仅 1 处` / ``仅 `EmailTrackController` `` / `仍需过滤器层统一限流`（`CRM_FEATURE_COMPARISON.md`） | 非零 | **2** ✅ |
| ② | `均无限流`（`CRM_FEATURE_COMPARISON.md`） | 非零 | **1** ✅ |
| ③ | `001~099` / `001–099` / `98 个功能模块`（`README.md` `specs/README.md` `specs/roadmap.md`） | 非零 | **README.md 2 · specs/README.md 0 · specs/roadmap.md 2** ✅（命令整体非零） |
| ④ | `全仓首个 429`（`common/ErrorCode.java`） | 非零 | **1** ✅ |
| ⑤ | `此处直接降级为 400 保持公开端点简单`（`controller/EmailTrackController.java`） | 非零 | **1** ✅（**第一次实跑是 0 —— 见 §Q ③**） |

⚠️ ③ 里 `specs/README.md` 单文件为 **0**：该文件**从来不写「N 个功能模块」这种句子**（它用「编号说明」
＋逐行模块表）⇒ 它的非零是**命令层面**的（三个文件里两个命中、命中数 ≥ 2）。
**不把单文件的 0 读成静默改写**；该文件本批要动的只有 100 行的**状态列**（交付态），
那是**状态**不是**数字**，故不产生需要留痕的旧值。
（本批改到 `specs/README.md` 的**另一处**是 100 行里那段 multi-catch 引文里的裸 `|` —— 它在表行里
**多切出一格**，已按本仓既有约定转义成 `\|`（全文 208 处同款）；这是**排版**修正，不动任何旧值。）

### 交付时复跑（2026-09-17，**全部登记类文档定稿之后**）

| # | 命中 | 与上表相比 |
|---|---|---|
| ① | **2** | 不变 ✅ |
| ② | **1** | 不变 ✅ |
| ③ | README.md **2** · specs/README.md **0** · specs/roadmap.md **2** | 不变 ✅ |
| ④ | **1** | 不变 ✅ |
| ⑤ | **1** | 不变 ✅ |

**五条全部非零** ⇒ 旧值一条都没被静默改写（**一条都没消失在 0 里**）。

⚠️ **计数口径写明**：上表是**匹配行数**（`grep -c`，与命令里的 `grep -n` 同一个量），
**不是出现次数**。两条口径都非零、且都记下以免误读：① 按**出现次数**是 **8**
（`仅 1 处`×4 · 仅 `` `EmailTrackController` ``×2 · `仍需过滤器层统一限流`×2）；③ 的 `specs/roadmap.md`
按出现次数是 **8**（`001–099`×8，全部落在**两条**行里）。**没有一条依赖「某个口径恰好非零」。**

---

## §P 四条如实登记的边界

1. **默认测试基类下限流是 no-op**（本批最大的假绿陷阱）。见 §0：`@MockBean RedisTemplate` + 裸 mock
   `opsForValue()` ⇒ `increment` 返回 `null` ⇒ fail-open ⇒ 限流不生效。**这不是缺陷**，是测试替身的既定形态；
   补救只有三条硬规则。⇒ **本项的门禁绿，不得被读成「限流在所有 IT 里都被验证过」**——
   被验证的是**装了替身的那一个类**（`RateLimitIT`）＋ **五个单测类**。
2. **`FormIT` / `LandingPageIT` 那颗 3/3 的雷的处置后果**：改到 Redis 后，那两个类在**默认基类**下限流
   **变成 no-op**。这**不是**弄丢护栏 —— 那颗雷今天**不是护栏而是跨用例共享状态**
   （`FormService.rateBuckets` 是单例 bean 的实例字段，`clearInProcessCaches()` **不清它**，
   `FormIT` 2 次 + `LandingPageIT` 1 次全来自默认 `remoteAddr=127.0.0.1` 而阈值恰为 3），
   且限流在 `src/test` 里改前**零用例**（`rateBuckets` / `RATE_LIMIT` / `提交过于频繁` 0 命中）。
   **净收益为正**，但**必须显式登记**，免得后人以为「IT 里限流一直生效」。
3. **XFF 首值可伪造 ⇒ 按 IP 分桶的匿名端点可被绕过**（`FR-039`）。本项**不修**、**也不声称**这些端点能抗敌手：
   能直连后端端口的人可以伪造 XFF。**本批给 `/public/**` 的多个匿名端点加了 IP 桶 ⇒ 这个洞是被「扩大」而
   不是被「绕开」**。验收口径**已降级**为「**误用与意外的阻尼**」（防一个死循环的前端把工单表灌满、
   防一个预取器把落地页打成 DB 热点），**不是抗敌手**；**不得**把限流宣传成攻击防护。
4. **线上（多实例）行为未验**：本项的 Redis 化**正是为多实例而做**，但**本机只跑一个实例**
   ⇒ 「阈值不再 ×N」这条**只有推理、没有实测**，**不得声称已验**。
   （§M 的冒烟打的是**单实例**；它验的是「真 Redis + 真 Tomcat」这一层。）

---

## §Q 本批（C6）自查出的三个缺口与处置

① **T3 是空断言**（§C）：D3 全绿暴露。「首次才设窗」与「每次都续窗」在**单一冻结瞬间**下不可分
   ⇒ 补一段**窗口中途**的流量。**判据被加严、没有被放宽**，且加严后**重跑 D3 才拿到红**。
② **`API_KEY` 维度没有直接判据**（§F）：D6 只让一条**间接**判据变红，而**生产端点写的全是显式 `API_KEY`**。
   ⇒ 补 `theDeclaredApiKeyDimensionIsAlsoBucketedByKeyId`（与 T7 同一主张、打在另一个分支上）。
③ **§6 ⑤ 的判据第一次实跑是 0**：不是静默改写 —— 原文**逐字保留着**，但它被 spotless 的 javadoc 折行
   **断成了两行**（「…降级为 400」/「保持公开端点简单…」），而行式 grep **匹配不到跨行的字面量**。
   ⇒ 处置是**改排版而不是改判据**：把引用的原文放在**它自己的一行**上（用 `<br>` 固定断点），
   并跑 `spotless:apply` 确认格式器**不会**再把它折回去（**794 文件 clean**），使**判据按字面成立**。
   ⚠️ **没有采用「登记为口径缺口」那条路**：那条路会让判据在任何正确实现下都不可满足；
   而这一条**两者可以同时满足**，只是需要把原文字面放在一行里
   （与 `quickstart.md` C3/C4 那两条 ⚠️ 的性质不同 —— 那两条是**判据与留痕规则互斥**，只能登记）。

---

---

## §S 数字落点一致自查（T052，**逐处点名**）

> 依据 `tasks.md` T052：**「逐处点名，不写『若干处』」**。本节把每一处**写出来**，并给出交付时读数。
> 计数口径**两把尺子**（下面 B 段说明），**没有一个数字同时住在两处**。

### A. Spec 模块数（**本项改了它**：四处在册 + 一处结构性不适用）

| 落点 | 交付时读数 | 判据 / 备注 |
|---|---|---|
| `ls -d specs/[0-9]* \| wc -l`（**权威**） | **99** | 编号面 001–100、**缺 069** |
| `README.md` 目录树 | `99 个功能模块，001~100，缺 069` | 与上行同值；旧值 `98 个功能模块，001~099，缺 069` **逐字留在同段 ⚠️ 块里**（§6 ③ 命中） |
| `PROJECT_FEATURES.md` Spec 模块行 | `**99 个（001–100，缺 069）**` | 同上；旧值 `98（001–099）` 逐字保留 |
| `specs/roadmap.md` 第 6 行「编号面判据」 | `由 **98** 变 **99**` | 交付态断言改为「100%（001–100 全部交付）」；勾选数 99 / 0 |
| `specs/README.md` 编号说明 | **无此数字** | 该文件**从不写「N 个功能模块」**（它说的是**编号空缺**与逐行模块表）⇒ **结构性不适用**，**不是漏改** |

### B. 用例 / 测试文件数（**本项改了它**：两处落点，注意是**两把尺子**）

| 落点 | 交付时读数 |
|---|---|
| `mvn -B -o verify` 的**实跑打印**（**用例**数，权威） | surefire **733** · failsafe **333**（Failures / Errors / Skipped **全 0**） |
| `PROJECT_FEATURES.md` 后端测试行（**类 / 文件**数） | **188 个含用例的类**（`src/test` 共 **194** 个 `.java`）；复算 `grep -rlE "@Test\|@ParameterizedTest" backend/src/test/java \| wc -l` → **188**；旧值 `182 / 188` **逐字保留在同行的 ⚠️ 段里** |

⚠️ **两把尺子（照 `PROJECT_FEATURES.md` 的同名口径）**：前半数的是「文件里出现过 `@Test`/`@ParameterizedTest`」，
后半数的是**文件数**。本项**两者同增 6**（新增的 6 个文件每一个都带用例）⇒ 两半一起动。
⚠️ **项目里不存在「用例总数」这种聚合数**（`README.md`、`specs/README.md`、`specs/roadmap.md` 都**没有**）
⇒ 本项**没有**「一处改了、另一处还是旧值」的风险；**唯一的同值对**是 A 段的四处在册落点，交付时**逐字相等**。
⚠️ **历史读数不回填**：别处那些「当时交付」的读数（如 099 行的覆盖率 `0.8109`、082 行的 surefire `695`）
是**它们各自交付时**的实测，**本项不改写、不回填** —— 回填等于用一次订正造出两处新矛盾。

---

## §R 各节由哪次实跑填

| 节 | 来源 |
|---|---|
| §0 · §P | 立项期勘察事实 + `quickstart.md` §2/§3 的既定口径（**非**本次新增读数） |
| §A–§K | `D1`–`D11` 逐条定向破坏（日志 `/tmp/destruct/D{1..11}.log`；`D3b`/`D6b` 为加严后复跑） |
| §L · §N | 交付批的完整 `mvn -B verify` 与 `quickstart.md` §5 逐条实跑 |
| §M | 隔离实例（8099 / `crm_rl100` / Redis db 5）的只读冒烟 |
| §O | `quickstart.md` §6 五条 grep |
| §Q | C6 实做期自身查出的三个缺口 |
| §S | **T052 的数字落点自查**（交付时逐处点名；A 段模块数四处在册 + B 段两把尺子） |
