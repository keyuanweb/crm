# 快速验证：全局限流收口（100）

---

## 0 前置

```bash
cd backend && mvn -B -q compile       # 确认能编译（本项有大量 Java 改动）
```

⚠️ 本项**有后端改动**（新类、注解标注、`ErrorCode` 新增、`application.yml`）⇒ **必须跑 `mvn`**。
⚠️ **不重启 8081 上已在跑的后端**（可能归并行会话所有）；需要带限流的实例时按 §4 起**隔离实例**。
⚠️ **不跑任何前端门禁**（本项**零前端改动**，`frontend/` 一个文件都不动）。

## 1 门禁（唯一权威判据）

```bash
cd backend && mvn -B spotless:apply   # 先修格式：spotless 在 verify 相位里比用例失败更早中止
cd backend && mvn -B verify
ls backend/target/jacoco.exec         # 必须存在
```

**逐道判据**：

| 门禁 | 判据 |
|---|---|
| `spotless:check`（在 `verify` 内） | **exit 0**。⚠️ 它与用例**同相位且更早中止** ⇒ 它红的时候**看不到**用例结果；改了 Java 先单独跑 `spotless:apply` |
| surefire（`test`） | 本批**新增的 5 个单测类全绿**（`RateLimitStoreTest` / `RateLimiterShapeTest` / `RateLimitIdentityTest` / `ClientIpResolverTest` / `RateLimitCoverageTest`） |
| failsafe（`integration-test`） | 本批**新增的 `RateLimitIT` 全绿**；⚠️ **失败集合 ⊆ 4 例已批准偏差**（见下），**不是 exit 0** |
| `jacoco:check`（`verify`，在 `failsafe:verify` **之后**） | 必须打印 **`All coverage checks have been met.`** —— **没有这行 = 门禁根本没被判定**（本仓有「空过」先例）。唯一规则：**INSTRUCTION COVEREDRATIO ≥ 0.73、粒度 BUNDLE** |
| `jacoco.exec` | **必须存在**。不存在 = jacoco 被静默跳过（多半是非预期地传了 `-DargLine`） |

⚠️ **4 例已批准偏差**（本仓既有状态，**与本项无关**）：
`IntegrationHubIT.integrationFlow`、`OpportunityIT.closeWithoutResultReturns422`、
`UserIT.disableUserRevokesAccess`、`UserIT.userLifecycle`。
⇒ **判据是「失败集合 ⊆ 这 4 例 + 本批新增用例全绿」，不是「退出码 0」。**
⚠️ **不许传 `-DargLine`** —— 它会挤掉 jacoco 的 Java agent，**静默**废掉覆盖率门禁而构建全程无报错。

### 1.1 定向跑（改一处跑一处，比全量 `verify` 快得多）

```bash
cd backend
# 纯单测（不启 Spring 的三个 + 装替身的那个）
mvn -B test -Dtest='RateLimitStoreTest,RateLimiterShapeTest,RateLimitIdentityTest,ClientIpResolverTest,RateLimitCoverageTest'
# 行为层 IT（本项唯一的真回归证据）
mvn -B verify -Dit.test=RateLimitIT -DfailIfNoSpecifiedTests=false
# 那颗雷的两个邻接用例，必须仍绿（零改动是验收的一部分）
mvn -B verify -Dit.test='FormIT,LandingPageIT' -DfailIfNoSpecifiedTests=false
```

⚠️ **`RateLimitCoverageTest` 是台账测试**，它红的时候先看**它自己的自检**（T11：是否真扫到了端点），
再看向导出的违规清单 —— 扫描 pattern 写错时「零违规」是**假绿**。

## 2 本项的证据形态（**读之前先看这条**）

⚠️ **本批最大的假绿陷阱**：`AbstractIntegrationTest` 的 Redis 是**裸 mock**
（`@MockBean RedisTemplate` + `mock(ValueOperations.class)`）⇒ `increment` 返回 `null` ⇒ **fail-open ⇒ 限流是 no-op**。
而真正的假绿**不是**「for 循环断言第 N+1 次 429」（那种在裸 mock 下**会红**），
而是「**把 Redis 返回值桩成 10、断言拒绝**」—— 它证明的是「**如果** Redis 说 10 我就拒」，**不证明接线**。

⇒ **每条 HTTP 层限流用例必须做满三件事，缺一即视为假绿**：
① `redis.install(redisTemplate, clock)` 装功能型替身；
② **正对照**：`redis.snapshot()` 里**有**该键（没接线时不存在 ⇒ 红，**这是核心**）；
③ **负对照**：未达阈值时**断 200**（防「限流恒拒」这种反向劣解）。

⚠️ **硬规则**：凡装了替身且会打到限流端点的用例，**必须显式给一个独立的 `X-Forwarded-For`**，
不得依赖 MockMvc 默认的 `remoteAddr`（它是**上下文级常量**，而替身是**类级安装、用例级清空**
⇒ 不这么做会重现事实 ⑬ 那颗跨用例共享状态的雷）。

## 3 只读冒烟（**不写库**）

> ⚠️ **本项没有「一眼可见的界面」**（限流是横切行为，前端零改动）⇒ 冒烟只能看 **HTTP 响应**。

```bash
# 找一个【只读 + 已接入限流 + 零副作用】的受限端点，连打至超限，观察 429 + Retry-After
# 例（P1 的 public-read 组，C6 之后才可用；需先取到合法的 formId/lpId）：
for i in $(seq 1 12); do
  curl -s -o /dev/null -w "%{http_code} " -H 'X-Forwarded-For: 203.0.113.7' \
    "http://localhost:8081/api/v1/public/forms/<id>/meta"
done; echo
curl -s -D - -o /dev/null -H 'X-Forwarded-For: 203.0.113.7' \
  "http://localhost:8081/api/v1/public/forms/<id>/meta" | grep -i '^retry-after'
```

**判据**：前 N 次 **200**，第 N+1 次起 **429**，且 **`Retry-After` 头存在且 ≤ 窗口秒数**；
响应体是**统一的 `ApiResponse` 信封**（`{"code":429,"errorCode":"RATE_LIMITED","message":"请求过于频繁，请稍后再试"}` 一类），
**不是** Security 的空体 401 那一套。

⚠️ **按提交阶段选端点**（本项分 5 次提交接入，见 `plan.md`）：
- **C2 之前**：仓里**没有任何**端点接入 ⇒ **无可冒烟的受限端点**，如实写「本阶段不适用」。
- **C3 之后**：`EmailTrackController` 两个端点（`GET/POST /api/v1/public/track/**`）**是写追踪记录**的
  ⇒ 连打会给**共享开发库**写行 ⇒ **按仓规需用户明确同意**，否则**不冒烟**。
- **C5 之后**：`/api/v1/open/**` 需 **API Key**（可在管理端建一个只读密钥），是**只读**的 ⇒ **可用于只读冒烟**。
- **C6 之后**：P1 的 `public-read` 组最干净（只读、无副作用、无需凭证）。

⚠️ **冒烟要选对端点这件事本身要如实写**：若当前阶段没有「只读 + 有限流 + 无副作用」的端点，
就**写「本阶段无可只读冒烟的受限端点」**，**不得**用一个会写库的端点代替。
⚠️ **不重启 8081**（可能归并行会话所有）。若 8081 上跑的是**改动前的旧实例**，冒烟会**看不到限流**
⇒ 必须按 §4 起隔离实例，并**在留痕里写明冒烟打的是哪个端口**。

## 4 隔离实例配方（**写端点或需要干净 Redis 时用**）

⚠️ **绝不碰共享开发库**。以下每一步都用**独立**的端口 / schema / Redis db：

```bash
# ① 独立 schema（MySQL 在 WSL 里；root 口令 123456，crm_user 无 CREATE DATABASE 权限）
wsl -e mysql -uroot -p123456 -e "CREATE DATABASE crm_rl100 CHARACTER SET utf8mb4;"
wsl -e mysql -uroot -p123456 -e "GRANT ALL ON crm_rl100.* TO 'crm_user'@'%'; FLUSH PRIVILEGES;"

# ② 独立端口 + 独立 schema + 另一个 Redis db（注意 Spring 的 JDBC URL 用 spring.datasource.url）
cd backend && mvn -B spring-boot:run \
  -Dspring-boot.run.arguments="--server.port=8099 \
    --spring.datasource.url=jdbc:mysql://localhost:3306/crm_rl100 \
    --spring.data.redis.database=5"

# ③ 冒烟（照 §3），把 <id> 换成隔离库里自建的样本行

# ④ 收尾（三项都要做，并**核对共享库未动**）
wsl -e mysql -uroot -p123456 -e "DROP DATABASE crm_rl100;"
wsl -e mysql -uroot -p123456 -e "REVOKE ALL ON crm_rl100.* FROM 'crm_user'@'%'; FLUSH PRIVILEGES;"
redis-cli -n 5 FLUSHDB      # 只清隔离用的 db 5，绝不 FLUSHALL
# ⑤ 核对共享库未动：隔离实例建表数与既有库逐表比对
```

⚠️ **隔离实例会自己跑 Flyway 建全套表**（这正是它「隔离」的含义）——**不要**把它指向共享 schema。
⚠️ 若 `target/` 里的胖 jar 被占用让 `spring-boot:run` 起不来，照记忆配方**直启 `target/classes`**
（`java -cp target/classes:... com.crm.CrmApplication`），**不要**去动别人的进程。
⚠️ Redis 与 MySQL 由**单个中继进程**暴露（6379 / 3306）⇒ 中继掉了这些命令会**连接失败**，先确认中继在。

## 5 可核判据（替代「没有门禁」的那几条）

```bash
cd /e/code/crm

# ① 3 份 clientIp 副本已收敛成 1 份（私有副本零命中）
grep -rn "X-Forwarded-For" backend/src/main/java/com/crm/ | grep -v ClientIpResolver
#   期望：只有 AuthService.resolveClientIp 的 1 行委托 + ApiKeyAuthFilter/JwtAuthFilter 的无关用途

# ② 两处内存桶已消失
grep -rn "rateBuckets\|cleanupRateBuckets" backend/src/main/java/
#   期望：零命中
grep -rn "rateBuckets\|cleanupRateBuckets\|提交过于频繁" backend/src/test/java/
#   期望：零命中（证明这两条路径此前【零用例】—— 事实 ⑬ 的判据）

# ③ 旧处理器与旧的裸字符串 code 已消失
grep -rn "TOO_MANY_REQUESTS" backend/src/ frontend/src/
#   期望：零命中（这是本项唯一一处对外 code 字符串变更，见 plan.md「五处对外可观测变更」①）
grep -rn "RateLimitedException" backend/src/
#   期望：零命中（旧的控制器私有内部类及其处理器同批删除）

# ④ 阈值逐字未变（收敛不得顺手改数字）
grep -n "public-form-submit\|public-email-track" -A3 backend/src/main/java/com/crm/service/FormService.java \
  backend/src/main/java/com/crm/controller/EmailTrackController.java
#   期望：3 / 60s 与 60 / 60s，与改造前逐字相同

# ⑤ 每个端点都有限流或显式豁免（台账测试自己会跑，这里是人工复核）
mvn -B test -Dtest=RateLimitCoverageTest
```

## 6 订正不静默自查（交付时必跑）

**判据：旧值必须仍能被 `grep` 到**（零命中 = 静默改写）。

```bash
cd /e/code/crm
grep -n "仅 1 处\|仅 \`EmailTrackController\`\|仍需过滤器层统一限流" CRM_FEATURE_COMPARISON.md   # 期望非零
grep -n "均无限流" CRM_FEATURE_COMPARISON.md                                                   # 期望非零
grep -n "001~099\|001–099\|98 个功能模块" README.md specs/README.md specs/roadmap.md           # 期望非零（原文保留）
grep -n "全仓首个 429" backend/src/main/java/com/crm/common/ErrorCode.java                     # 期望非零（原文保留 + ⚠️ 块）
grep -n "此处直接降级为 400 保持公开端点简单" backend/src/main/java/com/crm/controller/EmailTrackController.java  # 期望非零（原文保留 + ⚠️ 块）
```

⚠️ 第 4、5 条的「原文保留」是**代码注释**里的订正（不是文档），同样适用「订正不静默」：
**旧注释逐字保留 + 紧邻带日期 ⚠️ 块**说明它当年为什么那么写、以及今天为什么不成立。
⚠️ 改完做**竖线自证**：`CRM_FEATURE_COMPARISON.md` 的那两行是表格行，
改动后**列数必须与表头一致**（订正块落进单元格会打破表格）。
⚠️ **`specs/036-online-forms/` 与 `specs/055-open-platform/` 必须零改动**：
`git status --porcelain specs/036* specs/055*` 应为空（**契约是对的，改的是实现**）。
