# 快速上手与判据（104）

## 1 本项一句话

仓库里那个叫「AI 助手」且标 ✅ 的 **022 是规则引擎**（`tasks.md:61` 自述「规则引擎（非 LLM）」）。本项是**从零引入模型依赖**，做四件生成式文本能力，**默认关闭**（`crm.ai.enabled=false`）⇒ 出厂状态零出站、零成本、零行为变化。

## 2 会撞上的三堵墙（**先读这段再开工**）

| # | 墙 | 事实 | 处置 |
|---|---|---|---|
| 1 | **出站防火墙默认拒绝一切** | `OutboundUrlValidator` **默认拒绝所有目的地，含公网**；白名单键 `crm.outbound.allowed-hosts`（`application.yml:84`）/ env `CRM_OUTBOUND_ALLOWED_HOSTS`（`.env.example:48`） | 部署前置：把模型主机加白名单。**否则第一发就失败** |
| 2 | **官方 SDK 绕过这个校验器** | SDK 内部走 **OkHttp**，**不使用**本仓的共享 `RestTemplate` ⇒ 不逐跳校验 | 以**启动期一次校验**替代（plan D1）。**这是本项唯一的出站管控点**，不可省 |
| 3 | **`ai:` Redis 前缀已被 022 占用** | `SuggestionService.java:36` `IGNORE_PREFIX="ai:ignore:"` | 本项一律走 **`ai:gen:*`**，两族不得混用 |

**墙之外还有一条「足迹」**（T002 实测所得，不进上表以保持"三堵墙"这个叫法）：接 SDK 会**净新增** okhttp 4.12.0 / kotlin-stdlib / kotlin-reflect / victools jsonschema —— **本仓今天既无 okhttp 也无 kotlin**。这条属**部署足迹**而非阻塞项，落地位置是 `DELIVERY_SCOPE.md`（T044）。另有一处**版本落差须处置**：SDK 按 Jackson **2.19.4** 构建，而本仓 Spring Boot 3.2.0 的 BOM 管到 **2.15.3**。两者详见 `research.md` §11.3。

## 3 关键坐标

| 什么 | 在哪 |
|---|---|
| 未配置判据的房规体例 | `config/MailInboundStatus.java`（唯一判据源 + 消息常量）；`ErrorCode.java:153` `MAIL_INBOUND_NOT_CONFIGURED(409)` |
| 启动期配置校验的先例 | `config/SecurityDefaultsGuard.java`（空 = 合法但告警；有值但畸形 = 启动即失败） |
| 数据范围过滤的**正确**取数范例 | `CustomerService.java:226`（`applyDataScopeFilter(qw)`）——**本项逐能力取数照这个写** |
| ⚠️ **反面教材**（本项禁止复制） | `SuggestionService.java:87-92 / 110-115 / 150-154`（三条规则直查裸 Mapper，无任何 owner 条件） |
| 为什么反面教材没被发现 | `MybatisPlusConfig.java:19-24` 全仓只注册分页与乐观锁两个拦截器；`grep DataPermissionInterceptor\|DataScopeInterceptor\|TenantLineInnerInterceptor` **零命中** |
| 限流台账护栏 | `security/RateLimitCoverageTest.java`（**字节码扫描**：每个 Controller 方法须带 `@RateLimit` 或豁免且理由非空） |
| 前端超时逃生口 | `services/apiClient.ts:36` 注释 + `:38` 全局 30s ⇒ 生成类调用**显式 `timeout: 0`** |
| 契约台账 | `contracts/ai-content-generation.md`（**§5 逐能力字段白名单是本项最重的一张表**） |
| 迁移 | 新增 **`V92`**（仅权限码，无 DDL）+ 同步 `schema-h2.sql` 行尾 `-- V92` + `SchemaParityIT` 镜像清单 ⚠️ **2026-09-27 C2：本行整条待 C3 裁——按判据③ 若 `ai:generate` 一个角色都不授予，则本项迁移数为 0，本行不适用** ✅ **2026-09-27 C3 已裁即"零授予"：本行整条不适用**（`schema-h2.sql` / `SchemaParityIT` / 迁移表 / `INSTALL.md` / Flyway 计数**五处一律不动**；权威落点 `research.md` §13.6，复算命令见 §6）（原文逐字保留）|

## 4 怎么跑（本机）

IT 用 **H2 内存库 + `schema-h2.sql`**（`flyway.enabled: false`），**不碰** WSL 里的 MySQL；Redis 被 `@MockBean` 掉。

```bash
export JAVA_HOME="$HOME/.jdks/jdk-21.0.12.1+1"
# 单跑一条 IT（绕开全量单测）
cd backend && mvn -B test-compile failsafe:integration-test failsafe:verify -Dit.test=AiContentIT
```

⚠️ **未配置路径的 IT 是本项最值钱的一条**（I1）：它要在**零配置**下断言「409 + 出站 0 次 + 新增记录 0 条」。出站计数靠 `verify(..., never())` 于**出网点**（`AiContentService` 是唯一出网点，故只需看着它一个）。**不得**只靠事务推理——本仓有"默认路径的副作用无断言"的先例。

## 5 门禁判据

```bash
cd backend  && mvn -B spotless:apply && mvn -B verify      # ⚠️ 绝不传 -DargLine
ls backend/target/jacoco.exec
cd frontend && pnpm run typecheck && pnpm run lint && pnpm run i18n:check \
  && pnpm run menu:check && pnpm run perms:check && pnpm run ui:check \
  && pnpm run zh:check && pnpm run test:coverage && pnpm run build
```

- `jacoco:check` **必须打印结论行**「All coverage checks have been met.」——**没有这行 = 门禁根本没被判定**，别把「没搜到某串」当成「不存在结论」。
- **spotless 的「N were skipped because caching determined…」不算真解析过** ⇒ 移走 `target/spotless-index` 复跑，取 `skipped 0`。
- 交付态读数**只取那一次完整 `verify`**；门禁跑完**不再跑 Maven**（会覆盖 `jacoco.exec`，让交付块的读数变成假话）。
- 冻结债**双向**：`ui:check` **54 处**、`zh:check` **4 条 / 266 处** —— 新增违规红、**台账条目过期也红**。

## 6 复算命令（交付边界与文档落点用）

```bash
# 022 是否一字未改（应为空）
git diff --stat -- specs/022-ai-assistant/ backend/src/main/java/com/crm/service/SuggestionService.java
# 本项是否真的零裸 Mapper 取数（应只在禁令注释/测试负断言里命中）
grep -rn "Mapper.selectList" backend/src/main/java/com/crm/service/AiContentService.java
# 探针残留（应为 0）
grep -rn "留痕后还原" backend/src frontend/src
# 新端点是否全部带权限码与限流（应 4 处 RequirePermission + 4 处 RateLimit）
grep -c "RequirePermission(\"ai:generate\")" backend/src/main/java/com/crm/controller/AiContentController.java
# 密钥是否泄漏进日志/审计/异常（应 0 命中）
grep -rniE "apiKey|api-key" backend/src/main/java/com/crm/service/AiContentService.java | grep -viE "^\s*//|\*"
# 迁移是否只有新增、无编辑（V1–V91 应一动不动）
git diff --stat -- backend/src/main/resources/db/migration/
```

⚠️ **2026-09-27 C2 对上面两条命令的订正**（原命令逐字保留）：
- **`grep apiKey` 那条**：它现在**仍然是 0 命中**，但**判据的含义变了**——密钥构造被拆进 `config/AiClientFactory.java`（见 `tasks.md` 的实做订正 #2），所以"0 命中"不再等价于"密钥没被别处碰过"，只等价于"服务类不认识密钥"。**必须配一条肯定式对照**才算完整判据：
  ```bash
  # 全仓只应有一个文件取用密钥（读数应为 1 行：AiClientFactory）
  grep -rln "apiKey()" backend/src/main/java/com/crm/ | sort
  ```
  一条 0 命中的否定判据单独存在时是**自证不了**的：它无法区分"密钥被管住了"与"密钥被挪到别处了"。
- **`git diff --stat -- db/migration/` 那条**：其后半句的自证方式（空输出=正确读数）**只在 `V92` 真的存在时**才成立。C3 若按判据③ 裁为零授予 ⇒ 本项**根本不新增迁移**，"空输出"于是同时兼容两种情形（什么都没加 / 加了但被误提交为已跟踪文件的修改），**该命令失去分辨力**。此时正确的判据换成迁移计数不动：`ls backend/src/main/resources/db/migration/ | wc -l`（交付基线 **90**，最高 `V91`）。 ✅ **2026-09-27 C3：这个分支已被走到（裁为零授予）⇒ 上面那条 `ls | wc -l` 就是本项的**生效判据**，`git diff --stat` 那条自此只作补充；权威落点 `research.md` §13.6**

## 7 交付时填（**不得预填**）

| 项 | 读数 |
|---|---|
| 门禁那次 `verify` 的结果 | 交付时填，**权威读数写在 `tasks.md` §交付块**（本文件只留指针——「一个数字住在好几个地方」是本仓严打的） |
| `jacoco.exec` 字节数 / mtime | 同上 |
| 前端九道（含 `build`） | 同上 |
| `i18n:check` 键数 | 交付时填；**开工基线 2966/2966**（**2026-09-27 已实跑复测、与立项期读数逐字相同**，见 `research.md` §6.1——本项**新增 i18n 键**，故交付读数应与基线**不等**，差值 = 本项新增键数×2） |
| 定向破坏 D1–D10 的实测输出 | 见 `falsification-evidence.md`（**交付相位才写**，开工前不得编造） |
| 四项能力实际交付到哪一档（P1 单发 / P1+P2 / 全量） | 交付时填 —— 本文件的 §2/§3 对四档**均适用** |
