# 快速上手与判据（104）

## 1 本项一句话

仓库里那个叫「AI 助手」且标 ✅ 的 **022 是规则引擎**（`tasks.md:61` 自述「规则引擎（非 LLM）」）。本项是**从零引入模型依赖**，做四件生成式文本能力，**默认关闭**（`crm.ai.enabled=false`）⇒ 出厂状态零出站、零成本、零行为变化。

## 2 会撞上的三堵墙（**先读这段再开工**）

| # | 墙 | 事实 | 处置 |
|---|---|---|---|
| 1 | **出站防火墙默认拒绝一切** | `OutboundUrlValidator` **默认拒绝所有目的地，含公网**；白名单键 `crm.outbound.allowed-hosts`（`application.yml:84`）/ env `CRM_OUTBOUND_ALLOWED_HOSTS`（`.env.example:48`） | 部署前置：把模型主机加白名单。**否则第一发就失败** |
| 2 | **官方 SDK 绕过这个校验器** | SDK 内部走 **OkHttp**，**不使用**本仓的共享 `RestTemplate` ⇒ 不逐跳校验 | 以**启动期一次校验**替代（plan D1）。**这是本项唯一的出站管控点**，不可省 |
| 3 | **`ai:` Redis 前缀已被 022 占用** | `SuggestionService.java:36` `IGNORE_PREFIX="ai:ignore:"` | 本项一律走 **`ai:gen:*`**，两族不得混用 |

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
| 迁移 | 新增 **`V92`**（仅权限码，无 DDL）+ 同步 `schema-h2.sql` 行尾 `-- V92` + `SchemaParityIT` 镜像清单 |

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

⚠️ **最后一条的自证方式**：`git diff --stat` 只显示**被追踪文件的改动**；`V92` 是新文件，会出现在 `git status` 的未跟踪区而**不**在这里 ⇒ 该命令**空输出才是正确读数**。

## 7 交付时填（**不得预填**）

| 项 | 读数 |
|---|---|
| 门禁那次 `verify` 的结果 | 交付时填，**权威读数写在 `tasks.md` §交付块**（本文件只留指针——「一个数字住在好几个地方」是本仓严打的） |
| `jacoco.exec` 字节数 / mtime | 同上 |
| 前端九道（含 `build`） | 同上 |
| `i18n:check` 键数 | 交付时填；**基线 2966/2966**（开工前须实跑复测，见 `tasks.md` T004） |
| 定向破坏 D1–D10 的实测输出 | 见 `falsification-evidence.md`（**交付相位才写**，开工前不得编造） |
| 四项能力实际交付到哪一档（P1 单发 / P1+P2 / 全量） | 交付时填 —— 本文件的 §2/§3 对四档**均适用** |
