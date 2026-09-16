# 100-rate-limit-consolidation：全局限流收口

**Created**: 2026-09-16
**状态**: 实施中（立项阶段先登记，**勾选框不预勾**）
**形制**: **横切收口类**——**不加迁移、不加 DTO、不改权限矩阵、不产 `contracts/`、不产 `data-model.md`**。
生产改动 = **7 个新类 + 1 个新错误码 + 1 个新异常**（`com.crm.security` / `com.crm.common`）+ **2 处既有内存桶改委托**
（`EmailTrackController`、`FormService`）+ **一批 `@RateLimit` 标注** + **1 个字节码台账测试**。
**前端零改动**（含不新增 i18n 键）。
**上游**: `CRM_FEATURE_COMPARISON.md` 的 **P0 第 5 条「全局限流缺失（仅 1 处）」**（`:370`）与其 **2.9 速率限制行**（`:276`）。
本项**推翻**这两行的事实陈述、**采纳**其意图，**不采纳**其字面机制（见 §1.1）。
**用户裁决（2026-09-16）**: 「**补空档 + 收敛既有 2 处重复实现**」；登录侧「**不加，只订正文档**」。

---

## 1 由来

`CRM_FEATURE_COMPARISON.md:370` 把限流列为 **P0**（「阻断企业级采购 / 合规红线」），事实陈述是
「**全局限流缺失（仅 1 处）**」、动作列写「仍需**过滤器层**统一限流：登录、开放 API、导出」。
立项勘察**逐条读了实现**，两处陈述都站不住，而**真实的空档位置与它指的地方不同**。

### 1.1 文档那两行错在哪（本项要订正的对象）

**① 「仅 1 处」为假 —— 实测是 4 个宿主类里 6 处计数点、零共享件。**

| # | 宿主 | 介质 | 键 | 窗口 | 阈值 | 超限时 | 失败立场 |
|---|---|---|---|---|---|---|---|
| 1 | `EmailTrackController`（打开像素 / 点击重定向） | 进程内 `ConcurrentHashMap<IP, Deque<Long>>` | IP 字面量 | 60s | 60 | 私有内部类 `RateLimitedException` → **429** | — |
| 2 | `FormService.submit` | **同款（逐字复制）** | IP / `"unknown"` | 60s | 3 | `BusinessException(BAD_REQUEST)` → **400** | — |
| 3 | `AuthService.recordFailure` | Redis `auth:fail:` | username | 15min，**每次失败续整窗** | 5 | `UNAUTHORIZED` → **401** | fail-open |
| 4 | `AuthService.recordIpFailure` | Redis `auth:ip-fail:` | IP | 15min，同上 | 10 | `UNAUTHORIZED` → **401** | fail-open |
| 5 | `MfaStateStore.recordFailure` | Redis `auth:2fa-fail:` | userId | 900s，**恰好达阈值那次才 expire** | 5 | `MFA_LOCKED` → **429** | **fail-close** |
| 6 | `MfaStateStore.markTimeStepUsed` | Redis `auth:2fa-used:` | userId+时间步 | — | 1（`SET NX`） | false → 401 | **fail-close** |

三套状态码并存（**429 / 400 / 401**）、两种介质（内存 / Redis）、两种失败立场、阈值互不相干，
且 #1 与 #2 的 `checkRateLimit` + `clientIp` + 清理逻辑是**逐字复制**的两份。
⇒ 准确表述是「**缺失统一收口件**」，不是「只有 1 处限流」。

**② 「登录…均无限流」为假 —— 登录是全仓防护最厚的端点，共三层。**
① 017 的图形验证码**始终要求**（不是失败触发，`specs/017-login-captcha/spec.md:58`「每次登录都必须携带有效验证码」）；
② 用户名维度 5 次 / 15 分钟（表 #3）；③ IP 维度 10 次 / 15 分钟（表 #4）。
该行把「没有**过滤器层速率限制**」写成了「登录**没有防护**」。

### 1.2 真实的空档在别处（本项要补的）

- **`/api/v1/open/**`**（055 开放平台）：机器客户端、三个端点、**此前零限制**。
- **13 个导出面**：**全部**零限制，且**全部先物化进堆**（`XSSFWorkbook` + `ByteArrayOutputStream` /
  `Files.readAllBytes`；全仓 `SXSSF`/`EasyExcel` **零命中**）。
- **`/api/v1/public/**` 下除表单提交与邮件追踪之外的每一个端点**，其中两个最重：
  `POST /public/portal/tickets/status`（**全仓唯一的匿名凭证校验端点**：工单号 + 手机/邮箱双验证 ⇒ 枚举面）
  与 `POST /public/portal/tickets`（匿名建单，有写副作用）。

### 1.3 四条决定形状的实测事实

**① 本仓「按端点声明横切策略」的既有范式是注解 + AOP，不是过滤器。**
`security/PermissionAspect` 的 `@Before("@annotation(requirePermission)")` + `security/RequirePermission`
（由 `config/SecurityConfig` 的 `@EnableMethodSecurity` 打开）是全仓唯一的一处；
`spring-boot-starter-aop` 已在 `pom.xml`；全仓 `@Order` / `FilterRegistrationBean` / `HandlerInterceptor` /
`WebMvcConfigurer` **零命中**。`RequirePermission` 的类 javadoc 明写「方向是让本注解成为**唯一闸门**」。

**② 过滤器方案在本仓有一条**没有任何护栏**的顺序契约。**
`config/LoggingFilter` 是裸 `@Component`、注册在 Security 链**之外**，其 `SecurityContextHolder` 为空；
要靠过滤器按登录用户分桶，**必须**插进链内且排在鉴权**之后**——而 `SecurityConfig` 里的既有写法是
`addFilterBefore(..., UsernamePasswordAuthenticationFilter.class)`。照抄这个形状再挂一个，会落在鉴权**之前**，
于是 `SecurityUtil.currentUserId()` 恒 `null` ⇒ **所有已认证用户共用一个匿名桶**，
限流被静默降级成无效，而 78 个 IT **照样全绿**（它们的令牌都是 admin，看不出桶被合并）。

**③ 全部导出都是非流式 ⇒ 「响应已提交、改不了状态码」这个过滤器唯一的结构性优势在本批不存在。**
`@Before` 切面在方法体**之前**抛异常，对 13 个导出面全都有效。

**④ 机器主体的 `userId` 是密钥创建者 ⇒ 开放 API 必须按 keyId 分桶。**
`ApiKeyAuthFilter` 注入的 `CrmPrincipal` 是 `new CrmPrincipal(key.getCreatedBy(), "open-api", "OPEN_API", true)`，
密钥 id 另放在 `authentication.getDetails()` 的 `ApiKeyPrincipal(keyId, name)` 里。
按 `currentUserId()` 分桶 ⇒ **同一个管理员建的多个密钥共用一个桶**（一个密钥打满，其余全被拒）。

### 1.4 两条与「收口」直接冲突的既有事实

**① `MfaStateStore` 的全局 javadoc 是本仓对 fail-open / fail-close 的明文论述**，
且它**明确说 fail-open 对限流是合理的**：「它的代价是『限流暂时失效』，而不放行会让整个系统在 Redis 抖动时无法登录」。
它自己的 fail-close 是**为 2FA 语义**（`catch { return null; }` = 跳过二次验证）而立的 ⇒ **不可移植到限流**。

**② `FormService` 违反自己的冻结契约。**
`specs/036-online-forms/contracts/online-forms.md:44` 与 `tasks.md:50` 都承诺 **429**，
实现抛 `ErrorCode.BAD_REQUEST` ⇒ **400**。同类缺陷的处置范式已由 085 确立
（`GlobalExceptionHandler` 的注释即判例：**契约是对的，改的是实现，契约一个字符不改**）。

---

## 2 用户故事

### US1 新端点不会再「忘了设防，而且没人发现」（P1）

**角色**：往这个仓里加匿名端点或导出端点的人。**诉求**：漏设限流这件事**要变红**，而不是靠人记得。

**为什么**：注解是 **opt-in** 的——「忘了标注 = 无限流」，且**没有任何用例会红**。这是注解方案相对过滤器的
**唯一**结构性弱点，必须显式补上，否则本项只是换了个地方写同样的隐患。
**验收**：① 一个字节码台账测试枚举**全部控制器端点**，每个必须「有 `@RateLimit`」**或**「在豁免白名单里且白名单条目带**非空理由**」；
② 白名单**先只放本项确认不设防的端点**（粒度是「**类#方法**」，不是 URI 前缀）；③ 该测试带**自检**——
先断言「扫描确实扫到了 N 个端点」（N>0），否则扫描 pattern 写错时「零违规」会**假绿**。

### US2 限流只有一份实现，且它的时间语义是对的（P1）

**角色**：下一个改限流的人。**诉求**：一处改、处处生效；且窗口语义不要踩仓里已有的两个相反范式。

**为什么**：今天有两份逐字复制的内存桶（§1.1 表 #1/#2），必然分叉且**没有门禁看着**。
而 Redis 计数的两个既有范式**体例相反**：`MfaStateStore.recordFailure` 是「**恰好**达阈值那次才 `expire`」，
`AuthService.recordFailure` 是 `increment` 后**无条件** `expire`（每次失败续整窗）。
后者对**登录失败计数**是有意的，但对**请求限流**是**错的**：一个稳定 4 次/分钟的客户端会让窗口
永远续不上前，于是「3 次 / 60 秒」事实上变成「每 60 秒只准错一次，错一次就再也不许过」。
**验收**：① 两处内存桶只留调用点、方法体改委托，**阈值与窗口逐字不变**（60/60s 与 3/60s）；
② 新件照 `MfaStateStore` 的范式（**仅 `count == 1` 时设 TTL**）；
③ 定向破坏：把 TTL 改成「每次都续」，**窗口过期后应当恢复放行的用例必须变红**。

### US3 限流件自身不会变成新的故障源（P1）

**角色**：运维。**诉求**：Redis 抖动时**限流失效**可接受，**整站不可用**不可接受；且「永久 429」这种
自己造出来的锁不要出现。

**为什么**：本仓对限流已有明文立场（§1.4 ①，fail-open）。而 Redis 计数有一个**真实的洞**：
`INCR` 与 `EXPIRE` 是**两次**调用，中间断掉会留下「计数是满的、但**没有 TTL**」的键——
而 `INCR` 会一直让它满着 ⇒ 该主体**永久 429**，重启进程无效（键在 Redis 里），
症状是「这个人怎么都不行」，排查方向整个跑偏。`MfaStateStore.lockRemainingSeconds` 已经为 MFA 处理过这个洞，
但它的处置是「**补回整窗并继续锁**」——**限流必须相反**：**补回整窗并放行**。
**验收**：① 存储故障 ⇒ 请求**放行**（不是 500、不是 429），且**有一条 warn 日志**；
② 「计数满 + 无 TTL」⇒ 补窗**且放行**（定向破坏：删掉补窗，该用例必须红）；
③ 只 catch `RedisConnectionFailureException | DataAccessException`，**不 catch 裸 `Exception`**
（那会把「键构造写错」这类真 bug 也降级成静默放行）。

---

## 3 功能需求

### 3.1 声明面（注解）

- **FR-001** 新增 `com.crm.security.RateLimit`（`@Target(METHOD)`、`@Retention(RUNTIME)`），
  属性 `String scope()` / `int limit()` / `long windowSeconds()` / `RateLimitDimension by() default AUTO`。
  **`scope` 是显式字符串常量，不从 URI 推导**：改路径会让配额**悄悄换桶**（等于配额清零），
  而**没有任何测试会红**；显式常量可被台账测试逐字核对。
- **FR-002** 新增 `com.crm.security.RateLimitDimension { AUTO, IP, USER, API_KEY }`。
- **FR-003** `AUTO` 的判定顺序（**纯静态函数，可脱 Spring 单测**）：机器主体 ⇒ `key:<keyId>`；
  否则已认证 ⇒ `user:<userId>`；否则 ⇒ `ip:<resolved>`。
  **这把「开放 API 必须按 keyId 分桶」变成默认行为**（§1.3 ④），而不是每个端点要记得写对的属性。
- **FR-004** ⚠️ **显式声明 `USER` / `API_KEY` 而主体缺失时，回退到 `ip:<resolved>` 并 `log.warn`**（**不静默放行**）。
  三个候选处置都记进 `research.md`：回退（取）、静默放行（`USER` 写成无名端点 ⇒ 限流静默失效，**不取**）、
  抛 401（会把合法的匿名请求打成 401，**不取**）。取回退是因为它**方向安全**（仍然限住了）且**可观测**。

### 3.2 存储与时间语义

- **FR-005** 新增 `com.crm.security.RateLimiter`（`@Component`），依赖**只有 `RedisTemplate`**（**不依赖 `Clock`**：
  窗口是 Redis 自己的 TTL，不是应用算出来的）。
- **FR-006** **键族 `rl:<scope>:<identity>`**（例 `rl:export-generate:user:42`、`rl:open-api-read:key:7`），
  与 `auth:*` 同级前缀，便于运维按前缀注入故障与监控（`InMemoryRedisTestSupport.failOnKeyPrefix("rl:")` 正好可用）。
  键构造用具名静态方法，**供调用形状断言逐字核对键名**（照 `MfaStateStore` 的 `failKey` 那组先例）。
- **FR-007** 计数用 `opsForValue().increment(key)`，**仅当返回值为 `1` 时**才 `expire(key, window)`
  （照 `MfaStateStore.recordFailure` 的范式，**不照** `AuthService.recordFailure` 的无条件续窗）。
- **FR-008** ⚠️ **读时补窗并放行**：`count >= limit` 时先读 `getExpire(key, MILLISECONDS)`；
  若「无 TTL」(`-1`) 或「键不存在」(`-2`)，则**补回整窗**（`expire`）并 **`log.warn` 后放行**。
  **与 `MfaStateStore.lockRemainingSeconds` 刻意相反**（它补窗后继续锁，因为它锁的是一个人的一个流程；
  限流是可用性敏感的旁路控制，**多放一次的代价远小于一个永久 429 的客户端**）。这条必须在 javadoc 里写明对比。
- **FR-009** **不用 Lua / 不increment+expire 原子化**：功能替身刻意不实现管道/事务/Lua，
  而裸 mock 下 `execute(script)` 返回 `null` ⇒ 会开出**又一条静默 no-op 的假绿通道**。
  本批的原子性需求远弱于 MFA（最坏是多放一次），用「条件 `expire` + 读时补窗」换掉。

### 3.3 失败立场

- **FR-010** **fail-open**：Redis 异常 ⇒ `log.warn` 一条 ⇒ **放行**。依据是 `MfaStateStore` 的类 javadoc
  （§1.4 ①），且**必须在 `RateLimiter` 的 javadoc 里显式写明**这是照它、以及为什么 2FA 的 fail-close 不可移植。
- **FR-011** ⚠️ **只 catch `RedisConnectionFailureException | DataAccessException`，不 catch 裸 `Exception`**。
  理由写进代码注释：裸 `Exception` 会把「键构造写错」「类型不对」这类**真 bug** 也降级成静默放行，
  而它们的症状会变成「限流时有时无」——一种极难归因的形态。
- **FR-012** **不提供 `fail-open=false` 旋钮**（那等于给运维一个「Redis 一抖全站导出 503」的自伤开关）。

### 3.4 拒绝路径的可观测形态

- **FR-013** 新增 `ErrorCode.RATE_LIMITED(429, "RATE_LIMITED", "请求过于频繁，请稍后再试")`，
  **紧邻 `MFA_LOCKED` 放置但刻意不合并**：两者窗口语义相反（`MFA_LOCKED` 是「锁定到窗口走完，
  且 `lockRemainingSeconds` 会补窗续锁」；本码是「窗口走完即自动放行」，且补窗后**放行**），
  合并会让前端无法区分「等一会儿再来」与「账号被锁了」；且 `AuthMfaIT` **逐字断言** `MFA_LOCKED`。
- **FR-014** 同时**订正 `ErrorCode` 里那句「全仓首个 429（本项引入；此前本枚举里没有任何限流码）」**：
  该句描述的仍是 082 当时的事实（**原文逐字保留**），但其后的「此前」已不再成立 ⇒ 追加**带日期 ⚠️** 说明
  「此后新增了通用限流码 `RATE_LIMITED`，同样 429；『首个』照旧指 082 这一次」。
- **FR-015** 新增 `com.crm.common.RateLimitExceededException extends BusinessException`
  （放 `com.crm.common`：该包在 jacoco 的排除项里，且与 `ErrorCode` 同址）。
  **继承而非独立 `RuntimeException`** 是多重保障：万一某条路径没走到新处理器，父类的 `handleBusiness`
  仍给正确 429，不会掉进 `Exception` catch-all 变 500。
- **FR-016** `GlobalExceptionHandler`：**删掉** `handleRateLimited`（它处理的是
  `com.crm.controller.EmailTrackController.RateLimitedException`，连带消失的是 `exception → controller`
  的 FQN 反向依赖），**换成** `RateLimitExceededException` → 429 + **`Retry-After` 头**。
- **FR-017** 拒绝时的 `Retry-After` 取**该键当前的剩余 TTL 秒数**（向上取整，下限 1）。
  依据是本仓先例：`AuthMfaIT` 断言 429 的 message 里要有剩余锁定秒数（082 契约 §3 明确要求），
  而头是它的**机器可读版本**。
- **FR-018** `EmailTrackController.RateLimitedException`（控制器私有内部类）**删除，不留兼容壳**，
  且必须与 FR-016 的处理器删除**在同一次提交里**（否则中间态编译不过）。

### 3.5 客户端 IP 的单一实现

- **FR-019** 新增 `com.crm.security.ClientIpResolver`（`com.crm.security` 而**非** `com.crm.config`：
  `service → security` 是本仓**既有**方向——`FormService` 已用 `SecurityUtil`；放 `config` 会让
  `service → config` 成为一条**新**方向，而 `config` 是装配层不是工具层）。
- **FR-020** 语义**逐字取 `AuthService.resolveClientIp` 的那一份**（含「XFF 首段为空则回退 fallback」），
  并把 `AuthService.resolveClientIp` 的**方法体改成 1 行委托**（**签名不动** ⇒ `AuthController` 与
  `AuthServiceTest` **零改动继续绿**），另删 `EmailTrackController` 与 `FormService` 的两份私有副本。
  ⚠️ 三份副本**在退化输入上行为不一致**：`EmailTrackController` 只判整体非空，XFF 为 `","` 时返回**空串**
  （所有这类请求共用一个**键为 `""` 的桶**）；`AuthService` 多一层「首段非空」判定并回退。
  统一取**更严**的那一份 —— 这是一处**对外可观测的行为变更**（§5 第 ④ 条）。
- **FR-021** 新增 `@Value("${crm.rate-limit.trust-forwarded-for:true}")`：
  把 XFF 信任**显式化**（默认 `true` = 与今天三份副本的行为逐字相同，保住既有部署）。
  **它只在「反向代理是唯一入口」时成立**，此前提、残余风险与「公开端点限流**不是抗敌手**」的口径降级
  都写进 FR-025 的登记与 `application.yml` 注释里。

### 3.6 标注面（本项要接入的端点）

- **FR-022** **P0（必做）**：
  | 端点（按**符号**锚定，不按行号） | 维度 | scope | 阈值 |
  |---|---|---|---|
  | `CustomerPortalController#ticketStatus`（`POST /public/portal/tickets/status`） | IP | `public-portal-ticket-status` | **10/60s** |
  | `CustomerPortalController#submitTicket`（`POST /public/portal/tickets`） | IP | `public-portal-ticket-create` | **5/60s** |
  | `EmailUnsubscribeController#unsubscribe`（`POST /public/email/unsubscribe`） | IP | `public-email-unsubscribe` | **10/60s** |
  | **8 个重生成类导出面**（见 §3.7） | **USER** | `export-generate` | **10/60s** |
  | **3 个 `/open/**`**（`OpenPlatformController`） | **API_KEY** | `open-api-read` / `open-api-write` | **60/60s** / **30/60s** |
- **FR-023** **P1（同批做，形态完全一样、边际成本近零）**：
  **4 个匿名只读**（`FormController#meta`、`LandingPageController#publicView`、
  `CustomerPortalController#articles`、`#article`）共用 `scope=public-read`、**60/60s**；
  **3 个导入**（`CustomerController#importExcel`、`LeadController#importExcel`、`ContactController#importContacts`）
  共用 `scope=import-excel`、USER、**5/60s**（multipart + Excel 解析，成本与导出同级**还带写**；
  **漏掉它会形成明显的不对称**：能导出受限、能导入不受限）。
- **FR-024** **`SecurityUtil` 增 `currentApiKeyId()`**（与 `currentUserId()` 并列）：
  从 `authentication.getDetails()` 取 `ApiKeyAuthFilter.ApiKeyPrincipal.keyId()`（§1.3 ④）。

### 3.7 「导出面」的判定口径（**决定了谁进清单**）

- **FR-025** 判定依据是**响应类型 + 是否走 Excel/PDF 引擎**，**不是 URI 前缀**、也不是方法名里有没有 `export`。
  据此三档：
  - **`export-generate`（8 个，10/60s，USER）**——真在生成字节：
    `CustomerController#exportExcel`、`LeadController#exportExcel`、`QuoteController#exportPdf`、
    `ReportController#exportReport`、`ComplianceExportController#export`、`ExportController#create`、
    `ScheduledExportController#create`、`ScheduledExportController#executeNow`。
  - **`export-download`（5 个，30/60s，USER）**——生成模板或从盘上取字节：
    `CustomerController#importTemplate`、`LeadController#importTemplate`（`/template`）、
    `ContactController#importTemplate`、`ExportController#download`、`ContractAttachmentController#download`。
  - **不纳入**：`ExportController#page`（任务历史列表）、`ScheduledExportController` 的 5 个读/状态类端点、
    `ReportController#query` —— 都是普通读，纳入会与 §3.8 的「已认证常规读不纳入」自相矛盾。
- **FR-026** **两个 scope 分开**（`export-generate` / `export-download`）：模板下载是页面上点一下就走的轻操作，
  与「生成全量 Excel」差一个量级；合并会让点两次模板就把导出配额吃光。
- **FR-027** 既有的两处内存桶**保留调用点与方法签名**，只换方法体为委托，
  **阈值与窗口逐字不变**（`EmailTrackController` 60/60s、`FormService` 3/60s，含 `request == null → "unknown"` 字面量）：
  - `EmailTrackController` 的两个 scope 与新建的公开端点**分开**（`public-email-track` / `public-form-submit`）：
    合并会让「邮件客户端加载像素」的自然高频挤掉表单提交的配额。**这不算行为变更**——今天本来就是两个独立内存桶。
  - `FormService` 的 IP **必须先解析一次**（它同时要写进 `submission.setClientIp(ip)`）⇒ **不改成在
    `FormController` 上标注**，否则 IP 会被解析两次、有分叉的机会。

### 3.8 切面顺序与故意不纳入

- **FR-028** 给 `PermissionAspect` 与 `RateLimitAspect` 都加**显式 `@Order`**，**权限先于限流**
  （未授权者不消耗配额）。⚠️ 这条**必须落在第一次提交**：从那一刻起仓里有了第二个切面，
  两者同用默认序 = 并列，谁先谁后取决于排序实现。
- **FR-029** **故意不纳入**（逐条有理由，登记在 `research.md`）：
  `/actuator/health*`（**必须不限流**——探针被限流会让编排系统判死并重启，是把护栏变成故障源）、
  `/error`、`OPTIONS /**`（CORS 预检，被限流会让浏览器端整体不可用且症状极难归因）、
  swagger 三路径、**登录的 2 层失败计数**（用户裁决 + 它们是「失败计数」不是「请求速率」，无条件续窗是有意的）、
  **2FA 两处**（全仓唯一 fail-close 边界）、`/ws/**`（握手限流会误伤长连接重连）、
  **已认证的常规读接口**（业务主干，滥用已被数据权限与分页约束）、
  `@Scheduled` 任务与 `BATCH_CAP`/验证码 TTL 之类「上限/保鲜期」（**不是限流**）。

### 3.9 台账护栏

- **FR-030** 新增 `RateLimitCoverageTest`（surefire，**字节码扫描、不启 Spring**，复用
  `RequirePermissionScanTestSupport` 那套 `MetadataReader` 手法）：枚举 `com/crm` 下**全部控制器端点方法**
  （带 `@GetMapping` / `@PostMapping` / `@PutMapping` / `@DeleteMapping` / `@PatchMapping` / `@RequestMapping` 的），
  每个必须**有 `@RateLimit`** **或**在**豁免白名单**里。
- **FR-031** 豁免白名单的粒度是「**类#方法**」，**不是 URI 前缀**（`/public/**` 内部风险差一个量级，
  且 `/public/track/**` 两条**已限流** ⇒ 用前缀会让白名单变成「一放一大片」，那正是台账要防的东西），
  且**每条必须带非空理由**——无理由的条目**判失败**（防「随手加一行让测试变绿」）。
  初始条目只有四类：① `EmailTrackController` 两个（限流在私有方法里，本批只换介质）；
  ② `FormController#submit`（限流在 `FormService` 内部，同上）；③ FR-029 的**逐条**豁免；④ 登录/2FA 三处。
- **FR-032** 该测试**必须带自检**：先断言「扫描**确实**扫到了 ≥ N 个端点」（照
  `RequirePermissionCatalogTest` 的自检先例）——否则扫描 pattern 写错时「零违规」是**假绿**。

### 3.10 用例

- **FR-033** 新增 `integration/RateLimitIT`（MockMvc）与四个 surefire 单测
  （`RateLimitStoreTest` / `RateLimiterShapeTest` / `RateLimitIdentityTest` / `ClientIpResolverTest`），
  共 **T1–T14**，逐条见 `plan.md` 的用例表。**硬规则**：HTTP 层用例**必须**做满三件事
  ——⚠️ **2026-09-16 C4 实做期补登**：上句「共 T1–T14」<b>原文保留</b>，实为 **T1–T15**
  （T13 的落点后移到 C5；**T15 新增**：表单提交 400→429 的证伪判据 —— 原破坏台账 D7「把它改回 400」
  **点不出任何会变红的用例**，属「没有护栏」，故补出 T15；详见 `plan.md` 用例表与 §实做订正 20）。
  ——① 装功能替身 `InMemoryRedisTestSupport`；② **正对照**断言计数**真的落到存储**
  （`redis.snapshot()` 含该键，**没接线时该键不存在 ⇒ 红**，这是核心）；③ **负对照**断言未达阈值时 200。
  组件单测可用裸 mock 验**调用形状**，**不得**用它验**行为**（形状与行为不可互相替代）。
- **FR-034** **T12 钉住用户裁决**：连续失败登录应得 `INVALID_CREDENTIALS`(401) 而**非** `RATE_LIMITED`(429)
  ⇒ 防后人「顺手给 login 挂注解」造成两层叠加。
- **FR-035** **T13 钉住 FR-028 的顺序**：无导出权限的角色打导出端点 ⇒ 全 403 且 `redis.snapshot()`
  **无**该键（未授权者不消耗配额）。

### 3.11 登记、订正与前端

- **FR-036** 登记五处（`specs/README.md` 模块表 + 编号说明、`README.md` 的 `specs/` 行、
  `specs/roadmap.md` 的 `最后更新` / `整体覆盖度` / `## 当前进度`、`PROJECT_FEATURES.md`）：
  本项**新增 1 个编号** ⇒ `specs/` 目录数 **98 → 99**、编号面 **001~099 → 001~100**（**缺 069 不变**）；
  `## 当前进度` 变 **98 勾 / 1 未勾**（**方框留空、不预勾**）。**旧值一律原文逐字保留 + 带日期 ⚠️**。
  ⚠️ **分两批落，不是一次落齐**（**已是第二次偏离，见 `plan.md` §分步与提交**）：
  **立项（C1）** 只改「**本批自己的状态**」三处 —— `specs/README.md` 模块表 100 行、`specs/README.md` 编号说明、
  `specs/roadmap.md` 的 `最后更新` / `整体覆盖度` / `## 当前进度`；
  **交付（C6）** 才改**对外规模数字** —— `README.md` 的 `specs/` 行与 `PROJECT_FEATURES.md` 的 Spec 模块行
  （两者**必须与后端规模行的实测取值同批改齐**，只改一处等于用一次订正造出两处新矛盾）。
  依据：099 的 T007 实做订正即为此先例。
- **FR-037** **订正不静默**（原文逐字保留 + 带日期 ⚠️，**粒度到每一列**）：
  `CRM_FEATURE_COMPARISON.md` 的 **2.9 速率限制行**（`:276`）与 **P0 第 5 条**（`:370`）——
  逐列改写为 §1.1 的实测结果；**判定列与分值按仓规照旧**（订正留痕不改分）。
  ⚠️ 自查判据是「**旧值仍能被 grep 到**」；**且订正块自身的措辞要单独过一次**
  （「旧值仍可 grep」这条判据**查不出**「订正块自己写错了」——099 已踩过，见其 `tasks.md` 的同日补记）。
- **FR-038** **债务台账新增三条**（登记在 `specs/roadmap.md` 的遗留惯例处）：
  ① **XFF 首值无条件信任**（含修复动作 = 可信代理网段白名单 + 从右往左取，以及**口径降级**，
  见 FR-039）；② **固定窗口的 2× 边界突发**（两个窗口交界处可放行至 2× 配额，登记不修）；
  ③ **`INCR` + `EXPIRE` 非原子**（修它需 Lua，而 Lua 在测试替身里**未被实现** ⇒ 必须**同步扩
  `InMemoryRedisTestSupport`**，那正是本批刻意不做的）。
- **FR-039** ⚠️ **残余风险的口径必须写对**：本项新增的限流面**大部分**在已认证路径（导出按 `userId`、
  `/open/**` 按 `keyId`），伪造 XFF 打不穿它们；**但本项同时给 `/public/**` 的多个匿名端点加了 IP 桶，
  而那些端点只能按 IP 分桶** ⇒ 在这个洞上，本项是**扩大了它**而不是绕开了它。
  **结论仍取「不修」**，理由换成：① 修复需要「可信代理网段白名单」，而本仓**没有任何可信代理配置**；
  ② 生产形态下 nginx 是唯一入口，要伪造得先能直连后端端口，那是网络层的事。
  **不得在任何文档里把公开端点限流宣传成攻击防护**——它的定位是**误用与意外的阻尼**
  （防一个死循环的前端把工单表灌满）。
- **FR-040** **前端零改动**：`apiClient.ts` 的 `extractErrorMessage` 只读 `error.message` ⇒ 429 的新 message
  会被正常展示；`ErrorCode` 里的中文消息与本仓既有体例一致（`MFA_LOCKED` 就是中文）。
  给导出页做专门文案不在任何冻结契约里 ⇒ **本批不做**，**因此也不新增 i18n 键**
  （避免键数 2962 → 2963 连带一串数字落点）。

---

## 4 非目标（明确不做，且各有理由）

1. **不给登录加限流、不收敛登录的 2 层与 2FA 的锁定**：用户裁决 + 语义不同
   （锁定是「直到解锁为止」，限流是「窗口内容量」；前者无条件续窗是**有意**的）。
2. **不修 XFF 首值无条件信任**：登记 + 口径降级（FR-038/FR-039），不修的理由见 FR-039。
3. **不引入 `@ConfigurationProperties`**：全仓 **0 个**，不为本批开新范式；用 `@Value`，
   业务配置挂 `crm.*`（体例照 `crm.outbound.allowed-hosts` 那种「解释为什么是这个默认值」的注释）。
4. **不加 `fail-open` 开关**（FR-012）。
5. **不用 Lua、不扩 `InMemoryRedisTestSupport`**（FR-009）。
6. **不做滑动窗口 / 令牌桶**（固定窗口的 2× 边界突发登记不修）。
7. **不改 036 / 016 / 017 / 055 的任何 FR 与工件**：036 是「实现向冻结契约靠拢」；
   经核实 055 的 FR-O01~O10 **从来没有**速率限制这条（043 的建议里限流在 055 立项时就**没进 FR**）
   ⇒ 本项是「**从未规格化**」的补课，不是「规格了没做」。
8. **不动前端**（含不加 i18n 键，FR-040）。
9. **不碰 `.specify/feature.json`**、**不跑任何 `/speckit-*` 命令**（工件手写）；
   **不加迁移**（本项无 DDL/DML）。
10. **不顺手加 `RateLimit` 的端点级配置覆盖**（阈值只写在注解上，不做 yml 覆盖层）。
11. **不把限流模式暴露给 `@Scheduled` 作业与 `@Async` 路径**（它们没有 `HttpServletRequest`，
    且并发源不是外部请求）。

---

## 5 成功判据

- **SC-100-001（收口成立）**：全仓 `checkRateLimit` 的实现只剩 **1 处**（`RateLimiter`）；
  `EmailTrackController` 与 `FormService` 的私有方法体内**不再有** `ConcurrentHashMap` / `ArrayDeque` / `synchronized`；
  两个类的**阈值与窗口常量逐字未变**（60/60s 与 3/60s）。
- **SC-100-002（拒绝形态统一）**：三条限流路径（IP / USER / API_KEY）超限时**都是**
  HTTP **429** + `error.code == "RATE_LIMITED"` + **非空 `Retry-After`**；
  `FormService` 的 400 **不再出现**（这是**订正**一个违反冻结契约的偏差，见 §1.4 ②）。
- **SC-100-003（台账有牙）**：`RateLimitCoverageTest` 通过；**定向破坏「摘掉某个 endpoints 的注解且不加白名单」
  必须变红**；自检断言（扫到 ≥ N 个端点）在**把扫描 pattern 改错时变红**。
- **SC-100-004（fail-open 可观测）**：注入 `rl:` 前缀故障后请求**放行**（200），
  且**不是** 500 / 429；**定向破坏「改成 fail-close」必须变红**。
- **SC-100-005（永久 429 的洞被堵）**：造一个「计数 ≥ 阈值且无 TTL」的键 ⇒ 该主体**能通过**
  （补窗并放行）；**定向破坏「删掉补窗」必须变红**。
- **SC-100-006（顺序有牙）**：无导出权限的角色打导出端点 ⇒ **403** 且 Redis 里**没有**该计数键。
- **SC-100-007（订正不静默）**：`CRM_FEATURE_COMPARISON.md` 两处的**旧值全部仍可 grep 到**
  （零命中 = 静默改写）；且 `MFA_LOCKED` 的原文与 `AuthMfaIT` 的逐字断言**仍然成立**。
- **SC-100-008（数字落点一致）**：`specs/` 目录数、`specs/README.md` 的编号说明、`README.md` 的 `specs/` 行、
  `roadmap.md` 的覆盖度行 **四处一致**（均为 **99** 个目录 / `001~100`）。
  ⚠️ **该判据只在交付态（C6 之后）成立** —— 立项提交（C1）时 `README.md` 的 `specs/` 行**仍写着 98 / 001~099**
  （**有意如此**，见 FR-036 的两批落点：它与 `PROJECT_FEATURES.md` 的规模行必须同批改）。
- **SC-100-009（门禁不倒退）**：`cd backend && mvn -B verify` 的**失败集合 ⊆ 那 4 例已批准偏差**
  （`IntegrationHubIT.integrationFlow`、`OpportunityIT.closeWithoutResultReturns422`、
  `UserIT.disableUserRevokesAccess`、`UserIT.userLifecycle`）且**本批新增用例全部通过**；
  `ls backend/target/jacoco.exec` **存在**（**不是 exit 0** 作判据）；
  `FormIT` 与 `LandingPageIT` **零改动且仍绿**。
- **SC-100-010（前端零改动的证据）**：`git diff` 不含 `frontend/` 下任何路径。

---

## 6 未验证边界（如实声明）

- **默认测试基类下限流是 no-op，这是本批最大的假绿陷阱。**
  `AbstractIntegrationTest` 的 `@MockBean RedisTemplate` + 裸 mock `opsForValue()` ⇒ `increment` 返回 `null`、
  `get` 恒 `null` ⇒ **fail-open 生效 ⇒ 限流不生效**。而真正的假绿**不是**「for 循环断言第 N+1 次 429」
  （那种用例在裸 mock 下**会红**，它看到的是 200），而是 `AuthServiceTest` 那种
  **「把 Redis 返回值桩成 10、断言拒绝」**——它证明的是「**如果** Redis 说 10 我就拒」，**不证明接线**。
  ⇒ FR-033 的三件事硬规则（装替身 + 正对照断言键在 Redis + 负对照断 200）是**唯一**的补救，
  且**缺任一件即视为假绿**，须写进 `falsification-evidence.md`。
- **XFF 首值可伪造 ⇒ 按 IP 分桶的匿名端点可被绕过**（FR-039）。本项**不修**、
  **也不声称**这些端点能抗敌手；能直连后端端口的人可以伪造 XFF。**不得**把门禁绿读成「这些端点防住了滥用」。
- **固定窗口的 2× 边界突发未消除**（FR-038 ②）：恰好跨窗口的两个瞬间可各放行一整个配额。
- **`INCR` + `EXPIRE` 非原子**（FR-038 ③）：最坏是**多放一次**（fail-open 方向）或留下无 TTL 的键
  （由 FR-008 的补窗兜住）。**不用 Lua** 的理由见 FR-009。
- **原子性在测试环境不可证**：`InMemoryRedisTestSupport` 明说自己是单线程语义、
  **「不能用来证明任何原子的东西」** ⇒ 本项**不声称**任何原子性，调用形状断言（T6）只钉「用了哪个原语」。
- **`FormIT` / `LandingPageIT` 那颗 3/3 的雷的处置后果**：切到 Redis 后，那两个类在**默认基类**
  （裸 mock）下限流**变成 no-op**。这**不是**弄丢了护栏——那颗雷今天**不是护栏而是跨用例共享状态**
  （`FormService.rateBuckets` 是单例 bean 的实例字段，`clearInProcessCaches()` **不清它**），
  且限流在 `src/test` 里**零用例**。**净收益为正**，但**必须**在交付时**显式登记这个语义变化**，
  免得后人以为「IT 里限流一直生效」。
- **手工冒烟只做只读**：8081 与 5173 的实例是**共享开发库**，匿名写端点
  （`POST /public/portal/tickets`、`unsubscribe`）**不冒烟**（按仓规需用户明确同意）；
  写端点的端到端验证须起**隔离实例**（临时端口 + 独立 schema + 另一个 Redis db），
  收尾 `DROP`/`REVOKE`/`FLUSHDB` 并核对共享库未动。
- **线上（多实例）行为未验**：本项的 Redis 化正是为多实例而做，但**本机只跑一个实例**
  ⇒ 「阈值不再 ×N」这条**只有推理、没有实测**，不得声称已验。
