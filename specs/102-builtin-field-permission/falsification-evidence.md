# 定向破坏与订正留痕（102-builtin-field-permission）

**状态**：**已回填（2026-09-17，C7）**——§A–§N 的「破坏动作 / 该红哪条 / 为什么这条是承重的」在立项期就已写死（防止事后挑一条好红的补写），**读数全部为第一手实测**，逐条落在本文件。
**对应计划**：`plan.md` 「验证 → 定向破坏」表（D1–D14）。
**命名说明**：D 块占 `§A`–`§N`（14 条），故收尾各节改用**中文命名**（§门禁 / §冒烟 / §判据 / §订正 / §边界 / §来源）——不是漏了一节。

---

## §0 本项的证据形态与三条硬规则

**本项的证据形态**：一个**未配置时逐字直通**的收口点 + 一个**只在省略时才有差别**的回补护栏 ⇒ **大多数既有用例在破坏后仍会绿**。⇒ 破坏实验的判据必须落到**本项自己新配的权限行**上（T8–T16），否则整节留痕会退化成「破坏了几次、每次都全绿」。

### 硬规则（三条，逐条都踩过）

1. **先写一句「它该改变哪条可观察行为」，再动手**。跑完核对那条行为**确实变了**——**没变就是空操作**（破坏是空操作 ⇒ 绿是假的）。
2. **看到红先读是不是「手段的红」**：CRLF / spotless / 编译错**都不是目的的红**。本仓 Java 源是 **CRLF** ⇒ **就地改一律用 `Edit` 工具**，**绝不用 python/sed 原地重写**（会把整个文件翻成 LF，让 `spotless:check` 拒收并让 `mvn` 报 BUILD FAILURE——红的不是你要验的那件事）。判据取**测试层读数**（哪几条用例红/绿），**不取构建退出码**。
3. **还原禁用 `git checkout`**（它还原的是 HEAD，会把同一文件里本批**有意未提交**的订正一起吞掉）⇒ 一律 `cp` 备份回写。⚠️ **订正（2026-09-17，实测发现）**：原判据写的是「`git hash-object` 与破坏前相等」——**该判据在本批实测中不成立**，见下。

### ⚠️ 订正（2026-09-17，C6 实测）：还原判据改取「与 HEAD 逐字相等」

**原文（逐字保留在上方硬规则 3 内）**：「判据是 **`git hash-object` 与破坏前相等**」。

**实测为什么不成立**：`/tmp/fls-bak/` 的备份取自当日 **21:29**，而那次门禁的 `spotless:apply` 跑在 **21:42** ⇒ 备份文件是**格式化之前**的形态。`cp` 回写会把 javadoc 退回旧折行形态，于是「与**记录基线 hash** 相等」**不等于**「与 **HEAD** 相等」。实测中 `BuiltinFieldRegistry.java` 正是如此：`cp` 回写后 hash 与基线相等，却与 HEAD 不等；用 `mvn -B -o spotless:apply` 归一后才与 HEAD 逐字相等。

**订正后的判据（本文件全部 §A–§N 一律照此）**：

- **还原判据** = **`git diff --quiet -- <file>`（与 HEAD 逐字相等）**，并在必要时用 `mvn -B -o spotless:apply` 归一格式；**基线 hash 只作辅助**。
- **`CustomerService.java` 没有备份**（改动用 `Edit` 反向还原），判据同取 HEAD 相等。
- **探针残留判据** = 唯一标记 **`留痕后还原`** 在 `backend/src/main/java` 下**零命中**。⚠️ 原计划的措辞 **`定向破坏` 不能当标记**——它是**全仓既有措辞**（`grep -rl 定向破坏` 会命中 082 等无关文件的既有 javadoc）⇒ 用它会得到假命中，据此改用唯一标记。
- **失败归属必须并读用例名**：只读行号会把**两条不同的链看成同一条**（本文件的 D6/D6-B 就是靠并读用例名才分开的）；断言写在 helper 里时，行号报的是 **helper 的行**（本项目的 `assertInvisible`/`assertVisible` 恒报 `:122`/`:127`）。

### 读数格式（每节照抄）

```
- **破坏**：<逐字改了什么>
- **该红**：<用例名>（承重理由：<没有它，哪条主张就没有证据>）
- **实测**：<哪几条红、哪几条绿，逐字抄>
- **还原**：`cp` 回写 / `Edit` 反向还原后 `git diff --quiet -- <file>` ⇒ 与 HEAD 逐字相等
- **如实记**：<与预期不符的地方，**必须写**，不许抹平>
```

---

## §A D1 —— 收口点直接返回原体（不遍历）· **本项读侧主张的根**

- **破坏**：`FieldMaskingResponseBodyAdvice#beforeBodyWrite` 改为 `return body;`（快路径判断之前）。
- **该红**：T8（客户 HIDDEN 的 `phone` 在列表/详情/公海三处均应为 null）与 T9（商机详情子类）。
- **承重理由**：这是**全部**读侧主张的唯一执行点；它不跑，`FieldMaskPlanner` 再正确也没有出口。
- **实测**：`BuiltinFieldMaskingIT` **2 run / 2 fail**——`customerPhoneIsMaskedInListDetailAndPool:54`、`opportunityAmountMinIsMaskedInDetailAndList:89`，两条的断言都报在 helper 链尾 `assertInvisible:122`（**断言在 helper 里 ⇒ 行号是 helper 的，不是用例体的**）。与计划预判相符（T8 / T9）。
- **还原**：`cp` 回写 + `git diff --quiet -- FieldMaskingResponseBodyAdvice.java` ⇒ 与 HEAD 逐字相等。
- **如实记**：无偏差。

## §B D2 —— 只处理 `CustomerResponse`，不做 `isAssignableFrom`

- **破坏**：收口点的载体匹配由 `isAssignableFrom` 改成 `getClass() == CustomerResponse.class`。
- **该红**：T9（`OpportunityDetailResponse` 与 `CustomerDetailResponse` 都是**子类** ⇒ 详情路径被漏）。
- **承重理由**：两个 `*DetailResponse` 是 `extends` 出来的；**只用相等判断会把详情整条路径漏掉**，而列表仍然正确 ⇒ 一个很容易被读成「已经做了」的半成品。
- **实测**：**2 run / 2 fail**——`:62`（**客户详情**）、`:89`（**商机详情**）、`:122`（helper 链尾）；**`:54`（列表）仍绿**（列表载体是父类 `CustomerResponse`，相等判断仍命中）。
- **还原**：`cp` 回写 + `git diff --quiet` ⇒ 与 HEAD 逐字相等。
- **如实记**：⚠️ **与计划预判有偏差**：计划只写了「T9 会红」。实测**T8 的详情段也红**（`:62`）——即载体匹配做错会同时打中两个 `*DetailResponse`，不是只打中商机那一条。**订正**：该破坏的判据是「两个详情子类各一条」。

## §C D3 —— 只取 `ApiResponse.data`，不处理 `PageResult.items`

- **破坏**：收口点只下钻 `ApiResponse.data`，删掉对 `PageResult.items` 的展开。
- **该红**：T8 的**列表段**（列表泄漏、详情正常）。
- **承重理由**：**两层信封**（`ApiResponse<PageResult<T>>`）里，只处理外层会得到一个「详情对、列表错」的分裂状态——这是分页端点的**多数路径**。
- **实测**：**2 run / 2 fail**——`customerPhoneIsMaskedInListDetailAndPool:54->assertInvisible:122`（**T8 的列表段**）、`opportunityAmountMinIsMaskedInDetailAndList:95->assertInvisible:122`（**T9 的列表段**）；两条断言的文案都是「列表」；详情 `:62` / `:89` **仍绿**。
- **还原**：`cp` 回写 + `git diff --quiet` ⇒ 与 HEAD 逐字相等。
- **如实记**：⚠️ **两处订正**。① 计划只写了「T8 的列表段会红」，实测 **T9 的列表段也红**（`:95`）。② **我自己的早前读数把 `:95` 记成了「公海」——那是误标**：`:95` 是 **T9 的列表断言**；T8 的公海断言在 `:67`，它因同一方法已在 `:54` 先失败而**不可独立观测**（surefire 报的是该方法内**第一条**失败的断言）。**这条订正只靠行号发现不了，是并读用例名 + 读用例体才纠正的。**

## §D D4 —— 去掉 `restore` · **两条旗舰之一**

- **破坏**：`BuiltinWriteGuard` 的调用序列里删掉 `restore(...)`。
- **该红**：T10（客户 HIDDEN 字段省略后应保持原值）与 T11（商机金额应保持 5000）。
- **承重理由**：**省略即销毁**这条通路正是本项立项的理由之一（`spec.md` §1.4-5）；没有这条用例，本项交付的只是一半护栏。
- **实测**：`BuiltinFieldWriteGuardIT` **5 run / 1 fail**——`:96`（**T11**，旗舰 2）。**T10 仍绿。**
- **还原**：`cp` 回写 + `git diff --quiet` ⇒ 与 HEAD 逐字相等。
- **如实记**：⚠️ **这是一处证伪，且是本批最有价值的一条之一**：计划把 D4 的判据写成「T10 + T11 都红」，实测 **T10 抓不住 `restore` 的缺失**——客户路径上 `updateById` 默认策略是 `NOT_NULL`，**被省略的字段根本没进 UPDATE 语句**，于是「省略即销毁」在客户这条链上**不可观测**；只有商机那条链（`apply` 内部的 `null → 0L` 强转把一个非 null 的 0 写进去）才让差异显形。⇒ **「T10 是回补护栏的判据」这个说法不成立**，T10 真正的价值是**回归**（钉住「非掩码字段不被回补吞掉」），不是承重。

## §E D5 —— 把 `restore` 挪到 `apply` **之前** · **专杀「顺序无关」的错觉**

- **破坏**：调用序列改为 `snapshot → validate → restore → apply → updateById`（**只换顺序，不删代码**）。
- **该红**：**T11**（`OpportunityService` 的 `req.getX() == null ? 0L : req.getX()` 会把回补的 5000 盖成 **0**）。
- **承重理由**：`restore` 与 `apply` **不是可交换的**——只有后置回补才能盖掉 `apply` 内部的 `null → 0L` 强转。**这条破坏若不红，说明 T11 断言的其实是「回补存在」而不是「回补在后」**，那么 D4 与 D5 等价，本项就少了一条承重证据。
- **实测**：**5 run / 1 fail**——`:96`（**T11**），与 D4 **同一条用例、同一行**。
- **还原**：`cp` 回写 + `git diff --quiet` ⇒ 与 HEAD 逐字相等。
- **如实记**：与计划预判相符（红在 T11）。⚠️ **但必须写清 D4 与 D5 的关系**：两处破坏**红在同一点**、无法从判据上分开「回补不存在」与「回补顺序错」——D5 之所以仍有价值，是因为它是**独立的一条链**（顺序）而不是同一条链的重复；**只有把 D4/D5 两条都跑过**，才能说「回补既存在、又在 `apply` 之后」。计划原文里 D5 那句「专杀顺序无关的错觉」是成立的。

## §F D6 —— 导出的 `cell()` 不做掩码（只修 JSON）

- **破坏**：`ExportExecutor` / `CustomerExcelService` 的格值改回直接取值（不查 plan）。
- **该红**：T15（xlsx 里该列应格空）。
- **承重理由**：导出**不经过** Jackson 收口点 ⇒ 这是「界面看不见、导出里全在」的假护栏的**唯一**防守。
- **实测**：**分两条链、分成 D6 与 D6-B 两跑**（计划把两条链并成一条，实测发现必须拆开）：
  - **D6**（`ExportExecutor.cell()` 不掩码）：`BuiltinFieldExportIT` **2 run / 1 fail**——`scheduledExportMasksByOwnerRole:129`（**T16**）；**T15 仍绿**。
  - **D6-B**（`CustomerExcelService.cell()` 不掩码，**补 D6 暴露的空白**）：**2 run / 1 fail**——`hiddenPhoneColumnIsEmptyInCustomerExport:85`（**T15**）。
- **还原**：两条各自 `cp` 回写 + `git diff --quiet` ⇒ 均与 HEAD 逐字相等。
- **如实记**：⚠️ **结构性结论（写进本节，防止后人再并）**：**xlsx 有两条独立链**——`/api/v1/customers/export` → `CustomerExcelService`（**证人 T15**）；`ExportExecutor.writeCustomers`（手动任务 + 定时任务，**证人 T16**）。计划把两条链的证人都记在 T15 名下，**实测订正为「各守一条」**；⇒ **两条链都必须被破过才算被测过**，只破一条会留下另一条**零覆盖**且**全绿**。

## §G D7 —— 定时导出改用环境主体（`SecurityUtil`）

- **破坏**：`ScheduledExportServiceImpl` 的掩码角色改从 `SecurityUtil.currentPrincipal()` 取。
- **该红**：T16（该路径**无请求主体** ⇒ 回落 ADMIN ⇒ 掩码消失）。
- **承重理由**：定时导出是「无人值守生成一份完整文件」的路径，**泄漏面比交互式导出更大**；而它在单测里**恒为 null**，用一个看起来在取主体的写法也能「编译通过、用例不红」。
- **实测**：`BuiltinFieldExportIT` **2 run / 1 fail**——`scheduledExportMasksByOwnerRole:129`（**T16**）。
- **还原**：`cp` 回写 + `git diff --quiet` ⇒ 与 HEAD 逐字相等。
- **如实记**：与计划预判相符。

## §H D8 —— `upsert` 去掉「恰好一个非空」判定

- **破坏**：删掉 `fieldId`/`fieldKey` 的二选一检查。
- **该红**：T4（双空 / 双非空应 422）。
- **承重理由**：这是**唯一**强制 R1 的地方（`data-model.md` §3 实测：DB 层允许双 NULL 行）⇒ 删掉它，错行会被静默接受。
- **实测**：`FieldPermissionServiceBuiltinTest` **19 run / 2 fail**——`upsertRejectsBothIdentifiersMissing:96`、`upsertRejectsBothIdentifiersPresent:107`。
- **还原**：`cp` 回写 + `git diff --quiet` ⇒ 与 HEAD 逐字相等。
- **如实记**：与计划预判相符（T4 的两条各红一条）。

## §I D9 —— `upsert` 退化成 `.eq(fieldId, ...)`（不按 identifier 分支）

- **破坏**：把内置分支的查询改回 `.eq(FieldPermission::getFieldId, req.getFieldId())`（内置时 `fieldId` 为 null）。
- **该红**：T6（同键保存**两次** ⇒ 第二次应 UPDATE；不分支 ⇒ 生成 `field_id = NULL` **永不匹配** ⇒ 再 INSERT ⇒ 撞唯一键）。
- **承重理由**：MyBatis-Plus 的 `.eq(col, null)` 生成 `col = NULL`，**永不匹配** —— 这条实测把「`field_id` 改可空」的**真实代价**钉在用例里。
- **实测**：`FieldPermissionServiceBuiltinTest` **19 run / 1 fail**——`upsertBuiltinMatchesOnFieldKeyAndUpdates:160`（红在**该用例的 SQL 段断言**上：第二次保存走成了 INSERT 而不是 UPDATE）。
- **还原**：`cp` 回写 + `git diff --quiet` ⇒ 与 HEAD 逐字相等。
- **如实记**：与计划预判相符（T6）。⚠️ 记一句：该用例**只红一条**是**对的**——T5（未知 `fieldKey` ⇒ 422）与「第一次 INSERT」都不经过这条退化的分支，所以它们仍绿**不是漏网**。

## §J D10 —— `permissionForKey` 内部改调 `permissionFor(role, entity, null)`

- **破坏**：`permissionForKey` 的返回改为 `permissionFor(roleCode, entityType, null)`。
- **该红**：T7（三态/未配置应为 EDITABLE，但 **ADMIN 之外的已配置 HIDDEN 会回落 EDITABLE**）+ T8（读侧掩码整体消失）。
- **承重理由**：FAIL-OPEN 是安全缺陷**最坏的形态**：不报错、用例不崩、只是**该拦的没拦**。这条破坏证明「内置查找必须走 `field_key`」不是风格偏好。
- **实测**：三处读数——
  - `BuiltinFieldWriteGuardIT` **5 run / 3 fail**：`submittingHiddenBuiltinFieldIsRejected:115`、`submittingHiddenBuiltinFieldOnCreateIsRejected:137`、`changingReadOnlyBuiltinFieldIsRejectedButEchoingItBackIsNot:153`（**写侧三态全塌**）；
  - `FieldPermissionServiceBuiltinTest` **1 fail**：`permissionForKeyQueriesByFieldKey:251`（单元层直接看着它）；
  - `BuiltinFieldMaskingIT` **2 run / 0 fail（全绿）**。
- **还原**：`cp` 回写 + `git diff --quiet` ⇒ 与 HEAD 逐字相等。
- **如实记**：⚠️ **这是一处证伪**：计划写「T7 + T8 全链 fail-open」，实测**读侧一条都没红**。根因已查到：`permissionForKey` **只有一个生产调用者**（写侧的 `validateBuiltinWrite`），**读链走的是 `builtinPermissionsForRole`** ⇒ 改 `permissionForKey` 在结构上**不可能**影响读侧。⇒ **「T8 会因 `permissionForKey` 退化而红」不成立**；这条破坏的真实覆盖是「写侧 3 条 + 单元 1 条」。

## §K D11 —— 注册表删掉 `phone` 条目

- **破坏**：`BuiltinFieldRegistry` 的 CUSTOMER 表里删掉 `phone` 那一行（并同步改条数断言以隔离「只红在条数上」）。
- **该红**：T2（条数 11 → 10）+ T8 / T10（`phone` 的行为整体消失）。
- **承重理由**：注册表是**唯一**的字段清单；漏一条 ⇒ 该字段在所有面上**静默**不受控（没有异常、没有日志）。
- **实测**：合计 **15 run / 7 fail / 2 error**——
  - `BuiltinFieldRegistryTest` **8 run**：`:46`（条数 11 vs 10）、`:114`、`:132`，另两处 **error**（`:94->registered:189`、`:107->registered:189`）；
  - `BuiltinFieldMaskingIT`：`customerPhoneIsMaskedInListDetailAndPool:54->assertInvisible:122`；
  - `BuiltinFieldWriteGuardIT`：`:115` / `:137` / `:153`；
  - **T10 未红**。
- **还原**：`cp` 回写 + `git diff --quiet` ⇒ 与 HEAD 逐字相等（**本条即 §0 订正里那个「`cp` 后被 spotless 折行、需归一」的实例**）。
- **如实记**：⚠️ 计划期望的 **T10 会红那一半不成立**——与 D4 **同一根因**（客户路径上 `updateById` 的 `NOT_NULL` 让省略字段根本不进 UPDATE）。⇒ 这是**同一处结构性认识**在两条破坏上重复显形，不是两个独立发现。

## §L D12 —— 注册表加入 `name`（**必填**字段）

- **破坏**：CUSTOMER 表里加入 `name`（`CustomerRequest.name` 是 `@NotBlank`；同时改条数 11 → 12）。
- **该红**：T2 的「不许加必填字段」断言。
- **承重理由**：HIDDEN 一个必填字段会让实体**完全不可编辑**（客户端看不见却必须提交）⇒ 这条断言是**防后人顺手加**的**唯一**闸门；D12 证明它在承重。
- **实测**：`BuiltinFieldRegistryTest` **8 run / 2 fail**——`registeredFieldsArePinned:46`（**11 vs 12**）、`requiredAndOwnerFieldsAreNotRegistered:77`。
- **还原**：`cp` 回写 + `git diff --quiet` ⇒ 与 HEAD 逐字相等。
- **如实记**：与计划预判相符。⚠️ 记一句行号口径：第二条的**行号报在断言链末行 `.isNull()`**（不是 `assertThat(registry…)` 那一行）——这是本项目里「断言链的行号 ≠ 语义起点」的又一例。

## §M D13 —— 让 `plan` 把 READ_ONLY 也算进掩码集合

- **破坏**：`FieldMaskPlanner.plan` 收集 HIDDEN 的同时也收集 READ_ONLY。
- **该红**：**T13 的正对照那一半**（「原样回传 READ_ONLY 值应当**成功**」）——掩码化 READ_ONLY 会让该字段在**读侧**也消失，于是客户端**再也无法**原样回传 ⇒ 但它也可能**只**让读侧多一个 null 而写侧仍成功。
  ⚠️ ⇒ 这条破坏的预期要**跑完才知道是否成立**：若 T13 仍绿，说明**没有任何用例**看着「READ_ONLY 不参与掩码」这一条 ⇒ **必须如实记为缺口**，不得写成「三态没被压成二态已被验证」。
- **承重理由**：证明三态（HIDDEN / READ_ONLY / EDITABLE）在**读侧**没被压成二态。
- **实测**：**本条在实跑中裂成三条独立的破坏**（计划只写了一条，字面那条**改不出它预告的判据**）——
  - **D13-A**（`validateBuiltinWrite` 里 READ_ONLY 改**无条件拒绝**）：`BuiltinFieldWriteGuardIT` **5 run / 1 fail**——`changingReadOnlyBuiltinFieldIsRejectedButEchoingItBackIsNot:165`（**expected 200 but was 422**）。⇒ 正对照那一半是活的。
  - **D13-B**（READ_ONLY 分支**整段删掉**、一律放行）：**5 run / 1 fail**——同一条用例 `:153`（**expected 422 but was 200**）。⚠️ `:154` / `:155` 是同一用例内**其后的**断言，**未被执行**——不是「仍绿」。
  - **D13-C**（**计划字面那条**：让 `plan` 把 READ_ONLY 也算进掩码集合）：`FieldMaskPlannerTest` **7 run / 2 fail**——`planKeepsOnlyHidden:53`、`protectedKeysTreatUnknownPermissionAsProtected:105`；**T13 全绿**。
- **还原**：三条各自 `cp` 回写 + `git diff --quiet` ⇒ 均与 HEAD 逐字相等。
- **如实记**：⚠️ **D13-C 是双重证伪**。① 计划说该破坏应红 **T13 的正对照**——**实测 T13 全绿**（正对照走 ADMIN 快路径，ADMIN 恒 EDITABLE，掩码集合对它本就不生效）；② 而我先前登记的「预期仍绿 ⇒ 说明没有判据」**同样不成立**——**单元层有两条判据看着它**（`:53`、`:105`）。⇒ 残留缺口**收窄**为：「**没有集成层用例看着「READ_ONLY 字段在非 ADMIN 出参里仍然可见」**」。**D13-A 与 D13-B 合起来才证明三态在读/写两侧都没被压成二态**——只跑其中一条会留下另一半零覆盖。

## §N D14 —— 对**被缓存**的对象做就地置 null · **预期仍全绿**

- **破坏**：人为把一次 `plan` 的结果塞进某个手工缓存，再连发两次请求。
- **该红**：**预期无**。
- **为什么明知不红还要做**：收口点是**就地改对象**（置 null），这在「对象被缓存复用」的前提下是**结构性风险**。今天以**证据**排除（全仓三个手工缓存 `rolePermissions` / `visibleOwnerIds` / `opportunityStages` **都不存出参 DTO**；`@Cacheable` 被 `CacheConfig` javadoc 刻意禁用）——但**证据排除 ≠ 有判据看着**。
- **如实记（必须写死的一句话）**：**「没有端到端判据看着这条结构性风险」** —— **不许**写成「已验证无风险」。这是本项**唯一**一条「计划预期破坏不转红」的条目。
- **实测**：**该破坏在实跑中裂成 D14-A / D14-B 两个形态，且两个形态都推翻了「预期仍全绿」**——
  - **D14-A**（给 `FieldMaskPlanner.plan` 加**静态缓存**）：`FieldMaskPlannerTest` + `BuiltinFieldMaskingIT` + `BuiltinFieldExportIT` 合计 **11 run / 6 fail**——`planKeepsAllHiddenFields:80`、`protectedKeysTreatUnknownPermissionAsProtected:105`、`BuiltinFieldMaskingIT.customerPhoneIsMaskedInListDetailAndPool:54->assertInvisible:122`、`BuiltinFieldMaskingIT.opportunityAmountMinIsMaskedInDetailAndList:89->assertInvisible:122`、`BuiltinFieldExportIT.hiddenPhoneColumnIsEmptyInCustomerExport:85`、`BuiltinFieldExportIT.scheduledExportMasksByOwnerRole:129`。红的形态是**跨用例 / 跨角色中毒**，且**与用例执行顺序有关**。
  - **D14-B**（服务层按 id 缓存出参 DTO：`CustomerService.detail`）：`BuiltinFieldMaskingIT` **2 run / 1 fail**——`customerPhoneIsMaskedInListDetailAndPool:77->assertVisible:127`（**ADMIN 正对照**抓到了跨角色中毒）。
- **还原**：两条各自 `cp` 回写 + `git diff --quiet` ⇒ 均与 HEAD 逐字相等。
- **如实记（三条，缺一不可，逐条都是「不许抹平」的）**：
  1. **计划 D14 的「预期仍全绿」在两个形态上都不成立**——两处破坏都转红，且方向都是比计划假设的覆盖**更多**（不是更少）。⇒ 「本项没有任何判据看着缓存复用风险」这句**在 D14-A/D14-B 这两个形态上被证伪**；真正的空档要收窄到下面第 2、3 条。
  2. **仍未实测的形态**：「**同一角色跨配置变更后重读**」。四条 IT 的配置都在 `@BeforeEach` 里设一次，**没有任何用例「改配置后重读同一 (角色, 实体)」** ⇒ 这个形态**未测**，**不得**写成「已验证无风险」，也**不得**写成「有判据看着」。
  3. **未实现 D14-B 之外的其他缓存形态**（列表路径、公海路径、导出路径各自的缓存）——**只测了详情路径**。
  ⇒ 结论句（照抄）：**「就地改对象 vs 缓存复用」这条结构性风险，只在「plan 静态缓存」与「详情 DTO 缓存」两个形态上被实测过；其余形态与「改配置后重读」仍未测。**

---

## §门禁 门禁实跑读数（**交付批**，最终树）

| 项 | 读数 |
|---|---|
| `mvn -B spotless:apply` | 812 文件 clean / **0 were changed to be clean** / 812 skipped by cache；BUILD SUCCESS（0.776 s） |
| `mvn -B verify` 退出码 / BUILD 结论 | **退出码 0** / **BUILD SUCCESS** / Total time **02:23 min**（Finished at 2026-09-17T22:05:48+08:00） |
| surefire Tests / Failures / Errors / Skipped | **772 / 0 / 0 / 0** |
| failsafe Tests / Failures / Errors / Skipped | **347 / 0 / 0 / 0** |
| 失败集合 ⊆ 4 例已批准偏差？ | **∅ ⊆ 4 例**（比判据更严：那 4 例本次**一例也没红**） |
| spotless:check clean 文件数 | **812**（`0 needs changes to be clean`） |
| `jacoco:check` 结论行 | **「All coverage checks have been met.」** |
| `jacoco.exec` 字节数 / mtime | **118 930 688 字节** / **2026-09-17T22:05:43**（报告 **257 个类**） |
| INSTRUCTION / BRANCH / LINE / METHOD | **82.17%**（49 933/60 766）/ **64.37%**（3 292/5 114）/ **83.78%**（11 324/13 517）/ **86.32%**（1 849/2 142） |
| 本批 5 个新机制类的分母内覆盖 | `BuiltinWriteGuard` **100%**（136/136）· `FieldMaskPlanner` **100%**（81/81）· `BuiltinField` **100%**（12/12）· `BuiltinFieldRegistry` **92.3%**（465/504）· `FieldMaskingResponseBodyAdvice` **88.6%**（194/219）——**全部在分母里**，未藏进 `common/**` 排除包 |
| 前端五道 | `i18n:check` **2966/2966 键**（路由 **58** / 清单 **56** / 粗粒度别名 **3**）· `lint` 通过（无输出）· `typecheck` 通过（无输出）· `ui:check` 扫描 **271** 文件（125 tsx）/ **303** `Form.Item`（白名单内冻结既存债 **54** 处、**未新增**）· `zh:check` 扫描 **268** 文件 / 候选点 **9162**（台账内冻结 **266** 处 + 4 条、**未登记命中 0 处**） |
| 定向 vitest | `FieldPermissionPage.test.tsx`：**1 file / 3 passed**（8794ms；13.28s 总） |
| 本批新增用例 | 后端 **47 例 / 7 类**（`BuiltinFieldRegistryTest` 8 · `FieldMaskPlannerTest` 7 · `FieldPermissionServiceBuiltinTest` 19 · `BuiltinFieldMaskingIT` 2 · `BuiltinFieldWriteGuardIT` 5 · `BuiltinFieldExportIT` 2 · `FieldPermissionAvailableFieldsIT` 4）+ 前端 3 例 |

⚠️ **交付态读数不得被后一次 `mvn test` 冲掉** ⇒ 门禁跑完**不再跑 Maven**；本表引用**整次 verify** 的读数并写明出处（日志 `/tmp/fls102-final-verify.log`）。⚠️ 本仓已知现象：同一棵未改动的树两次 verify 的分母会漂移（IDE 语言服务原地增量编译）⇒ 取那一次并写明出处。
⚠️ **一处例外已登记**：`quickstart.md` §6 ⑧ 的字面命令含一次 `mvn -B spotless:apply`，它在门禁**之后**跑过一次。**该命令不执行任何测试、不触碰 `jacoco.exec`**（`spotless:apply` 是直接目标调用，不走生命周期），故本表读数**未受其影响**；实测该次同样打印 **0 were changed to be clean**。

## §冒烟 隔离实例冒烟（a/b/c 三档，**不碰共享开发库**）

**配方照 `quickstart.md` §4**：8099 + schema `crm_fls102` + Redis **db 5**，**真 MySQL 8.0.46 + 真 Redis + 真 HTTP**。

| # | 档 | 读数 |
|---|---|---|
| 0 | 迁移 | Flyway **90 条**，末条 `91 \| field permission builtin fields \| success=1` ⇒ §5 ② 在真 MySQL 上自证 |
| a | **默认档**（未配置任何内置权限） | SALES 建客户 id=1（phone `13800000001`）⇒ SALES 与 ADMIN 的**列表 / 详情 `phone` 均为真值**（收口点未命中 ⇒ **逐字直通**） |
| b | **配置档**（SALES + CUSTOMER：`phone=HIDDEN`、`email=READ_ONLY`；响应里 `fieldName:"电话"`/`"邮箱"` 已赋值 ⇒ 056 的 `fieldName` 缺陷已修） | SALES **列表/详情 `phone`=None**、`email` 真值；SALES **公海响应里 `phone` 键直接缺失**（`spring.jackson.default-property-inclusion: non_null`）；**ADMIN 列表/详情 `phone` 为真值（正对照，防「全 null」假绿）**；SALES `PUT` **省略** phone+email ⇒ **200**，库中 `13800000001` / `a@example.com` **原值保留**、`remark` 已改、`version` 0→1（**回补护栏在真库上成立**）；`PUT` **带** `phone` ⇒ **422 `FIELD_HIDDEN`**；`PUT` **改** `email` ⇒ **422 `FIELD_READ_ONLY`**；**原样回传** `email` ⇒ **200**（三态未被压成二态） |
| c | **导出档** | `/customers/export` 作为 SALES ⇒ xlsx 的 **D 列表头「电话」在、两行该格空**；ADMIN 同数据为 `13900000002` / `13800000001`；`email`（READ_ONLY）仍有值 |
| d | 配置面端点 | `available-fields?entityType=CUSTOMER` ⇒ **7 条内置**（`fieldKey` + 中文 `fieldName`、`fieldId=null`、`builtin=true`），`total=7`；**未知 `entityType` ⇒ 422 `FIELD_PERMISSION_INVALID`** |
| e | 收尾 | `DROP DATABASE crm_fls102` / `REVOKE` / `FLUSHDB` db5；核对 `SHOW DATABASES LIKE 'crm_fl%'` **0 行**、db5 **DBSIZE=0**；共享库 `crm_db` **87 表 / 89 条迁移历史未动**；**8081（PID 5032）与 5173（PID 14116）全程未重启** |

**未做（如实记，不写成已做）**：
1. **`quickstart.md` §3 ③ 的前端 UI 观察未做**（需浏览器）——代替物是**定向 vitest 3 passed**。
2. **`ExportExecutor` 那条链未做真机冒烟**（只有 H2 的 T16）⇒ §F 的两条链里，**只有 `CustomerExcelService` 那条过了真机**。
3. **`sleep 65` 那一次重试**：首次以 SALES 调 `/customers/export` 得到 **403**（无角色持有 `customer:export`），在**隔离库**里补授后**仍 403** ⇒ 根因是 `RoleService.permissionsOf` 的 **60 秒缓存**（`CacheConfig.ROLE_PERMISSIONS_CACHE`，缓存由应用内 CacheManager 持有 ⇒ **`FLUSHDB` 无效**）；`sleep 65` 后重试 ⇒ **200**。**该 403 与 102 的代码无关**，是既有权限模型 + 缓存 TTL 的表现，记此以免读成缺陷。

## §判据 可核判据（`quickstart.md` §5）实跑

⚠️ **口径**：下表凡标「0 命中」的，一律说明**是否已排除行首注释**——本仓「订正不静默」要求旧值留在 ⚠️ 注释里，照字面跑 0 在正确实现下**拿不到**。

| # | 判据 | 期望 | 实测 |
|---|---|---|---|
| ① | 056 一字未动（`git log --name-only a599884..HEAD -- specs/056-field-permission \| wc -l`） | 0 | **0** ✅ |
| ② | 迁移面：总数 / V91 / V72 / 两处清单 `^\| V91 ` | 90 / 1 / 0 / 各 1 | **90 / 1 / 0 / `specs/README.md`:1 · `INSTALL.md`:1** ✅ |
| ③ | 镜像标记：`schema-h2` 的 `field_permission` 块 `-- V91` / `SchemaParityIT` 的 `"91"` | 3 / 1 | **3 / 1** ✅ |
| ④ | 零新增错误码 / 零新增权限码 | 0 / 0 | **0 / 0** ✅（`ErrorCode.java` 无新增 `F(` 行；`RoleConstants.java` diff 为空） |
| ⑤ | 未引入全局 `@JsonInclude` | 0 | **0**（**严格口径**：排除**所有**注释行后 0；`^\s*@JsonInclude` 真实注解使用 **0**）。⚠️ **字面口径命中 2**——两处都是**本批自己写的 javadoc 提及**（`BuiltinFieldRegistry.java:192`、`FieldMaskingResponseBodyAdvice.java:46`，都在讲「**有意不引入**它」）；其中 `:192` 那行以 `/**` 开头，**落在 §5 那条 filter（`(\*\|//\|--)`）的缝里**（它匹配 `*` 不匹配 `/`）⇒ 判据的 filter **自身有一处口径缺口**，本表一并登记 |
| ⑥ | 056 的「后续扩展」原文仍在 | 各 1 | **1 / 1** ✅ |
| ⑦ | i18n 实测 | — | **2966 键 / 2966 键**；路由 58 / 清单 56 / 粗粒度别名 3 ✅ |

## §订正 订正不静默自查（`quickstart.md` §6）命中数

| # | 判据 | 期望 | 实测（新值栏 → 旧值留痕栏） |
|---|---|---|---|
| ① | P0 第 1 条 | `基本闭合` ≥1；`仍缺` 未被注释掉的仍存在 | **5** → 未被 `\|`/`>` 开头的 `仍缺` 行 **23** ✅ |
| ② | 2.9 两句原文 | 各 ≥1 | `内置字段（客户名/金额等）与 API 出参` **1** · `FLS 内置字段、SSO、字段加密、IP 白名单四项一个都没动` **1** ✅ |
| ③ | 4.1 结论 3 | ≥1 | `字段加密 / IP 白名单` **2** ✅ |
| ④ | 迁移数 6 处活落点 | 见下（逐条） | 见下（逐条）✅ |
| ⑤ | README 编号 | 各 ≥1 | 旧 `99 个功能模块，001~100` **2** · 新 `101 个功能模块，001~102` **2** ✅ |
| ⑥ | PROJECT_FEATURES 模块数 | 各 ≥1 | 旧 `100（001–101，缺 069）` **4** · 新 `101（001–102，缺 069）` **2** ✅ |
| ⑦ | 两份登记 | 各 ≥1 | `specs/README.md` **1** · `specs/roadmap.md` **1** ✅ |
| ⑧ | spotless 重排自证 | 无变化 | `812 files clean / 0 were changed` ✅（⚠️ 见下） |

**④ 逐条读数（旧值留痕 / 新值）**：

| 落点 | 旧值留痕 | 新值 |
|---|---|---|
| `INSTALL.md` 正文①（`:124` 附近） | `V1~V90 共 89 个脚本` **1**（⚠️ C7 补记） | `V1~V91 共 90 个脚本` **2** |
| `INSTALL.md` 正文②（`:262` 附近） | `共 89 个迁移脚本` **1**（⚠️ C7 补记） | `V1~V91，共 90 个迁移脚本` **2** |
| `INSTALL.md` 迁移表 | `^\| V90 ` **1**（补登行）/ `^\| V91 ` **1**（新行） | — |
| `specs/README.md` 章节标题 | `Flyway V1~**V90**，共 **89** 个脚本` **1**（⚠️ C7 补记的 102 订正块）/ `V1~**V90**，共 **89**` **1** | `V1~**V91**，共 **90**` **2** |
| `specs/README.md` 迁移表 | `^\| V91 ` **1** | — |
| `PROJECT_FEATURES.md` | `**89 个（V1–V90，缺 V72）**` **2** | `**90 个（V1–V91，缺 V72）**` **1** |
| 根 `README.md` 目录树 | — | `db/migration（V1~V91` **1** |
| **历史读数（不得变）** | `**89（V1–V90）**` **1** · `V1~V90 / 89 个` **1** · `Flyway **89（V1–V90，缺 V72）**` **2** | **逐条与改造前相同** ✅ |

⚠️ **④ 里有一处必须单独写清的偏差（本批自查的最大收获）**：三处落点（`specs/README.md` 章节标题、`INSTALL.md` 两处正文）在 **C2 落地时只改了现行值、没留下旧值** ⇒ 交付自查按判据复算时命中 **0**（「旧值仍能被 grep 到」**不成立**，即**静默改写**）。**处置**：**不是把判据改松**，而是**补回本该有的三个 ⚠️ 订正块**（各带日期 2026-09-17 / 责任批 102 / 原文逐字引用 / 「本条订正块由交付批补记」的说明），补后复算全部达标。⇒ **教训**：判据在**改造前**跑出的「改造前读数」必须在**改造后**逐条复算——**C2 当时只跑了自己那一半**。

⚠️ **⑧ 的口径**：字面命令是 `mvn -B spotless:apply && git diff --stat -- ../CRM_FEATURE_COMPARISON.md`，期望「无变化」。**在未提交的工区上它必然非空**——实测输出 `1 file changed, 25 insertions(+), 4 deletions(-)`，那是**本批自己的 6 处 102 订正**，**不是 spotless 的重排**。⇒ **该判据要读的是 spotless 自己那行 `0 were changed to be clean`**（实测：812 clean / 0 changed，且门禁相位的 `spotless:check` 同为 `0 needs changes`）；`git diff --stat` 只适合在**已提交的树**上跑。

## §边界 如实登记的边界（**不得被读成已解决**）

1. **「恰好一列非空」无 DB 级约束**（H2/MySQL 实测都允许双 NULL 行）⇒ 只有 `upsert` 保证。
2. **自定义字段的 READ_ONLY 被省略 ⇒ 值被删**（既有缺陷，本项**不修**）——本项只在**内置**侧做了回补。
3. **`permissionFor(..., null)` 回落 EDITABLE（fail-open）** ⇒ javadoc + T7，但**没有机制**阻止后人误用。
4. **定时导出是混合态**：字段掩码接了业主角色，063 的 `mask()` 与 `visibleOwnersOrNull` 仍 fail-open。
5. **只覆盖 CUSTOMER + OPPORTUNITY 的 11 个字段**；**必填字段永久排除** ⇒ 上游点名的「**客户名**」这一半**本项不覆盖**。
6. **掩码后是 `null` 而非缺键** ⇒ 与「无值」不可区分（无侧信道，**有意**）。
7. **收口点每响应算一次 plan、未加缓存** ⇒ 不声称零成本。
8. **056 契约与实现的既有漂移**（`roleId` vs `roleCode`、`PUT` 不存在、两枚错误码从未抛出、`roleCode` 用 `@NotNull` 而非 `@NotBlank`）⇒ 登记、**不改 056**。
9. **§N（D14）：改「就地改对象 vs 缓存复用」的结构性风险**——⚠️ **订正（2026-09-17，C7）**：本条原文写「**没有端到端判据看着「就地改对象 vs 缓存复用」这条结构性风险**」。**该说法的适用范围比原文写的窄**：D14-A（plan 静态缓存）与 D14-B（详情 DTO 缓存）**两个形态实测都转红**，且 **D14-B 的红是 ADMIN 正对照（`assertVisible:127`）抓到的**——即**有**集成层判据看着这两个形态。**收窄后的真实空档是**：① **「同一角色跨配置变更后重读」这个形态从未被测**（四条 IT 的配置都在 `@BeforeEach` 里设一次）；② **列表 / 公海 / 导出路径各自的缓存形态未测**（只测了详情路径）。**原文保留在上、不静默改写**；**无论哪一版，都不许写成「已验证无风险」。**
10. **导出两条链只有一条过了真机**：`CustomerExcelService`（§冒烟 c）过了，**`ExportExecutor`（手动 + 定时）只过了 H2 的 T16**。
11. **前端 UI 面未做真机观察**（`quickstart.md` §3 ③）：无浏览器，代替物是定向 vitest **3 passed**。

## §来源 各节由哪次实跑填

| 节 | 来源 |
|---|---|
| §A–§N | C6 之后、C7 之前的逐条定向破坏（**逐条 `cp` 还原 / `Edit` 反向还原**，判据取「与 HEAD 逐字相等」，期间不提交）；原始读数见工作笔记 `/tmp/fls-d-series.md` |
| §门禁 | C7 的那一次完整 `mvn -B spotless:apply && mvn -B verify`（日志 `/tmp/fls102-final-verify.log`，退出码 0）+ 前端五道 + 定向 vitest |
| §冒烟 | 隔离实例（§4 配方），**不与并行会话抢端口、不写共享库** |
| §判据 / §订正 | 交付树上的只读 grep（§5 ①–⑦、§6 ①–⑧），读数逐个抄自 `quickstart.md` 里**改造前**登记的基线与交付时复算值 |
