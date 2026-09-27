# CRM 交付边界说明（A 档）

> **本文件是「交付信封」**：它说明本次交付**包含什么**、**不包含什么**、以及**这些条款在什么前提下成立**。
>
> **它刻意不含任何数量断言**（模块数 / 迁移数 / 用例数 / i18n 键数 / 覆盖率 / 字段数），也**不抄任何清单**。
> 数量与清单会随每次提交漂移，而本文件的目标是**不在数字上过期**：凡读者需要数量的地方，这里给的是
> **复算命令**，或者**指向拥有那个数字的文档**。**不抄，就不会烂。**
>
> ⚠️ **自陈盲点**：**没有任何门禁看着这个文件**（构建、lint、i18n、UI 规范、覆盖率等门禁都不读它）。
> 它的一致性只能靠两件事维持：**每条断言都自带可复核的判据**，以及 §6 的**复算命令**。
>
> 📌 **本文件的表行一律用「名称」引用、不编号**，例如「§3 的『多租户』行」。

---

## 1. 适用范围与失效条件

本文件描述的是 **A 档交付**：**单租户**、**私有化 / 内部部署**、**单一客户**、**无对外 SLA**。

**以下任一条命中，本文件即失效，必须改述（改述时旧文逐字保留、不删行，照本仓 `README.md` 与
`specs/README.md` 的复核块体例追加带日期的 ⚠️ 段落）：**

| 触发项 | 为什么它会让本文件的条款失效 |
|---|---|
| 开启**多租户** | §3 的「多租户」行当场作废，且数据隔离、权限、索引都要重审 |
| 把后端端口**暴露到不受信网络** | §3 的「限流是安全能力」行会被读成安全能力，而它**不是** |
| 与客户签订 **SLA**（可用性 / 响应时间 / 恢复时间承诺） | 单实例部署假设与「多实例 / 集群部署」行直接冲突 |
| **多实例 / 集群部署** | 定时任务与实时通知都会出问题（见 §3 该行） |
| §3 中任何一项**实际交付**（含从「未交付」清单移出） | 该行必须改述为已交付，而不是留在表里 |

---

## 2. 已交付范围

按**模块组**给出，**不枚举模块、不抄计数**：

| 模块组 | 位置 | 拥有其清单与进度的文档 |
|---|---|---|
| 后端服务 | `backend/`（Spring Boot 单体，分层为 `controller` / `service` / `repository` / `entity` / `dto` / `config` / `security` / `support`） | `specs/README.md` 的模块表 |
| 前端应用 | `frontend/`（React + TypeScript + antd） | 同上 |
| 设计文档（SDD 工件） | `specs/`（每个批次一个目录，含 `spec.md` / `plan.md` / `research.md` / `tasks.md` 等） | `specs/README.md` 模块表与 `specs/roadmap.md` 的 `## 当前进度` |
| 数据库迁移 | `backend/src/main/resources/db/migration/`（Flyway） | `INSTALL.md` 的迁移表 |
| 部署与运维 | `docker-compose.yml` / `Dockerfile` / `nginx.conf` | `INSTALL.md` |

**哪些批次已交付、哪些仍在进行中，一律以 `specs/roadmap.md` 的 `## 当前进度` 为准**——
本文件**不复制那份勾选状态**（复制来的状态会在下一次交付时变成假话）。

---

## 3. 明确不在交付范围内

⚠️ **每一行都带判据**，请**复核**而不是相信。判据里的命令都可以在本仓库根目录直接跑。

| 项 | 现状 | 判据（可复核） |
|---|---|---|
| **多租户** | 无。单租户数据模型，全仓没有租户维度 | `grep -rni "tenant" backend/src/main/java backend/src/main/resources/db/migration` ⇒ **零命中**（2026-09-18 实测） |
| **多实例 / 集群部署** | 不支持。**单实例假设**是本交付的前提 | ① **定时任务没有任何集群锁**：`config/` 下的 `DataRetentionScheduler` / `ScheduledExportScheduler` / `SlaEscalationScheduler` / `WebhookDeliverySweepScheduler` 各带一个 `@Scheduled`，而全仓 `ShedLock` / `Quartz` **零命中** ⇒ 每个实例都会各跑一遍；② **实时通知的连接表在单进程内存里**：`ws/NotificationWebSocketHandler` 的 `sessionsByUser` 是 `ConcurrentHashMap` ⇒ 通知只到达**持有该连接的那个实例** |
| **限流是安全能力** | ❌ **不是**。公开端点限流的定位是「**误用与意外的阻尼**」（防一个死循环的前端把表灌满） | `com.crm.security.ClientIpResolver` 取 `X-Forwarded-For` 的**首段**当客户端 IP，**无条件信任** ⇒ 能直连后端端口的人即可伪造 IP，绕过一切按 IP 分桶的桶。⚠️ **不得在任何材料里把限流宣传成攻击防护**（口径降级取自 `specs/100-rate-limit-consolidation/`，**不得升级措辞**） |
| **流式导出** | 无。所有导出把整个工作簿物化进堆内存 | 全仓零**可执行**命中：`grep -rn "SXSSF\|EasyExcel" backend/src/main` ⇒ **仅一行注释**（`security/RateLimitAspect` 的 javadoc），无任何调用 ⇒ 导出全走 `XSSFWorkbook` + `ByteArrayOutputStream`，大表导出受堆内存约束 |
| **邮件收信能力** | 零。没有 IMAP 客户端，**也不生成假收信记录** | `POST /api/v1/mail-accounts/{id}/sync`：默认部署下返回 **409 `MAIL_INBOUND_NOT_CONFIGURED`** 且**不写任何记录**；只有部署方显式打开演示开关时才生成一条 `SIMULATED` 记录（界面渲染为橙色「模拟」，不冒充真实收信）。见 `CRM_FEATURE_COMPARISON.md` 对应域的行与该批次的 `specs/101-mail-inbound-honesty/` |
| **字段级权限的全面覆盖** | 部分。只覆盖**注册表登记的实体**内的字段；**必填字段被永久排除** | 唯一判据源是 `support/BuiltinFieldRegistry`（它列出哪些实体的哪些字段受控）。**必填字段不在其中**——理由与后果（也就意味着上游常点名的「客户名」这类必填字段**不受字段级权限控制**）见 `specs/102-builtin-field-permission/` |
| **「`field_id` 与 `field_key` 恰好一列非空」的 DB 级约束** | 无。只有服务层的 `upsert` 保证 | 实测 H2 与 MySQL **都允许双 NULL 行**（读数落在 `specs/102-builtin-field-permission/data-model.md`）⇒ 这类错行**没有任何自动判据**能发现 |
| **「省略即销毁」缺陷类的全面修复** | 只修了**一处**（自定义字段的受保护值）。其余同类路径**登记不修** | 见 §5。盘点判据与其余各处的登记在 `specs/103-omission-not-destruction/research.md` |
| **自定义字段的读写边界** | 有三处已知边界，均**登记不修** | ① **必填校验对权限不可见** ⇒ 必填且 `READ_ONLY` 的字段被省略时先 422（回补够不着）；必填且 `HIDDEN` 的字段**任何调用方都无法满足**；② **回传格式脆弱**：`READ_ONLY` 的回传之所以安全，只因其初始值是库中的原始字符串，任何「格式化后回传」都会变成 422；③ **分页端点的 `permission` 漂移**（`/custom-fields/definitions` 下发、分页端点不下发）⇒ 前端只能按可选字段编码 |
| **模型主机、模型账号与 API key** | 零。**不在交付范围内**——本项只交付「**一条可配置的生成式通路**」，**不附带**任何模型主机、账号或密钥 | `crm.ai.enabled` 出厂 `false`（`application.yml`）⇒ 四个 AI 入口 **409 + 零出站**；部署侧自备出网 / 密钥 / Anthropic 兼容端点，见 §4 第 6 条与该目录 `contracts/ai-content-generation.md` §1。<br>⚠️ **2026-09-27 订正（104 的 C6 档 / P2 交付后）——上句「四个 AI 入口」现读为「**两个已交付的 AI 入口**」，原文逐字保留不改写**：`POST /api/v1/ai/email-draft`（P1 邮件草稿）与 `POST /api/v1/ai/customer-summary`（P2 客户 360 摘要）在两处入口上**一律 409 `AI_NOT_CONFIGURED` 且零出站**；「**四个**」是**规格里的计划数**（P1–P4），**不是现存端点数**——另两项能力**未交付、本就没有入口**。 |
| **生成式 AI 的其余三项能力**（客户 360 摘要 / 跟进润色·总结 / 商机下一步建议） | 零。**未交付**——本批只交 **P1 邮件草稿**；另三项是同一规格里的 P2 / P3 / P4 组 | `specs/104-ai-content-generation/tasks.md` 的 Phase 5+；`CRM_FEATURE_COMPARISON.md` 2.10 该行「四项生成能力只交了 1 项」。<br>⚠️ **2026-09-27 订正（104 的 C6 档）——本行标题与正文现读为「其余两项能力（跟进润色·总结 P3 / 商机下一步建议 P4）」，原文逐字保留不改写**：**客户 360 摘要（P2）已于 2026-09-27 交付**（`POST /api/v1/ai/customer-summary`，宿主与形态同 P1：客户详情页 + 展示/复制、**不落库**）。⚠️ **本行记的是"未交付的能力"，不是"判据缺口"** ⇒ P2 交付后**只剩 P3 / P4 两项**；`specs/104-ai-content-generation/tasks.md` 的 Phase 5+ **仍未勾**（那正是 P3 / P4 的框），**与本次订正不矛盾**。 |

**另有两条不写成「缺陷」、只写成「没有判据看着」**（措辞刻意如此，**不得**读成「已验证无风险」）：

- **无条件 `set(null)` + `updateById` 那一族**：其「不销毁」的结论建立在「全仓无 `update-strategy`
  覆盖、无 mapper XML」之上，而**没有任何门禁**阻止后人加上其中任一项。
- **实时通知的进程内连接表**（即上表「多实例 / 集群部署」行的第 ② 点）：单实例下正确，**跨实例没有任何判据**。

---

## 4. 部署与运维前提

1. **MySQL 与 Redis 是必需依赖**，不是可选缓存：没有它们，后端**起不来**或**关键功能不可用**
   （限流、登录锁定、2FA 状态、通知均在 Redis 上）。容器编排与最小配置见 `INSTALL.md`
   与 `docker-compose.yml` / `.env.example`。
2. **数据库迁移只前进**：Flyway 在启动时自动执行 `backend/src/main/resources/db/migration/`。
   **已应用的迁移永不编辑**，改动一律新增一个迁移文件。
3. **单入口**：生产部署假设 nginx 是**唯一入口**（`nginx.conf`）。**后端端口不应直接暴露给不受信
   网络**——这不只是加固建议，它是 §3 的「限流是安全能力」行那条口径的**前提**。
4. **单实例假设**：见 §3 的「多实例 / 集群部署」行。需要多实例时，先解决定时任务的集群锁与
   实时通知的跨实例投递。
5. **部署步骤、环境变量与首次启动的说明**以 `INSTALL.md` 为唯一真源，本文件不复制其内容。
6. **AI 文本生成（104）给部署包新增了一条「可选、默认关闭」的对外依赖** —— `crm.ai.enabled`
   出厂为 `false` ⇒ 四个 AI 入口一律 **409 `AI_NOT_CONFIGURED` 且零出站**（⚠️ **2026-09-27 订正：现读为**「**两个已交付的入口**」**——`/ai/email-draft` 与 `/ai/customer-summary`**；「四个」是规格里的计划数、不是现存端点数。**原文逐字保留**），**不配置就与交付前
   无差别**。显式打开后，部署侧须**自备**三样东西（本仓**不附带**任何模型主机、账号或密钥）：
   ① **出网**：目标模型主机必须允许出站，**且必须同时把它加进 `crm.outbound.allowed-hosts`
   （env `CRM_OUTBOUND_ALLOWED_HOSTS`）——否则后端启动即失败**。本项唯一的出站准入控制就是
   启动期的这一次校验（官方 Java SDK 内部走 OkHttp，**绕过** `OutboundUrlValidator` 的逐跳校验；
   该偏离的威胁模型论证已显式声明在 `specs/104-ai-content-generation/plan.md` 的 Constitution Check
   原则三，**不是静默绕过**）。
   ② **API key**：`crm.ai.api-key`（env `CRM_AI_API_KEY`）由部署方提供并保管；它**不进日志、
   不进响应、不进审计 detail**（判据见该目录 `quickstart.md` §6 与 `falsification-evidence.md` 的 D7）。
   ③ **接口形状**：接口标准是 **Anthropic Messages API 兼容** —— **「可配置 base URL」不等于
   「任何模型都能用」**：Ollama / vLLM 的**原生** API 不是该形状，**必须前置一层协议转换网关**
   （约束原文见该目录 `contracts/ai-content-generation.md` §1 与 `spec.md` 的 A-001）。
   ⚠️ **依赖足迹**：本项新增 `com.anthropic:anthropic-java` 及其传递依赖（okhttp / okio / kotlin-stdlib
   等），逐条读数见 `specs/104-ai-content-generation/research.md` §12.1；**这些 jar 随包分发**，
   与「出网」是两件事（不发一个请求也会进类路径）。

---

## 5. 已知缺陷与债务的入口

**本文件不复制任何债务列表**（抄来的列表会在下一次修复时变成假话）。请到**拥有它们的地方**看：

| 想找什么 | 去哪里 |
|---|---|
| 每个批次的**未闭合项与债务台账** | `specs/roadmap.md`（每个批次行下方都有债务 blockquote） |
| 功能覆盖与竞品的**差距分析**（哪些是「仍缺」「部分闭合」） | `CRM_FEATURE_COMPARISON.md` |
| **「省略即销毁」**这一缺陷类的**全类盘点**（哪些销毁、哪些不销毁、哪些可达）、本批修的那一处、以及**其余各处的登记** | `specs/103-omission-not-destruction/research.md` 的两级盘点表与债务节 |
| 各批次的**伪造证据 / 定向破坏留痕**（哪些护栏真的会被破坏测试打红） | 各批次目录下的 `falsification-evidence.md` |

---

## 6. 复核方式

### 6.1 复算命令

```bash
# §3「多租户」行：零命中
grep -rni "tenant" backend/src/main/java backend/src/main/resources/db/migration | wc -l   # 期望 0

# §3「多实例 / 集群部署」行：注解存在、集群锁不存在
grep -rn "@Scheduled" backend/src/main/java | grep -vE ':[[:space:]]*\*'    # 命中才是注解本身（另一条是注释）
grep -rniE "shedlock|quartz" backend/src/main/java | wc -l                  # 期望 0

# §3「流式导出」行：唯一命中应为一行注释，无调用
grep -rn "SXSSF\|EasyExcel" backend/src/main

# §2 / §5：清单与进度以这两处为准，不在本文件里
grep -n "^| " specs/README.md | head          # 模块表
grep -n "^- \[" specs/roadmap.md              # 当前进度
```

### 6.2 本文件的判据口径（**刻意窄于「不含数字」**，写清楚免得被误读）

本文件禁止的是**数量断言**（「N 个模块」「N 条用例」「覆盖率 N%」）。文中仍然出现的数字只有三类，
它们**不是数量、且不会随提交漂移**：**批次标识**（如 `specs/101-...` 的目录名）、**端点与错误码**
（如 `409` `MAIL_INBOUND_NOT_CONFIGURED`）、**复核日期**。

⇒ 判据是下面这条命令输出为 **0**：

```bash
grep -nE "[0-9]+ *(个|条|处|项|张|次|行|键|页|种|类|万)" DELIVERY_SCOPE.md
```

⚠️ **两处刻意偏离「更粗的口径」，写明理由免得被读成漏做**：

1. **不用「全文 `[0-9]{3,}` 零命中」**：它会同时命中批次目录名与复核日期，而这两者**恰恰是**本文件
   要求写的东西 ⇒ 口径比规则本身还严时会制造**假红**，而不是制造安全。
2. **表行一律用名称引用、不编号**（见文件开头的 📌）：`第 N 行` 这种交叉引用会被上面那条命令里的
   `行` 误判成数量断言。**改的是排版，不是判据**——换成名称引用同时让读者不必回头数行，
   比原样更清楚。

两处偏离都已登记在本批的 `tasks.md` §实做订正。

### 6.3 复核记录

（尚无。复核时按下面的体例**追加**，**旧文逐字保留、不删行**：）

> 【复核, YYYY-MM-DD】复核人 / 复核了哪几条 / 结论 / 与上次的差异
