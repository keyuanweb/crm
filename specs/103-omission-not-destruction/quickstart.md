# 快速上手与判据（103）

## 1 这条缺陷一句话

`CustomFieldService.saveValues` 是**先删后插**。客户端**看不见**（HIDDEN）或**改不动**（READ_ONLY）的字段不会出现在提交里，于是**被一起删掉**。

102 给 HIDDEN 补了回补，**判据只覆盖 `hidden`** ⇒ **READ_ONLY 被省略时仍然丢值**，而前端今天**就在渲染 READ_ONLY 字段**、邀请用户去清空一个他改不动的字段。

## 2 修了什么 / 没修什么

| | |
|---|---|
| **修** | 回补判据 `hidden` → `!EDITABLE`（HIDDEN ∪ READ_ONLY），**并新增「已提交 id 排除」** |
| **不修** | 其余约 18 处同类实例（**登记在 `research.md` §2**） |
| **不改** | LEAD `ownerId`（**实测为非缺陷**，`research.md` §1） |
| **零** | 迁移、schema 变更、新类 |

⚠️ **「已提交 id 排除」不是可选优化**：`custom_field_value` 上有 `uk_field_entity_value (field_id, entity_id)`，而 READ_ONLY 的值**会被客户端原样回传并活着穿过 `validateWrite`** ⇒ 少了这个排除，**每次 PUT 都 500**。机理见 `research.md` §0.1。

## 3 关键坐标

| 什么 | 在哪 |
|---|---|
| 修复点 | `backend/src/main/java/com/crm/service/CustomFieldService.java` —— `hiddenFieldIds:73-78`（孪生处）、回补块 `:252-265` |
| 判据同口径的孪生 | `backend/src/main/java/com/crm/support/FieldMaskPlanner.java:58-67`（`!EDITABLE`，javadoc「往严的一侧倒」） |
| 写侧校验 | `backend/src/main/java/com/crm/service/FieldPermissionService.java:279-307`（`validateWrite` 只遍历**已提交**的值） |
| 必填校验（**早于**回补） | 同文件 `:218-222` |
| 唯一索引 | `db/migration/V39__custom_field_value.sql:13` + `src/test/resources/schema-h2.sql:740` |
| 前端两个消费点 | `frontend/src/components/CustomFieldItems.tsx`、`frontend/src/hooks/useCustomFieldFilters.ts:13` |
| 标记从哪来 | `CustomFieldService.listByEntity:59-61`（`/definitions` **已下发**，分页端点**不下发**） |
| 契约台账 | `contracts/omission-restore.md` |

## 4 怎么跑（本机）

IT 用 **H2 内存库 + `schema-h2.sql`**（`flyway.enabled: false`），**不碰** WSL 里的 MySQL；Redis 被 `@MockBean` 掉。

```bash
# 单跑一条 IT（绕开全量单测）
export JAVA_HOME="$HOME/.jdks/jdk-21.0.12.1+1"
cd backend && mvn -B -Djava.version=21 test-compile failsafe:integration-test failsafe:verify -Dit.test=FieldPermissionIT
```

⚠️ **`-Djava.version=21` 是本批的环境偏离**：工区里有一处**不属于本批**的未提交改动（`backend/pom.xml` 的 `<java.version>` 21 → 25，属 `appmod/java-upgrade-*` 线）让**门禁在任一 JVM 下都跑不完**——JDK 25 下 spotless 崩（`NoSuchMethodError` on `Log$DeferredDiagnosticHandler.getDiagnostics()`）、JDK 21 下 release 25 编译不过。处置：**不碰那处改动**，以命令行覆盖该 property（磁盘零改动、不碰 `-DargLine`、JaCoCo 不受影响）。详见 `research.md` §5。

## 5 门禁判据

```bash
cd backend && mvn -B -Djava.version=21 spotless:apply     # verify 相位首个门禁
cd backend && mvn -B -Djava.version=21 verify
ls backend/target/jacoco.exec
cd frontend && pnpm run i18n:check && pnpm run lint && pnpm run typecheck && pnpm run ui:check && pnpm run zh:check
```

- `jacoco:check` **必须打印结论行**「All coverage checks have been met.」——**没有这行 = 门禁根本没被判定**，别把「没搜到某串」当成「不存在结论」。
- `zh:check` 的硬编码中文台账**不得增加**（本批**加零个 i18n 键**）。
- **交付态读数只取那一次完整 `verify`**；门禁跑完**不再跑 Maven**（会覆盖 `jacoco.exec`，让交付块的读数变成假话）。

## 6 复算命令（交付边界与文档落点用）

```bash
# 受保护字段的判据是否两处同口径（应各命中一处）
grep -rn "PERM_EDITABLE.equals" backend/src/main/java/com/crm/
# 唯一索引是否仍在两处（应各命中一处）
grep -rn "uk_field_entity_value" backend/src/main/resources/db/migration/ backend/src/test/resources/
# 056 目录是否一字未改（应为空）
git diff --stat -- specs/056-field-permission/
# 探针残留（应为 0）
grep -rn "留痕后还原" backend/src frontend/src
# DELIVERY_SCOPE.md 是否含快照数字（预期 0 命中）
grep -nE "[0-9]+ *个|[0-9]{3,}" DELIVERY_SCOPE.md
```

## 7 交付时填（**不得预填**）

| 项 | 读数 |
|---|---|
| 门禁那次 `verify` 的结果 | （交付时填） |
| `jacoco.exec` 字节数 / mtime | （交付时填） |
| 前端五道 | （交付时填） |
| `i18n:check` 键数 / 行数 | （交付时填） |
| 定向破坏 D1–D11 的实测输出 | 见 `falsification-evidence.md` |
