# 实施任务：双因素认证（TOTP 2FA）

## 阶段 1：数据层

- [x] T001 Flyway 迁移 `V78__two_factor_auth.sql`：`user` 表新增 4 列（是否启用 2FA、加密后的 TOTP 密钥、绑定时间、最近一次二次验证时间），新建 `user_recovery_code` 表（所属用户、码哈希、是否已使用、使用时间、创建时间），并补齐相应索引
- [x] T002 同步镜像 `backend/src/test/resources/schema-h2.sql`，使 H2 测试库结构与 `V78__two_factor_auth.sql` 保持一致

## 阶段 2：基础设施

- [x] T003 `ErrorCode` 枚举新增 8 个错误码：`MFA_REQUIRED`、`MFA_TICKET_INVALID`、`MFA_CODE_INVALID`、`MFA_LOCKED`、`MFA_NOT_ENABLED`、`MFA_ALREADY_ENABLED`、`RECOVERY_CODE_INVALID`、`MFA_SECRET_MISSING`
- [x] T004 新增配置项 `crm.security.mfa.secret-key` / 环境变量 `MFA_SECRET_KEY`；缺失时 fail closed，报 `MFA_SECRET_MISSING`，禁止硬编码降级
- [x] T005 `User` 实体扩展 4 个字段，新增 `UserRecoveryCode` 实体，并实现对应的 MyBatis-Plus repository / mapper

## 阶段 3：核心服务

- [x] T006 实现 `TotpService`（RFC 6238：HMAC-SHA1 / 6 位 / 30 秒步长 / ±1 步容差，使用 `javax.crypto.Mac` + commons-codec Base32），并附官方测试向量单测
- [x] T007 实现 `SecretEncryptionService`（AES-256-GCM，随机 IV，密文格式 `base64(iv):base64(ct+tag)`），密钥材料取自 T004 配置
- [x] T008 实现 `QrCodeService`（`com.google.zxing:core` 生成二维码，输出 PNG Base64）
- [x] T009 实现 `MfaTicketService`（Redis `mfa:ticket:{token}`，TTL 300 秒，一次性消费）
- [x] T010 实现 `MfaAttemptService`（Redis `mfa:fail:{userId}` INCR，TTL 900 秒，连续满 5 次锁定 15 分钟）
- [x] T011 实现 `MfaReplayGuard`（Redis `mfa:used:{userId}:{timeStep}`，TTL 90 秒，同一时间步防重放）
- [x] T012 实现 `RecoveryCodeService`（生成 10 个 8 位 Base32 恢复码，每码独立盐 + SHA-256 哈希落库，明文仅返回一次）

## 阶段 4：后端端点

- [x] T013 `POST /api/v1/auth/2fa/setup`：生成 Base32 密钥、`otpauth://` 链接与二维码，账号保持未启用状态
- [x] T014 `POST /api/v1/auth/2fa/enable`：校验动态码通过后置为已启用并一次性返回 10 个恢复码明文
- [x] T015 `POST /api/v1/auth/2fa/verify`：校验动态码（`code`）或恢复码（`recoveryCode`），签发访问令牌
- [x] T016 `GET /api/v1/auth/2fa/status`：返回启用状态、绑定时间与剩余恢复码数量
- [x] T017 `POST /api/v1/auth/2fa/recovery-codes/regenerate`：重新生成恢复码并使旧码全部失效
- [x] T018 `POST /api/v1/auth/2fa/disable`：凭密码 + 动态码或恢复码关闭 2FA，作废密钥与恢复码并记录审计日志
- [x] T019 `POST /api/v1/admin/users/{id}/2fa/reset`：管理员重置指定用户 2FA（`user:manage` 权限 + 审计日志）
- [x] T020 改造 `/api/v1/auth/login`：已启用 2FA 的用户返回 `{mfaRequired, mfaToken, expiresIn: 300}` 且不含 `accessToken`；未启用用户行为逐字节不变

## 阶段 5：前端

- [x] T021 登录页二次验证视图：输入 6 位动态码，支持切换为恢复码输入
- [x] T022 绑定向导：扫码/录入密钥 → 输入动态码 → 展示一次性恢复码
- [x] T023 账户安全页：展示 2FA 状态、剩余恢复码数量，提供关闭与重新生成恢复码入口
- [x] T024 管理员用户管理页新增「重置 2FA」入口
- [x] T025 新增中英文 i18n 文案资源

## 阶段 6：测试与回归

- [x] T026 后端单测：TotpService 官方测试向量、密钥加密/解密、恢复码哈希
- [x] T027 集成测试：verify 重放拒绝、恢复码重用拒绝、连续 5 次失败锁定、Redis 不可用 fail closed、非 2FA 登录逐字节兼容
- [x] T028 前端测试：既有 58 项保持全绿，新增二次验证视图测试
- [x] T029 全量回归：`mvn test`、`pnpm run typecheck`、`pnpm run lint`、`pnpm run test` 全绿

## 依赖关系

- T013 → T006 / T008
- T014 → T012
- T015 → T009 / T010 / T011
- T019 → T003
- T020 → T009
- T021 → T013 / T014 / T015 / T016
- T022 → T013 / T014
- T023 → T016 / T017 / T018
- T024 → T019

---

## ⚠️ 订正（2026-09-16，**事后按现状订正**）

**性质说明**：本文件写于 2026-09-12，此后 13 批落地。以下按**今天的实测现状**订正，
**属"事后按现状订正"（照 090/094/095 的先例）**。
**上方的任务原文与勾选行一律逐字保留**；本块只追加，**不回写**。
**勾选纪律**：实施时**做一项勾一项**，**不预勾**。

### 一、逐条订正

| 任务 | 原文（保留在上方） | 订正 |
|---|---|---|
| T001 | 迁移 `V78__two_factor_auth.sql` | **`V89__two_factor_auth.sql`** —— `V78` 已被 `V78__sla_escalation.sql`（1.3-sla-escalation 批）占用，当前最高 `V88`。见 `plan.md` 订正块 §一 |
| T002 | 镜像 `schema-h2.sql` 与 `V78` 一致 | 同上，改为与 `V89` 一致。**且"一致"是指表名与列**：镜像**历来不含任何外键**（实测 0 处，而主迁移链有 12 处），本批照旧，见 `data-model.md` 订正块 §四 |
| T003 | `ErrorCode` 新增 **8** 个码，**含 `MFA_REQUIRED`** | **`MFA_REQUIRED` 删除**（HTTP 200 的错误码 = 永不可达的死数据），**新增 `MFA_STORE_UNAVAILABLE`(503)**。仍是 **8** 个码，但**集合与原文不同**：`MFA_TICKET_INVALID`、`MFA_CODE_INVALID`、`MFA_LOCKED`(429)、`MFA_NOT_ENABLED`、`MFA_ALREADY_ENABLED`、`RECOVERY_CODE_INVALID`、`MFA_SECRET_MISSING`、`MFA_STORE_UNAVAILABLE`。见 `contracts/auth-mfa.md` 订正块 §四 |
| T004 | 缺失密钥时 fail closed，报 `MFA_SECRET_MISSING` | **触发时机改为"懒惰失败"**：密钥**缺失/空白 ⇒ 启动只告警**、调用 2FA 时抛 `MFA_SECRET_MISSING`；密钥**存在但解码后非 32 字节 ⇒ 启动即抛**。照 `SecurityDefaultsGuard` 抄会**炸掉整个测试套件**（`@ActiveProfiles("test")` 不含 `"dev"`，会被判为非 dev 而硬失败）。见 `plan.md` 订正块 §八 |
| T008 | `QrCodeService`（zxing core → PNG Base64） | 类名定为 **`MfaQrCodeService`**（与 `MfaService` / `MfaStateStore` 同前缀）。zxing **不在本机 m2 仓**，首次构建需网络 |
| T009–T011 | 三个独立服务：`MfaTicketService` / `MfaAttemptService` / `MfaReplayGuard` | **合并为单个 `MfaStateStore`**。理由：三者是**同一个 fail-closed 边界**的三个面 —— 拆成三个类，`catch { return null; }` 就有**三个**地方可以写错；合一个类，**全仓只有一处**能写错。**Redis 键与 TTL 逐字不变**（见 §二） |
| T012 | 恢复码"生成 10 个 **8 位 Base32**" | 措辞改 **"8 位无歧义字符"**（字母表 = `CaptchaService.CHARS`，31 符号，含 `8`/`9`）。**标准 Base32 不含 `8`/`9`**，原文自相矛盾。见 `contracts/auth-mfa.md` 订正块 §十一 |
| T015 | verify 校验动态码或恢复码，**签发访问令牌** | 签发的是**完整 `AuthResponse`**（含 `user`），**不是**裸令牌 + `expiresIn`。见 `contracts/auth-mfa.md` 订正块 §二 |
| T019 | `POST /api/v1/**admin**/users/{id}/2fa/reset` | **`POST /api/v1/users/{id}/2fa/reset`** —— 后端不存在 `/api/v1/admin/**`，用户管理端点在 `UserController` 的 `/api/v1/users`。权限码 `user:manage` 不变 |
| T020 | login 返回 `{mfaRequired, mfaToken, expiresIn: 300}` | 字段名与值**全部成立**；但 **`expiresIn` 只在此分支出现**（`verify` 不返回它）。另：**未启用用户"逐字节不变"必须有自动化用例守**（新增 `LoginResponseShapeIT`），不能只靠"我没改那段代码" |
| T027 | 集成测试覆盖……、非 2FA 登录逐字节兼容 | 覆盖项**增加**：`SecurityConfig` 的 permitAll（无 `Authorization` 的 `verify` 要能到控制器）、`lastLoginAt` 写入**不得自增 `user.version`**（085 回归，本批最高风险） |
| T028 | 既有前端 **58** 项保持全绿 | 实测 **86 文件 / 429 用例**。判据（零回归）不变，基数按实测 |

### 二、Redis 键与 TTL（**这三行是全批最不能写错的东西**）

| 用途 | 键 | 值 | TTL | 原语 |
|---|---|---|---|---|
| 二次验证票据 | `auth:2fa-ticket:{token}` | userId | 300 s | `set` / **`getAndDelete`**（消费） |
| 失败计数与锁定 | `auth:2fa-fail:{userId}` | 计数（int） | 900 s | `increment` + 达阈值时 `expire` |
| 防重放时间步 | `auth:2fa-used:{userId}:{timeStep}` | 1 | 90 s | **`setIfAbsent`** |

原计划的 `mfa:*` 顶层命名空间改为 `auth:2fa-*`（与 `auth:refresh:` / `auth:fail:` / `auth:captcha:` 同族）。
**TTL 三个数一字不改**（300/900/90）。

### 三、原任务清单**未列、但本批必须做**的工作（如实记账，不摊派给既有任务号）

这几件在原文里**没有对应的任务号**，是实施中实测出的必要件。**不塞进 T001–T029 的某一个号里**，
以免造成"原计划已覆盖"的错觉：

1. **`TokenService` 抽取**：把 `AuthService.issueTokens` / `toUserInfo` 原样搬进新类。
   不抽则 `AuthService → MfaChallengeService` 与 `MfaVerificationService → AuthService`
   构成 **bean 环**，Spring 启动即失败。
2. **`Clock` 接缝**（`@Bean Clock`）：没有它，「防重放」与「15 分钟锁定到期」两条
   都**只能靠真实等待**才能测 —— 前者要等 30 秒、后者要等 15 分钟。
3. **`LoginResponseShapeIT`**：把 FR-M14「非 2FA 响应逐字节不变」钉成断言（T020 的订正里已提）。
4. **两条测试基建**：`InMemoryRedisTestSupport`（功能性 Redis 替身 + 按 key 前缀注入故障）
   与 `FixedClockTestSupport`。**不建则 2FA 的端到端路径一条都测不了** ——
   `AbstractIntegrationTest` 只装一个**未被捕获**的 `mock(ValueOperations.class)`，
   读恒返 `null`、写是空操作，票据永远查不到。**该基类本批不改**（JUnit 5 父类 `@BeforeEach` 先跑，
   子类重装即可生效；改基类默认值会动到 74 个 IT 的可观测行为）。
5. **`AuthController` 的 permitAll**：`/api/v1/auth/2fa/verify` 必须**作为精确路径**加进
   `SecurityConfig`（`permitAll` 是逐条列举的），**不能用 `/api/v1/auth/2fa/**` 通配** ——
   那会把 `setup`/`status`/`disable` 一起放出去。

---

## 勾选说明（2026-09-16，交付时追加）

**29/29 全部完成**，逐条按"代码在、门禁过、留痕在"三条对过。勾选行本身的文字**未改写**
（订正块在上方另立），勾选时**做一项勾一项**。

**几处落地形态与任务号字面略有出入，按实质判定、逐条注明**：

| 任务 | 落地形态 |
|---|---|
| T001 / T002 | `V89__two_factor_auth.sql`（非 V78）+ `schema-h2.sql` 镜像 + `SchemaParityIT` 的 `"89"` |
| T003 | 8 个码，集合按订正块（无 `MFA_REQUIRED`、含 `MFA_STORE_UNAVAILABLE`）；`ErrorCode` 里另有一段 ⚠️ 注释**写明为什么没有** `MFA_REQUIRED` |
| T004 | 懒惰失败：缺失/空白 ⇒ 启动告警 + 调用时抛；存在但非 32 字节 ⇒ 启动即抛 |
| T007 / T008 | 类名为 `MfaSecretEncryptionService` / `MfaQrCodeService` |
| T009–T011 | 合并为 `MfaStateStore`（键与 TTL 逐字未变，见订正块 §二） |
| T012 | 字母表 = `CaptchaService.CHARS`（31 符号，含 `8`/`9`），措辞按订正改为"8 位无歧义字符" |
| T019 | `POST /api/v1/users/{id}/2fa/reset`（非 `/admin/users/...`），`@RequirePermission("user:manage")` |
| **T022 / T023** | **落点是既有 `PersonalCenterPage` 的安全卡（`data-testid="security-card"`），不新建 `/account/security` 页** —— 用户裁决 ③。绑定向导与"账户安全"是同一块屏内的两个分支，不是两个页面 |
| T028 | 基数按实测：**86 文件 / 429 用例 → 89 文件 / 448 用例**（净增 3 文件 / 19 用例） |
| T029 | 后端 `mvn -B verify` 退出码 0（surefire 695 / failsafe 323，见 §H 还原段）；前端七道门禁整链退出码 0（读数见 `falsification-evidence.md` §I.11） |

**证据分布**：定向破坏留痕按步分节 —— 后端 §A–§H（第 1–9 步）、前端 §I（第 10 步），
均在 `falsification-evidence.md`；本批共 **18 次提交**（17 次实施 + 1 次收口，提交号见 `roadmap.md` 交付后记）。

**未做且需用户放行**：`quickstart.md` §a–§j 的手工冒烟（真机 + 认证器 App）。
前置条件当前不满足（`8081/5173/3306/6379` 无监听），且**不得**擅自重启可能归并行会话所有的
共享后端 —— 这一项**没有**被算进上面的 29/29。

> ⚠️ **订正（2026-09-16 当日，用户放行后追加；上面原文逐字保留）**
>
> **该项于当日即被用户放行，手工冒烟已跑完。** 隔离实例（18081 + `crm_mfa_smoke` + Redis db 5；
> 缺密钥/畸形密钥另起 18082 / 18083），真 MySQL 8.0.46 + 真 Redis，**全程未碰 8081 / 5173 / `crm_db`**。
> 逐条读数见 `falsification-evidence.md` **§J**（收尾零残留见 §J.7）。
>
> **已跑**：§a–§j **全部**（含 §j 的 `SC-M04` 五次错码 4→1 递减、第 5 次 429、
> **第 6 次用正确码仍 429**），另**计划外补跑** §k（关闭→重新启用后旧恢复码全废、新码可用）
> 与 §m（缺密钥 ⇒ 启动只告警 + 调用时 500 `MFA_SECRET_MISSING`；畸形密钥 ⇒ 启动退出码 1）。
>
> **未跑**（明确列出，不得被读成"全验了"）：**浏览器 UI 一律未跑**（没起 5173、没开浏览器 ⇒
> `PersonalCenterPage` 安全卡与登录二次验证视图的版式、真机扫码**均未验证**）；
> **Redis 不可用 ⇒ 503 fail closed** 未在真 Redis 上验（6379 是共享的，按仓规不得为验收停它，
> 仅有 H2 + 替身级别的证据）；**锁定期满 15 分钟自动恢复**未等；**`SC-M01` / `SC-M02`** 未测；
> `SC-M03` 的**延迟本身**未测。
>
> **上面的 29/29 计数不因冒烟而改变** —— 手工冒烟**从未计入** 29 项任务，它是 `plan.md`
> 「验证」章里独立于任务清单的一项，原文也写明"这一项没有被算进 29/29"。
