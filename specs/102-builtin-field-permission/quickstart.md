# 快速验证：字段级权限的内置字段（102）

## 0 前置

- 在 `E:\code\crm` 下按本文件逐节跑；**不要**动 `.specify/feature.json`；**不要**跑任何 `/speckit-*`。
- **不要**在共享开发库上写数据（本项观测掩码**必须**有配置行 ⇒ 一律走 §4 的隔离实例）。
- **不要**重启 8081 后端（PID 5032）与 5173 前端（PID 14116）——那是并行会话的。

---

## 1 门禁（唯一权威判据）

```bash
cd backend && mvn -B spotless:apply     # spotless 是 verify 相位首个门禁，比用例失败更早中止
cd backend && mvn -B verify             # 不传 -DargLine（会静默废掉 jacoco）
ls -l backend/target/jacoco.exec        # 必须存在
grep -c "All coverage checks have been met." <本次 verify 日志>   # 必须 ≥1（成功判据就这一行）
```

**后端判据**：**失败集合 ⊆ 4 例已批准偏差 + 本项新增全绿**——**不是** exit 0（exit 0 会把「那 4 例没红」读成必要条件）。surefire / failsafe 的 Failures · Errors · Skipped 三栏数字**逐次如实记**。

### 1.1 定向跑（改一处跑一处，比全量 verify 快得多）

```bash
cd backend && mvn -B -o test -Dtest='BuiltinFieldRegistryTest,FieldMaskPlannerTest,FieldPermissionServiceBuiltinTest'
cd backend && mvn -B -o verify -Dit.test='BuiltinFieldMaskingIT,BuiltinFieldWriteGuardIT' -DfailIfNoSpecifiedTests=false
cd backend && mvn -B -o verify -Dit.test='BuiltinFieldExportIT,FieldPermissionAvailableFieldsIT' -DfailIfNoSpecifiedTests=false
```

### 1.2 前端

```bash
cd frontend && pnpm run i18n:check && pnpm run lint && pnpm run typecheck && pnpm run ui:check && pnpm run zh:check
cd frontend && pnpm exec vitest run src/pages/settings/FieldPermissionPage.test.tsx   # 定向，不跑全量（本机默认 worker 池超订）
```

---

## 2 本项的证据形态（**读之前先看这条**）

本项**最容易被误读**的一点是：**未配置内置权限时，收口点是逐字直通的** ⇒ **既有全量用例全绿，不能用来证明新机制正确**（那是**默认档**该验的，见 §3）。

⇒ 本项读侧/写侧/导出侧的**全部**主张，都由**真的配了权限行**的用例承担（T8–T16）。三条假绿通道逐条写在 `research.md` §13.1，写用例与读用例时都要对上：

| 通道 | 为什么不能采信 | 防守 |
|---|---|---|
| 未配置 ⇒ plan 空 ⇒ 直通 | 既有用例**全部**在这个状态下跑 | T8–T13 真的配置 |
| 导出不经过 Jackson 收口点 | JSON 全绿与 xlsx 无关 | T15 / T16 |
| 定时导出无请求主体 | 用 `SecurityUtil` 取主体恒为 null | T16 + D7 |

---

## 3 只读冒烟（**不写库**）

用已在跑的 8081/5173（**只读，不点任何保存**）：

```bash
# ① 默认档直通：未配置内置权限时，客户/商机响应与改动前逐字相同
curl -s -H "Authorization: Bearer $SALES" localhost:8081/api/v1/customers | head -c 400
#    期望：phone/email/address/remark 等**都有值**（收口点未命中 ⇒ 逐字直通）
# ② 配置面可读：可用字段端点返回内置 + 自定义
curl -s -H "Authorization: Bearer $ADMIN" 'localhost:8081/api/v1/field-permissions/available-fields?entityType=CUSTOMER'
#    期望：items 含 fieldKey=phone 等 7 条内置项（fieldId 为 null、builtin=true）+ 既有自定义项
# ③ 前端：打开 /settings/field-permissions，「字段」下拉里**同时**有内置字段（如「电话」）与自定义字段，
#    且列表的「字段」列显示**名称**而不是一个数字
```

⚠️ 观察**掩码生效**需要先写配置行 ⇒ **必须**用 §4 的隔离实例，**不得**在共享库上配。

---

## 4 隔离实例配方（**配置档 / 导出档冒烟必须用它**）

```bash
# ① 独立 schema（MySQL 在 WSL 里；root 口令 123456；crm_user 无 CREATE DATABASE 权限）
#   ⚠️ 先确认 crm_user 的 host：SELECT user,host FROM mysql.user; —— 本仓实测是 localhost
#   （不是 %，用 % 会 ERROR 1410）。按查到的那个 host 写 GRANT。
# ② 独立端口 + 独立 schema + 另一个 Redis db 启动后端（直启 target/classes 或另跑 spring-boot:run）
#    ⚠️ 不要用 8081；不要碰共享 Redis 的那个 db
# ③ 三档冒烟（逐条如实记读数）：
#    a. 默认档（不配任何内置权限）⇒ 响应与改动前逐字相同
#    b. 配置档（在**隔离库**里配 SALES + CUSTOMER + phone + HIDDEN）
#       ⇒ SALES 令牌读 列表/详情/公海 ⇒ 三处 phone 均为 null
#       ⇒ ADMIN 令牌读同样接口 ⇒ phone 为真值（正对照，防「全 null」假绿）
#       ⇒ SALES 提交一次**不含 phone** 的 PUT ⇒ 200，随后 phone **仍是原值**
#       ⇒ SALES 提交一次**改过** 某个 READ_ONLY 字段的 PUT ⇒ 422 FIELD_READ_ONLY
#       ⇒ SALES 提交一次**带 phone** 的 PUT ⇒ 422 FIELD_HIDDEN
#    c. 导出档：SALES 触发客户导出 ⇒ xlsx 的「电话」列**在**、该行该格**空**；ADMIN 导出同数据有值
# ④ 收尾（三项都要做，并**核对共享库未动**）：DROP 隔离 schema / REVOKE / FLUSHDB 另一个 db
# ⑤ 核对共享库未动 + 8081/5173 未重启（比对进程启动时间）
```

---

## 5 可核判据（替代「没有门禁」的那几条）

⚠️ **口径先写清**：下面的「**0 命中**」判据**必须**排除**行首注释**（`grep -vE ':[0-9]+:\s*(\*|//|--)`）——本仓的「订正不静默」规则**要求**旧值留在 ⚠️ 注释里，照字面跑「0」**在任何正确实现下都拿不到**（先例：101 批 §5 ① 按字面跑得 4，全是留痕注释）。
⚠️ 该 filter **漏得掉行尾注释**；且 `src/test` 里的**负断言**是「旧值已消失」的**正向证据**，要**显式许可**。

```bash
# ① 056 一字未动（判据：本项的全部提交里，specs/056-field-permission/** 一个文件都没出现）
git log --name-only --pretty=format:'%h %s' <C1..C7 的区间> -- specs/056-field-permission | wc -l   # 期望 0

# ② 迁移面：V91 存在、总数 90、V72 仍不存在；**两处迁移清单**都要列到 V91
ls backend/src/main/resources/db/migration | wc -l                      # 期望 90
ls backend/src/main/resources/db/migration | grep -c '^V91__'           # 期望 1
ls backend/src/main/resources/db/migration | grep -c '^V72__'           # 期望 0（V72 从未存在）
grep -c '^| V91 ' specs/README.md INSTALL.md                            # 各 1（⚠️ 两处清单状态不同：改造前 specs/README.md 末行是 V90、**INSTALL.md 末行是 V89**）

# ③ 镜像标记：schema-h2 的 field_permission 块有 3 处 -- V91
sed -n '/CREATE TABLE field_permission/,/^CREATE TABLE currency_rate/p' backend/src/test/resources/schema-h2.sql | grep -c -- '-- V91'
# 期望 3（field_id 改可空 / 新增 field_key / 新增第二条唯一索引）
grep -c '"91"' backend/src/test/java/com/crm/integration/SchemaParityIT.java   # 期望 1

# ④ 零新增错误码 / 零新增权限码
git diff <区间> -- backend/src/main/java/com/crm/common/ErrorCode.java | grep -c '^+.*F('      # 期望 0
git diff <区间> -- backend/src/main/java/com/crm/security/RoleConstants.java | wc -l           # 期望 0

# ⑤ 未引入全局 @JsonInclude（仍是 0 命中）
grep -rn 'JsonInclude' backend/src/main/java | wc -l                    # 期望 0

# ⑥ 056 的「后续扩展」三处原文仍在（本项**引用**它，不改它）
grep -c '内置字段不在 v1 范围，后续扩展' backend/../specs/056-field-permission/spec.md   # 期望 1
grep -c '内置字段权限后续扩展' backend/../specs/056-field-permission/spec.md             # 期望 1

# ⑦ i18n 实测（值取交付时那一次 i18n:check 的输出）
cd frontend && pnpm run i18n:check 2>&1 | grep -E '键|行|key|line'
```

---

## 6 订正不静默自查（交付时必跑）

```bash
# ① P0 第 1 条：状态列**新值**在，**旧值**也仍可 grep 到（旧值在 ⚠️ 块内）
grep -c '基本闭合' CRM_FEATURE_COMPARISON.md          # 期望 ≥1
grep -n '仍缺' CRM_FEATURE_COMPARISON.md | grep -vE ':[0-9]+:\s*(\||>)' # 旧值仍在（含本行原文）

# ② 2.9 FLS 行 + 小结：被订正的两句原文逐字仍在
grep -c '内置字段（客户名/金额等）与 API 出参' CRM_FEATURE_COMPARISON.md    # 期望 ≥1
grep -c 'FLS 内置字段、SSO、字段加密、IP 白名单四项一个都没动' CRM_FEATURE_COMPARISON.md  # 期望 ≥1

# ③ 4.1 结论 3 与时间线块：原文逐字仍在
grep -c '字段加密 / IP 白名单' CRM_FEATURE_COMPARISON.md                  # 期望 ≥1

# ④ 迁移数 **6 处活落点**：旧值仍在（留痕）、新值已到 —— 逐处各查一次
grep -c 'V1~V90 共 89 个脚本' INSTALL.md                                # 期望 ≥1（旧值留痕）
grep -c 'V1~V91 共 90 个脚本' INSTALL.md                                # 期望 ≥1（新值）
grep -c '共 89 个迁移脚本' INSTALL.md                                   # 期望 ≥1（旧值留痕）
grep -c 'V1~V91，共 90 个迁移脚本' INSTALL.md                           # 期望 ≥1（新值）
grep -c '^| V90 ' INSTALL.md                                            # 期望 1（补齐的行）
grep -c '^| V91 ' INSTALL.md                                            # 期望 1（本项新增）
# ⚠️ 下面用 grep -Fc（定长字面量，-F）：这些串里嵌着 markdown 的 `*`，用正则会让判据自己先烂掉
# ⚠️ 每条后面括注的「改造前」是本判据**立项期实跑的自证读数**（判据必须自证它到底匹配到了什么）
grep -Fc 'V1~**V90**，共 **89**' specs/README.md                        # 改造前 2 → 期望 ≥1（旧值留痕：前言版本行 + 章节标题各一处，**两处都命中是对的**）
grep -Fc 'V1~**V91**，共 **90**' specs/README.md                        # 改造前 0 → 期望 2（新值，同上两处）
grep -Fc 'Flyway V1~**V90**，共 **89** 个脚本' specs/README.md          # 改造前 1（**章节标题**——082 批漏过的那一处）
grep -c '^| V91 ' specs/README.md                                       # 改造前 0 → 期望 1（迁移表末行；同文件 `^| V90 ` 改造前为 1）
grep -Fc '**89 个（V1–V90，缺 V72）**' PROJECT_FEATURES.md              # 改造前 1 → 期望 ≥1（旧值留痕）
grep -Fc '**90 个（V1–V91，缺 V72）**' PROJECT_FEATURES.md              # 改造前 0 → 期望 1（新值）
grep -Fc 'db/migration（V1~V91' README.md                               # 改造前 0 → 期望 1（根目录树）
# ⚠️ 历史读数**不得**被改：重测块/订正块里的旧值应**原样**（改造前读数逐个记在下面，改完必须**逐个相同**）
grep -Fc '**89（V1–V90）**' PROJECT_FEATURES.md                         # 改造前 1（096 重测块的历史读数）
grep -Fc 'V1~V90 / 89 个' PROJECT_FEATURES.md                           # 改造前 1（096 重测块）
grep -Fc 'Flyway **89（V1–V90，缺 V72）**' PROJECT_FEATURES.md          # 改造前 2（097/099 重测块）

# ⑤ README 编号：旧值仍在、新值已到
grep -c '99 个功能模块，001~100' README.md                              # 期望 ≥1（旧值留痕）
grep -c '101 个功能模块，001~102' README.md                             # 期望 ≥1（新值）

# ⑥ PROJECT_FEATURES 的模块数/规格数：旧值仍在、新值已到
grep -c '100（001–101，缺 069）' PROJECT_FEATURES.md                    # 期望 ≥1（旧值留痕）
grep -c '101（001–102，缺 069）' PROJECT_FEATURES.md                    # 期望 ≥1（新值）

# ⑦ 两份登记
grep -c '102-builtin-field-permission' specs/README.md specs/roadmap.md  # 各 ≥1
grep -n '100 → 101\|001–102' specs/roadmap.md | head

# ⑧ spotless 重排自证（长句留痕用 <br> 自占一行，改完重跑确认没被折回）
cd backend && mvn -B spotless:apply && git diff --stat -- ../CRM_FEATURE_COMPARISON.md   # 期望无变化
```

---

## 7 交付块要抄的读数（**交付时填，不得预填**）

| 项 | 从哪取 |
|---|---|
| 后端门禁 | 那一次完整 `mvn -B verify` 的退出码 / BUILD 结论 |
| surefire / failsafe | 同一次日志的 Tests / Failures / Errors / Skipped 四栏 |
| spotless | 同一次日志的 clean 文件数 |
| jacoco | **结论行**（「All coverage checks have been met.」）+ `jacoco.exec` 的字节数与 mtime + INSTRUCTION / BRANCH / LINE / METHOD 四项 |
| 前端五道 | `i18n:check`（键/行数、路由数、manifest 数）、其余四道的结论 |
| 定向 vitest | `FieldPermissionPage.test.tsx` 的文件数 / 用例数 |
| 冒烟 | §4 的 a/b/c 三档**逐条**读数 |
| 订正自查 | §6 的 ①–⑧ **逐条命中数** |

⚠️ **交付态读数不得被后一次 `mvn test` 冲掉**：门禁跑完**不要再跑 Maven**；交付块引用**整次 verify** 的读数并写明**出处**。
⚠️ 本仓已知现象：**同一棵未改动的树两次 verify 的分母会漂移**（IDE 语言服务原地增量编译改写 `target/classes`）⇒ 取那一次并写明出处，**不去**「确认哪一次更准」。
