# 数据模型：内置字段权限（102，**只记 V91 的增量**）

**本文件的范围**：**只写 `V91` 相对 `V64` 的增量**——新列、可空性变更、第二条唯一索引、以及「恰好一列非空」这条不变式的**强制位置**与实测读数。
⚠️ **`V64` 已定的 6 列（`role_code` / `entity_type` / `field_id` / `permission` / `created_by` / 时间戳）不在此重列**——基准见 `specs/056-field-permission/data-model.md:5`，重列会造出「一个数字住在两个地方」的第二份副本。
⚠️ **本项加列不加表 ⇒ 表数不变**（不新增 `CREATE TABLE`）。

---

## 1. `field_permission` 的 V91 增量（Flyway V91）

| 变更 | 前（`V64`） | 后（`V91`） |
|---|---|---|
| `field_id` 可空性 | `BIGINT NOT NULL` | **`BIGINT NULL`** |
| 新列 | — | **`field_key VARCHAR(64) DEFAULT NULL`** |
| 唯一索引 | `uk_field_perm (role_code, entity_type, field_id)` | **保留不动** + 新增 **`uk_field_perm_builtin (role_code, entity_type, field_key)`** |

**语义**：一行权限配置要么指向一个**自定义字段**（`field_id` 非空，`field_key` 为空），要么指向一个**内置字段**（`field_key` 非空，`field_id` 为空）——**二选一**。

⚠️ **`V64__field_permission.sql` 不得编辑**（已应用的迁移永不编辑）；全部变更落在新的 `V91__field_permission_builtin_fields.sql`。
⚠️ **不加 `CHECK` 约束**：本仓 89 个迁移里**零 `CHECK` 约束**，且 `V79__opportunity_stage.sql:15` 留了明文先例（「同理不做 CHECK 约束：停用一个阶段不应让历史行变成非法数据」）。
（口径须自证：`grep -ri CHECK` 在迁移目录里**有 5 处命中，逐条都不是约束**——枚举值 `'TRANSFER/CASH/CHECK/OTHER'`、列名 `check_in_time`、上面那句 V79 先例注释、`V90` 的 `checksum` 与 `checkEntityVisible`。⇒ 「零 CHECK」只在**这个口径**下成立。）

### 幂等与镜像

- `schema-h2.sql` 的 `DROP TABLE IF EXISTS field_permission;` **已存在**（该文件头部自带 DROP 块）⇒ 本项**不需要**加 DROP。
- `schema-h2.sql` 改动 **3 处**，按该文件头部的**版本标记约定**（不建表的迁移，其影响的列/索引行尾标 `-- V<n>`）：① `field_id` 改可空那行；② 新增的 `field_key` 列那行；③ 新增的第二条唯一索引那行。
- `SchemaParityIT.MIRRORED_MIGRATIONS` 加 `"91"`（**刻意逐项枚举、不用区间**——该清单自己的 javadoc 写了理由：区间会让「顺手加一」看起来无害）。该清单在 `V91` 后共 **90** 个版本号（V72 不存在）。

---

## 2. 业务规则与**强制位置**

| # | 规则 | 由谁强制 | DB 层？ |
|---|---|---|---|
| R1 | `field_id` 与 `field_key` **恰好一个非空** | `FieldPermissionService.upsert`（违者 **422 `FIELD_PERMISSION_INVALID`**） | ⚠️ **否**——见 §3 实测 |
| R2 | 同一 (角色, 实体) 的**自定义**配置唯一 | 旧索引 `uk_field_perm` | **是** |
| R3 | 同一 (角色, 实体) 的**内置**配置唯一 | 新索引 `uk_field_perm_builtin` | **是** |
| R4 | `permission` ∈ {HIDDEN, READ_ONLY, EDITABLE} | **`FieldPermissionRequest` 的 `@Pattern`**（既有，本项不动）+ 实体常量 | 否（`V64` 无 CHECK） |
| R5 | `field_key` 必须是注册表里**该实体**的字段 | `FieldPermissionService.upsert`（违者 422） | 否 |
| R6 | ADMIN 恒 `EDITABLE`；未配置 ⇒ `EDITABLE` | `FieldPermissionService.permissionForKey` | 不适用 |

⚠️ **R1 是「应用层唯一」的规则**：任何**绕过 `upsert`** 直接写这张表的代码都能插进错行（含两列全空的行），且**没有任何自动判据**会发现它（`SchemaParityIT` 自陈不覆盖列级漂移）。**登记为债务 1。**

---

## 3. 实测读数（H2 2.2.224，本项探针）

**为什么必须实测**：本方案的形状**完全依赖**「唯一索引视多个 `NULL` 彼此不等」。若 H2 把 `NULL` 当作彼此相等，则第二条索引会拒绝**第二个**内置字段配置 ⇒ 方案当场不成立。同时，**MySQL 与 H2 必须同向**，否则测试绿而生产红。

| 探针插入 | 读数 | 推论 |
|---|---|---|
| 同 (角色, 实体) 两行**内置**（`field_id` 均 `NULL`，`field_key` 不同） | **通过** | H2 视 `NULL` **彼此不等** ⇒ **与 MySQL 同向**；⇒ 每个 (角色,实体) 可以有**多行**内置配置 ✅ |
| 重复的**自定义**行（同 (角色,实体,`field_id=5`)） | **被拒** | 旧索引 `uk_field_perm` 仍在工作 ⇒ **自定义路径没有被本项放松** ✅ |
| **双 NULL 行**（`field_id` 与 `field_key` 都空） | **通过** | ⚠️ **「恰好一列非空」没有 DB 级约束** ⇒ 只能靠 `upsert`（R1）+ 用例守住 |

⚠️ 第三条是本文件里最重要的一条**负面**读数：它把「R1 只是应用层规则」从**推断**变成了**实测**。

---

## 4. 与 `specs/056-field-permission/data-model.md` 的关系

- **不取代、不修改**：056 那份记的是 `V64` 的基线，其列表述**逐字**是 `role_id BIGINT NOT NULL`（`:10`）、`field_id BIGINT NOT NULL`（`:12`）、唯一约束 `uk_field_perm（role_id, entity_type, field_id）`（`:17`）。
- 其中 `field_id BIGINT NOT NULL` 这一句**自 `V91` 起与实现不再一致**——但 **056 的工件一字不改**（授权依据见 `research.md` §1：本项是兑现 056 自己留的「后续扩展」）。
- 该文件与实现之间的**既有漂移**（`role_id` vs `role_code` 等）**逐条登记在** `research.md` §1.2。
