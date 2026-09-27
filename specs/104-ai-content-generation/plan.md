# 实施计划：AI 文本生成（104）

**输入**: [spec.md](./spec.md)（31 条 FR / 7 条 SC / 8 条非目标 / 6 条边界）、[research.md](./research.md)（13 项已核实事实）
**上游**: `CRM_FEATURE_COMPARISON.md` **2.10 AI 能力**（判定 `1.5` / 八项无一项变化 / 差距清单 P1 第 8 项）
**形制**: 后端 + 前端 + 文档；**产 `contracts/`（对外行为确有新增）**；**不产 `data-model.md`（无新实体，见 D7）**；**一个迁移 `V92`（仅权限码）**

> ⚠️ **2026-09-27 C2 开工实测订正（范围）**：上面这句里的「**一个迁移 `V92`**」**不能按原样读**，两件事同时被实测推翻，**原文逐字保留在上，不静默改写**：
>
> **① 权限码整体后移到 C3。** 原定 C2 含「`V92` 权限码 + `schema-h2.sql` 同步 + 前端 `permissions.ts`」，却又要求 C2「**无端点、无 UI**」——**码先于引用它的端点存在，会被三道护栏按住**（`UnwiredPermissionCodeTest` 的双向冻结台账只收 22 条**存量**、096 非目标 4「只建被端点真引用的码」、`PermissionMatrixIT` 的 FR-G14 判例）。**已实测**：把 `ai:generate` 加进字典即转红，断言原文 `Expecting empty but was: ["ai:generate"]`（破坏前绿 / 破坏后红 / 还原后复绿，`sha256` 逐字相等）。⇒ **字典 + `V92` + `schema-h2.sql` + 前端 `permissions.ts` 四件随 C3 的第一个端点走**，读数见 **`research.md` §12.3**。
>
> **② `V92` 到底存不存在，是 C3 要裁的问题（本文件不再预设它存在）。** 本项是**新能力**：改造前既无粗粒度门（判据① 无对象）也无菜单承诺（判据② 无对象）⇒ 落**判据③「一个码都不补」**。仓内对这条判据**有已执行的先例**：`PermissionMatrixIT` 的 FR-G14 六码——**在字典里、注解在端点上、全部迁移中一条授予都没有**，且把"零授予"写成可执行断言。若按该先例，**`V92` 根本不该存在**（没有 DDL、没有授予 ⇒ 迁移是空文件）。**故 C3 必须先裁这一条再动手**；两种裁法对本文件下游三处的影响不同，见下方落点表的 ⚠️。
>
> ⚠️ **受 ①② 直接影响的落点**（每一处都已就地标注，此处汇总以便一次找齐）：`plan.md` 的 **D5**、**D6**、**Project Structure 后端树**、**落点表**（`specs/README.md` 迁移表行 / `INSTALL.md` / `PROJECT_FEATURES.md` 的 Flyway 计数 / 「与 103 的关键差别」整段）、**提交拆分 C2 行**；以及 `tasks.md` 的 **T015 / T016 / T017**、`quickstart.md` §3 与 §6、`contracts/ai-content-generation.md` §6、`spec.md` **FR-020**。


---

## Constitution Check

### 一、契约优先的 API 设计（不可协商）

✅ **通过，且必须产新契约**。本项**新增对外端点**（生成类），章程要求「端点在契约被定义、评审并版本化之前不得实现」⇒ 先落 [contracts/ai-content-generation.md](./contracts/ai-content-generation.md)：端点路径/方法、请求/响应结构、**四个错误码**、**逐能力的字段白名单**，以及 ⚠️ **「Anthropic Messages API 兼容」这一接口标准及其推论**（本地模型须前置协议转换网关，见 A-001）。
契约还要**显式收存**一处既有行为作对照：022 的 `GET /suggestions` **不带任何权限码**（其类注释 `SuggestionController.java:21-26` 的"撤门也安全"论证**已被证伪**，见 D2）——**本项不沿用该判断**。

### 二、分层架构与关注点分离（不可协商）

✅ **通过**。判据类放 `com.crm.config`（与 `MailInboundStatus` / `MailStatus` 同包同形）；异常放 `com.crm.common`（与 `MailInboundNotConfiguredException` 同包，**继承 `BusinessException`**）；SDK 客户端封装放 `com.crm.service`（唯一出网点）；控制器只做 HTTP 与权限注解。**不新增包、不新增范式**（全仓 `@ConfigurationProperties` **0 命中** ⇒ 照旧 `@Value`）。
业务规则不落 SQL/JSX：提示词模板落服务层常量与 `contracts/`，**前端不拼提示词**。

### 三、数据完整性、安全与校验（不可协商）

✅ **通过，但有一处**必须显式声明的偏离**：
⚠️ **偏离：SDK 自带 OkHttp，绕过 `OutboundUrlValidator`**。仓库的 SSRF 护栏（逐跳校验、拒内网段）靠共享 `RestTemplate` 走在调用路径上，而官方 Java SDK 不使用它 ⇒ **本项的出站不经过逐跳校验**。
**处置与理由**：`base-url` 来自**运维配置**，不是用户输入（与 Webhook 的威胁模型不同——后者 URL 来自用户数据，故必须逐跳）⇒ 以**启动期一次校验**替代（D1 的 FR-005 姿态）。**此偏离不改章程、不豁免原则**；它落在原则三「每个信任边界都必须校验输入」的**同一侧**（配置同样是输入，只是校验时机提前到启动期且失败即拒启）。**若日后出现"允许在界面上填 base URL"的需求，本条的成立前提即消失，必须回到逐跳校验。**

- **密钥**：只经配置层读取，**不进日志/响应/审计 detail/异常消息**（FR-004）；空 = 合法但告警、有值但畸形 = 启动即失败（照 `SecurityDefaultsGuard`）。
- **字段级权限一致性（FR-013）**：输入取数**必须**经既有数据范围 + 102 FLS 过滤链；输出不得泄露发起者无权读的字段。**这是本项最大的安全面。**
- **输出无执行能力（FR-012）**：不注册工具、不发起二次请求、不拼 SQL；渲染按既有路径同等待遇。
- **事务**：生成路径**无写**（FR-019），故不涉及事务边界；审计写入是既有的 best-effort 机制。

### 四、测试优先与质量门禁（不可协商）

✅ **通过**。判据在「验证」节定稿（U1–U8 单元 / I1–I6 集成 / F1–F3 前端；D1–D10 定向破坏）。
⚠️ **三处已识别的假绿通道**（本仓有先例，逐一封堵）：
1. **默认路径零副作用**不能靠事务推理 ⇒ **必须正面断言**（单测 `verify(..., never())` + IT 计数为 0）。
2. **yml 默认值 vs Java 兜底值**：IT 读 yml ⇒ 改 Java 侧 `@Value` 的 `:false` 不会让任何 IT 变红 ⇒ **必须有一条直接构造判据类的单测**（照 101 的 T8 教训）。
3. **`i18n:check` 是本仓唯一能抓"加了页面没加键"的门禁**（测试里 react-i18next 被桩掉，缺键会静默渲染键名）⇒ 键必须**双侧同数**。
⚠️ 中文断言**必须**显式 `StandardCharsets.UTF_8`（本仓有 ISO-8859-1 假红先例）；能断 `error.code` 优先断 code。

### 五、简洁、可维护与可观测（不可协商）

✅ **通过**。**不新增表、不新增实体、不加 `@Async` 执行器、不引入流式基础设施、不加 markdown 渲染依赖**（见 D3/D5 与 spec 的非目标）。新增的是：1 个客户端封装 + 1 个判据类 + 1 个异常 + 4 组提示词常量 + 若干端点。
可观测性：四个错误码语义稳定；每次调用落审计（模型名 / token 取自 SDK usage / 耗时 / 发起人 / 能力名）；结构化日志（SLF4J）**不得**含提示词原文与客户数据。

---

## 结构决策

### D0 交付分期：四项同一个 spec，**四个可独立交付的阶段**

四项共用基础设施（判据类 / 客户端 / 审计 / 限流 / 前端入口形态），但各自的**提示词、判据、i18n 键、字段白名单**是独立增量。故 `tasks.md` 按 **P1 → P2 → P3 → P4** 分四段，每段末尾都有独立的门禁点，**任一段之后都可停**。

**建议首期只交 P1（邮件草稿）**，理由：它**零新增持久化、零新写入路径**，输入是页面已有数据、输出是给人工审阅的文本 ⇒ 判据最好写、破坏实验最有价值。**但本计划不擅自收窄**——用户已明确选择四项齐做，故 plan/tasks 覆盖四项，分期只是让它可安全截断。

### D1 FR-005 的失败姿态：**启动即失败**（不是启动告警 + 调用时 409）

```java
// 判据类内，启动期一次的校验（伪码，命名以实施为准）
if (enabled && !outboundUrlValidator.isAllowed(baseUrl)) {
  throw new IllegalStateException("crm.ai.base-url 的主机不在 crm.outbound.allowed-hosts 内："
      + host + "；请先把它加入该白名单（见 .env.example 的 CRM_OUTBOUND_ALLOWED_HOSTS）");
}
```

- **为什么严格**：因为 SDK 绕过 `OutboundUrlValidator`（原则三的偏离条），**这个白名单检查是本项唯一的出站准入控制**。若只告警，白名单就成了**建议性**的——一个读起来像护栏、实际不拦的东西，比没有更坏。
- **为什么不会伤及默认部署**：`crm.ai.enabled` **默认 `false`** ⇒ 校验只在运维**显式打开**后才生效。即"你既已声明要用 AI，就必须同时声明它能出网"——两件事本就该一起配置。
- **与 `MailInboundStatus`（调用时判门）不矛盾**：那里判的是"运行时依赖是否可用"，这里判的是"静态配置是否自洽"，后者在启动期即可判定，且错在运维不在地理。

### D2 取数：**必须经既有过滤链，严禁直查裸 Mapper**（本项最重要的安全决策）

**上游是一个已实测确认的既有缺陷**，本项不得重蹈：

| 022 的四条规则 | 取数方式 | 数据范围过滤 |
|---|---|---|
| 客户流失 | `CustomerService.atRiskCustomers` | ✅ `CustomerService.java:226` 有 `applyDataScopeFilter(qw)` |
| 商机停滞 | `soMapper.selectList(...)` | ❌ **无** |
| 待跟进客户 | `followUpMapper.selectList(...)` | ❌ **无** |
| 高分线索 | `leadMapper.selectList(...)` | ❌ **无** |

`MybatisPlusConfig.java:19-24` **只注册了** `PaginationInnerInterceptor` + `OptimisticLockerInnerInterceptor`；全仓 `grep DataPermissionInterceptor\|DataScopeInterceptor\|TenantLineInnerInterceptor` **零命中** ⇒ **不存在任何隐式兜底**。而 `SuggestionController.java:21-26` 的类注释却声称"前三者本身已按数据范围过滤……撤门不会让任何人看到范围外的数据"——**该论证不成立**（详见 `research.md` §2）。

⇒ **本项要求**：所有输入数据经**已应用过滤的 Service 方法**读取得出。**禁止**在新代码里出现 `*Mapper.selectList` 直接取客户/商机/线索/跟进数据。此条**须有用例看着**（I2 / D5）。

⚠️ **本项不修 022**：那是独立缺陷、独立立项（不同关注点、须独立回退——见 `spec.md` 的非目标与 `research.md` §2 末）。**本项只是不复制它。**

### D3 同步调用，**不引入 `@Async`**（并由此确定前端超时姿态）

- **现状**：`@EnableAsync` 已开，但**无 `AsyncConfigurer` / 无 `ThreadPoolTaskExecutor` bean**（`CrmApplication.java:12-13` 是全仓唯一的开关处）⇒ `@Async` 跑在 Spring 默认的 `SimpleAsyncTaskExecutor` 上，**每任务一线程、无上界**。给一个慢外部调用接上它，等于给了一个**不受限的线程放大器**。
- **决定**：**同步**调用。超时由 `crm.ai.timeout-seconds`（默认 60）控制；拒绝并发烧钱交给 `@RateLimit` + 预算（FR-014/FR-015）。
- **代价（明写）**：一次生成占用一个 servlet 线程至多 60s。在限流把并发封顶的前提下可接受；**若将来要放开并发，必须先有有界执行器**——那时它是独立的一次变更，不塞进本项。
- **前端**：走既有 `timeout: 0` 逃生口（`apiClient.ts:36-38` 已把这个模式写成房规），**不用**改全局 30s；生成中态由组件本地状态承载。

### D4 提示词分层：稳定前缀进 `system`，客户数据一律进 `user`

- 系统提示词（角色、格式、语气、"不得编造"、"信息不足须直说"）是**稳定常量**，按章程与 SDK 体例可挂 `CacheControlEphemeral` 做前缀缓存。
- ⚠️ **但不得宣称"省了钱"**：提示缓存有**最小可缓存前缀**（不足即静默不缓存）。本项的系统提示词**很可能短于该下限** ⇒ **默认按"不缓存"预期**，实施时以 `response.usage().cacheReadInputTokens()` 实测为准，**若为 0 就照实记为"未命中"，不得写成"已启用缓存"**。
- **客户数据一律进 `user` 消息**（FR-009）——这既是缓存正确性要求，也是提示注入的边界（不进 system 就不会被误当指令来源）。

### D5 **零新增表** ⇒ 审计复用既有机制；生成结果不落库

- 生成结果插入前端编辑区，经**既有保存路径**持久化（FR-019）⇒ 不需要新表、不需要新写路径、不需要新的回滚语义。
- 每次调用落 `AuditService.record(...)`（141 个既有调用点同形）。**detail 只放元数据**：模型名 / 输入输出 token / 耗时 / 能力名。**不放提示词原文、不放客户数据**（FR-016，以 grep 断言，SC-004）。
- **本项唯一的迁移是 `V92`**（FR-020 的权限码），**不是**表结构变更。 ⚠️ **2026-09-27 C2 订正：这一条的原样表述已被推翻两次——`V92` 落点后移到 C3，且它「存不存在」本身待 C3 按判据③ 裁决（详见文首 ⚠️ 与 `research.md` §12.3）。原句逐字保留。**

### D6 权限码 `ai:generate`：**不设码的路径已被证伪**

022 撤除权限码的论证**依赖一个错误的取数假设**（D2 表）。假设不成立 ⇒ 论证不成立 ⇒ **本项必须设码**。
- 落点四处：`RoleConstants.PERMISSION_DEFS`（`RoleConstants.java:105`）、迁移 `V92`（授给角色）、前端 `constants/permissions.ts`、以及既有的三道护栏（`RequirePermissionCatalogTest` 注解 ⊆ 字典、`PermissionMatrixIT` 授权 ⊆ 字典、`check-perms.mjs`）。 ⚠️ **2026-09-27 C2 订正**：这「四处」**必须同批**（码先于端点存在会被 `UnwiredPermissionCodeTest` 按住，实测转红见 `research.md` §12.3），故整体从 C2 后移到 **C3**；且「**授给角色**」这个括号里的内容正是 C3 要按判据③ 裁的那一条（先例 `PermissionMatrixIT` 的 FR-G14 六码是**一条都不授**，那样 `V92` 就成了空迁移）。另：`check-perms.mjs` 是**单向**判据，字典里有、前端没有它**不管**（实测，见 §12.3 末）。原句逐字保留。
- 因此**必须同步 `schema-h2.sql`**（行尾 `-- V92` 标记，当前该文件已有 65 处该形式）与 `SchemaParityIT` 的镜像清单；`SchemaIdempotencyIT` 要求脚本可重跑。 ⚠️ **2026-09-27 C2 订正：前提是 `V92` 存在**——若 C3 按判据③ 裁为「零授予」，则**没有迁移、这几处同步全部不发生**，本句整条不适用（同样后移，不是取消判断）。原句逐字保留。

### D7 为什么**刻意不产** `data-model.md`

本项**零新实体、零新表、零列变更**（D5）。唯一的迁移 `V92` 是**数据行插入**（权限授予），不改 schema。
照 **103 的 D7 判例**：`data-model.md` 的用途是"抄一份就造出第二个家"，无可抄之物时产出它只会制造一个需要与迁移同步维护的**第二事实源**。**本项的状态迁移面由 `contracts/` 的错误码表 + `research.md` §ErrorCode 分布承载。**

### D8 Redis 命名空间：`ai:gen:*`（**不得**用 `ai:*`）

`ai:` 前缀**已被 022 占用**：`SuggestionService.java:36` 的 `IGNORE_PREFIX = "ai:ignore:"`，键形 `ai:ignore:{userId}:`、成员 `entityType:entityId`、TTL 90 天。
⇒ 本项预算键走 **`ai:gen:budget:{scope}:{id}:{yyyyMMdd}`**，两族**互不影响**（022 的忽略集不会误伤本项，反之亦然）。

---

## Project Structure

### 后端（新增为主，改动极少）

```
backend/src/main/java/com/crm/
├── config/
│   └── AiStatus.java                     【新】唯一判据源，照 MailInboundStatus 形制
├── common/
│   ├── AiNotConfiguredException.java     【新】继承 BusinessException
│   ├── AiGenerationException.java        【新】继承 BusinessException
│   └── ErrorCode.java                    【改】+3 个码（见下）
├── service/
│   ├── AiContentService.java             【新】唯一出网点：SDK 调用、超时、usage 取数、审计
│   └── AiPromptCatalog.java              【新】四组提示词常量 + 逐能力字段白名单
├── controller/
│   └── AiContentController.java          【新】4 个生成端点，全部 @RateLimit + @RequirePermission
└── resources/db/migration/
    └── V92__ai_generate_permission.sql   【新】仅权限授予  ⚠️ 2026-09-27 C2：本行**待 C3 裁决**——若按判据③ 零授予，它不存在（见文首 ⚠️）
```

**不新增的（刻意）**：无实体、无 DTO 包（响应是纯文本 + 元数据的小 record）、无新的 MyBatis Mapper、无 `AsyncConfigurer`。

**新增依赖（C2）**：`backend/pom.xml` 加 **`com.anthropic:anthropic-java:2.65.0`**——版本与**全部实测绑定**（`baseUrl(String)` 的大小写、`maxTokens(long)`、`usage()` 四个取值方法、`errorType()` 返回 **`Optional`**）见 **`research.md` §11**，那里还记了两处 C2 必办：**Jackson 版本落差实测**（SDK 按 2.19.4 构建 vs 本仓 BOM 的 2.15.3）与**净新增运行时足迹**（okhttp / kotlin / victools）。⚠️ **本项是仓内第一次引入 okhttp 与 kotlin**（`grep -E 'okhttp|kotlin' pom.xml` 零命中）。

**`ErrorCode` 新增三个码**（⚠️ **具体状态码须开工时实测 `ErrorCode` 分布再定，不得引用本表的预测**——这是 101 批立的规矩）：

| 码（名） | 建议 | 语义 | 依据 |
|---|---|---|---|
| `AI_NOT_CONFIGURED` | **409** | 未配置 | 409 有 31 处先例、语义族正是"服务端当前状态不允许该操作"；照 `MAIL_INBOUND_NOT_CONFIGURED` (`ErrorCode.java:153`) |
| `AI_UPSTREAM_UNAVAILABLE` | **503** | 上游不可用、**可重试** | 503 已被 `MFA_STORE_UNAVAILABLE` 占为"依赖暂时不可用、可重试"，语义精确吻合 |
| `AI_GENERATION_REJECTED` | **422** | 拒答/截断/输入不合规 | 422 有 48 处先例（校验族）（⚠️ **2026-09-27 T000 实测订正：该数为 `49`**——旧值系 grep 漏掉一处跨行常量，**权威读数与复算命令见 `research.md` §7.1**；`48` 逐字保留，**不静默改写**） |

**⚠️ 不发明 501**：101 批实测全仓 **501 零先例**（`ErrorCode` 分布 400×10 / 401×8 / 403×8 / 404×38 / 409×31 / 422×48 / 429×2 / 500×3 / 503×1），引入客户端与八道门禁都没见过的状态类是净成本。（⚠️ **2026-09-27 T000 实测订正**：上串分布**逐字保留**；其中 `422×48` **不成立**，实测 **`422×49`**——旧值系 grep 漏掉 `ErrorCode.java:35-36` 那处**跨行常量**。**结论不变**：501 依旧**零先例**、三个建议状态码**全部得到真实先例支持**（见下方 T000 结论）。**权威读数与复算命令在 `research.md` §7.1**。）

**✅ T000 开工实测结论（2026-09-27，表格三行的建议值全部维持）**：全仓 **150 个** `ErrorCode` 常量（`grep` 命中数**必须等于**该总数，否则模式有漏——本次正是靠这条自证发现的），分布 400×10 / 401×8 / 403×8 / 404×38 / 409×31 / **422×49** / 429×2 / 500×3 / 503×1。三个新码的**先例逐条核过**：
- **`AI_NOT_CONFIGURED` = 409** ✅ 先例 `MAIL_INBOUND_NOT_CONFIGURED`（`ErrorCode.java:153`）——**同族**（"本功能未接入，故未执行"），房规体例直接成立；
- **`AI_UPSTREAM_UNAVAILABLE` = 503** ✅ 先例 `MFA_STORE_UNAVAILABLE`（`:175`）——**全仓唯一的 503**，语义正是"依赖暂时不可用、请稍后重试"，与本码**逐字吻合**；
- **`AI_GENERATION_REJECTED` = 422** ✅ 校验族 49 处先例；
- **预算耗尽** 不新增码，复用既有 `RATE_LIMITED`（`:181`，429）——FR-017 的原意。
- **501 仍零先例**（`grep -c '501, "'` → 0）⇒ **不发明 501** 的结论**维持**。

### 前端

```
frontend/src/
├── services/aiContentService.ts          【新】走既有 apiClient，生成类调用显式 timeout: 0
├── types/aiContent.ts                    【新】联合类型：能力名 + 受控错误码（让缺分支在编译期被挡）
├── components/AiGenerateButton.tsx        【新】生成中态/失败态/截断态 + 结果插入回调
├── i18n/zh-CN.ts / en.ts                 【改】双侧同数新增键
└── constants/permissions.ts              【改】+ ai:generate
```

**接入点（4 处，分阶段）**：客户详情页（P2 摘要）、邮件编辑/活动页（P1 草稿）、跟进记录表单（P3 润色）、商机详情页（P4 建议）。

⚠️ **新增组件须自证不违反 `ui:check` 的 R1–R8**（品牌色字面量、Modal 固定宽、裸 `<Col span>`、`required:true` 须带 message+label、裸 `placeholder`/`aria-label`、孤儿组件、`Descriptions column`）；冻结台账 **54 处不得增长**，且**台账条目过期也会红**（双向）。

### 测试

- 单元：`AiStatusTest`【新，直构判据类】、`AiContentServiceTest`【新，桩 SDK 客户端】、`AiPromptCatalogTest`【新，白名单断言】
- 集成：`AiContentIT`【新】——**扩既有 IT 的助手，不新造脚手架**（照 103 的做法）
- 前端：`AiGenerateButton.test.tsx`【新，四态】
- ⚠️ **不得加 `@Transactional` / `@TestMethodOrder`**（IT 的 `resetDatabase()` 依赖无序）

### 工件与登记

`specs/104-ai-content-generation/`：`spec.md` / `plan.md` / `research.md` / `contracts/ai-content-generation.md` / `quickstart.md` / `tasks.md` / `checklists/requirements.md`（**七件**；`data-model.md` 刻意不产见 D7；`falsification-evidence.md` **在交付时**才产，见「提交拆分」C5 的说明）。

---

## 一个数字住在好几个地方 —— 落点表

⚠️ **与 103 的关键差别**：103 零迁移 ⇒ `INSTALL.md` 不动、对比报告"很可能不改"。**本项有一个迁移（`V92`）且是 AI 域的能力增量** ⇒ **两处都必须动**。**这条差异须明写**，免得读者照 103 的经验去找一处并不存在的"不动"。

> ⚠️ **2026-09-27 C2 订正（整段的另一半不成立，须与上面逐字保留的原文一起读）**：本段把两个**独立**的论断捆成了一句，实测表明它们的成立性不同。
> - **「AI 域的能力增量」这半边成立** ⇒ `CRM_FEATURE_COMPARISON.md` **必须动**（见下方小节的处置）。
> - **「有一个迁移 `V92`」这半边待裁** ⇒ 若 C3 按判据③ 裁为零授予，则**没有新增迁移**，于是 `INSTALL.md` **不动**、`specs/README.md:3` 的版本行与**迁移表都不增行**、`PROJECT_FEATURES.md` 的 Flyway 计数**不移动**（90 → 90）。
> - ⇒ **本项到底更像 103 还是更像有迁移的那一类，取决于 C3 的裁决**；下表相应四行都已就地标注。**在此之前不得预填这些数字。**

| 文件 | 改什么 | 旧值处置 |
|---|---|---|
| `specs/README.md` | 模块表加 104 行（**6 列**：`# \| 模块 \| 阶段 \| 状态 \| 文档 \| 契约`）；`:3` 版本行；编号说明段（仍「`069` 未创建」）；**迁移表加一行 V92** ⚠️ **2026-09-27 C2：仅当 C3 裁出 `V92` 才加；零授予则迁移表不动、`:3` 版本行也不动**（原文逐字保留）| 旧值逐字保留 |
| `specs/roadmap.md` | `## 当前进度` 加 104 行（立项**刻意不预勾**）；计数移动；104 行下**债务 blockquote**；`:4` `**最后更新**` | 上一条**逐字保留** |
| `README.md` | 目录树 `specs/` 计数 `001~103` → `001~104`；文档索引加指针 | 旧值逐字保留 + 带日期 ⚠️ |
| `PROJECT_FEATURES.md` | 一次重测块，**只写真正移动的行**：Flyway 迁移 **90 → 91** ⚠️ **2026-09-27 C2：若 C3 裁为零授予则本行不动（90 → 90）**、i18n 键（以门禁实测为准，当前 **2966**）、后端测试类计数、前端单测计数、Spec 模块 `103 → 104`（**原文逐字保留**）| 旧值保留可 grep |
| `CRM_FEATURE_COMPARISON.md` | ⚠️ **本项必须改，且要改得对**：见下 | 见下 |
| `INSTALL.md` | **迁移列表加 `V92`**（103 不动的理由在本项不成立）⚠️ **2026-09-27 C2：本行整条以「存在 `V92`」为前提；C3 若按判据③ 裁为零授予 ⇒ 无新增迁移 ⇒ 本文件不动**（原文逐字保留）| 旧值逐字保留 |
| `DELIVERY_SCOPE.md` | 本项**给部署包新增一个外部依赖**（出网 + API key）⇒ 交付边界须记这一条 | — |

### ⚠️ `CRM_FEATURE_COMPARISON.md` 的处置（本项最容易做错的一处）

该报告 **2.10 AI 能力** 现有表述是「八项判定**无一项变化**」「本域是**唯一完全静止**的能力域」，`AI 能力` 行 **1.5**，并自定规则 **「分值只随能力增量动，不随判定修正动」**。

本项**是能力增量**（从"零 LLM 依赖"变为"有一条可配置的生成式路径"），故按该报告**自己的规则**：**分值应当移动**，且八项判定中至少「生成式 AI（邮件/文案/摘要）」由 ❌ 变化。

**但必须同时做到三件事，否则是用一次交付造出两处新矛盾**：
1. **只动真正移动的那一格**（生成式 AI 行），其余七项**逐字保留**；
2. 第十行的**等权算术平均**（现 **3.35 / 显示 3.4**）随 `AI 能力` 行一起重算，并在正文声明取整口径（该报告已因 3.35 的进位边界声明过一次，不得再让它成为争议）；
3. 该报告另有 **「判定列与分值一律不动」** 的**订正留痕**规则——**那条规则管的是"订正"，不是"能力增量"**。本项属后者。**区分必须在文件内写明**，否则下一个读者会以为有人违反了规则。

**并且**：§2.10 的「总闸门实测」段落引用的零命中清单（`openai`/`llm`/`anthropic`/…）**在交付后即失效** ⇒ 照该报告既有体例，**原文逐字保留 + 追加带日期 ⚠️ 块**，不是重写。

**必须不动**：`.specify/feature.json`、`db/migration/V1–V91`（**不得编辑任何已应用的迁移**）、`specs/022-ai-assistant/**`（本项不改 022）、`specs/103-omission-not-destruction/**`、`pom.xml` 的 jacoco 排除项。

---

## 验证

### 用例清单（唯一的行为层证据）

**单元**

| # | 用例 | 断言 | 钉住 |
|---|---|---|---|
| U1 | `AiStatusTest` 直构判据类：`enabled=false` / `enabled=true 但 base-url 空` / 两者齐备 | 三种构造分别得出"未配置 / 未配置 / 已配置" | **FR-002 + 假绿通道 2**（IT 读 yml，改 Java 兜底值不会让任何 IT 变红） |
| U2 | `AiContentServiceTest`：未配置时调用 | `verify(client, never())` 出站、`verify(audit, never())` 记录 | **FR-003 正面断言（假绿通道 1）** |
| U3 | 同上：SDK 抛 `RateLimitException` | 映射为 `AI_UPSTREAM_UNAVAILABLE`，**不是** 500 | FR-018 |
| U4 | 同上：SDK 抛 `AnthropicServiceException`（其他） | 映射为受控码 + 结构化日志（**日志中不含提示词**） | FR-004 / FR-016 |
| U5 | 同上：响应 `stop_reason == "max_tokens"` | 标记 `truncated=true`，**不得**当完整结果返回 | spec 边界情况（截断） |
| U6 | 同上：token 读数 | 取自 SDK 的 `usage()`，**不是**估算/字数换算 | FR-016 / SC-004 |
| U7 | 同上：键形断言 | Redis 键匹配 `ai:gen:budget:*` 且**不含** `ai:ignore:` | **D8**（命名空间互斥） |
| U8 | `AiPromptCatalogTest`：逐能力字段白名单 | 白名单**不含**电话/邮箱/证件/账号类字段；且**含**该能力必需字段 | FR-011 |

**集成**（`AiContentIT`）

| # | 用例 | 断言 |
|---|---|---|
| I1 | `unconfiguredGenerationIsRejectedWithZeroSideEffects` | 默认档 ⇒ **409 + `AI_NOT_CONFIGURED`**；**出站请求数 0**、**审计新增 0**（正面断言，不靠事务推理） |
| I2 | `restrictedRoleCannotLeakHiddenFieldIntoSummary` | SALES 对某字段持 **HIDDEN**（102 FLS）；生成客户摘要 ⇒ **该字段值在响应中零出现**（逐字段断言）<br>⚠️ **本项最值钱的一条** |
| I3 | `salesUserDoesNotSeeOtherOwnersData` | SALES-A 生成；库中有 SALES-B 的客户/商机 ⇒ **响应中零出现 B 的实体**（**D2 的护栏，直接对标 022 的缺陷**） |
| I4 | `unreachableUpstreamReturnsControlledCode` | 指向不可达地址 ⇒ 受控码（**非 500**），且**不产生部分写入** |
| I5 | `emptyOrOversizedInputIsRejectedBeforeEgress` | 空输入 / 超长输入 ⇒ **422**，且**出站请求数 0**（判门在出站之前） |
| I6 | `generationWritesNoBusinessTables` | 生成前后：客户/商机/线索/跟进/合同表**行数与内容逐字不变**（FR-019） |

⚠️ 中文断言**必须**显式 `StandardCharsets.UTF_8`；能断 `error.code` **优先断 code**。
⚠️ **不得加 `@Transactional`、不得加 `@TestMethodOrder`**。

**前端**（`AiGenerateButton.test.tsx`，四态）

| # | 断言 |
|---|---|
| F1 | 未配置：点按钮 ⇒ 展示失败文案，**且编辑区保留用户已输入内容**（不得清空） |
| F2 | 生成中：按钮 `disabled` + 进行中态；**不得**可重复点击发起第二次 |
| F3 | 成功：结果插入回调被调用**一次**，参数为返回文本 |
| F4 | 截断：展示截断提示，**不得**把半截文本当完整结果呈现 |

### 定向破坏（D 系列，逐条**观测到转红**）

**破坏表不是占位符**：「该改变哪条行为」在**开工前定稿**，留空的只有读数。

| # | 破坏 | 该红 |
|---|---|---|
| D1 | 判据类的 Java 兜底值改成 `true`（yml 仍 `false`） | **U1**（**并实测 I 系列是否照旧绿**——若全绿，就实证了假绿通道 2 的存在，须逐字记录） |
| D2 | 未配置路径去掉判门、直接调 SDK | U2、I1 |
| D3 | 取数改为直查裸 Mapper（**复现 022 的错法**） | **I3**（这是本批最重要的一次破坏：若不变红，说明 I3 没有判据看着） |
| D4 | 字段白名单里加入一个 HIDDEN 字段 | I2、U8 |
| D5 | 输入校验挪到出站之后 | I5（证明判门**在出站之前**，而非仅仅存在） |
| D6 | 截断不标记、当完整结果返回 | U5、F4 |
| D7 | 审计 detail 写入提示词原文 | SC-004 的 grep 判据 |
| D8 | 预算键改成 `ai:ignore:*`（撞 022 命名空间） | U7 |
| D9 | token 用估算（字数 ÷ 3）替代 SDK usage | U6 |
| D10 | 前端失败态清空编辑区 | F1 |

⚠️ 每条**先写一句「它该改变哪条可观察行为」**，跑完核对那条行为**确实变了**——没变就是**空操作**；看到红先读**是不是手段的红**（CRLF / spotless / 编译错 / TS6133 都不是目的的红）。
**禁止 `git checkout` 还原**（用 `cp` 回写 + 逐字相等判据）。**就地改一律用 Edit 工具**（本仓 Java 源是 CRLF，脚本原地重写会把 CRLF 换成 LF 并把 spotless 打红）。探针残留判据用唯一标记 `留痕后还原`。

### 门禁

```bash
cd backend && mvn -B spotless:apply
cd backend && mvn -B verify                                  # 不传 -DargLine（会静默废掉 JaCoCo）
ls backend/target/jacoco.exec
cd frontend && pnpm run i18n:check && pnpm run lint && pnpm run typecheck \
  && pnpm run menu:check && pnpm run perms:check && pnpm run ui:check \
  && pnpm run zh:check && pnpm run test:coverage && pnpm run build
```

- **判据**：`jacoco:check` 必须打印 **「All coverage checks have been met.」**（阈值 INSTRUCTION **0.73**，当前实测 82.18%）；前端八道全绿；`i18n:check` 的键数与 `PROJECT_FEATURES.md` 的实测值一致。
- **冻结债不得增长**：`ui:check` **54 处**、`zh:check` **4 条 / 266 处**。
- **交付态读数只取那一次完整 `verify`**；门禁跑完**不再跑 Maven**（会覆盖 `jacoco.exec`，使交付块读数变成假话）。
- ⚠️ **spotless 的缓存命中不算"真解析过"**：若输出是「N were skipped because caching determined…」，须移走 `target/spotless-index` 后复跑 `spotless:check`，取得 `skipped 0` 的真读数（本仓有先例）。
- 定向不跑全量：`pnpm exec vitest run <file>`；后端 `-Dtest='A,B'`。

---

## 提交拆分（每次可独立回退）

| # | 提交 | 内容 |
|---|---|---|
| C1 | `docs(104): 立项` | 七件工件 + 登记（`specs/README.md` 104 行与编号说明段、`specs/roadmap.md` 104 行 + 计数 + 债务 blockquote）。**不含任何代码** |
| C2 | `feat(104): AI 客户端与配置门` | `AiStatus` + 两个异常 + 三个 `ErrorCode` + `AiContentService`（出网点）+ `AiClientFactory`（**实施期新增**：密钥只在这一个文件里被取用，好让"密钥不进审计类"成为结构性事实——原计划的客户端构造写在 `AiContentService` 里，会使 §6 那条 grep 判据自相矛盾）+ pom 依赖 `com.anthropic:anthropic-java:2.65.0` + `application.yml` / `.env.example`。**无端点、无 UI**。<br>⚠️ **2026-09-27 订正**：原列的「`V92` 权限码 + `schema-h2.sql` 同步 + 前端 `permissions.ts`」**整体后移到 C3**（码先于端点存在会被护栏按住，实测转红见 `research.md` §12.3）。原文逐字保留。 |
| C3 | `feat(104): 邮件草稿生成（P1）` | `AiPromptCatalog` 的 P1 组 + 1 个端点 + `contracts/` 的字段白名单段 + 前端 `aiContentService.ts` / `types` / `AiGenerateButton` + 邮件页接入 + i18n 键 |
| C4 | `test(104): P1 用例与定向破坏读数` | U1–U8 + I1–I6 + F1–F4；D1–D10 逐条做、逐条还原 |
| C5 | `docs(104): P1 交付登记与对比报告订正` | 落点表全部；**`falsification-evidence.md` 在此产生**（它记的是**实测读数**，开工前无法写，**不得预先编造**）；`CRM_FEATURE_COMPARISON.md` 的 2.10 处置（见落点表的三条要求） |
| C6+ | `feat(104): 客户摘要（P2）` / `跟进润色（P3）` / `商机建议（P4）` | **各一组，每组 = 一个可交付、可独立过门禁的阶段**（D0）。**任一组之后都可停** |

**C2 落地时允许新代码暂无对应用例**（照 102 先例的**显式红窗**），门禁在 C4 之后跑第一次；C2 只做 `mvn -B -q compile` 级自证。

---

## 提交纪律

`ListAgents` 确认无并行会话写同一批文件 → **逐路径 `git add`，禁用 `git add -A` / `git commit -a`**（本仓多会话共用工作区，**同文件里的对方 hunk 也会被扫走**，甚至能撞在**同一行**）→ 提交信息末尾带 `Co-Authored-By: Claude Code <noreply@anthropic.com>`（逐字）。

**不把提交自己的哈希写进它携带的文件**；**复选框绝不回填/预勾**（`tasks.md` 只在交付时勾）。**订正不静默**：原文逐字保留 + 带日期 ⚠️ 块，粒度到**每一列**，自查判据是「**旧值仍能被 grep 到**」（排除行首注释后统计，并显式许可测试里的负断言）。**不编辑任何已应用的迁移**；**不跑任何 `/speckit-*`**（`.specify/feature.json` 是共享单槽指针，会被并行会话覆盖）。
