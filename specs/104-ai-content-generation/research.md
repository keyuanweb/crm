# 调研与已核实事实（104）

**原则**: 本节每条**要么读过实现、要么实测**。凡引用二手读数的地方**逐条复跑**（本仓有"引用旧数字导致假绿"的先例）。**未核实的写进 §10，不混进结论。**

---

## §1 上游与立项依据（**2026-09-27 亲测复跑**，非引用 09-14 快照）

`CRM_FEATURE_COMPARISON.md` **2.10 AI 能力** 自述其"总闸门实测"为零命中。**该快照日期为 2026-09-14**，故本项**逐项复跑**：

| 探针 | `backend/src/main/java` + `pom.xml` | `frontend/src` + `package.json` |
|---|---|---|
| `openai` / `llm` / `anthropic` / `dashscope` / `qwen` / `deepseek` / `embedding` / `langchain` / `ollama` | **各 0** | **各 0** |
| `/ai/` 端点 | **0** | — |

**结论**：该域的"完全静止"表述**今天仍然成立**（09-27 复跑，零漂移）。

⚠️ **一处口径教训（须记）**：`find -iname "*Ai*"` 会把 `Email*` 全部捞出来（"Em**ai**l" 命中），**不能**用作 AI 存在的判据。本项的所有 AI 判定一律用**依赖名探针**，不用文件名匹配。

---

## §2 ⚠️ 022 的数据权限缺口（**本项 D2 决策的上游，已复核**）

`SuggestionController` 的类注释（`SuggestionController.java:21-26`）逐字承诺：

> 建议列表的全部数据来自 `CustomerService.atRiskCustomers` / `FollowUpMapper` / `SalesOpportunityMapper` / `LeadMapper`，**前三者本身已按数据范围过滤**，第四条线索同样受角色的可见范围约束——撤门不会让任何人看到范围外的数据

**实测：四条规则里只有第一条成立。**

| 规则 | 取数 | 数据范围过滤 | 依据 |
|---|---|---|---|
| 1 客户流失 | `CustomerService.atRiskCustomers` | ✅ | `CustomerService.java:226` 有 `applyDataScopeFilter(qw)` |
| 2 商机停滞 | `soMapper.selectList(...)` | ❌ | `SuggestionService.java:87-92` |
| 3 待跟进客户 | `followUpMapper.selectList(...)` | ❌ | `SuggestionService.java:110-115` |
| 4 高分线索 | `leadMapper.selectList(...)` | ❌ | `SuggestionService.java:150-154` |

**验证方法（三层，全仓口径）**：
1. 直读源码，确认三条走**裸 Mapper**、无任何 owner 条件；
2. `MybatisPlusConfig.java:19-24`：全仓**只注册两个** `InnerInterceptor`（`PaginationInnerInterceptor` + `OptimisticLockerInnerInterceptor`）；
3. `grep DataPermissionInterceptor|DataScopeInterceptor|TenantLineInnerInterceptor` 全仓 **零命中** ⇒ **不存在隐式兜底**。

**后果**：022 的 spec Edge Cases 明文要求「建议仅包含当前用户可访问的客户/商机/线索（SALES 仅自身）」，**不满足**；且该端点按注释 1.5 **已撤除权限码** ⇒ 任一登录用户可取得全库的停滞商机、待跟进客户、高分线索。

**为什么既有测试抓不到**：`SmartSuggestionIT` 三个用例全是形状断言（`isOk()` + `isArray()`），不断言内容，且用管理员令牌——**管理员在权限切面直通**。这是本仓已知的那类假绿。

**对照留痕**：同仓的 `OrderController.java:45` **诚实地写明**「两者都没有任何数据范围过滤」。022 的做法相反。

⚠️ **本项不修 022**：那是**独立缺陷、须独立立项与独立回退**（不同关注点）。**本项只是要求自己不得复制它**（D2 / I3）。

---

## §3 LLM 接入就绪度：既有 vs 缺失

**EXISTS（可复用）**

| 能力 | 落点 |
|---|---|
| 出站 `RestTemplate` | `config/RestTemplateConfig.java:34`（`SimpleClientHttpRequestFactory`，**禁跟随重定向**） |
| 出站调用范例 | `service/WebhookDeliverer.java:53,146`（HMAC 签名 + ≤3 次退避重试 + `@Async`） |
| 「未配置」判据类房规 | `config/MailInboundStatus.java:19,22`（唯一判据源 + 消息常量）；`config/MailStatus.java:29`（"能否真的发出去"的唯一判据） |
| 受控拒绝码 | `ErrorCode.java:153` `MAIL_INBOUND_NOT_CONFIGURED(409)` |
| 启动期配置校验先例 | `config/SecurityDefaultsGuard.java`（空 = 合法但告警；有值但畸形 = 启动即失败） |
| Redis | `config/RedisConfig.java:20`；消费者含 `RateLimitStore` / `MfaStateStore` / `TokenService` / `CaptchaService` / `SuggestionService` |
| 限流 | `security/RateLimit.java` + `RateLimitAspect.java` + `RateLimiter.java` + `RateLimitStore.java`；429 + `Retry-After`（`GlobalExceptionHandler.java:159-163`） |
| **限流台账护栏** | `security/RateLimitCoverageTest.java` —— **字节码扫描**：每个 Controller 方法须带 `@RateLimit` **或**在豁免清单内且理由非空 |
| 权限 | `common/RoleConstants.java:105` `PERMISSION_DEFS`（145 处 `entity:action` 形式）；`security/RequirePermission.java` + `PermissionAspect.java`（ADMIN 直通） |
| 权限三道护栏 | `RequirePermissionCatalogTest`（注解 ⊆ 字典）/ `PermissionMatrixIT`（授权 ⊆ 字典）/ `frontend/scripts/check-perms.mjs` |
| 审计 | `service/AuditService.java`（`record` :29 用户归属 / `recordAsSystem` :44 后台），**141 个调用点** |
| 错误模型 | `common/ErrorCode.java`（150 个常量）+ `BusinessException` + 统一处理器 `GlobalExceptionHandler` |
| 进程内缓存 | `config/CacheConfig.java:22`（Caffeine，**刻意不用 `@Cacheable`**，理由见其 `:15-19`） |
| Flyway | `application.yml:16-19`（`baseline-on-migrate: true`） |

**ABSENT（须新增或缺席，均已实测）**

| 缺失 | 影响 |
|---|---|
| **任何 LLM 依赖**（§1 零命中） | 须新增 SDK 依赖 |
| **任何第三方 API key 占位**（`.env` 只有 MySQL/Redis/JWT/MFA/SMTP/出站白名单） | 须新增配置项与文档 |
| **SSE / 服务端流式**（`SseEmitter` / `Flux` / `text/event-stream` 全零） | 非目标，不引入 |
| **markdown 渲染**（`react-markdown`/`marked`/`remark` 全无） | 输出按纯文本处理（FR-012） |
| **前端流式读取**（无 `EventSource`/`ReadableStream`/`getReader`） | 同上 |
| **有界 `@Async` 执行器**（无 `AsyncConfigurer`、无 `ThreadPoolTaskExecutor`） | **D3：故用同步调用** |
| **`@ConfigurationProperties`** | 全仓 0 命中 ⇒ 照旧用 `@Value`（原则二） |
| **数据权限 MyBatis 拦截器** | §2 的根因；本项靠"禁止直查裸 Mapper"纪律规避 |

---

## §4 ⚠️ 出站防火墙：**默认拒绝一切外部主机**

- `common/OutboundUrlValidator.java` **默认拒绝所有出站目的地，含公网**；白名单键 `crm.outbound.allowed-hosts`（`application.yml:84`），环境变量 `CRM_OUTBOUND_ALLOWED_HOSTS`（`.env.example:48`）。
- 校验是**三阶段决策**（scheme → 显式白名单 → CIDR），白名单优先；契约见其 `:59-63`（**禁跟随重定向 + 逐跳校验**）。
- ⚠️ **架构冲突**：官方 Anthropic Java SDK 内部走 **OkHttp**，**不使用**这个 `RestTemplate`，因此**绕过**该校验器。本项以**启动期一次校验**替代（plan D1），并在 `plan.md` 的 Constitution Check 原则三显式声明该偏离。

**这是本项第一个会撞上的墙**：三档方案的部署前置都是"把模型主机加入白名单"。

---

## §5 版本与迁移现状（**实测**）

| 项 | 实测值 | 命令 |
|---|---|---|
| 迁移文件数 | **90** | `ls db/migration/*.sql \| wc -l` |
| 最高版本 | **V91**（`V91__field_permission_builtin_fields.sql`） | — |
| 缺号 | **仅 V72** | 逐号比对 |
| ⇒ 本项新迁移 | **V92** | — |
| `schema-h2.sql` 的版本标记 | 行尾 `-- V<n>` 形式，**65 处**；如 `field_key VARCHAR(64), -- V91（…）` | `grep -c -- "-- V[0-9]+"` |
| 平价护栏 | `SchemaParityIT.java:50`（镜像版本清单）+ `SchemaIdempotencyIT`（须可重跑）+ `MigrationDdlCollisionIT` | — |
| `AbstractIntegrationTest.java:38` 加载 H2 脚本 | — | — |

---

## §6 门禁与冻结债现状（**实测**）

**前端八道**（CI 顺序，`ci.yml:53-110`）：`typecheck` → `lint` → `i18n:check` → `menu:check` → `perms:check` → `ui:check` → `zh:check` → `test:coverage` → `build`（+ 独立 e2e job）

| 门禁 | 现状读数 | 说明 |
|---|---|---|
| `i18n:check` | **2966 / 2966 键**；路由 58 / 清单 56 / 别名 3 | ⚠️ **我 grep 得 3176，与门禁不符** ⇒ **以门禁为准**，grep 是另一套口径，**不能当判据** |
| `ui:check` | **冻结台账 54 处**（`check-ui.mjs:901` 的 `allowedTotal`） | **双向**：新增违规红 + 台账条目**过期也红** |
| `zh:check` | **4 条 / 266 处**（`ZH_ALLOWED`，`check-zh.mjs:190`） | 三分支：未登记命中 / 计数偏高 / 计数偏低或归零 |
| 覆盖率阈值 | `jacoco:check` INSTRUCTION **0.73**（`pom.xml:322`）；当前实测 **82.18%** | ⚠️ `pom.xml:316-320` 自述「固定阈值必然逐渐变松」 |

⚠️ **`mvn verify` 的坑**：不给 `-Dmaven.test.failure.ignore=true` 时可能在覆盖率判定**之前**就中止；**绝不传 `-DargLine`**（会静默挤掉 JaCoCo agent，`jacoco.exec` 不生成而构建全程无报错）。
⚠️ **spotless 缓存命中不算"真解析过"**（「N were skipped because caching determined…」）⇒ 须移走 `target/spotless-index` 复跑取 `skipped 0`。

### §6.1 ✅ 开工基线实测（T004，2026-09-27）——**上表逐格复跑，零漂移**

上表是立项期写的；开工前**逐道实跑复测**，读数**与表中逐字相同**（故本项**没有**触发任何订正；本仓少见的一次"复测即吻合"）：

| 门禁 | 开工实测（原样输出摘要） | 与上表 |
|---|---|---|
| `i18n:check` | `zh-CN 2966 键 / en 2966 键；路由 58 条 / 清单 56 项，粗粒度别名 3 条`；退出码 **0** | **一致** |
| `menu:check` | `56 个菜单项`，来源 `RoleConstants.MENU_TREE`；退出码 **0** | 一致 |
| `perms:check` | `68 个权限码；8 个文件含已登记的 ADMIN 判断，共 9 处`；退出码 **0** | 一致（**本项加 `ai:generate` ⇒ 交付时应为 69**） |
| `ui:check` | `扫描 271 个产品文件（其中 125 个 tsx）、303 个 Form.Item`；`白名单内冻结的既存债 54 处，未新增违规`；退出码 **0** | **一致** |
| `zh:check` | `扫描 268 个产品文件、候选点 9162 个`；`未登记命中 0 处；台账内冻结 266 处、4 条`；口径外 55（只印不判）；退出码 **0** | **一致** |

**结构性计数基线**（口径 = **文件 / 类数**，**不是用例数**——用例数要跑 `mvn verify` 与 `test:coverage` 才有，属 T040/T041，**不得在此混用**）：

| 项 | 基线 |
|---|---|
| 迁移脚本 | **90** 个，最高 **V91**，**缺号仅 V72**（集合差判据，见下方 ⚠️） |
| `schema-h2.sql` 的 `-- V<n>` 标记 | **65** |
| 后端主代码类 | **608** |
| surefire **用例类** | **110** |
| failsafe **用例类** | **86** |
| 前端测试文件 | **95** |

⚠️ **一处探针失效（同类第二例，须记）**：判"迁移有没有缺号"时我第一版写的是

```bash
for i in $(seq 1 91); do ls */V$i__*.sql >/dev/null 2>&1 || echo -n "V$i "; done   # ❌ 恒不报缺号
```

**它错在 `$i__` 被 shell 当成变量名 `i__`**（下划线是合法变量名字符）⇒ 展开成空串 ⇒ glob 变成 `V*.sql` ⇒ **总是命中、退出码 0 ⇒ 永不报缺号**。**它给出的是"无缺号"，与真值（缺 V72）相反，且失败形态是静默的**。
**正确判据是集合差**：

```bash
ls src/main/resources/db/migration/ | grep -oE '^V[0-9]+' | sed 's/V//' | sort -n > /tmp/have.txt
comm -13 /tmp/have.txt <(seq 1 91)     # 应输出 72
```

**元教训**：与 §7.1 同一族——**探针必须自证覆盖了整个总体**；本例还多一层，**变量名的边界也是一种"模式的边界"**。凡在 shell 里拼 `$var` 后紧跟 `_` 或字母数字，一律写成 `${var}` 显式定界。

---

## §7 `ErrorCode` 分布（用于选码，**2026-09-27 实测**）

400×10 / 401×8 / 403×8 / 404×38 / **409×31** / 422×48 / 429×2 / 500×3 / **503×1**；**501 零先例**。

⇒ 本项的选码理由见 `plan.md` 的表与 `contracts/` §4。**开工时须重测**（分布会随并行批次变化）。

### §7.1 ⚠️ **2026-09-27 开工实测（T000）：上面那串分布里 `422×48` 是错的，真值 `422×49`**

**旧值 `422×48` 逐字保留在上**（不是静默改写）。它是**我的 grep 漏了一处**造成的，不是分布变了：

| | |
|---|---|
| 失效模式 | `grep -oE '\([0-9]{3}, "'` —— 要求 `(` **紧跟**数字 |
| 漏掉的 | `ErrorCode.java:35-36` 的 `OPPORTUNITY_STAGE_PROBABILITY_INVALID(` —— 它的 `422` 在**下一行**，故整条常量没被计数 |
| **自证方式（关键）** | 命中数 **149** ≠ enum 常量数 **150** ⇒ **该不符本身就是判据**。若只看 `422×48` 这一格的绝对值，这个漏是**自证不了的**——缺的那项不会出现在结果里 |
| 修正后 | `grep -oE '[0-9]{3}, "'` ⇒ **150** 命中，与常量数**逐字吻合**；逐码点交叉复核亦一致 |

**实测分布（2026-09-27，T000）**：400×10 / 401×8 / 403×8 / 404×38 / **409×31** / **422×49** / 429×2 / 500×3 / **503×1**；**501 仍为零先例**（`grep -c '501, "'` → **0**）。

**复算命令**（读者可自证）：
```bash
f=backend/src/main/java/com/crm/common/ErrorCode.java
grep -oE '[0-9]{3}, "' $f | wc -l            # 必须 = 150（= enum 常量数）
grep -oE '[0-9]{3}, "' $f | grep -oE '^[0-9]{3}' | sort | uniq -c
grep -c '501, "' $f                          # 0
```

**教训（同类，本仓已有第二条）**：这是「**grep 模式的边界要自证**」的又一例。**判据不是"我扫到了什么"，而是"我的扫描覆盖了整个总体"**——凡按模式数一个**已知总数**的集合，**命中数必须等于那个总数**，否则模式有漏；只报分格数（如 `422×48`）时，这个漏**永远自证不了**。

---

## §8 前端可复用件与缺口

| 复用的 | 落点 |
|---|---|
| API 客户端 | `services/apiClient.ts:40`（全局 **30s** 超时 `:38`）；**逃生口 `timeout: 0`** 已是房规（`:36` 注释） |
| 错误提取 | `extractErrorMessage` `:86` / `extractErrorCode` `:109` / `isVersionConflict` `:68` |
| 最接近的对话/消息形 UI | `components/CommentSection.tsx`（Avatar + Timeline + Input + `@mention`） |
| 实时推送先例 | `hooks/useNotificationSocket.ts`（指数退避 1s→30s，降级轮询）+ `NotificationCenter.tsx` |
| 时间线/卡片 | `FollowUpTimeline.tsx` / `AnnouncementCard.tsx` |

**缺口**：无 markdown 渲染、无流式读取 ⇒ 本项输出按**纯文本**处理。

---

## §9 命名空间与既有占用

| 资源 | 现状 | 本项 |
|---|---|---|
| Redis `ai:*` | **已被 022 占用**：`SuggestionService.java:36` `IGNORE_PREFIX="ai:ignore:"`，键 `ai:ignore:{userId}:`、成员 `entityType:entityId`、TTL 90 天 | 走 **`ai:gen:budget:*`**（D8），两族互不影响 |
| `/api/v1/suggestions` | 022 已占 | 本项走 **`/api/v1/ai/*`** |
| 权限码 | 145 处 `entity:action` | 新增 **`ai:generate`** |
| 迁移号 | 已到 V91 | 本项 **V92** |

⚠️ 本项新增 `/api/v1/ai/*` ⇒ **会使对比报告 2.10 的「`/ai/` 端点零命中」表述失效** ⇒ 必须按落点表的三条要求处置（原文逐字保留 + 带日期 ⚠️ 块 + 分值重算）。

---

## §10 ⚠️ 未核实事项与存疑（**不得读成已确认**）

1. **官方 Java SDK 的 base URL 配置方法未验证**。捆绑参考只列了 `.apiKey(...)`；**未列出** base URL 的 builder 方法名。SDK 的 client config 确实支持 base URL（或 `ANTHROPIC_BASE_URL` 环境变量），但**具体 Java 端写法未确认**。**实施时以编译报错为准**（照该参考的显式指引：先写文件再让编译器指路），**不得**在此臆断方法名。<br>⇒ ✅ **2026-09-27 T001 已实测结清**：原文逐字保留在上，**该条已不成立**——方法名实测为 **`baseUrl(String)`**（javap 读数 + 复算命令见 **§11.1**）。
2. **SDK 版本未锁定**。捆绑参考列的是 `com.anthropic:anthropic-java:2.34.0`；**实施时须以当时的最新稳定版为准**，并确认与本仓 Spring Boot / JDK 21 的兼容性。<br>⇒ ✅ **2026-09-27 T002 已实测结清**：原文逐字保留在上，**该条已不成立**——锁定 **`2.65.0`**（最新 release），JDK 21 兼容性以**字节码主版本**判过（major 52），见 **§11.2**。

⚠️ **本段两处的留痕口径（自查记录，2026-09-27）**：这两条**第一稿写错了**——当时写的是「~~**官方 Java SDK 的 base URL 配置方法未验证**。~~ ⇒ ✅ …（原文逐字保留在上，不静默改写）」，即**一边声称"原文逐字保留"、一边把原文删掉了**（原句里那些实测后才知其重要的话，如"捆绑参考只列了 `.apiKey(...)`""以编译报错为准"，**全被抹掉**）。**这正是「订正不静默」要防的形态：改写者自己声明保留了，判据却没人核。** 自查判据 = **旧值仍可 grep 到**：`grep -n '以编译报错为准\|2\.34\.0' research.md` ⇒ 修正后**两条都能命中**。

⚠️ **这段自查的可复现性必须说清（不得含糊）**：被删的那两句**只出现在工作区的中间态里，从未提交** ⇒ 「修正前零命中」这个读数**读者无法从 git 复现**。可复现的部分是反方向的：`git show HEAD:specs/104-ai-content-generation/research.md | grep -c '以编译报错为准'` ⇒ **1**，即 **C1 提交里原文是在的**，是我随后的编辑把它删掉又补回来的。**记此以免把一句不可复现的读数当成证据。**
3. **模型默认值**。`spec.md` FR-001 写默认 `claude-opus-5`。**这是"能力最强"的默认而非"最便宜"的默认**——`plan.md` 未做成本测算，**因本项出厂默认 `enabled=false`**（不配置即零成本）。若将来要给出成本敏感的默认，须另做测算。
4. **提示缓存是否命中未测**。D4 已要求"默认按不命中预期"，实施时以 `usage().cacheReadInputTokens()` 实测为准。
5. **跟进记录的 `N`（送入条数）未定稿**。`contracts/` §5.2 留作实施期决定，须与超预算截断策略一起定。
6. **四项能力的相对价值未做用户验证**。P1–P4 的优先级来自**工程风险排序**（P1 零新增持久化 ⇒ 风险最低），**不是**来自用户调研。若实际使用中 P3/P4 更高频，优先级应据实调整。
7. **022 缺陷的运行时复现未做**。§2 的结论是**代码级**判定（三层验证，见上），**未**用 SALES 令牌实调 `GET /suggestions` 观察跨 owner 泄漏。若要作为独立立项的依据，**须补一次运行时的行为层验证**（并注意管理员令牌会直通、抓不到）。

---

## §11 ✅ 开工前置实测：SDK 面（T001 / T002，2026-09-27）

**验证方式（决定了这些读数的可信度）**：从 Maven Central 取回制品到**本地 `~/.m2`**（**仓库工作区零改动**），再用 `javap` / `unzip -l` 读**真实 class 文件**。
⇒ 本节的每一条都是**字节码级读数**，**不是**文档引用、**不是**记忆推断。**上一条被标红的未核实项（§10.1）就此结清。**

### §11.1 ✅ T001：base URL 的方法名是 `baseUrl(String)`（**大写 U**）

```java
// 实测签名（javap -classpath <client-okhttp.jar>:<core.jar> 'com.anthropic.client.okhttp.AnthropicOkHttpClient$Builder'）
public final AnthropicOkHttpClient.Builder baseUrl(java.lang.String);
public final AnthropicOkHttpClient.Builder baseUrl(java.util.Optional<java.lang.String>);
```

⚠️ **注意大小写**：是 **`baseUrl`**，**不是** `baseURL`、`base_url`、`setBaseUrl`。这正是 §10.1 拒绝臆断的那一格 —— 猜的话有三个都说得通的写法。
**入口**：`AnthropicOkHttpClient.builder()`（静态）→ `Builder`；另有 `AnthropicOkHttpClient.fromEnv()`（读环境变量，**本项不用**——FR-001 要求配置显式经 `@Value`）。

**同一批实测到的其它绑定**（全部命中，无一落空）：

| 用途 | 实测绑定 |
|---|---|
| `Message` 参数 | `MessageCreateParams.Builder.{model, maxTokens(long), system, systemOfTextBlockParams, thinking, outputConfig, build}` |
| 自适应思考 | `thinking(ThinkingConfigAdaptive)` 有**专用重载**；`ThinkingConfigAdaptive` 类存在 |
| usage 取数（FR-016） | `Usage.{inputTokens, outputTokens, cacheReadInputTokens, cacheCreationInputTokens}` |
| 错误判别 | `AnthropicServiceException.errorType()` → **`Optional<ErrorType>`**（⚠️ **是 Optional，不是裸值**）；`RateLimitException` / `AnthropicRetryableException` / `AnthropicIoException` / `UnprocessableEntityException` 均存在 |
| Builder 方法名清单另含 | `apiKey` / `timeout` / `maxRetries` / `build`（**仅读到方法名，参数类型未逐个读**） |

### §11.2 ✅ T002：版本锁定 **`2.65.0`**（**不是**捆绑参考的 `2.34.0`）

`maven-metadata.xml` 实测：`<latest>2.65.0</latest>` / `<release>2.65.0</release>`；**`2.34.0` 是旧版**。
**上表与 §11.1 的每一个探针都在 `2.65.0` 上复跑过一遍，全部命中** ⇒ 这次锁定**不是**照旧版抄，也**不是**赌新版没改。

**JDK 21 兼容性（决定性判据 = 字节码主版本，与 089 升 21 时用的是同一条）**：SDK core 的 class **major 52（Java 8）**、`okhttp 4.12.0` 同为 **major 52** ⇒ 远低于 JDK 21 的 **65**，**在读取上限之内**。

⚠️ **一个会让人误判的形态（必读）**：`com.anthropic:anthropic-java` **本身是个 346 字节的空壳 jar**（只有 `META-INF/MANIFEST.MF`，**零个 class**）。真正的类来自它的依赖链：`anthropic-java` → `anthropic-java-client-okhttp` → `anthropic-java-core`（19 MB）。**看到那个 346 字节的 jar 不要以为依赖没拉下来。**

### §11.3 ⚠️ 两处须在 C2 处置的新发现（**本节新增，立项期未知**）

| # | 发现 | 处置 |
|---|---|---|
| a | **Jackson 版本落差**：SDK 按 **2.19.4** 构建；本仓 Spring Boot **3.2.0** 的 BOM 把 jackson 管理在 **2.15.3** ⇒ Maven 最近者优先会让 SDK 跑在**比它构建时更老**的 Jackson 上 | C2 加依赖后**必须**跑 `mvn -B dependency:tree -Dincludes=com.fasterxml.jackson.core` **取实际解析版本并记录**。**不得**为迁就 SDK 而全局升 Jackson（会牵动全仓每个模块，**超出本批范围**） |
| b | **净新增的运行时足迹**：`okhttp 4.12.0` + `kotlin-stdlib-jdk8 1.9.0` + `kotlin-reflect 1.9.0` + `jackson-datatype-jdk8/jsr310` + `jackson-module-kotlin` + `com.github.victools:jsonschema-* 4.38.0`。**仓内今天既无 okhttp 也无 kotlin**（`grep -E 'okhttp\|kotlin' backend/pom.xml` **零命中**） | 这些是**本项净新增**的运行时依赖 ⇒ 须进 `DELIVERY_SCOPE.md`（T044），并核对胖 jar 体积；`quickstart.md` §2 的"三堵墙"之外，部署侧多了这一条**依赖足迹** |

**工作区状态自证**：本次只读制品 + 写 `~/.m2`（**不在 git 树内**）⇒ `git status` 应仍只有 `104-ai-content-generation` 之外无改动。<br>复算命令：
```bash
J="$JAVA_HOME/bin/javap.exe"
CP="$HOME/.m2/repository/com/anthropic/anthropic-java-client-okhttp/2.65.0/anthropic-java-client-okhttp-2.65.0.jar:$HOME/.m2/repository/com/anthropic/anthropic-java-core/2.65.0/anthropic-java-core-2.65.0.jar"
"$J" -classpath "$CP" 'com.anthropic.client.okhttp.AnthropicOkHttpClient$Builder' | grep baseUrl
```
