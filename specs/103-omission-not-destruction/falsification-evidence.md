# 定向破坏证据（103）

> **格式说明（照 102 的 §0/§D）**：每一行在**动手之前**就写清「它该改变哪条**可观察行为**」。
> 破坏用 `cp` 备份回写还原，判据是 **`git diff --quiet -- <file>`（与 HEAD 逐字相等）**；
> **禁用 `git checkout`**（它还原的是 HEAD，会把同文件里**有意未提交**的订正一起吞掉）。
> **就地改一律用 Edit 工具**（本仓 Java 源是 **CRLF**，脚本重写会把整个文件翻成 LF 并让 spotless 报 BUILD FAILURE）。
> 探针残留判据用唯一标记 **`留痕后还原`**。
>
> ⚠️ **一条绿的破坏不是「已验证无风险」**——要**如实记为「没有判据看着」**。

---

## 0 破坏前的自问（每条都要先答）

1. **它该改变哪条可观察行为？** —— 答不出来就是**空操作**，不要跑。
2. **看到红，先读是不是「手段的红」** —— CRLF / spotless 折行 / 编译错 / `TS6133` **都不是目的的红**。
3. **绿了** —— 是「破坏是空操作」还是「判据没盖住」？**这两种的处置完全不同**。

---

## D 系列

| # | 破坏 | 该改变哪条可观察行为 | 该红 | 实测输出 | 判定 |
|---|---|---|---|---|---|
| D1 | 回补谓词缩回 `HIDDEN`（= 103 之前的代码） | READ_ONLY 字段的省略**从「被回补」变回「被删除」** | U1、U2、I1（**U3 应保持绿**——它不是为这条谓词而设的） | 单测 17 例**4 红**：U1（`{2L="新"}` 里缺 `1L="只读原值"`）、U2、U5、空白值钉子；IT 8 例**1 红**：I1 `expected: "只读原值" but was: null`。**U3 保持绿**（与预测一致）；LeadIT 8 例全绿 | **变红，判据成立**。附带实测：谓词一缩，**U5（未知权限值）与空白值钉子也一起红**——它们的覆盖面与 D4 在单测层重叠，不是独立判据 |
| D2 | **去掉 `submittedIds` 排除** | 原样回传的 READ_ONLY 值**从「插一行」变成「插两行同一键」** | U3、I2 —— 预测 `DuplicateKeyException` 撞 `uk_field_entity_value` ⇒ **500** | 单测 17 例**1 红**：U3（断「恰好一行」的 `containsExactly(1L)` 失败）；IT 8 例**1 红**：I2 `Status expected:<200> but was:<409>`；日志 `Type = org.springframework.dao.DuplicateKeyException` | **变红 ⇒ 机制成立；但状态码预测错**：抛的确实是 `DuplicateKeyException`（撞的确实是 `uk_field_entity_value`），**而 063 的 `GlobalExceptionHandler:130-138` 把它渲染成 409 `DUPLICATE_KEY`，不是 500**。已逐字记录，并据此订正**六份文件里的十处「500」**（见下方 D2 特别说明） |
| D3 | **反方向**：把每个 id 都当受保护 | **EDITABLE 字段的省略从「清空」变成「保留」** ⇒ 清空能力失效 | U4、I3 | 单测**3 红**：U4 `saveValuesStillClearsOmittedEditableField`、U6 `saveValuesRestoresNothingWhenNoPermissionConfigured`、**102 的 `saveValuesKeepsHiddenFieldValue`**；IT**1 红**：I3 `omittedEditableCustomFieldValueIsStillCleared` | **变红 ✓**（多出的两条是同方向的伴生：无差别回补同时翻转了「无配置 ⇒ 不回补」与 102 的既有断言）。**清空能力确实只有这几条看着** |
| D4 | 谓词改成枚举 `HIDDEN \|\| READ_ONLY` | **未知权限值**从「受保护」变成「不受保护」 | U5 | 单测**恰好 1 红**：U5；IT 16 例全绿 | **变红 ✓ 且是精确打击**——只有钉未知权限值的那条红 |
| D5 | 回补块挪到 `valueMapper.delete`（`:235`）**之前** | 回补的行**先被插入、随后被 delete 删掉** ⇒ 等价于没回补 | U1、I1 | **单测 17 例全绿**；IT**2 红**：I1 `expected:<200> but was:<409>` 与 `hiddenFieldValueIsNeitherLeakedNorDeleted` | **只红一半，且红的原因与预测不同**：① **单测看不见顺序**——`valueMapper` 是 mock、`delete` 不真删，而判据是 `insertedValues()`（插过哪些），顺序对它不可观测；② IT 红的机理**不是**「插了又被删」，而是**插入发生在 delete 之前、库里那一行还在 ⇒ 当场撞唯一键 ⇒ 409**。⇒ 顺序是**只有真库能看见**的性质，其可观察形态是 **409** 而非「值消失」 |
| D6 | 读路径接上受保护集合（`dropHidden` → `protectedFieldIds`） | READ_ONLY 的值**从「下发」变成「被过滤」** ⇒ 客户端无从「原样回传」 | I4 + 056 的 `fieldPermissionFlow`（改值 422 那半） | IT**1 红**：I4 `readOnlyCustomFieldValueIsStillVisibleToTheRestrictedRole`；**056 的 `fieldPermissionFlow` 保持绿**；单测**1 红**：102 的 `readValuesKeepsEverythingWhenNothingHidden` | **部分成立**：I4 红 ✓，读路径确实有判据；但这一行预测的**第二个判据不存在**——`fieldPermissionFlow` 那半发的是**改过的值**（`"v2"`），`validateWrite` 无论读路径返回什么都会 422，与 `dropHidden` 无关。如实记录，不回填 |
| D7 | 前端：重新渲染 HIDDEN / 去掉 READ_ONLY 的 `disabled` | HIDDEN 字段**从「不出现在 DOM」变成「出现」**；READ_ONLY 控件**从 disabled 变成可编辑** | 两个 vitest 用例 | `CustomFieldItems.test.tsx` **2 红**（HIDDEN 渲染出来 · READ_ONLY 未 disabled）；`useCustomFieldFilters.test.tsx` 3 例全绿。合计 7 例中 **2 红 5 绿** | **变红 ✓** |
| D8 | 前端：去掉 `useCustomFieldFilters` 的 HIDDEN 过滤 | HIDDEN 字段**从「无筛选列」变成「多出一列 `cf_<id>`」** | 筛选列 vitest | `useCustomFieldFilters.test.tsx` **1 红**：`expected [ 'cf_911', 'cf_912', 'cf_913' ] to not include 'cf_911'`（隐藏字段的列真的冒出来了）；另一文件 4 例全绿 | **变红 ✓** |
| D9 | **让 null 也能写库**（给 `Lead.ownerId` 加 `updateStrategy = FieldStrategy.IGNORED`） | `ownerId` **从「保留」变成「被置 null」** ⇒ 省略真的销毁了它 | `omittedOwnerIdSurvivesLeadUpdate` | LeadIT **1 红**：`[省略 ownerId 后负责人被清空（该字段从响应里消失）] Expecting value to be true but was false`；其余 7 例全绿 | **变红 ✓ ⇒ 该用例有牙齿**，SC-006 成立：分支 A 下没有任何生产改动，那条钉住式用例看着的**确实是一条真实性质**（不是文档性断言） |
| D10 | LEAD `create` 的默认逻辑改经守卫（**仅当日后落地守卫**） | create 的**默认负责人**行为改变（非 ADMIN 不再默认成自己 / ADMIN 不再留池） | create 两条路径的用例 | **未跑** | **无判据**——分支 A 下**不存在守卫**，这个破坏无法表达。如实记为「没有可破坏的对象」，**不是「跑绿了」** |
| D11 | LEAD `apply` 改成无条件写 `ownerId` **且** `update` 用全字段覆盖 | 同 D9，**不同的破坏面**（D9 改策略，D11 改写法） | `omittedOwnerIdSurvivesLeadUpdate` | LeadIT **1 红**（同 D9 的那条断言）；其余 7 例全绿 | **变红 ✓**——与 D9 是**两个不同的破坏面**（D9 改 ORM 的 `updateStrategy`，D11 改成 `LambdaUpdateWrapper` 的具名 `set`），**两者都只被这同一条用例抓住** ⇒ 该用例是这条性质在全仓的**唯一判据** |

### D2 的特别说明（**这条最重要**）

D2 是本批**唯一**验证「为什么修法必须是这个样子」的破坏。若它**不变红**，则说明：

> `uk_field_entity_value` 的唯一性 / 插入路径 / `validateWrite` 的放行条件**与 `research.md` §0.1 记录的机制不符**。

那时**必须逐字记录该证伪结果**，**不得**回填成「验证通过」，也不得继续以 §0.1 的口径写交付说明——因为**整个 fix 的形状（谓词 + 排除）正是由那条机制推出来的**。

**实测结果（2026-09-18）**：D2 **变红** ⇒ 上面这段**预登记的证伪分支未触发**，§0.1 记录的机制成立。预登记原文**逐字保留在上**（不得被结果改写）。三点补充：

1. **红的形态与预登记一致**：撞的确实是 `uk_field_entity_value`——单测 U3 的 `containsExactly(1L)` 红（真的插了两行），IT I2 的日志逐字是 `Type = org.springframework.dao.DuplicateKeyException`。
2. **红的出口与预登记的预测不一致**：本文件 D2 行、`research.md` §0.1、`spec.md`、`quickstart.md`、`plan.md` 与生产注释**六份文件里的十处**都写「⇒ 500」，**实测是 409 `DUPLICATE_KEY`**——063 的 `backend/src/main/java/com/crm/exception/GlobalExceptionHandler.java:130-138` 已把 `DuplicateKeyException` / `DataIntegrityViolationException` 显式映射成 409。**旧值「500」在十处全部逐字保留**，订正一律以**带日期 ⚠️ 块**追加（判据：`grep -rn "500" specs/103-omission-not-destruction/ backend/src/main/java/com/crm/service/CustomFieldService.java` 逐处可见）。
3. **结论不变**：少了 `submittedIds` 排除，**原样回传 READ_ONLY 值的 PUT 一律失败**，只是失败码是 **409** 而非 500。排除照旧必需，fix 的形状不变。

### D9 的特别说明（**本批的诚实项**）

分支 A 下 `LeadService` **没有任何生产改动**（`research.md` §1）⇒ `omittedOwnerIdSurvivesLeadUpdate` 今天之所以绿，靠的是 **MyBatis-Plus 的默认 `NOT_NULL` 策略**，而不是本批写的任何代码。

⇒ **D9 是这条用例唯一的判据**：它把「策略」人为改掉，看用例会不会红。

- **转红** ⇒ 该用例**有牙齿**，它钉住的是一条真实性质（SC-006 成立）。
- **仍绿** ⇒ 该用例**没有判据看着** —— 如实记录，并说明它至多是一条「文档性断言」。

**实测结果（2026-09-18）**：D9 **转红**（LeadIT 1 红 7 绿，红在 `[省略 ownerId 后负责人被清空（该字段从响应里消失）] Expecting value to be true but was false`）⇒ 走上表**第一条**分支：该用例**有牙齿**，SC-006 成立。

**并且**：D11 换了一个**不同的破坏面**（改 `LambdaUpdateWrapper` 具名 `set`，而非 ORM 的 `updateStrategy`）**同样只被这同一条用例抓住** ⇒ 上句「D9 是这条用例唯一的判据」说的是**判据唯一**（没有别的用例看着这条性质），不是说**只有一种破坏能证伪它**。两者不矛盾，但**必须并列写明**，否则会把 D11 的读数读成对上一句的反驳。

---

## 边界（本批**不可证**或**未证**的项）

1. **1c 类无任何门禁看着**（`research.md` §6-7）：本批「被 `NOT_NULL` 跳过」的结论建立在「无 `update-strategy` + 无 mapper XML」上，而**没有任何门禁**阻止后人加上其中任一项。LEAD 那一条由 D9/D11 部分覆盖；`TicketService` / `ContactService` 的候选**完全没有**判据。
2. **盘点中 1a/1b 的 ☆ 成员全部未核实**（`research.md` §7）：本批**没有**为它们写任何用例，**也没有**逐行读过。它们进工作单时标「未核实」。
3. **回传格式脆弱性**（债务 3）**没有任何判据**：它是一条**前提**（初始值来自库中原始字符串），不是一个被断言的性质。若将来有人改了 `toCustomFieldPayload`，**没有用例会红**。
4. **必填 HIDDEN 字段的不可能性**（债务 2）只由 I5 的边角钉住，**没有**端到端的「管理员配错 HIDDEN 且必填」用例。
5. **`ui:check` 的冻结计数是否移动未经实测**（`research.md` §7-4）——新增一个同目录 `*.test.tsx` 时，产品文件数 / `Form.Item` 计数是否变化**须跑一次看，不要假设**。
6. **`DELIVERY_SCOPE.md` 没有任何门禁看着**（FR-028 自陈）：以「复算命令 + 旧值仍可 grep 到」替代门禁。
