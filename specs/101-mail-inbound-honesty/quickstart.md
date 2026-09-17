# 快速验证：邮件同步收信侧诚实化（101）

## 0 前置

- 工作目录 `E:\code\crm`；后端 JDK **21**（裸 `java` 在长命 shell 里可能仍是 17）。
- 后台可能已有**别的会话**在跑的 8081（后端）与 5173（前端）——**不要重启它们**。
- **绝不碰共享开发库**：本项的冒烟会**写数据**（演示档）⇒ 一律走 §4 的隔离实例。

## 1 门禁（唯一权威判据）

```bash
cd /e/code/crm/backend && mvn -B spotless:apply    # spotless 是 verify 相位首个门禁，比用例失败更早中止
cd /e/code/crm/backend && mvn -B verify            # ⚠️ 不传 -DargLine（会静默废掉 jacoco）
ls -la /e/code/crm/backend/target/jacoco.exec      # 必须存在
```

- **判据**：**失败集合 ⊆ 4 例已批准偏差**（`IntegrationHubIT.integrationFlow`、`OpportunityIT.closeWithoutResultReturns422`、`UserIT.disableUserRevokesAccess`、`UserIT.userLifecycle`）**且本项新增用例全绿**——**不是** exit 0。
- 读 `jacoco:check` 的结论行（「All coverage checks have been met.」）；INSTRUCTION 阈值 0.73 未改。
- ⚠️ **门禁跑完不要再跑 Maven**（会重写 `jacoco.exec`，让交付块里的字节数变成假话）。若某条自查判据要求实跑某个测试类，而这已经在那次 verify 里跑过，就**引用整次 verify 的读数**并写明为什么没有单独复跑。

### 1.1 定向跑（改一处跑一处，比全量 verify 快得多）

```bash
cd /e/code/crm/backend
mvn -B test -Dtest='MailInboundStatusTest,MailSyncRecordServiceTest'
mvn -B verify -Dit.test='EmailSyncIT,MailInboundDemoIT,PermissionEnforcementIT' -DfailIfNoTests=false
```

⚠️ 定向跑会重写 `jacoco.exec` ⇒ **只在门禁之前**用它；门禁之后一概不跑（见上）。

### 1.2 前端

```bash
cd /e/code/crm/frontend
pnpm run i18n:check && pnpm run lint && pnpm run typecheck && pnpm run ui:check && pnpm run zh:check
pnpm exec vitest run src/pages/mail/MailSyncPage.perm.test.tsx
```

判据：全绿；`i18n:check` 报 **2963/2963** 键、路由 **58** / manifest **56** **不变**；`zh:check` 的硬编码中文台账**不得增加**。
⚠️ **不跑全量 vitest**（本机默认 worker 池超订 ⇒ 重文件假红：撞 `testTimeout`）。定向跑即可，必要时用文件内 `it(n, fn, 60_000)` 放宽耐心（**不动断言**）。

## 2 本项的证据形态（**读之前先看这条**）

本项交付的证据是**四条行为**，不是一个功能：

1. **默认部署下，同步端点拒绝且零副作用**（T1/T2 + 手工冒烟默认档）——本项的核心主张；
2. **演示档下生成的记录被明确标注**（T3/T4 + 手工冒烟演示档）；
3. **界面上不存在把 `SIMULATED` 读成「已同步」的通路**（T6/T7）；
4. **配置缺省即 false**（T8）——⚠️ 这一条**只能**由直接构造该类的单测钉住：IT 读的是 `application.yml`，改 Java 侧 `@Value` 的 `:false`**不会**让任何 IT 变红。

⚠️ **本项没有「限流那类假绿陷阱」的等价物，但有一个形态相同的坑**：断言「默认路径没写记录」时，**不能**写成「事务会回滚所以没写」——必须是**正面断言**（`verify(mapper, never()).insert(any())` 与 `total == 0`）。定向破坏 **D3** 专门证明这两条断言真的看着副作用。

## 3 只读冒烟（前端，**不写库**）

```bash
# 用已在跑的 5173 打开 /mail-sync（不点「同步收件」按钮 —— 它会打后端）
# 看两处：① 表上方那条 Alert 的新文案；② 状态列的三种渲染（若列表里有历史记录）
```

**不点按钮**：默认档下它必然 409（无害），但**演示档若误开就会写库**——所以只读冒烟里一律不点。

## 4 隔离实例配方（**演示档冒烟必须用它**）

⚠️ **绝不碰共享开发库**。每一步都用**独立**的端口 / schema / Redis db：

```bash
# ① 独立 schema（MySQL 在 WSL 里；root 口令 123456，crm_user 无 CREATE DATABASE 权限）
#   ⚠️ 先确认 crm_user 的 host：SELECT user,host FROM mysql.user; —— 本仓实测是 localhost（不是 %，
#   用 % 会 ERROR 1410）。按查到的那个 host 写 GRANT。
wsl -e mysql -uroot -p123456 -e "CREATE DATABASE crm_ib101 CHARACTER SET utf8mb4;"
wsl -e mysql -uroot -p123456 -e "GRANT ALL ON crm_ib101.* TO 'crm_user'@'localhost'; FLUSH PRIVILEGES;"

# ② 默认档：独立端口 + 独立 schema + 另一个 Redis db
cd /e/code/crm/backend && mvn -B spring-boot:run \
  -Dspring-boot.run.arguments="--server.port=8099 \
    --spring.datasource.url=jdbc:mysql://localhost:3306/crm_ib101 \
    --spring.data.redis.database=5"

# ③ 默认档冒烟（下面 §4.1 的 ①–③），然后**重启**为演示档：
#   ... 同上再加 --crm.mail.inbound.demo-enabled=true
```

⚠️ 隔离实例会自己跑 Flyway 建全套表（这正是「隔离」的含义）——**不要**把它指向共享 schema。
⚠️ 若 `target/` 的胖 jar 被占用让 `spring-boot:run` 起不来，照记忆配方**直启 `target/classes`**，**不要**去动别人的进程。

### 4.1 冒烟脚本（两次启动，逐条如实记读数）

```bash
TOKEN=<登录拿到的 token>; BASE=http://localhost:8099/api/v1

# ① 默认档：建一个账户（拉取它 id）→ 连调两次同步端点
#   期望：两次都是 409，且 error.code == MAIL_INBOUND_NOT_CONFIGURED、error.message 含「未接入收信源」
curl -s -o /dev/null -w '%{http_code}\n' -X POST "$BASE/mail-accounts/<id>/sync" -H "Authorization: Bearer $TOKEN"
# ② 默认档：随后查记录数
#   期望：total 与调用前**逐字相同**（连点不累积）
curl -s "$BASE/mail-accounts/<id>/records" -H "Authorization: Bearer $TOKEN"
# ③ 演示档：重启后调一次
#   期望：200，data.syncStatus == "SIMULATED"，data.subject 带演示标记；records 的 total +1

# ④ 收尾（三项都要做，并**核对共享库未动**）
wsl -e mysql -uroot -p123456 -e "DROP DATABASE crm_ib101;"
wsl -e mysql -uroot -p123456 -e "REVOKE ALL ON crm_ib101.* FROM 'crm_user'@'localhost'; FLUSH PRIVILEGES;"
redis-cli -n 5 FLUSHDB      # 只清 db 5，绝不 FLUSHALL
# ⑤ 核对共享库未动 + 8081 未重启（进程启动时间）
```

⚠️ Redis 与 MySQL 由**单个中继进程**暴露（6379 / 3306）⇒ 中继掉了这些命令会连接失败，先确认中继在。

## 5 可核判据（替代「没有门禁」的那几条）

```bash
cd /e/code/crm

# ① 旧标识符已消失（新名已在）——判据是**代码目录**零命中
grep -rn "simulateSync" backend/src frontend/src   # 期望：0
grep -rn "btnSimulateSync" frontend/src            # 期望：0
grep -rn "模拟同步邮件" backend/src                 # 期望：0（旧主题）

# ② 状态值域三值齐备
grep -n "STATUS_SIMULATED\|STATUS_SYNCED\|STATUS_FAILED" backend/src/main/java/com/crm/entity/MailSyncRecord.java
grep -n "SIMULATED" frontend/src/types/mail.ts     # 联合类型里必须有

# ③ 默认值两处同向（都 false）
grep -n "demo-enabled" backend/src/main/resources/application.yml
grep -n "demo-enabled" backend/src/main/java/com/crm/config/MailInboundStatus.java

# ④ 062 一字未动（判据是「本项的 5 次提交一个文件都没碰它」）
git diff --stat 66bb71f..HEAD -- specs/062-email-sync/   # 期望：空输出

# ⑤ 权限码未动（086 的两码不合并）
grep -n "mail_sync:manage\|mail_account:manage" backend/src/main/java/com/crm/controller/MailAccountController.java

# ⑥ 零迁移（迁移数 89 的 4 个落点本项一个都不动）
git diff --stat 66bb71f..HEAD -- backend/src/main/resources/db/migration/   # 期望：空输出
```

## 6 订正不静默自查（交付时必跑）

**判据是「旧值仍能被 grep 到」**。逐条记命中数，**0 命中即静默改写**（先分辨「不存在」与「跨行」，再决定是改排版还是登记缺口）：

```bash
cd /e/code/crm

# ① P0 第 6 条的原文（`:371`）
grep -c "收信侧 \`simulateSync\` 仍在" CRM_FEATURE_COMPARISON.md          # 期望 ≥1（保留在 ⚠️ 块内）
grep -c "接 IMAP 或从功能清单移除" CRM_FEATURE_COMPARISON.md              # 期望 ≥1
# ② 模块表行与小结（`:223` / `:228`）
grep -c "不可对客户宣称已具备邮件同步" CRM_FEATURE_COMPARISON.md          # 期望 ≥1
grep -c "它是\"诚信缺口\"里唯一没被一期关掉的一条" CRM_FEATURE_COMPARISON.md  # 期望 ≥1
# ③ 评分/深度两行（`:318` / `:347`）
grep -c "邮件同步收信侧仍为模拟" CRM_FEATURE_COMPARISON.md                # 期望 ≥1
# ④ 新发现表（`:477`）
grep -c "邮件同步为 \`simulateSync\` 模拟实现" CRM_FEATURE_COMPARISON.md   # 期望 ≥1
# ⑤ README 的旧文（`:448` 引用它的那一句必须一起处理）
grep -c "同步流程为模拟实现，尚未接入 IMAP" CRM_FEATURE_COMPARISON.md      # 期望 ≥1（留在 ⚠️ 块内）
grep -c "邮件账户配置、同步记录框架" PROJECT_FEATURES.md                   # 期望 ≥1（旧模块表描述）
# ⑥ i18n 数字落点（旧值必须仍在文本里）
grep -c "2962" PROJECT_FEATURES.md                                        # 期望 ≥1
grep -c "3452" PROJECT_FEATURES.md                                        # 期望 ≥1
grep -c "99（001–100" PROJECT_FEATURES.md                                 # 期望 ≥1
```

⚠️ **口径**：这些判据要 grep 的正是**旧值**，而留痕规则要求旧值**仍留在注释/⚠️ 块里** ⇒ 命中数 ≥1 才是「订正不静默」的**正向证据**，**不是**「没改干净」。真正要判的是：新值有没有到位（`i18n:check` 2963、`grep -c "2963"` ≥1、`grep -c "100（001–101"` ≥1）。
⚠️ 若某条 0 命中，**先分辨**是「字面量不存在」还是「被格式化器折行断成两行」（`grep -o` 不带行锚再试）；后者处置是**改排版**（用 `<br>` 把原文固定在自己一行），**不是**登记成口径缺口。

## 7 交付块要抄的读数（交付时填，不得预填）

| 项 | 来源 |
|---|---|
| 后端门禁 | 那次完整 `mvn -B verify` 的退出码 / surefire / failsafe / 失败集合 |
| `jacoco.exec` | 字节数（**门禁之后不再跑 Maven**） |
| 覆盖率 | `jacoco:check` 结论行 + INSTRUCTION / BRANCH / LINE |
| 前端 | `i18n:check` 的键数与路由数、五道门禁的结论、定向 vitest 的用例数 |
| 定向破坏 | D1–D7 逐条的「该红的判据 → 实际观测」与还原判据 |
| 冒烟 | 隔离实例的端口 / schema / Redis db、默认档两次的读数、演示档一次的读数、收尾核对 |
