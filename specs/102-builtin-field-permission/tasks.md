# 任务：字段级权限的内置字段（102）

**Created**: 2026-09-17
**图例**：`[ ]` 未完成 / `[x]` 已完成。**立项阶段一律不预勾。**
**⚠️ 本项的实际编写顺序是「先实现、后补用例」**（沿用 087–101 的既有做法）：C2–C5 落地时**允许新代码暂无对应用例**，门禁（含 jacoco）在 **C6 之后跑第一次**；C2–C5 各做 `mvn -B -o -q compile` 级编译自证。**不得**据此声称走过 spec-first；定向破坏留痕证明的是**护栏有牙齿**，不是「红先出现」。
**⚠️ 本项的活动半径**：**后端 + 前端 + 1 个迁移 + 文档**。**1 个新端点、零新错误码、零新权限码**；**产 `contracts/`（对外行为确有变更）**、**产 `data-model.md`（只写 V91 增量）**。
**⚠️ 行号口径**：本文件里的行号是**立项时的实测值**，只作定位辅助。**权威锚点是符号名**（类名 / 方法名 / 字段名 / 字符串字面量）——本仓已实测过「行号引用会腐坏」。

**⚠️ 五处「必须同批」（分成两次就是错的）**：
1. **T009 / T010 / T011 同批**：`V91` 迁移、`schema-h2.sql` 的 3 处镜像、`SchemaParityIT.MIRRORED_MIGRATIONS` 加 `"91"`——缺任何一个，H2 库要么缺列（依赖该表的 IT 以**误导性**方式失败）、要么 `SchemaParityIT` 直接红。
2. **T012 的两行同批**：`INSTALL.md` 的 **V90 行**（既有遗漏）与 **V91 行**——**只加 V91 会让列表看着像本项跳了号**。
3. **T013 / T018 / T019 同批**：注册表与收口点——收口点按注册表的载体匹配；注册表先落地时**尚无消费者**（可编译、无行为）。
4. **T019 与 T021 / T022 同批**：C3 是**唯一**带行为变更的提交，其默认不变式是「**未配置 ⇒ plan 为空 ⇒ 逐字直通**」；读侧与写侧分两次提交会让中间态**同时**存在「掩码已开、回补未开」——那是一个**比改动前更坏**的状态（看得见却仍被销毁）。
5. **T030 与 T038 同批**：i18n 新键与引用它的前端测试——删/改键而测试仍引用旧名 ⇒ `i18n:check` 的孤儿键分支与测试**同时**红。

---

## 阶段 A 工件与登记（提交 C1 = `docs(102): 立项`）

- [x] T001 写**九件**工件：`spec.md` / `plan.md` / `research.md` / **`data-model.md`** / **`contracts/field-permission-builtin.md`** / `quickstart.md` / `tasks.md` / `falsification-evidence.md` / `checklists/requirements.md`
      ⚠️ **`falsification-evidence.md` 在立项期就建全**（D1–D14 的破坏表与「该红的判据」**在开工前定稿**，读数留空待 T046）——**不得**等交付时再补一张「照着结果编的」破坏表
- [x] T002 `contracts/field-permission-builtin.md` 必须装齐：① **逐字收存** 056 的三处「后续扩展」原文（`spec.md:46`/`:86` 的 `内置字段不在 v1 范围，后续扩展` 与 `内置字段权限后续扩展` 必须可 grep）；
      ② 056 契约的端点/请求体/错误码表/`data-model.md` 列定义的**原文逐字**；③ **056 契约 vs 实现的漂移对照表**（`roleId` vs `roleCode`、无 `roleName`、`fieldName` 从不赋值、`PUT` 端点不存在、两枚错误码从未抛出）；
      ④ 变更对照（新端点 / `POST` 请求体 / **响应体掩码** / 写路径 / 导出 / **明确不变的部分**）；⑤ 生效后的可观测行为 8 条；⑥ **与 056 的关系**（谁不改、谁为准、**本项不是偏离而是兑现**）
- [x] T003 `research.md` 必须装齐：① 056 回引（**兑现待扩展项，不是偏离冻结契约**，方向与 101 相反）与 **056 vs 实现漂移**；② 为什么复用同一张表（否掉的替代）；
      ③ V91 形状 + **H2/MySQL 实测读数**（两条索引可行 / 自定义唯一仍生效 / **双 NULL 行可插 ⇒ 无 DB 级约束**）；④ 为什么是单一收口点（否掉 53 个逐装配点）；
      ⑤ 置 null 而非删键（无侧信道 vs 全局 `@JsonInclude` 的代价）；⑥ 写侧为什么必须回补、**且必须在 `apply` 之后**；⑦ `create` 的语义（**明文声明**）；
      ⑧ 导出为什么照「列在、格空」；⑨ 定时导出为什么取**任务业主**（混合态）；⑩ `upsert` 三处 + 两条**实测**（`toMap` null 键不 NPE / `.eq(col, null)` 永不匹配）；
      ⑪ 注册表为何 `Map` 而非 `Set` + 为何必须有 `@PostConstruct` 自检 + 排除项；⑫ 隔离实例配方；⑬ **覆盖缺口与假绿通道（3 条）+ 债务 8 条 + 「无判据看着」一条**
- [x] T004 `data-model.md` **只写 V91 增量**：新列 / 可空性变更 / 第二条唯一索引 / R1–R6 的**强制位置表** / H2 实测三项读数 / 镜像与幂等（含「`DROP TABLE IF EXISTS` 已存在」）/ 与 056 `data-model.md` 的关系。
      ⚠️ **不重列** V64 已定的 6 列；⚠️ **本项加列不加表 ⇒ 表数不变**（须写明）
- [x] T005 `specs/README.md` 模块表加 **102 行**（**状态列如实写「⏳ 进行中」**，**不预勾**）
- [x] T006 `specs/README.md` 的**编号说明**段纳入 102（**按文本锚定位，不按行号**），必须写明两条：① 本项的形制（后端+前端+**1 个迁移**，产 `contracts/` 与 `data-model.md`）；
      ② ⚠️ **本项兑现 056 自己留的待扩展项，056 一字不改、无偏差台账**（方向与 101 相反，不得混用判例）
- [x] T007 `specs/roadmap.md` 加 102 行（**勾选框留空、不预勾**）+ `最后更新` 前置 102 立项条目（旧值降级为「**上一条（原文保留）**」，逐字不改）+
      `## 当前进度` 计数由 **100 勾 / 0 未勾** 变 **100 勾 / 1 未勾**（实测复核：`grep -c '^- \[x\]' specs/roadmap.md` 与 `'- \[ \]'` 各计一次）+
      **债务台账新增 8 条**（`research.md` §13.2 逐条；**只写在 research 里等于没登记**）
- [x] T008 ⚠️ **立项阶段不改「整体覆盖度」那句交付态断言**（它说的是**已交付**的编号面，而 102 **尚未交付** ⇒ 改成 `001–102` 等于把在办项写成已交付）。
      照 097–101 立项时的同一处置：只前置带日期的 ⚠️ 块登记**随入列漂移的编号面计数**（**101 → 102** 个目录），**旧值逐字保留**；交付时（T044）再改那句
- [x] T009 ⚠️ **立项阶段不动任何「对外规模数字」**（i18n 键数/行数、Spec 模块数、后端测试类数、迁移数）：它们必须与**交付时的实测**同批改齐
      ⇒ 前移到 **T031（前端改键后）与 T044（交付收口）**。只改一处等于用一次订正造出两处新矛盾。**该前移在此登记**

---

## 阶段 B V91 迁移与内置字段注册表（提交 C2 = `feat(102): V91 迁移与内置字段注册表`）

- [x] T010 新增 `backend/src/main/resources/db/migration/V91__field_permission_builtin_fields.sql`：`field_id` 改可空、加 `field_key VARCHAR(64) DEFAULT NULL`、
      新增 `uk_field_perm_builtin (role_code, entity_type, field_key)`，**旧索引 `uk_field_perm` 保留不动**。
      ⚠️ **不得编辑 `V64`**；⚠️ **不加 `CHECK`**（本仓 89 个迁移里零 CHECK 约束，先例见 `V79__opportunity_stage.sql:15`「同理不做 CHECK 约束」）
- [x] T011 `backend/src/test/resources/schema-h2.sql` 的 `field_permission` 块改 **3 处**并按该文件头部约定行尾标 **`-- V91`**：
      ① `field_id BIGINT NOT NULL` → 可空；② 新增 `field_key VARCHAR(64)`；③ 新增第二条唯一索引。⚠️ `DROP TABLE IF EXISTS field_permission;` **已在**，**不加**
- [x] T012 `SchemaParityIT.MIRRORED_MIGRATIONS` 加 `"91"`（**刻意逐项枚举、不用区间**——该清单 javadoc 自己写了理由）。加完共 **90** 个版本号
- [x] T013 **迁移数 6 处活落点同批收口**（⚠️ 实测枚举所得，计划初稿只列了第 1 处 ⇒ 立项期已改正；**旧值一律逐字保留在 ⚠️ 块内**）：
      ① `INSTALL.md:262` 正文改 **「当前 V1~V91 共 90 个脚本，末条为 V91」** + **补 V90 行**（带日期标明「**本行此前缺失**」——既有遗漏，不是本项造成）+ **加 V91 行**；
      ② `INSTALL.md:124` 的「（V1~V90，共 89 个迁移脚本…）」；
      ③ `specs/README.md:3` 前言版本行（并同段加 102 的形制句）；
      ④ `specs/README.md:150` **章节标题** + **迁移表末行 V91**（⚠️ 082 批踩过的坑正是「只改表行、漏标题」）；
      ⑤ `PROJECT_FEATURES.md:13` 的 Flyway 行；⑥ 根 `README.md:156` 目录树那行（加 `V91 = 102 内置字段权限`）。
      ⚠️ **只改 ① 会同时留下 5 处假数字**；⚠️ 订正块内的**历史读数不动**（`PROJECT_FEATURES.md:41/61/80/109/135/316`、`CRM_FEATURE_COMPARISON.md:89-91/452`、`roadmap.md:386`）
- [x] T014 新增 `backend/src/main/java/com/crm/support/BuiltinField.java`（record：`entityType` / `fieldKey` / `label` / 三侧载体属性名）与
      `support/BuiltinFieldRegistry.java`：**11 条**（CUSTOMER 7：`contactPerson` `phone` `email` `address` `remark` `status` `campaignId`；OPPORTUNITY 4：`expectedAmountMin` `expectedAmountMax` `remark` `status`），
      查找表用 `Map<String, BuiltinField>`（键 `entityType + ":" + fieldKey`）**不用 `Set.contains`**；
      `@PostConstruct validate()` 用反射在**每个载体类**上解析属性名与类型，**任一失败即抛**（让 ApplicationContext 启动失败）；
      javadoc 写死**入选规则四条**与**排除项**（必填字段 / `ownerId`（空=公海，置 null 是说谎）/ `ownerName`（只在两条装配路径被赋值）/ 派生列）
- [x] T015 `FieldPermissionService` 新增**并行方法族**：`permissionForKey(roleCode, entityType, fieldKey)`（ADMIN 恒 `EDITABLE`、未配置 `EDITABLE`）、
      `builtinPermissionsForRole(roleCode, entityType) → Map<String,String>`、`validateBuiltinWrite(roleCode, entityType, req, original)`；
      ⚠️ **不动** 056 的既有签名（`permissionFor` / `permissionsForRole` / `validateWrite`——改签名会牵动 15 个自定义字段调用点）；
      ⚠️ `permissionForKey` **不得**退化成 `permissionFor(role, entity, null)`（`.eq(col, null)` 永不匹配 ⇒ **fail-open**）
- [x] T016 `FieldPermissionService.upsert` 重写三处：① `fieldId`/`fieldKey` **恰好一个非空**否则 **422 `FIELD_PERMISSION_INVALID`**；② `fieldKey` 必须在注册表里**且属于该 `entityType`**；
      ③ 查询**按 identifier 分支**（`.eq(fieldKey)` vs `.eq(fieldId)`）；并让 `permissionsForRole` / `hiddenFieldIds` 把内置行（`field_id` 为 null）**排除出 custom 映射**（**理由是卫生，不是防炸**——`toMap` 的 null 键实测合法）
- [x] T017 `FieldPermissionRequest`：`fieldId` 去掉 `@NotNull`、加 `fieldKey`（`@Size(max=64)`）；⚠️ `permission` 的 `@Pattern` 与 `roleCode` 的注解**一字不动**
- [x] T018 编译自证（`mvn -B -o -q compile`）+ 复核「既有路径行为不变」（内置行此刻**尚无法写入** ⇒ 无新行为）

---

## 阶段 C 出参收口点与写侧回补（提交 C3 = `feat(102): 出参收口点与写侧回补`）⚠️ **唯一带行为变更的提交**

- [x] T019 新增 `support/FieldMaskPlanner.java`：`plan(roleCode, entityType) → Set<String>`（**不可变**）作为**唯一判据源**（收口点 / `ExportExecutor` / `CustomerExcelService` 共用）；
      **不加缓存**（配置一改即生效）；**不返回记录类**（少一个类、少一份 record 覆盖税）
- [x] T020 新增 `support/FieldMaskingResponseBodyAdvice.java`（`@ControllerAdvice implements ResponseBodyAdvice<Object>`）：
      **快路径** `principal == null || ADMIN` ⇒ 原样返回；**遍历** `ApiResponse.data` / `PageResult.items` / `Collection` / 数组 / `Map.values()` / 载体类（`isAssignableFrom` 覆盖两个 `*DetailResponse`）；
      **JDK 类型直接跳过**（`java.*` / `String` / 枚举 / `byte[]`，避免 `InaccessibleObjectException`）；**身份集环guard + 深度上限**；
      命中载体 ⇒ 按**该载体所属实体**取掩码集合置 `null`；⚠️ **不向子对象传播**；⚠️ **不引入全局 `@JsonInclude`**
- [x] T021 新增 `support/BuiltinWriteGuard.java`：`require(id)` / `snapshot` / `validateBuiltinWrite` / `restore`；
      ⚠️ **调用序列逐字固定**：`require → snapshot → validate → apply → restore → updateById`，**`restore` 必须在 `apply` 之后**（`OpportunityService` 的两处 `null → 0L` 只在后置回补下才盖得掉）
- [x] T022 `CustomerService.create/update` 接护栏（`create` 只做 422 校验，**无原值可回补**）
- [x] T023 `OpportunityService.create/update` 接护栏（同上）
- [x] T024 自证：`mvn -B -o test` 跑**既有全量**用例 ⇒ **预期仍全绿**（未配置内置权限 ⇒ plan 为空 ⇒ 逐字直通）。
      ⚠️ **若红，先查是不是 `restore` 回补错了字段**（`snapshot/restore` **只**遍历注册表条目，且只回补 plan 命中的键）

---

## 阶段 D 导出接同一判据源（提交 C4 = `feat(102): 导出接同一判据源`）

- [x] T025 `ExportExecutor.writeCustomers` / `writeOpportunities` 的内置列改走 `cell(plan, key, value)`（**列在、格空**）；⚠️ **列头不动**
- [x] T026 `CustomerExcelService`（另一套 7 列、**不调** `mask()`）同样接入
- [x] T027 `ScheduledExportServiceImpl.executeExport` 取**任务业主的角色**算掩码（该路径**无请求主体**，`SecurityUtil` 恒 null）；
      ⚠️ **只**接字段掩码；063 的 `mask()` 与 `visibleOwnersOrNull` 在该路径**仍 fail-open** ⇒ **混合态必须写进 `research.md`/`contracts/`**，不得含混成「定时导出已修好」

---

## 阶段 E 配置面（提交 C5 = `feat(102): 配置面（可用字段端点与前端）`）

- [x] T028 新增 `GET /api/v1/field-permissions/available-fields?entityType=X`：返回**内置 + 自定义**可配字段（`{fieldId, fieldKey, fieldName, builtin}`，复用 `PageResult` 形）；
      **未知 `entityType` ⇒ 422 `FIELD_PERMISSION_INVALID`**（**不静默返回空表**）
- [x] T029 `FieldPermissionResponse.fieldName` 补齐赋值（今天**只声明、从不赋值**；`setFieldName` 全仓只出现在 `CustomFieldService.java:295,348`，作用于另一个 DTO）；内置项取注册表标签
- [x] T030 前端：`FieldPermissionPage.tsx` 的字段列表改调新端点、表单下拉选项值编码 `fieldId|fieldKey`、表格「字段」列渲染**名称**（不是 `dataIndex: 'fieldId'` 的数字）；
      `types/fieldPermission.ts` 加 `fieldKey?`；`services/fieldPermission.ts` 加 `availableFields`
- [x] T031 i18n 两文件加新文案 + **同批**改 `PROJECT_FEATURES.md` 的键数/行数**三处落点**（`:19`/`:77`/`:111`，**取交付时 `i18n:check` 的实测值**，旧值逐字保留可 grep）；
      ⚠️ 只改一处等于用一次订正造出两处新矛盾。⚠️ `zh:check` 的硬编码中文台账**不得增加**
- [x] T032 前端五道门禁（`i18n:check` / `lint` / `typecheck` / `ui:check` / `zh:check`）

---

## 阶段 F 用例（提交 C6 = `test(102): 内置字段权限用例`）

- [x] T033 `support/BuiltinFieldRegistryTest`：**T1**（坏属性名/坏类型 ⇒ `validate()` 抛）+ **T2**（11 条逐条 (entityType, fieldKey) + **「不许加必填字段」**断言）
- [x] T034 `support/FieldMaskPlannerTest`：**T3**（SALES+HIDDEN(phone) ⇒ 含 phone；ADMIN ⇒ 空；未配置 ⇒ 空）
- [x] T035 `service/FieldPermissionServiceBuiltinTest`：**T4**（双空/双非空 ⇒ 422 `FIELD_PERMISSION_INVALID`）+ **T5**（未知 `fieldKey`/属别实体 ⇒ 422）+
      **T6**（同键保存**两次** ⇒ 第二次是 UPDATE）+ **T7**（`permissionForKey` 三态 + ADMIN 恒 EDITABLE + 未配置 ⇒ EDITABLE）
- [x] T036 `integration/BuiltinFieldMaskingIT`：**T8**（客户 HIDDEN(phone)：**列表 / 详情 / 公海**三处均 null，**ADMIN 仍见真值**）+ **T9**（商机：`OpportunityResponse` 与 `OpportunityDetailResponse` 均 null）
- [x] T037 `integration/BuiltinFieldWriteGuardIT`：**T10**（旗舰 1：省略 HIDDEN 客户字段 ⇒ 库中原值不变）+ **T11**（旗舰 2：省略 HIDDEN `expectedAmountMin` ⇒ **保持 5000 而非 0**）+
      **T12**（提交 HIDDEN ⇒ 422 `FIELD_HIDDEN`）+ **T13**（改 READ_ONLY ⇒ 422 `FIELD_READ_ONLY`；**原样回传 ⇒ 成功**，正对照）
- [x] T038 `integration/FieldPermissionAvailableFieldsIT`：**T14**（内置 11 + 自定义 N；未知 `entityType` ⇒ 422）
- [x] T039 `integration/BuiltinFieldExportIT`：**T15**（xlsx 里 `phone` **列在格空**；ADMIN 有值）+ **T16**（定时导出：任务**业主是 SALES**、环境主体是 ADMIN ⇒ **仍按 SALES 掩码**）
- [x] T040 `pages/settings/FieldPermissionPage.test.tsx`：**T17**（选项含内置字段、提交带 `fieldKey`、表格显示 `fieldName` 而非数字 id）
- [x] T041 ⚠️ **中文断言必须显式 UTF-8**（`getContentAsString(StandardCharsets.UTF_8)`——本仓有 ISO-8859-1 假红的先例）；**能断 `error.code` 的地方优先断 code**

---

## 阶段 G 交付登记与文档订正（提交 C7 = `docs(102): 交付登记与文档订正` = 交付）

- [x] T042 对比文档 **5 处**按「**订正不静默**」改写（原文逐字保留 + 带日期 ⚠️，粒度到**每一列**，判据 = **旧值仍可 grep**，**排除行首注释后统计**）：
      `:19-23`（时间线块）/ `:273`（2.9 FLS 行——**写明已覆盖什么、没覆盖什么**，含「**客户名这一半不覆盖**」）/
      `:285`（2.9 小结——**四项改三项**）/ `:349`（4.1 结论 3）/ `:367`（P0 第 1 条——状态列改 **`**⚠️ 基本闭合**（102，2026-09-17）`**，**不能是 ✅**）。
      ⚠️ **判定列与分值一字不动**
- [x] T043 `README.md:163`（**已腐坏**：`99 个功能模块，001~100`，实际应为 100 个 / 001–101）订正为 **101 个 / 001–102**（旧值逐字保留）+
      `PROJECT_FEATURES.md:20`/`:107` 的 Spec 模块数改 **101（001–102，缺 069）**；⚠️ `:290` 那条 097 的历史读数注记**不动**
- [x] T044 交付收口：`specs/README.md` 的 102 行状态列改 ✅ 并补实测读数；`specs/roadmap.md` 的 102 行勾上、`最后更新` 前置交付段、
      「整体覆盖度」那句交付态断言改为 **001–102**、编号面计数 **101 → 102**（**旧值逐字保留**）
- [x] T045 复核 `INSTALL.md`（正文 + V90 行 + V91 行三处是否同期到位）；复核 `specs/056-field-permission/**` **一个字符未改**（判据：本项全部提交的 `--name-only` 里它零出现）
- [x] T046 写 `falsification-evidence.md` 的实测读数：D1–D14 逐条观测（**含 D14 的「预期无红」如实记**）、门禁读数（surefire/failsafe/失败集合/jacoco 结论行与覆盖率/`jacoco.exec` 字节数）、
      隔离实例三档冒烟读数与收尾核对、§订正不静默自查命中数、§可核判据的实跑读数
- [x] T047 `tasks.md` 勾选（**只勾真正做完的**）+ 填**交付块**（不得预填、**不写任何提交哈希**）+ 在「实做订正」里如实记录与计划的偏差
- [x] T048 交付前复跑 `quickstart.md` §5/§6 两节的全部判据（**不跑 Maven**——门禁的读数已经取过，再跑会覆盖 `jacoco.exec`）

---

## 实做订正（**如实记录与本计划的偏差**，交付时填）

| # | 偏差 | 处置与理由 |
|---|---|---|
| 1 | **导出掩码的落点名与计划不同**：计划把入口写成 `copyInto`，实做落在 `ExportExecutor.cell(plan, key, value)` 与 `CustomerExcelService` 的 `write` 上 | **以实做为准**：`copyInto` 是计划期勘察时的**临时称呼**，仓里没有这个符号。**判据不受影响**——D6/D6-B 证的是「两条链各自被破过」，与函数叫什么无关 |
| 2 | **出参收口点刻意「不反射非载体对象」**（计划未写这条边界） | 载体在本仓**都是叶子 DTO**，响应图里不存在「载体套载体」；为不存在的形态反射每个 DTO 要引入 `setAccessible`、JDK 类型与 record 的风险。⇒ **如实登记为债务 5**：将来若载体被嵌在非载体对象里，**掩码会漏** |
| 3 | **`FieldPermissionResponse.fieldName` 由「只声明」变「真赋值」** | 056 的既有缺陷（`setFieldName` 全仓只出现在 `CustomFieldService`）⇒ 本项**顺带修好**，否则配置页的「字段」列只能显示数字 id（T17 的判据之一）。**这是既有缺陷的修复，不是本项的新功能**，故记在此处而非 spec |
| 4 | **`availableFields` 的入参校验规则计划未写死**：未知 / 空 `entityType` ⇒ **422 `FIELD_PERMISSION_INVALID`** | 计划只写了「复用 `PageResult` 形」。**取 422 而非空表**：静默返回空表会让管理员以为「没有可配字段」，把配置面缺陷伪装成数据现状。**该码是 056 定义了却从未抛过的两枚之一**，本项让它第一次真正被抛出 |
| 5 | **`PROJECT_FEATURES.md` 的两处 i18n 落点（`:77`/`:111`）刻意未动** | 那两处是 **099 与 100 的历史留痕块** ⇒ 改它等于**改写那两批的留痕**。**与 101 的处置一致**（101 也只落了 `:19`、未动 `:77`/`:111`）。**判据是「旧值仍能被 grep 到」而非「每一处都换成新值」** |
| 6 | **交付实测：`PROJECT_FEATURES.md` §一 动的不是 3 行而是 6 行** | 计划只列了键数/行数**三处落点**。交付时按「依据」列逐行复算，**迁移数 / 后端测试类 / 前端页面组件 / 前端单测 / i18n / Spec 模块 六行都动了** ⇒ 按 **097 的先例**（「§一 改的是 5 行不是 4 行」）**一并改齐并新增「第六次重测」块**。漏掉它们等于**用一次订正当场造出一处新矛盾**（表与表头打架） |
| 7 | **「置 null vs 删键」的偏差与一处同步修复** | 收口点是**就地置 `null`**；实测发现 `application.yml` 早有 `spring.jackson.default-property-inclusion: non_null` ⇒ **置 null 的字段在 JSON 里表现为「键缺失」**。**如实记**：冒烟实测**公海响应里 `phone` 键直接缺失**（不是 `"phone": null`）。⇒ 交付口径写「置 null（出参表现为缺键）」，**不写成「删键」**（那是另一套机制）；亦**不引入**全局 `@JsonInclude` |
| 8 | **定向破坏四处证伪**（详见 `falsification-evidence.md`） | ① **D4**：删 `restore` **只有 T11 红、T10 不红**（客户路径 `updateById` 的 `NOT_NULL` 让省略字段根本不进 UPDATE）；② **D10**：`permissionForKey` 退化 **读侧一条不红**（它只有一个生产调用者，读链走 `builtinPermissionsForRole`）；③ **D6**：xlsx **有两条独立链**（T15 守 `CustomerExcelService`、T16 守 `ExportExecutor`），计划的「T15 是唯一」不成立；④ **D13-C**：计划字面那条**既改不出它预告的判据（T13 全绿），也不是「没有判据」**（单元层有两条看着它）。⇒ **四处都按实测改写，不按计划改写** |
| 9 | **D14 的「预期仍全绿」不成立**（两个形态都转红） | **D14-A**（plan 静态缓存）6 条红、**D14-B**（详情 DTO 缓存）被 **ADMIN 正对照 `assertVisible:127`** 抓到跨角色中毒 ⇒ 空档**收窄**为「**同一角色跨配置变更后重读未测**」+「**列表/公海/导出路径的缓存形态未测**」。**原文保留在 `falsification-evidence.md` §边界 9 内 + 带日期 ⚠️ 订正**，三句都不得抹平 |
| 10 | **还原判据订正**：`git hash-object 与破坏前相等` **不成立** | 备份取自 21:29、**早于**门禁的 `spotless:apply`（21:42）⇒ `cp` 回写可能把 javadoc 退回旧折行。**改取「与 HEAD 逐字相等」（`git diff --quiet`）**，必要时 `mvn -B -o spotless:apply` 归一（`BuiltinFieldRegistry.java` 即此例）；探针残留判据改用唯一标记 **`留痕后还原`**（`定向破坏` 是全仓既有措辞、会假命中）。**并加一条**：失败归属必须**并读用例名**，不能只凭行号（D6/D6-B 靠它才分开） |
| 11 | ⚠️ **C2 的三处落点只改了现行值、没留下旧值**（**交付自查抓到的真偏差**） | `specs/README.md` 章节标题、`INSTALL.md` 两处正文——按 `quickstart.md` §6 ④ 的「旧值仍能被 grep 到」复算，**命中 0**（即**静默改写**）。**处置：不是把判据改松，而是补回三个 ⚠️ 订正块**（带日期 / 责任批 / 原文逐字引用 / 「本条由交付批补记」），补后复算全部达标。**教训**：改造前登记的读数**必须在改造后逐条复算**——C2 当时只跑了自己那一半 |
| 12 | **`mvn -B spotless:apply` 在门禁之后又跑过一次** | `quickstart.md` §6 ⑧ 的字面命令含它。**已登记为例外**：`spotless:apply` 是**直接目标调用、不走生命周期、不执行任何测试**，**不触碰 `jacoco.exec`** ⇒ 交付态读数未受影响（实测该次同为 `812 files clean / 0 were changed`）。⚠️ 同时订正 §6 ⑧ 的**读法**：`git diff --stat` 在**未提交的工区**上必然非空（本批实测 `1 file changed, 25 insertions(+), 4 deletions(-)`，那是**本批自己的订正**），**该判据要读的是 `0 were changed to be clean`** |

---

## 交付块（**交付时填**，不得预填）

| 项 | 值 |
|---|---|
| 提交数 / 末条提交主题 | **7 次**（C1 `docs(102): 立项` → C7 **`docs(102): 交付登记与文档订正`**）。⚠️ **按纪律不写任何提交哈希**（哈希写进被它携带的文件，一次 `--amend` 就变成不存在的对象） |
| `mvn -B verify`（surefire / failsafe） | 退出码 **0 / BUILD SUCCESS**（02:23 min）；surefire **772 / 0 / 0 / 0**、failsafe **347 / 0 / 0 / 0** ⇒ **失败集合 ∅ ⊆ 4 例已批准偏差**（那 4 例本次**一例也没红**） |
| `jacoco:check` 结论行 | **「All coverage checks have been met.」** |
| 覆盖率（INSTRUCTION / BRANCH / LINE / METHOD） | **82.17%**（49 933/60 766）/ **64.37%**（3 292/5 114）/ **83.78%**（11 324/13 517）/ **86.32%**（1 849/2 142）——报告 **257 个类** |
| `ls backend/target/jacoco.exec` | **118 930 688 字节**，mtime **2026-09-17T22:05:43** |
| 新增/改动的测试文件与用例数 | 后端 **7 类 / 47 例**：`BuiltinFieldRegistryTest` 8 · `FieldMaskPlannerTest` 7 · `FieldPermissionServiceBuiltinTest` 19 · `BuiltinFieldMaskingIT` 2 · `BuiltinFieldWriteGuardIT` 5 · `BuiltinFieldExportIT` 2 · `FieldPermissionAvailableFieldsIT` 4；另 **1 个不带 `@Test` 的夹具** `BuiltinFieldPermissionFixture`。前端 `pages/settings/FieldPermissionPage.test.tsx`（**新文件**）**3 例**。⚠️ 因此 §一「后端测试类」是 **`.java` +8 / 含用例 +7**（两半不同增） |
| 定向破坏 D1–D14 | **逐条实测**（含 D6-B、D13-A/B/C、D14-A/B），**四处证伪** + **D14 三条如实登记** ⇒ 全部落在 `falsification-evidence.md` |
| 隔离实例冒烟 | **a/b/c 三档 + 配置面 + 422** 全部跑通（8099 / `crm_fls102` / Redis db 5；真 MySQL 8.0.46 + 真 Redis + 真 HTTP）；收尾 DROP / REVOKE / FLUSHDB，共享库未动、8081 与 5173 未重启。⚠️ **未做**：前端 UI 真机观察、`ExportExecutor` 链的真机冒烟 |
| 可核判据（`quickstart.md` §5） | ① **0** ✅ ② **90/1/0/1+1** ✅ ③ **3/1** ✅ ④ **0/0** ✅ ⑤ 严格口径 **0**（字面 2，均为 javadoc 提及）⑥ **1/1** ✅ ⑦ **2966/2966** ✅ |
| 订正不静默自查（`quickstart.md` §6） | ① **5** / 旧值仍在 ✅ ② **1/1** ✅ ③ **2** ✅ ④ 六处落点旧值新值**两栏达标**（含 **C7 补记的三个 ⚠️ 订正块**）+ 历史读数 **1/1/2 未变** ✅ ⑤ **2/2** ✅ ⑥ **4/2** ✅ ⑦ **各 1** ✅ ⑧ **812 clean / 0 changed** ✅ |
| 前端五道 + 定向 vitest | `i18n:check` **2966/2966 键**（路由 58 / 清单 56 / 别名 3）· `lint` ✅ · `typecheck` ✅ · `ui:check` 271 文件 / 303 `Form.Item`（冻结债 54 处**未新增**）· `zh:check` 268 文件 / 候选点 9162（冻结 266 处 + 4 条 / **未登记命中 0**）；定向 vitest **1 文件 / 3 passed** |
| ⚠️ 本项**不把提交自己的哈希**写进任何被它携带的文件 | 遵守（只引用**提交主题/序号**） |
