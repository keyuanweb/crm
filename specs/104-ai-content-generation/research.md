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
| a | ⚠️ **2026-09-27 C2 已实测**：解析结果**没有一个 2.19.x**（全部 2.15.3），且 SDK 在 2.15.3 上**真跑通了一次**（探针读数见 **§12.1 / §12.2**）。本行余下原文逐字保留。**Jackson 版本落差**：SDK 按 **2.19.4** 构建；本仓 Spring Boot **3.2.0** 的 BOM 把 jackson 管理在 **2.15.3** ⇒ Maven 最近者优先会让 SDK 跑在**比它构建时更老**的 Jackson 上 | C2 加依赖后**必须**跑 `mvn -B dependency:tree -Dincludes=com.fasterxml.jackson.core` **取实际解析版本并记录**。**不得**为迁就 SDK 而全局升 Jackson（会牵动全仓每个模块，**超出本批范围**） |
| b | ⚠️ **2026-09-27 C2 实测订正：本行 kotlin 版本号 `1.9.0` 不准确（实测 `1.9.20`），且漏记 `okio 3.6.0`。旧值逐字保留在本行，权威读数见 §12.1**。**净新增的运行时足迹**：`okhttp 4.12.0` + `kotlin-stdlib-jdk8 1.9.0` + `kotlin-reflect 1.9.0` + `jackson-datatype-jdk8/jsr310` + `jackson-module-kotlin` + `com.github.victools:jsonschema-* 4.38.0`。**仓内今天既无 okhttp 也无 kotlin**（`grep -E 'okhttp\|kotlin' backend/pom.xml` **零命中**） | 这些是**本项净新增**的运行时依赖 ⇒ 须进 `DELIVERY_SCOPE.md`（T044），并核对胖 jar 体积；`quickstart.md` §2 的"三堵墙"之外，部署侧多了这一条**依赖足迹** |

**工作区状态自证**：本次只读制品 + 写 `~/.m2`（**不在 git 树内**）⇒ `git status` 应仍只有 `104-ai-content-generation` 之外无改动。<br>复算命令：
```bash
J="$JAVA_HOME/bin/javap.exe"
CP="$HOME/.m2/repository/com/anthropic/anthropic-java-client-okhttp/2.65.0/anthropic-java-client-okhttp-2.65.0.jar:$HOME/.m2/repository/com/anthropic/anthropic-java-core/2.65.0/anthropic-java-core-2.65.0.jar"
"$J" -classpath "$CP" 'com.anthropic.client.okhttp.AnthropicOkHttpClient$Builder' | grep baseUrl
```

---

## §12 ✅ C2 实做实测（2026-09-27，加依赖之后跑的）

**本节是 C2 全部读数的权威落点**；`plan.md` / `tasks.md` / `contracts/` / `quickstart.md` 只留指针，不复制数字。

### §12.1 解析出的版本（**权威口径 = `dependency:list`，不是文档也不是 pom**）

复算：`cd backend && mvn -B dependency:list -DincludeScope=runtime | grep -E 'anthropic|okhttp|okio|kotlin|jackson'`

| 制品 | 解析版本 | 备注 |
|---|---|---|
| `com.anthropic:anthropic-java` | **2.65.0** | 聚合 jar（346 字节，只有 MANIFEST）|
| `anthropic-java-client-okhttp` / `-core` | **2.65.0** | 真正的类在这两个里 |
| `com.fasterxml.jackson.core:{core,databind,annotations}` | **2.15.3** | **全部** 2.15.3，**没有一个 2.19.x** |
| `jackson-module-kotlin` | **2.15.3** | 运行时；随之新进 classpath |
| `com.squareup.okhttp3:okhttp` | **4.12.0** | 净新增 |
| `com.squareup.okio:{okio,okio-jvm}` | **3.6.0** | 净新增（§11.3 未记，本次补）|
| `org.jetbrains.kotlin:*` | **1.9.20** | 净新增，五件（stdlib / stdlib-jdk7 / jdk8 / reflect / stdlib-common）|
| `com.github.victools:jsonschema-*` | **4.38.0** | 三件，净新增 |

⚠️ **两处订正 §11.3 自己的读数**（旧值逐字保留在 §11.3，不静默改写）：
- §11.3(b) 记的 `kotlin-*-1.9.0` **不精确**，实测 **1.9.20**。原因：当时读的是 **2.34.0 的 pom**（那里是 1.8.0）与模糊印象，**没有对 2.65.0 的解析结果取数**。教训与 §6.1 同类——**版本要取解析结果，不要取声明**。
- §11.3(b) 未记 **okio 3.6.0**（okhttp 的传递依赖）。本次补上。

**Jackson 落差（§11.3(a)）的实测结论**：没有 2.19.x 出现在任何范围里，**最近者优先把 SDK 按 2.15.3 跑**。是否致命 —— 见 §12.2 的实测，**不靠推理**。

### §12.2 一次性探针：SDK 在 2.15.3 上**真跑通了一次**

推理"2.15.3 应该够用"不算数，故起了一个**回环桩 HTTP 服务**、用 SDK 真发一次请求（请求序列化 + 响应反序列化都会真的走 Jackson），**用完即删**（探针文件在临时目录，未进仓库）。读数：

```
PROBE client built  (baseUrl binding works at runtime)
PROBE call returned (serialize + deserialize both worked)
PROBE text            = PROBE_OK
PROBE stopReason      = Optional[max_tokens]
PROBE MAX_TOKENS.equals(stopReason) = true
PROBE of("max_tokens").equals(MAX_TOKENS) = true
PROBE inputTokens     = 11
PROBE outputTokens    = 7
PROBE request body    = {"max_tokens":4096,"messages":[{"content":"USER PAYLOAD","role":"user"}],
                        "model":"claude-opus-5","system":"STABLE SYSTEM PREFIX"}
PROBE RESULT = OK
```

它同时结清了四件事：
1. **`baseUrl(String)` 在运行时也成立**（不只是编译期），§11.1 的大小写结论**运行期复核过**。
2. **2.15.3 上请求/响应两个方向都通** ⇒ Jackson 落差**在本项用到的这条路径上不是阻塞项**。
   ⚠️ **边界（不得读过头）**：这证明的是**本项用到的那几条路径**，不是"SDK 的全部特性在 2.15.3 上都没问题"。本项不用工具、不用流式、不用 beta，故这是够用的范围。
3. **截断判定成立**：`StopReason.MAX_TOKENS.equals(...)` 与 `of("max_tokens").equals(MAX_TOKENS)` **两条都为 true** ⇒ `AiContentService` 里那个判定不是猜的。
4. **FR-009 的分层在报文体上成立**：`system` 是稳定前缀、`messages[0].content` 才是含数据的那段，两者在**线上**确实是分开的两个字段。

📌 **一条给部署方的实测发现（`baseUrl` 的用法）**：SDK 请求的路径是 **`/v1/messages`，由 SDK 自己拼**。故 `CRM_AI_BASE_URL` 应填**主机根**（如 `https://api.anthropic.com`），**不要**带 `/v1` ——带了会打到 `/v1/v1/messages`。这条已写进 `.env.example`。

### §12.3 ⚠️ **C2 范围订正：权限码与迁移 `V92` 不能在 C2 落地**

`plan.md` 原定 C2 = `AiStatus` + 两个异常 + 三个 `ErrorCode` + `AiContentService` + **`V92` 权限码** + `schema-h2.sql` 同步 + 前端 `permissions.ts`，且"C2 **无端点、无 UI**"。**后三项与"无端点"自相矛盾**，且被**三条独立的仓内规矩**同时按住：

| # | 来源 | 内容 |
|---|---|---|
| 1 | `UnwiredPermissionCodeTest`（**双向冻结台账**）| 字典里**没有被任何 `@RequirePermission` 引用**的码，必须逐条登记在那份 **22 条**台账里（A 类 11 / B 类 11，`hasSize` 写死）；其类 javadoc 明写台账管**存量**（"不要因未用而删"），不管新增 |
| 2 | `specs/096-.../spec.md:116`（非目标 4）| 「**只建被端点真引用的码**」——码与端点同批 |
| 3 | `PermissionMatrixIT:59-99`（FR-G14 判例）| 六个"零授予"码的先例：字典 + 注解 + **迁移里一条授予都没有**，且把"零授予"**写成可执行记录** |

**实测（不是推理）**：把 `ai:generate` 临时加进 `PERMISSION_DEFS`（**按顶层分组**）后跑三道护栏 ——
`UnwiredPermissionCodeTest` **转红**，断言原文 **`Expecting empty but was: ["ai:generate"]`**（破坏前 5/5 绿，还原后 5/5 复绿；`sha256` 逐字相等）。**故 C2 加码必红，三条落点（字典 / `V92` / 前端）整体后移到 C3**（与第一个端点同批）。

⚠️ **同一次实验里有一条预测被打脸，必须记下**：我预判 `FrontendPermissionCodeAlignmentTest` 也会红——**没有**。那道护栏是**单向的**（`FrontendPermissionCodeAlignmentTest:76` 校验"前端登记的码 ⊆ 字典"），**字典里有、前端没有**它**不管**。 含义：前端 `permissions.ts` 落后于字典时**没有任何门禁看得见**（`RequirePermissionCatalogTest` 同理，它管"注解 ⊆ 字典"）。本条是本项 T017 的**人工判据**，不是自动判据。

⚠️ **一次失败实验的形态**：第一次破坏我把 `permGroup(...)` 写进了**另一个 group 的 varargs 里**（嵌套分组）⇒ 三道护栏一起抛 `NullPointerException`（`TreeSet.add(null)`），**红在与判据无关的原因上**。若就此收尾，会得出一条错误结论（"加码会 NPE"）。**先分辨红的形态，再采信红的含义**。

### §12.4 ⚠️ 订正 `plan.md` D1 的伪码：`isAllowed` 不存在

`plan.md` D1 写的是 `outboundUrlValidator.isAllowed(baseUrl)`。**该方法不存在**（C1 自己标了"命名以实施为准"）。真实可用的入口是 **`OutboundUrlValidator.validate(String url, ErrorCode onReject)`**（抛 `BusinessException`）。 落地取后者：`AiStatus.configurationProblem()` 调它并 `catch (RuntimeException)` 转成一句可读描述，启动期再抛 `IllegalStateException`。
**刻意不改 `OutboundUrlValidator`**（083 所有、多个调用方共用）：加一个只为本项存在的方法，等于把本项的语义塞进一个共享件。用 `validate` 还顺带保证**拒绝措辞与全仓其他出站调用一致**。

### §12.5 FR-002 的"默认值逐字一致"：**六项全等**

```bash
awk '/^  ai:/{f=1;next} /^  [a-z]/{f=0} f' backend/src/main/resources/application.yml \
  | grep -E "^    [a-z-]+:" | sed -E 's/^ +([a-z-]+): \$\{[A-Z_]+:([^}]*)\}.*/\1=\2/'
grep -oE 'crm\.ai\.[a-z-]+:[^}"]*' backend/src/main/java/com/crm/config/AiStatus.java | sed -E 's/crm\.ai\.//;s/:/=/'
```
两侧各输出六行，**逐字相同**（`enabled=false` / `base-url=` / `api-key=` / `model=claude-opus-5` / `max-tokens=4096` / `timeout-seconds=60`）。

⚠️ **第一次跑这条判据时它是假绿的**：我按 4 空格缩进 grep，结果**扫到了别的段**（`captcha.enabled` 等），输出里混进 `enabled=true` ——两侧"都有六行"却**不是同一批**。判据必须**锚在 `ai:` 段内**（如上 `awk`），否则它比的是两个不同的集合。

### §12.6 C2 的门禁子集（**非交付读数**）

| 检查 | 读数 |
|---|---|
| `mvn -B -q compile` | exit 0 |
| `mvn -B spotless:apply` | exit 0 |
| `pnpm run perms:check` | ✓ **68 个权限码；8 个文件 9 处** —— 与 T004 基线**逐字相同**（零漂移）|
| `UnwiredPermissionCodeTest` + `RequirePermissionCatalogTest` + `FrontendPermissionCodeAlignmentTest` | **5/5 绿** |
| `SearchIT`（**证 IT 上下文能带着新 bean 起来**）| 2/2 绿，18.5s |

⚠️ 这一栏**不是**交付读数：C2 按 plan 的"显式红窗"允许暂无对应用例，且这些读数会被 C4/C5 那次完整 `verify` 覆盖（见 `quickstart.md` §5 末条）。

---

## §13 ✅ C3 实做实测（2026-09-27，P1 邮件草稿）

**本节是 C3 全部读数的权威落点**（`plan.md` / `tasks.md` / `contracts/` 只留指针，不复制数字）。

### §13.1 落地形态订正：多了一个 `AiEmailDraftService`（**实现期新增件**）

`plan.md` 的后端结构树里 `service/` 只有 `AiContentService`（出网点）与 `AiPromptCatalog`（常量与白名单），**没有 `AiEmailDraftService`**。落地时新增了它：装配 P1 上下文的那段逻辑（取数 → 判可见 → 过 FLS → 渲染）既不属于"出网点"（那层不认识客户），也不属于"常量与纯函数"（那层刻意不认识 Spring / 数据库 / 当前用户，见其类注释）。

**为什么不能塞进 `AiContentService`**：`quickstart.md` §6 有一条判据是 `grep -rln "apiKey()" backend/src/main/java/com/crm/` **读数应为 1**（只有 `AiClientFactory` 取密钥）。取数逻辑一旦进 `AiContentService`，这个类就会同时持有"密钥的消费方"与"客户数据的装配方"两种身份，那条 grep 断言随之失去区分力——它本来靠的就是"取密钥与别的关注点分居不同文件"。⇒ **分层本身是那条第 6 条判据能成立的前提**，不是洁癖。

**同类的实现期新增件在本项里是第三次出现**（C2 的 `AiClientFactory`、C2 的门禁订正、本次的 `AiEmailDraftService`）：plan 的结构树是**立项期**的预期，落地时的分层按"每层能否被单独断言"定。每次偏离都在本文件留痕，`plan.md` 侧只留指针。

### §13.2 P1 上下文预算定稿（契约 §5.2 "N 见实施" 的落点）

| 项 | 定稿值 | 依据 |
|---|---|---|
| 最近跟进条数 **N** | **5** | `AiPromptCatalog.P1_FOLLOWUP_LIMIT`。取数走 `FollowUpService.page(customerId, null, null, 1, 5)`，其排序是 `createdAt DESC`（`contracts/` 要求"最近 N 条"，而"第 1 页"正是这个语义） |
| 每条节选长度 | **200 字符** | `P1_FOLLOWUP_EXCERPT_CHARS`。**被截的条目在提示词里明写"（本条已节选）"**——"截断并告知"在 P1 退化为逐条告知，而不是一个总阈值 |
| 联系人条数 | 2（只要姓名，用于在最近一条无姓名时有备选） | `P1_CONTACT_LIMIT` |
| 商机明细行数 | 1（取 `stage` / `amount` / `expectedCloseDate`） | `P1_SALES_OPPORTUNITY_LIMIT` |

**单条 user 消息的上界 = 结构给的上界，不是估的**：

```
5 × (200 节选 + ~15 日期与前缀)  ≈ 1.08k
+ 固定字段（客户名/公司/状态/联系人/商机名/金额/阶段/成交日期）  ≈ 0.2k
+ instruction 上限（INSTRUCTION_MAX_CHARS）                     = 4.0k
------------------------------------------------------------
标称上界                                                       ≈ 5.3k 字符
```

⚠️ **本条的第一稿是错的（订正留痕）**：`AiPromptCatalog` 初稿只写"跟进 + 固定字段 ≈ 1.5k"，**把 `instruction` 漏在算式外**——而那是唯一由调用方直接控制、上限还最大（4000）的一段。⇒ **"上界由构造给出"在漏掉最大项时是假命题**，且它朝"看起来更安全"的方向失真。订正后 P1 **仍然不设**"总预算截断阈值"（上界既然由构造给出，就不存在静默丢弃这个失败模式），但那个上界必须是**真的**。

### §13.3 取数路径表（FR-010 的落地，**逐字段**）

规则：**只经既有服务方法取数**（本类不注入任何 Mapper）。每行写出"这一步自带什么校验、缺什么、缺的由谁补"。

| 数据 | 入口 | 自带校验 | 本服务另补的 |
|---|---|---|---|
| 客户 | `CustomerService.require(id)` | **仅 404**（`CUSTOMER_NOT_FOUND`，`:450-456`）——**无范围校验** | `EntityAccessService.canViewCustomer` → 不可见 **403** |
| 商机 | `OpportunityService.require(id)`（`:188`）| **仅 404**。⚠️ **实测：该文件里没有任何范围校验的调用**（`grep -nE "entityAccessService\|dataPermissionService\|canView\|visibleOwnerIds\|visibleCustomerIds\|FORBIDDEN"` → **0 命中**）| `EntityAccessService.canViewOpportunity` → 不可见 **403** |
| 商机明细（stage/amount/日期）| `SalesOpportunityService.page(null, opportunityId, null, 1, 1)` | ⚠️ **无**（同上：`grep` → 0 命中；`require` 在 `:159`，也只管 404）| ① 上游已判过商机可见性；② 本调用**只按 `opportunityId` 过滤** ⇒ 取不到别人家的行（这是"结构性安全"而非"检查后安全"）|
| 联系人姓名 | `ContactService.page(null, customerId, null, 1, 2)` | **有**：非 ADMIN 时按 `customerId IN 可见集` **过滤**（不是抛错） | 无需（客户可见性上面已判）|
| 最近跟进 | `FollowUpService.page(customerId, null, null, 1, 5)` | **有**：行级可见性校验，不可见 → **403**（`:71/74/77`）| 无需 |

📌 **这张表本身是一条判据**：三处"仅 404"的服务（客户 / 商机 / 商机明细）**都必须由调用方补可见性检查**——`CustomerService` 与 `OpportunityService` 的 `require` 都不做范围校验，而本项是**第一个**把这两个 `require` 用在"读出来给外部模型"场景的调用方。⇒ C4 的 I2/I3 必须**分别**覆盖这三条（只测客户那条，会漏掉商机那两条）。

📌 **`canViewOpportunity` 的委托关系不足以省掉客户那一判**：它目前**恰好**委托到 `canViewCustomer(opp.customerId)`（商机无 owner 字段），但那是**另一个类的内部实现**。若它日后改成按商机 owner 判，而本服务只调它，就会静默丢掉客户侧的检查。故本服务**各判各的**（代价：一次查询）。

### §13.4 FLS 门（FR-013）：哪些字段要过、哪些**不需要**过

| 送入项 | 102 是否登记该字段 | 处置 |
|---|---|---|
| `customer.name` / `company` | **未登记**（`BuiltinFieldRegistry` 的 CUSTOMER 组是 `contactPerson` / `phone` / `email` / `address` / `remark` / `status` / `campaignId`）| **不过 FLS**——"既无权限可言，就不存在无权读" |
| `customer.status` | **已登记** | 过：`plan(currentRole(), ENTITY_CUSTOMER)` 含 `status` ⇒ **整行不渲染**（不是渲染成"未知"）|
| `customer.contactPerson` | **已登记** | 过：同上；被 HIDDEN 时**退到联系人表**取姓名 |
| `contact.name` | 联系人实体**不在** CUSTOMER 组内 | 不过 FLS（登记表的实体范围就是 `ENTITY_CUSTOMER`）|
| 商机 `name` / `amount` / `stage` / `expectedCloseDate` | **均未登记**：OPPORTUNITY 组登记的是 `expectedAmountMin` / `expectedAmountMax` / `remark` / `status` 四个，与 P1 送入集**无一相交** | **不过 FLS**。⚠️ 这条结论**依赖白名单**：白名单一旦变动到与那四个相交，本行即失效（`AiEmailDraftService` 的类注释里也写了同一句）|

⚠️ **必须自己判，不能指望响应侧**：`FieldMaskingResponseBodyAdvice` 只作用于 **HTTP 响应**链路，本项拼出来的**提示词不经过它**。"响应会擦掉"这种想法在这里保护不到任何东西——FR-013 的判据（结果里不得出现无权读的字段值）在"提示词"这个出口上没有自动兜底。

### §13.5 状态码终裁（契约 §3/§4 的订正落点）

| 情形 | 原表 | **终裁** | 理由（详见契约 §3 的两段 ⚠️）|
|---|---|---|---|
| 实体不存在 | 404 不区分 | **404** | 与既有实体端点一致 |
| 实体不可见 | 404 不区分 | **403** | 既有端点对"不可见"一律 403（`CustomerService:430` / `ContactService:171` / `FollowUpService:71`）；"不区分"的括号是**假前提**，且"避免探测"在别处已有预言机 |
| `instruction` 过长 / `tone` 非法 / 两个 id 都没有 / 两个 id 指向不同客户 | 422 | **400** + 既有 `BAD_REQUEST` | 全仓**无通用 422 校验码**；`@Valid` 那条路在本仓一律 400 且禁扩张；**不新增码**（三个码的承诺不变）|
| 未配置 | 409 | 409 | `AiNotConfiguredException`，零出站 |
| 上游超时 / 429 / 5xx | 503 | 503 | `AiGenerationException.upstreamUnavailable()` |
| 模型拒答 / 空输出 | 422 | 422 | `AiGenerationException.rejected()` |
| 预算耗尽 | 429 | 429（**未实现**，见 §13.6）| 复用 `RATE_LIMITED` |
| 截断 | 200 | 200（`truncated=true`）| 不是错误 |

**限流参数**：`@RateLimit(scope="ai-generate", limit=10, windowSeconds=60, by=USER)`——取值理由与"这道注解不被任何门禁要求"的坑，写在契约 §4 的 C3 块里。

### §13.6 权限码与迁移的**终裁**：零授予 ⇒ **没有 `V92`**

判据③（既无"改造前的粗粒度门"可继承，也无菜单承诺可依据）⇒ `ai:generate` **一个角色都不授予**，因此**本项零新增迁移**（原计划的 `V92__ai_generate_permission.sql` **不存在**）。同判例：`mail_account:manage` / `workflow:read` / `integration:manage` / `open_platform:manage`（都是"建了码、一个角色都不授、且注释里写明理由"）。

零授予的**实际后果**（一处容易写反）：`PermissionAspect` 里 `ADMIN` 内建角色**恒放行**（`PermissionAspect.java:43-44`）⇒ 后果**不是**"连 ADMIN 也被拒"，而是 **ADMIN 照旧能用、其余全部角色一律 403**，直到管理员在角色页上把它勾给某个角色。

**可执行痕迹**（照 `PermissionMatrixIT` 的 FR-G14 判例）：`com.crm.integration.AiPermissionGrantIT#aiGenerateIsGrantedToNoPresetRole`——正对照（`customer:claim` 必须查得出持有者）+ 前提（本码必须在字典里，否则"零授予"恒真）+ 断言（`rolesHolding("ai:generate")` 为空）。**行为面的那一半**（预置角色真打端点 → 403）归 C4。

⚠️ **FR-015 / FR-017 的每日预算闸（Redis `ai:gen:budget:{scope}:{id}:{yyyyMMdd}`）在本批未实现**：C3 只落了限流的**突发**那一半。`tasks.md` 的 Phase 2 里原本**没有**对应任务（只有 plan D8 的设计与 C4 的 U7 断言）——该缺口已在 `tasks.md` 就地补记（**不勾选**）。

### §13.7 ⚠️ 未决事项：**P1 的前端宿主不存在**（须用户裁决）

`plan.md:186` 写的 P1 接入点是"**邮件编辑/活动页**（P1 草稿）"。**实测：本仓前端没有"客户上下文的邮件编辑器"这个页面。**

| 现状 | 读数 |
|---|---|
| 唯一的"邮件主题 + 正文"编辑器 | `pages/marketing/EmailTemplatePage.tsx`——**模板 CRUD 弹窗**，字段名 `content`，走 `createEmailTemplate` / `updateEmailTemplate`，**与客户无关**（没有客户选择器、没有客户上下文）|
| 022 的"商机下一步行动" | `playbookService.fetchOpportunityActions` **全仓无引用** ⇒ 022 那条规则式建议**当前没有任何页面在渲染**，也就没有"与它做视觉区分"的现成落点（契约 §2.5 的前提）|

⇒ C3 的前端半边（`aiContentService.ts` / `types/aiContent.ts` / `AiGenerateButton.tsx` / **页面接入** / i18n 键 / `constants/permissions.ts`）**无法照 plan 落地**，三种可选宿主见 `tasks.md` 的对应 ⚠️ 块（含各自的代价）。**本批不擅自替用户选**：三种都在改产品可见形态。

### §13.8 C3 的门禁子集（**非交付读数**）

| 检查 | 读数 |
|---|---|
| `mvn -B compile` | **exit 0** |
| `mvn -B spotless:check` | **exit 0**（`0 needs changes`；⚠️ 同一次输出里 `821 were skipped because caching determined they were already clean`——**这条是缓存判定、不是"真解析过"的证据**，我无法在本次拿到 `skipped=0` 的读数，故不把它当交付证据） |
| `pnpm run perms:check` | **未跑**：C3 **刻意不动前端**（前端半边随 §13.7 的裁决整批走）。⇒ 读数仍是 **68**，不是 69 |

⚠️ 这一栏**不是**交付读数：C3 同样处于 plan 允许的"显式红窗"（新代码暂无对应用例），最终读数由 C4/C5 那次完整 `verify` 给出。
