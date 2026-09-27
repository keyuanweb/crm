# 定向破坏证据（104）

> **格式说明（照 103 的 §格式说明/§0/§D）**：每一行在**动手之前**就写清「它该改变哪条**可观察行为**」。
> **本批的还原判据不是 `git diff --quiet`**（103 的口径）——本工区里有**有意未提交**的改动（`AiPromptCatalog.java`
> 的 `toneLabel` 空值修复 +10/−2）与**未跟踪**的新用例/组件，`git diff` 无法区分「破坏残留」与「本批交付物」。
> 故判据改为 **`cp` 回写 + md5 逐字相等**（备份 `%TEMP%/crm104-dbackup`、`%TEMP%/crm104-fbackup`）。
> 这**不是口径松弛**：md5 相等的强度不低于 `git diff --quiet`，且额外要求探针标记零命中。
> **禁用 `git checkout`**（它还原的是 HEAD，会把同文件里**有意未提交**的订正一起吞掉）。
> **就地改一律用 Edit 工具**（本仓 Java 源是 **CRLF**，脚本重写会把整个文件翻成 LF 并让 spotless 报 BUILD FAILURE）。
>
> ⚠️ **探针标记与 T035 的措辞不一致（如实记录）**：T035 写的判据标记是 `留痕后还原`（实测 **0** 命中），
> 但本批**实际使用**的临时标记是 **`破坏 D<n>（临时）`**。后者在五个被改的 main 文件与前端组件里**各 0 处**；
> 全仓另有 6 处 `破坏 D` 命中，逐处核对如下：4 处属 103/C6/082 旧批的说明性注释，2 处是**本批测试文件里的
> 交付性注释**（`AiContentIT.java:239`、`AiGenerateButton.test.tsx:21`），**不是残留**。
>
> ⚠️ **一条绿的破坏不是「已验证无风险」**——要**如实记为「没有判据看着」**。
>
> ⚠️ **行号是快照，锚字符串才是耐久坐标**：下表的 `file:line` **逐字抄自当次运行日志**，
> 而 C4 提交前跑的 `mvn -B spotless:apply` **改写过 `AiContentIT.java`**（见 §边界 10）。
> 用锚字符串逐处定位后实测的漂移区间：**该文件在 248–277 行之间被合并掉 4 行**，故 **`:277` 之后整体 −4**
> （三处独立测得：日志 `:281`/`:283`/`:318` 现依次为 `:277`/`:279`/`:314`，
> 对应的锚分别是 `contains("inputTokens=10")` / `doesNotContain(NAME_SENTINEL)` / `hasSize(1)`）；
> **`:247` 及之前未漂移**（`:173` `doesNotContain(STATUS_SENTINEL)`、`:195`、`:239`、`:247` 四处均当时即今）。
> ⇒ 本文件里需要做映射的只有两处：D7 的 `:283` → **`:279`**、D8 的 `:318` → **`:314`**。
> **引用行号时必须连同锚字符串一起引**，否则文件一改，坐标集体失效。

---

## 0 破坏前的自问（每条都要先答）

1. **它该改变哪条可观察行为？** —— 答不出来就是**空操作**，不要跑。
2. **看到红，先读是不是「手段的红」** —— CRLF / spotless 折行 / 编译错 / `TS6133` / `UnnecessaryStubbing` **都不是目的的红**。
3. **绿了** —— 是「破坏是空操作」还是「判据没盖住」？**这两种的处置完全不同**。

**本批一条操作纪律（实测所得，写在这里供复算）**：Maven 会打印 `Nothing to compile - all classes are up to date`——
因为 **IDE 把 class 编进了 `target/classes`**（class 的 mtime 晚于源）。故每次破坏前先
`rm -f target/classes/.../<Class>.class`，并确认日志里有 `Compiling 617 source files with javac [debug release 21]`。
**不确认这一句，就无法排除「破坏根本没被编进去」**（本批 D1/D2 两次核过）。

---

## D 系列

**mvn 命令**（本机 `JAVA_HOME` 指向 JDK 21；**不传 `-DargLine`**）：

```bash
cd backend && mvn -B -o -Dtest='<类名>' test
cd backend && mvn -B -o -Dit.test='AiContentIT,AiContentUnconfiguredIT' \
  test-compile failsafe:integration-test failsafe:verify
cd frontend && pnpm exec vitest run src/components/AiGenerateButton.test.tsx
```

| # | 破坏 | 该改变哪条可观察行为 | 该红 | 实测输出 | 判定 |
|---|---|---|---|---|---|
| D1 | `AiStatus.java` 的 `@Value("${crm.ai.enabled:false}")` → `true`（`application.yml:93` 仍 `${CRM_AI_ENABLED:false}`） | Java 兜底值与 yml 默认值**从一致变不一致** ⇒ `isConfigured()` 的结果随「有没有 yml 覆盖」而变 | **U1**（并实测 I 系列是否照旧绿） | 单测 `Tests run: 8, Failures: 1, Errors: 0`，红的**唯一**一条 `javaFallbacksMatchApplicationYmlDefaults`（`AiStatusTest.java:173`）：`[yml 的默认值与 @Value 兜底值必须逐字一致：不一致时 isConfigured() 会随「有没有 yml 覆盖」而变]` `expected: {… "enabled"="false" …} but was: {… "enabled"="true" …}`（7 键逐字比对）。**I 系列：`AiContentIT` 6/6 绿 + `AiContentUnconfiguredIT` 1/1 绿 = 7 绿，`BUILD SUCCESS`** | **U1 变红 ✓，且 I 系列照旧全绿 ⇒ 「假绿通道 2」被实证**（见 D1 特别说明） |
| D2-a | 摘掉 `AiContentService.generate` 的结构性判门（`if (!aiStatus.isConfigured()) throw new AiNotConfiguredException();`） | 未配置时**从「出站前即拒」变成「直接走到 `clientFactory.client()`」** | U2、I1（按 `plan.md:311`） | 单测 `Tests run: 15, Failures: 1, Errors: 14`：**目的的红 1 条** `unconfiguredMeansZeroEgressAndZeroAudit` → `org.mockito.exceptions.verification.NoInteractionsWanted`，`AiContentServiceTest.java:163`；`But found these interactions on mock 'clientFactory': -> at com.crm.service.AiContentService.generate(AiContentService.java:103)`；`Actually, above is the only interaction with this mock.` **手段的红 14 条**：12 条 `UnnecessaryStubbingException` 指向共享夹具 `AiContentServiceTest.configured(AiContentServiceTest.java:110)`，另 2 条指向用例自带桩（`…StillRejected(AiContentServiceTest.java:172)`、`budgetGateRunsBeforeEgress(AiContentServiceTest.java:186)`）。**I 系列：7 例全绿，`BUILD SUCCESS`** | **U2 变红 ✓（目的的红只有这一条）；I1 保持绿 ⇒ `plan.md:311` 对 I1 的预测被证伪**（见 D2 特别说明） |
| D2-b | **补刀**：再摘掉外层 `AiEmailDraftService.generate` 的同一道门（`:96-98`） | 未配置时**连「取数都不做」这条也没有了** ⇒ 顺序不变式「配置门在最前」失效 | I1 | `AiContentUnconfiguredIT` **1 红**：`unconfiguredRefusesBeforeAnythingElse`（`AiContentUnconfiguredIT.java:71`），`[空 body {} 应当先被配置门拦下] expected: 409 but was: 400`；`AiContentIT` 6/6 仍绿 | **I1 变红 ✓**。⚠️ 红的**形态**是 **400**（不是 500）：两道门都没了，`{}` 先撞上 `validate()`。故 I1 看着的是**顺序**，**不是**零出站 |
| D3 | 取数改为直查裸 Mapper（**复现 022 的错法**） | 跨 owner 的客户数据**从「403 被拒」变成「被读出并拼进提示词」** | **I3**（`plan.md:312` 称本批最重要的一次破坏） | **I 系列 6 例全绿，`BUILD SUCCESS`** —— `plan.md:312` 的预测**被证伪** | **单刀不变红 ⇒ 如实记为「I3 的 403 不是被本类自己那道判据看着的」**（见 D3 特别说明） |
| D3-b | **补刀**：再跳过 `followUpService.page(...)` 那次取数 | 402/403 的第二道出口也没了 ⇒ 出站真的发生 | I3 | `AiContentIT` **1 红**：`anotherOwnersCustomerIsRejectedBeforeEgress`（`AiContentIT.java:195`），`[同一角色、同一客户实体，只有归属不同：这条 403 只能来自数据范围判定] expected: 403 but was: 500`；并伴 `java.lang.NullPointerException: Cannot invoke "com.anthropic.models.messages.Message.content()" because "message" is null` `at com.crm.service.AiContentService.extractText(AiContentService.java:174)` —— **证明真的走到了出站之后** | **变红 ✓ ⇒ 402/403 的判据落在下游同一条判据的第二道出口上** |
| D4-a | 摘掉装配期的 FLS 守卫（`hidden.contains("status")` 那一支） | HIDDEN 的字段值**从「不送」变成「送进提示词」** | I2（`plan.md:313`） | `AiContentIT` **1 红**：`hiddenBuiltinFieldsAreNeverSentToTheModel`（`AiContentIT.java:173`），`[HIDDEN 的字段值绝不出网：提示词不经过出参收口点，只能靠装配时自己判] Expecting actual: "业务资料：\n- 客户名称：哨兵客户名甲\n- 公司：哨兵公司甲\n- 客户状态：哨兵状态甲\n- 联系人：哨兵联系人表乙\n补写要求：无\n语气：正式、专业、书面" not to contain: "哨兵状态甲"` | **变红 ✓** |
| D4-b | 往 `P1_CUSTOMER_FIELDS` 白名单里加入 `"phone"` | **白名单声明本身**从「不含敏感字段名」变成「含」 | U8（`plan.md:313`） | `AiPromptCatalogTest` **1 红**：`noSensitiveFieldInAnyWhitelist`，`Expecting ["status", "company", "contactPerson", "name", "phone"] not to contain ["email", "mobile", "idCard", "idNumber", "bankAccount", "passport", "password", "address", "remark", "phone"]`。**`AiContentIT` 6 例全绿，`BUILD SUCCESS`** | **U8 变红 ✓；I2 保持绿** ⇒ 「白名单」是**声明**、不是**出站控制**（见 D4 特别说明） |
| D5 | 把输入校验挪到出站之后 | 非法请求**从「400 且零出站」变成「先出站、再 4xx/5xx」** | I5（证明判门**在出站之前**，而非仅仅存在） | **原版 I5**：`AiContentIT` 1 红，`invalidInputIsRejectedBeforeAnyEgress`（`AiContentIT.java:241`），`[超长 1 个字符即拒（400 而不是 422：全仓没有通用的 422 校验码）] expected: 400 but was: 500`。⚠️ **plan 点名的那个判据（`verifyNoInteractions`）此时开不了口**——状态位断言排在它前面 | **变红 ✓，但暴露了判据自身的排列缺陷**（见 D5 特别说明） |
| D5-b | 同上破坏 + **把 I5 的三句状态断言挪到 `verifyNoInteractions` 之后** | 同 D5 | 零出站那句应当**先**开口 | `AiContentIT` **1 红**：`NoInteractionsWanted`，`-> at com.crm.integration.AiContentIT.invalidInputIsRejectedBeforeAnyEgress(AiContentIT.java:247)`，`-> at com.crm.service.AiContentService.generate(AiContentService.java:123)`。**还原后该条复跑 `Tests run: 1, Failures: 0`，`BUILD SUCCESS`** | **变红 ✓** ⇒ 该重排**被采纳为交付物**（见 D5 特别说明） |
| D6 | 截断不标记、当完整结果返回 | `truncated` **从 true 变 false** ⇒ 前端把半截文本当完整结果呈现 | U5、F4 | 后端单测 `Tests run: 15, Failures: 1, Errors: 1`：**2 红**——`truncatedResponseIsFlagged`（`AiContentServiceTest.java:313`，`Expecting value to be true but was false`）与 `blankOutputIsRejectedUnlessTruncated`（`Expecting value to be true but was false`）。前端 `AiGenerateButton.test.tsx` **1 红 5 绿**：F4，`Unable to find an element with the text: pages.customer.detail.aiDraftTruncated`，`AiGenerateButton.test.tsx:143` | **变红 ✓**（实际靶面比预测宽：U5 **与** U5-b 两条后端 + F4） |
| D7 | 审计 detail 写入提示词原文（`+ " prompt=" + request.userPrompt()`） | 审计 detail **从「只有元数据」变成「含提示词原文与客户数据」** | SC-004 的 grep 判据 | ⚠️ **该判据在本项工件里没有登记过命令**（见 D7 特别说明）⇒ 本次现定命令并两态读数：`sed -n '/auditService\.record(/,/);/p' <file> \| grep -cniE "prompt\|instruction\|content\|customer\|name\|company"` → **还原态 0，破坏态 2**（`+ " prompt="` 与 `+ request.userPrompt()`）。**行为层孪生**：`AiContentServiceTest` 1 红 `usageComesFromTheSdkAndAuditHasMetadataOnly`（`AiContentServiceTest.java:363`，`"capability=email-draft; inputTokens=1234; outputTokens=567; durationMs=0; prompt=哨兵客户资料-13900001111" not to contain "哨兵客户资料-13900001111"`）；`AiContentIT` 1 红 `generationWritesNothingButOneMetadataOnlyAuditRow`（`AiContentIT.java:283`），把整段提示词（含 `哨兵客户名甲`/`哨兵状态甲`）逐字打进了 detail | **变红 ✓，但红的不是 SC-004 点名的那个 grep**——那是一个**没有落地的指针**，真实判据是本次 C4 新写的 U6/I6 |
| D8 | 预算键改成 `ai:ignore:*`（撞 022 命名空间） | 预算桶**从「独立族」变成「与 022 的忽略集共用」** ⇒ 两族互相污染 | U7 | `AiTokenBudgetTest` **1 红**：`budgetKeyShape`（`AiTokenBudgetTest.java:73`），`expected: "ai:gen:budget:user:42:20260927" but was: "ai:ignore:user:42:20260927"`。`AiContentIT` **1 红**：`exhaustedDailyBudgetIsAControlled429`（`AiContentIT.java:318`），`[记账必须真的落到 Redis 上：单测里 store 是桩，这条是唯一的接线判据] Expected size: 1 but was: 0 in: []` | **变红 ✓，两处独立读数**（单测的键形 + IT 的键族接线） |
| D9 | token 用估算（字数 ÷ 3）替代 SDK usage | 记账与审计里的 token 数**从「SDK 真值」变成「按字符数估算」** | U6 | 单测 `Tests run: 15, Failures: 3, Errors: 0`：`budgetUsesTheRequestsUserId`、`chargeSumsInputAndOutput`、`usageComesFromTheSdkAndAuditHasMetadataOnly`（`AiContentServiceTest.java:346`，`expected: 1234L but was: 6L`）；三条的调用链都指向 `AiTokenBudget.charge(AiTokenBudget.java:96)` ← `AiContentService.generate(AiContentService.java:104)` ← `(AiContentService.java:159)`。`AiContentIT` **2 红**：`exhaustedDailyBudgetIsAControlled429`（`expected: 429 but was: 200`）+ `generationWritesNothingButOneMetadataOnlyAuditRow`（`"…inputTokens=25; outputTokens=1;…" to contain "inputTokens=10"`） | **变红 ✓，且靶面比预测宽**（预测只点 U6；实测 3 单测 + 2 IT，其中 e2e 预算闸给出 `429 → 200`） |
| D10 | 前端失败态清空编辑区 | 失败后编辑区**从「保留用户已输入」变成「被清空」** | F1 | `AiGenerateButton.test.tsx` **1 红 5 绿**：F1，`expect(element).toHaveValue(用户手打的内容)`，`AiGenerateButton.test.tsx:85:19`，`Expected the element to have value: 用户手打的内容` / `Received:` （空） | **变红 ✓** —— F1 是这条性质在全仓的**唯一判据**（本组件没有任何持久化，编辑区里的字是内存里的唯一一份） |

---

### D1 的特别说明（**本批唯一实证「假绿通道」的一次**）

`plan.md:310` 要求 D1 跑完**同时**实测 I 系列。实测结果：**U1 恰好 1 红、I 系列 7 例全绿**。

这实证了「假绿通道 2」的存在形态：**IT 类通过 `properties`/`@DynamicPropertySource` 覆盖了 `crm.ai.*`**，
于是**Java 的 `@Value` 兜底值在这条通道上根本不被读到**。后果是——

> **`@Value` 兜底值这条性质，在全仓只有 U1 一条判据看着。**

两次独立读数一致（14:59 与 15:15，`Tests run: 8, Failures: 1`，断言文案与期望/实际值逐字相同）。
⇒ 「IT 全绿」**不能**用来反驳「兜底值被改坏了」，反之亦然。

### D2 的特别说明（**这条最重要**）

`plan.md:311` 预测 D2 该红 **U2、I1**。实测：**U2 红 ✓，I1 绿 ✗（预测被证伪）**。

**机理**：本项有**两道**配置门，且**各自有各自的判据**：

| 门 | 位置 | 它挡住的是 | 看着它的判据 |
|---|---|---|---|
| 结构性门 | `AiContentService.generate` 第一句 | 出站（`它之上没有任何网络调用`） | **U2**（`NoInteractionsWanted` 于 `clientFactory`） |
| 取数门 | `AiEmailDraftService.generate`（`:96-98`） | **连数据库往返都不做** | **I1**（HTTP 层的 409 顺序不变式） |

⇒ 只摘结构性那道门，I1 走的是 HTTP 路径，「取数门」还在最前面把它拦成 409，症状**完全不变**。
**这不是判据缺失**——两道门都**有**判据，只是判据**各看各的门**。故 D2 必须**拆成两刀**才能把两处都证伪。

**必须如实记录的两点**：

1. **D2-a 的红里有 14 条是「手段的红」**：摘掉判门后，测试夹具里那句 `when(aiStatus.isConfigured()).thenReturn(true)`
   变成**多余桩**，Mockito 的严格模式因此把 12 条用例判成 `UnnecessaryStubbingException`（另 2 条是各用例自带的桩）。
   **这 14 条不是目的的红**，不得计入「判据成立」。目的的红**只有 1 条**（U2）。
2. **D2-a 的 Failure/Error 边界不稳定**：同一破坏两次实测分别为 `Failures: 2, Errors: 13`（14:59）与
   `Failures: 1, Errors: 14`（15:16）——差异只在 `configurationWithoutClientIsStillRejected` 记成 Failure 还是
   `UnnecessaryStubbing` Error。**目的的红（U2）两次都稳定**，故本行按 15:16 那次落数，并把不稳定如实记入 §边界。

### D3 的特别说明（**预测被证伪 + 补刀证明**）

`plan.md:312` 把这次破坏称为「本批最重要的一次破坏」，并写明「**若不变红，说明 I3 没有判据看着**」。
实测：**D3 单刀下 I 系列 6 例全绿**。这个结果**不能**直接读成「I3 没有判据看着」——必须先分辨**是空操作还是判据没盖住**（§0 第 3 问）。分辨过程：

1. **排除空操作**：`AiContentIT` 是本批新写的、且 I3 用的是**跨 owner 的真实客户 id**；
2. **读代码找第二道出口**：`FollowUpService.page` 自带 063 安全加固的可见性复核——
   `if (customerId != null && !entityAccessService.canViewCustomer(userId, customerId)) throw new BusinessException(ErrorCode.FORBIDDEN);`
   ⇒ 即便本类用裸 Mapper 读到了别人家的客户行，随后那次 `followUpService.page(customer.getId(), …)` 仍会抛 403，**症状一模一样**；
3. **用 D3-b 证伪该论断**：把那次 follow-up 取数也跳过 ⇒ I3 立刻变红（`403 → 500`），
   且栈里出现 `NullPointerException … at com.crm.service.AiContentService.extractText(AiContentService.java:174)` —— **出站确实被走到了**。

**结论（如实记为一条缺口）**：`AiEmailDraftService` 里那道**客户可见性判定没有专属判据**。
它今天之所以有效，靠的是**下游同一条判据的第二道出口**（`FollowUpService.page`）兜着。
⇒ 一旦有人删掉那次 follow-up 取数（或给 P1 换一个不需要跟进的宿主），这道检查会**静默失效而无任何用例变红**。
`plan.md:312` 的预测原文**逐字保留**，本文件只追加读数。

### D4 的特别说明（**「声明」与「装配」是两件事**）

`plan.md:313` 预测 D4 该红 **I2、U8**。实测：**两种不同形态的破坏分别命中，但不是同一次同时红两条**：

- **D4-a（改装配）**：摘掉 `hidden.contains("status")` 守卫 ⇒ I2 红 ✓、**U8 绿**（白名单声明没变）；
- **D4-b（改声明）**：往 `P1_CUSTOMER_FIELDS` 加 `"phone"` ⇒ U8 红 ✓、**I2 绿**（出站内容没变）。

根因是一条**结构性事实**：`P1_CUSTOMER_FIELDS` / `P1_CONTACT_FIELDS` / `P1_OPPORTUNITY_FIELDS` /
`P1_FOLLOWUP_FIELDS` 四个常量在 main 代码里**零引用**（grep 实证）——它们**是声明，不是机制**；
真正控制出网的是 `Context` 记录的组件与 `renderEmailDraftUserPrompt` 的 `line(...)` 调用。
⇒ **U8 是一条「声明纯度」判据，没有任何行为层判据看着它**（如实记入 §边界）。

### D5 的特别说明（**判据的排列顺序本身就是被实测出来的缺陷**）

D5 的**预设判据**是 `verifyNoInteractions(messageService)`（「零出站」）。实测发现它**开不了口**：

> I5 里三句状态断言写在 `verifyNoInteractions` **之前**。出站一旦发生，状态码**先**从 400 变 500，
> 排在后面的 `verifyNoInteractions` 于是**永远不会被执行到**——报出来的是 `expected: 400 but was: 500`（一个状态位差异），
> **而不是**「出站发生了」这句安全结论。

**处置（已落入交付物，不还原）**：把三句状态断言挪到 `NoInteractionsWanted` 之后，让**安全面先说**。
重排后 D5-b 给出 `NoInteractionsWanted … -> at com.crm.service.AiContentService.generate(AiContentService.java:123)`，
`AiContentIT.java:247`；还原后该条复跑 `Tests run: 1, Failures: 0`。

⚠️ **这条必须写明**：重排是 D5 **实测的产物**，不是开工前的设计。它同时意味着
**同一个破坏在重排前后给出的「实测输出」字符串不同**（`expected: 400 but was: 500` → `NoInteractionsWanted`），
两者都是真读数，**不得只留其一**。

### D7 的特别说明（**被判据点名的那个 grep 并不存在**）

`plan.md:316` 写「SC-004 的 grep 判据」，`spec.md:159` 写「审计记录中不含提示词原文与客户数据（**以 grep 断言**）」。
但**本项没有任何工件登记过这条命令**——`SC-004` 的「以 grep 断言」在 4 处被引用
（`spec.md:159`、`plan.md:117`、`plan.md:316`、`checklists/requirements.md:14`），
而 `quickstart.md:70` 那唯一一条与「泄漏」有关的 grep 看着的是 `apiKey|api-key`，**属 FR-004 的密钥半句**。

**实证**：D7 破坏态下，把 `quickstart.md:70` 那条命令原样跑一遍，读数仍是 **0**
（`grep -rniE "apiKey|api-key" …/AiContentService.java | grep -viE "^\s*//|\*" | wc -l` → 0）
——**它对「提示词原文进了审计 detail」这件事完全无感**。一条 0 命中的否定判据单独存在时是**自证不了**的。

⇒ 本次**现定**一条并给出两态读数（`sed -n '/auditService\.record(/,/);/p' … | grep -cniE "prompt|instruction|content|customer|name|company"`，
还原态 **0** / 破坏态 **2**）。**这条命令是本次新引入的** ⇒ 必须在 C5 的落点表里补登记，
否则 D7 的判据下次仍然无从复算（同「一个数字住在好几个地方」的纪律）。

### D9 的特别说明（**靶面比预测宽，且宽的正是最有价值的那一条**）

`plan.md:318` 只点了 U6。实测红出 **3 单测 + 2 IT**。额外红出来的两条里，
`AiContentIT.exhaustedDailyBudgetIsAControlled429` 给出的是 **`expected: 429 but was: 200`**：
估算值（25）小于真实预置用量，闸**没被触发**，e2e 的「耗尽 ⇒ 受控 429」整条通路失效。
**这正是「单测里 store 是桩」所无法覆盖的接线层**（该用例自己的断言文案就写着这句）
⇒ 被证伪的不只是「token 数算错了」，而是**「闸真的会拦」这件事**。

---

## 边界（本批**不可证**或**未证**的项）

1. **`plan.md:311`（D2 ⇒ I1）与 `plan.md:312`（D3 ⇒ I3）两处预测均被实测证伪**。真实落点分别是
   **另一道门**（`AiEmailDraftService` 的取数门）与**下游同一判据的第二道出口**（`FollowUpService.page` 的 063 复核）。
   两处的旧值**逐字保留在 `plan.md` 里**，本文件只追加读数。
2. **`AiEmailDraftService` 自己的客户可见性判定没有专属判据**（D3 特别说明）——
   它由下游 `FollowUpService.page` 兜着。删掉那次 follow-up 取数即静默失效。
3. **`SC-004` 的「审计不含提示词原文与客户数据（以 grep 断言）」没有登记命令**（D7 特别说明）。
   本次现定的命令须在 C5 补登记。
4. **`P1_*_FIELDS` 四个常量在 main 代码里零引用**（D4 特别说明）⇒ 「白名单」是**声明**不是**机制**；
   D4-b 只由 U8 看着，**没有任何行为层判据**。
5. **D2-a 的 Failure/Error 边界不稳定**：同一破坏两次实测 `Failures: 2, Errors: 13` / `Failures: 1, Errors: 14`，
   差异只在 `configurationWithoutClientIsStillRejected` 的记法上；目的的红（U2）两次稳定。
   ⇒ **引用本批的 `Tests run:` 读数时必须连同「哪一次、几点几分」一起引**。
6. **本批的还原判据不是 `git diff --quiet`**（103 的口径）：工区里有有意未提交的改动与未跟踪的新文件，
   `git diff` 无法区分「破坏残留」与「本批交付物」。改用 `cp` 回写 + **md5 逐字相等**
   （六个被改文件：`AiContentService.java 889322ea…`、`AiEmailDraftService.java 89fa6cec…`、
   `AiPromptCatalog.java 910112c0…`、`AiStatus.java 8e609031…`、`AiTokenBudget.java 743c23a1…`、
   `AiGenerateButton.tsx 96a6503c…`，全部逐字相等）。
   该判据是「回写后与备份逐字相等」这个**事件**，**不是**这几个常量的永久值：**还原当时的读数**。
   任何后续对同一文件的改写都会让这串 md5 失效（本批 C4 提交前的 `spotless:apply` 就改写过
   `AiContentIT.java`；该文件不在上列六者之内，故上列 md5 仍然有效）。
7. **D1 只跑了 `AiStatusTest` 与两个 AI IT 类，没有跑全仓单测** ⇒ 「Java 兜底值被改」对**非 AI 用例**的影响**未观测**。
   （预期为零——只有一个 `AiStatus` bean；默认 `enabled=false` 时其余路径不读它。**这是推理，不是读数**。）
8. **D2 只做了「去掉判门」这一个方向**，没有做「结构性判门挪到出站之后」。
   「挪到出站之后」这一形态由 D5 在**另一个类**（`AiEmailDraftService`）上覆盖，两者**不可互替**。
9. **D7 无任何 IT/单测的「运行期」证据之外的静态判据**：`sed|grep` 那条命令是**文本层**的，
   它证明的是「这个文件的这个调用块里没有提示词标识符」，**不证明**运行时真正写进审计表的值是什么。
   运行期的那半句由 U6/I6 的实际断言字符串兜着（它们读到的是**真的 detail 串**）。
10. **「spotless 未清」这个开工期判断本身是假读数**（**本批订正一处先前的结论**）：C2/C3 段曾据 IDE 的
    spotless 诊断判定「`AiStatus` / `AiContentService` / `AiEmailDraftService` / `AiPromptCatalog` /
    `AiTokenBudget` 五个文件在盘上未格式化」。C4 提交前按判据**移走 `target/spotless-index`** 后复跑，
    真读数是：

    ```
    Spotless.Java is keeping 829 files clean - 1 were changed to be clean, 828 were already clean,
    0 were skipped because caching determined they were already clean
    ```

    ——**被改写的只有 1 个文件（`AiContentIT.java`，本批新写的用例），未清的那五个 main 文件里一个都没有**。
    复跑 `spotless:check`（索引已删）得 `0 needs changes to be clean, 829 were already clean, 0 were skipped`。
    ⇒ 先前的「五文件未清」**不是**格式问题，而是**把 IDE 的诊断当成了插件的读数**（两者的解析/缓存路径不同）。
    教训与前例同形：**「N were skipped because caching determined…」不能当「真解析过」**，
    而**IDE 的诊断同样不能当插件的读数**——判据必须来自那条要过门禁的命令本身。
    格式化后复跑：单测 **45 例全绿**（`AiStatusTest` 8 / `AiContentServiceTest` 15 / `AiPromptCatalogTest` 9 /
    `AiTokenBudgetTest` 11 / `AiPermissionGrantIT` 2）、IT **7 例全绿**，两次 `BUILD SUCCESS` ⇒ **行为未变**。

---

# E 系列（P2 客户 360 摘要 / C6）

> **格式**照 D 批的 §0 自问：每条先写清「它该改变哪条**可观察行为**」，答不出来就是空操作、不跑。
> **两处与 D 批不同、如实记录的口径**：
>
> 1. **备份目录 `/tmp/p2bak/`**（D 批是 `%TEMP%/crm104-dbackup`），还原判据是 **`cp` 回写 + `sha1sum` 逐字相等**
>    （D 批是 md5）。强度相同，换算法只是为了批间隔离。**仍旧禁用 `git checkout`**。
> 2. **破坏态下的 IT 不能用 `mvn verify` 跑**：`spotless-check` 绑在 `verify` 相位、排在 failsafe **之前**，
>    而破坏态的源码**本身就是格式违规**（E4 那个去掉守卫后缩进错乱的裸 `{}` 就是一例）⇒ `verify` 会以
>    `spotless-check` 失败中止，**IT 一次都没跑**，而那会是一次**「手段的红」**。本批 IT 一律用：
>
>    ```
>    mvn -B -o test-compile failsafe:integration-test failsafe:verify -Dit.test=AiContentIT
>    ```
>
>    直调 goal ⇒ 不经过 `verify` 相位 ⇒ 不触发 spotless；`test-compile` 负责编译，`failsafe:verify` 只汇总
>    结果并给出正确退出码。（单测用 `mvn -B -o test -Dtest=<类>`，本来就不经过 `verify`，无此问题。）
>
> **⚠️ 一处必然的顺序**：E4 / E5 / E6 的 IT 读数**是在 `AiContentIT.java` 被加固之后重做的**。加固改动了判据
> 文件 ⇒ 加固之前的红/绿读数**一律失效**（本仓纪律：以红/绿充当证据的留痕，在被测文件改动后必须重做）。
> E1 / E2 / E3 只涉及单元判据（`AiPromptCatalogTest.java` 未变），无需重做。

## E 系列

| # | 破坏 | 它该改变哪条可观察行为 | 点名的判据 | 实测读数 | 结论 |
|---|---|---|---|---|---|
| E1 | `P2_CUSTOMER_FIELDS` 加入 `"remark"`（`AiPromptCatalog.java`） | **声明层**白名单从「只含 name/company/status」变成「含一个 §5.3 明文排除的字段」 | **U8-a** `noSensitiveFieldInAnyWhitelist`（T054 点名的就是这一条） | `AiPromptCatalogTest` `Tests run: 17, Failures: 1`，红的**唯一**一条正是 U8-a（`Expecting […] not to contain […]`，形状与 D4-b 同） | **变红 ✓，且只红这一条** ⇒ T054 的判据成立 |
| E2 | `SummaryContext` 的分量 `company` → `companyFull`（**渲染器同一处同步改名**） | 上下文白名单的**分量名集合**发生变化 | **U9-b** `p2ContextComponentsAreExactlyTheWhitelist` | `Tests run: 17, Failures: 1`，红的**唯一**一条正是 U9-b | **变红 ✓**。⚠️ 破坏取的是**改名**不是**加字段**：加字段会让 5 处**位置构造点**编译失败 ⇒ 红在编译上（**形态②**）。改名改的是**同一个观测**（`getRecordComponents()` 给出的名字集合），且只需两处编辑。**推理（未跑）**：交换两个**同类型**分量的声明顺序**不会**被这条判据抓住（它用 `containsExactlyInAnyOrder`）——顺序不是本处的风险，故不补 |
| E3 | `renderSummaryUserPrompt` 里插一行 `line(sb, "手机号", "13800000000")` | 渲染出的**标签集合**从「全在白名单内」变成「多一个白名单外的字段」 | **U9-d** `noLabelOutsideTheP2WhitelistIsRendered` | `Tests run: 17, Failures: 1`，红的**唯一**一条正是 U9-d | **变红 ✓** ⇒ 「白名单常量」与「渲染函数」这两处表述是**同一条判据看着的**（D4-b 曾证明「只断声明」会假绿） |
| E4 | 摘掉 `renderSummaryUserPrompt` 里「交易与回款」整段的**存在性守卫**（`if (hasOrders \|\| !contractStatuses.isEmpty())` → 无条件输出段头） | 空客户的提示词**从「没有这一段」变成「有一个空段头」** | **U9-e** + **I7** | 单测 `Tests run: 17, Failures: 1`，红的是 U9-e `emptyContextHasNoSectionToFabricate`；IT `Tests run: 10, Failures: 1`，红的是 I7 `emptyCustomerGetsNoSectionToFabricate` | **变红 ✓，两层各一条**（本批唯一一条两层同时开口的破坏） |
| E5 | 摘掉 `AiCustomerSummaryService.buildContext` 的**「健康度素材」守卫**（`health != null && (…)` → `health != null`） | 零业务客户的提示词**从「不含健康度」变成「含 健康度：100（GREEN）」**——即**我们自己**喂给模型的编造 | **I7**（本批预测：**只有**它） | IT `Tests run: 10, Failures: 1`，红的是 I7 | **变红 ✓，且确实只有它**。⚠️ **这道守卫在单元层零判据，且是结构性的**：全仓引用 `AiCustomerSummaryService` 的测试文件**只有** `AiContentIT.java` 一个（本能力没给装配器写单测——它要 6 个协作者，写出来也只是桩的地图）⇒ 删掉这一行，`AiPromptCatalogTest` 17 例**必然**绿（它不引用本类）。正面例证「接线批次的回归只有行为层用例能抓」 |
| E6 | 摘掉 `AiCustomerSummaryService.generate` 里的 `entityAccessService.canViewCustomer` 判门（**只**摘这一道） | 不可见客户的请求**从「403 且零聚合」变成「403，但对方的 360 已被读进内存」** | **I9** | ⚠️ IT `Tests run: 10, Failures: 0, Errors: 0`，`BUILD SUCCESS` —— **预测被证伪，单刀全绿** | **形态①「判据没盖住」**（不是空操作，见 E6 特别说明）。加固 I9 后**复跑同一破坏** ⇒ `Tests run: 10, Failures: 1`，红的正是 I9，且落在**新断言行** `AiContentIT.java:456`（锚：`verify(customer360Service, never()).aggregate(anyLong())`） |

### E 批的前端段（T052 的两条新判据）

> **判据文件**：`frontend/src/components/AiCustomerSummaryButton.test.tsx`（6 例，F5-a…F5-f）。
> **跑法**：`npx vitest run src/components/AiCustomerSummaryButton.test.tsx`（**不要**给 `--maxWorkers`——
> vitest 1.6 在这个仓会直接以 `options.minThreads and options.maxThreads must not conflict` 崩掉、**0 用例**）。
> **还原判据**同前：`cp` 回写 + `sha1sum` 逐字相等（备份在 `/tmp/p2bak/`）。

| # | 破坏 | 它该改变哪条可观察行为 | 点名的判据 | 实测读数 | 结论 |
|---|---|---|---|---|---|
| E7 | `AiCustomerSummaryButton` 的出站换成 **P1 的函数**（`generateCustomerSummary({customerId})` → `generateEmailDraft({customerId})`，import 同步） | 摘要按钮打的是**另一个端点**：用户点"生成摘要"，服务端生成的是邮件草稿 | **F5-a**（"接错端点"这条缝隙是 P1 的 F1–F4 看不见的） | 6 例 **6 红 0 绿**。F5-a 红在 `waitFor(() => expect(mockSummary).toHaveBeenCalledWith({ customerId: 7 }))`，报 `expected "spy" to be called with arguments: [ { customerId: 7 } ]` / `Number of calls: 0` | **变红 ✓**。⚠️ **F5-f 一度假绿**（见下面的"补刀"） |
| E8-a | `AiCustomerSummaryButton` 的 `keyPrefix="aiSummary"` → `"aiDraft"`（**编译合法**：联合类型里有这个成员） | 摘要按钮上写的是**邮件草稿那组文案**（两个能力同屏） | **F5-b** | 6 例 **6 红 0 绿**，且**六条全红在同一行**——`AiCustomerSummaryButton.test.tsx:63` 的 `screen.getByRole('button', { name: /aiSummaryButton/ })`（`Unable to find an accessible element with the role "button" and name /aiSummaryButton/`） | **变红 ✓，但红在入口**：F5-b 自己的断言**根本没被执行到**。⇒ 如实记：本文件六条用例的**入口**都走 `openModal` 里那个由前缀派生的查找，所以"前缀错"这一种破坏**不做区分**地让全文件变红。它证明了前缀是被看着的，**没有**证明 F5-b 这张判据本身有分辨力——那一半由 E8-b 补 |
| E8-b | **补刀**：外壳 `AiTextGenerateButton` 的 catch 里把键组写死成 P1（`setErrorKey(k(…))` → `setErrorKey(\`${KEY}.aiDraft${…}\`)`） | 成功/截断路径照旧，**只有受控错误码那条路**给出别组的文案 | **F5-b / F5-c** | 两文件 12 例 **2 红 10 绿**：红的正是 **F5-b** 与 **F5-c**；F5-a/d/e/f 与 **P1 的六条全绿**（写死的是 P1 组，故 P1 侧观测不变） | **变红 ✓，且靶面精确到两条** ⇒ 「错误码 ⇒ 文案组」这条映射**只**被 F5-b / F5-c 看着；成功与截断路径的用例**不**看它 |

#### E7 的补刀（**一条"只断言通用文案"的用例，分不出"接错了"与"未知失败"**）

E7 跑完后 F5-f **全绿**：该条只断言"通用文案出现了、且不是任何一条受控码的文案"。而接错的
`generateEmailDraft` 是裸 `vi.fn()`，返回 `undefined` ⇒ `result.text` 抛 `TypeError` ⇒ 走 catch ⇒
拿不到码 ⇒ 落 `extractErrorMessage(…)` 的 fallback（非 axios 错误直接返回 fallback，`apiClient.ts:94`）
⇒ 渲染出的**正是** F5-f 要的那句。**同一个观测有两个生产者**，而其中一个是错的。

**处置（已采纳为交付物）**：给 F5-f 末尾补一句出站身份断言（`expect(mockSummary).toHaveBeenCalledTimes(1)`），
补后 E7 下 F5-f **也变红** ⇒ 读数由 `5 红 1 绿` 变为 **`6 红 0 绿`**。两次读数都记在这里：
**先记假绿、再记补刀**，因为"这条判据当初为什么会绿"正是本批要留下的信息。

#### E7 暴露的**判据排列缺陷**（与 P1 的 D5 同形，处置也照 D5-b）

F5-a 的**初版**把 `await waitFor(() => expect(box).toHaveValue('生成的客户摘要'))` 放在**最前**，
出站身份断言排在它后面。E7 下红的是**第一行**（`Unable to find` / 值为空），而"接错了端点"这件事只有
`toHaveBeenCalledWith` 能说出它的名字——**那条断言当时没有被执行到**。这与 D5（"判门在出站之前"被排在
状态断言之后）是同一形态：**判据的排列顺序本身就是被实测出来的缺陷**。

**处置（已采纳为交付物）**：把 F5-a 重排为——先 `await waitFor(() => expect(mockSummary).toHaveBeenCalledWith({ customerId: 7 }))`
（这条**既是等待、又是点名缺陷的那一条**：接错函数 ⇒ `Number of calls: 0`；请求体多/少字段 ⇒ 逐字相等失败并打出实际入参），
再 `toHaveBeenCalledTimes(1)`、再反向的 `expect(mockDraft).not.toHaveBeenCalled()`、最后才是编辑区的值。
E7 的**上表读数就是重排之后**的（红在 `toHaveBeenCalledWith` 那一行）。

### E6 的特别说明（**本批最重要的一条：绿的那次不是「无风险」，是「没有判据」**）

破坏只摘了**本端点**那一行，IT 却 10/10 全绿。逐层读出原因——**两处都读源码确认，不是猜**：

1. `CustomerService.require(customerId)`（`CustomerService.java:450-456`）**只做存在性判定**（`null` → `CUSTOMER_NOT_FOUND`），
   **不含**任何可见性判定；可见性在同文件的 `private checkViewPermission`（`:412`）里，而 **`require` 不调它**。
2. ⇒ 摘掉本端点的门之后，流程照旧走到 `buildContext`：`customer360Service.aggregate(customerId)` 与
   `tagService.customerTags(customerId)` **都执行了**；直到 `followUpService.page(...)`
   （`FollowUpService.java:66-72`）里**它自己**那道行级校验
   （`entityAccessService.canViewCustomer(userId, customerId)` → `BusinessException(FORBIDDEN)`）才把请求拦下。

所以 I9 原来的三行断言（`status=403` / `code=FORBIDDEN` / `verifyNoInteractions(messageService)`）**全绿**：
`order` 是**别人的门**挡下的，抛的是**同一个** `ErrorCode.FORBIDDEN`（连错误码都分不出来），而 `messageService`
是**出站**拦截点——它没被碰过，恰恰因为 403 发生在它之前。

**两者的差别是实质的**：被打断的**位置**不同。「本端点的门」在**任何读取之前**返回；「下游的门」是在
**另一个 owner 的 360 数据（订单 / 金额 / 合同 / 工单 / 状态）已经被读进内存并送进渲染函数**之后才返回。
客户数据没有出网（`messageService` 零调用是真的），但「**不可见 ⇒ 一行都不读**」这个不变式**已经破了**，
而它**没有任何判据看着**。

⇒ 这**不是**「E6 是空操作」：破坏确实改变了可观察行为（聚合发生了，且是真的跨 owner 读），只是**没有判据
落在那个可观察量上**。两种绿的处置完全不同——空操作要去掉那次破坏，**没有判据**要补判据。

**加固（已采纳为交付物）**：给 `AiContentIT` 加 `@SpyBean private Customer360Service customer360Service;`，
并在 I9 的 403 断言之后、**归 owner 的那次调用之前**加：

```java
verify(customer360Service, never()).aggregate(anyLong());
```

- 「**之前**」是判据的一部分：归 owner 的那次调用会**真的**聚合，这条断言放在它后面必假。
- `aggregate` 零调用是本类**唯一**能分辨这两条路径的观测点（同一错误码、同一状态码、同一「零出站」，
  只有「读没读」不同）。
- 加固后复跑 E6 ⇒ 红在 `AiContentIT.java:456`（锚即上面那一行）；还原 ⇒ 10/10 绿、`BUILD SUCCESS`。
- 加固**没有**打扰其余 9 例（`@SpyBean` 委托真实实例，行为不变）：加固后先拿到 10/10 绿，再被 E6 打红，
  再还原回 10/10 绿——**三次独立读数**。

⚠️ **同一形态在 P1 已经出现过一次，且当时没有被判据抓住**：D3「取数改为直查裸 Mapper」单刀同样**全绿**，
D3-b 的「补刀」（再跳过 `followUpService.page` 那次取数）才证明「403 落在下游的第二道出口上」——也就是说
**P1 的 I3 今天仍然只能证明「有 403」，不能证明「这道 403 是本端点自己那道门给的」**。本批的加固手法
（spy 住「门之后、出站之前」的那次读取）在 I3 上同样适用。**未做**：改 `AiContentIT.java` 并重跑 P1 会让
C4/C5 已登记的读数（`Tests run: 6` / `7` 等）全部失效，须单独一个批次，故本批只在文档里点名，**不去动它**。

## 边界（E 批**不可证**或**未证**的项）

1. **E5 的「单元层零判据」是结构性事实，不是本轮实测**：判据是「全仓引用 `AiCustomerSummaryService` 的测试
   文件只有 `AiContentIT.java` 一个」（`grep -rln` 读数）；**没有**在破坏态下单跑 `AiPromptCatalogTest`——
   它不引用该类，跑它只会得到一个平凡的绿。
2. **E2 的「同类型分量换序不被抓」是推理，未跑**（见上表 E2 行）。
3. **没有做「白名单加字段 ⇒ 行为层也红」的双层破坏**：E1 只打在**声明层**（U8-a）。渲染侧的对应破坏由
   E3 覆盖，两者**不可互替**——E1 红了**不**说明「送出去的字段也是被看着的」，E3 红了也**不**说明白名单
   常量本身有判据。
4. **E 批没有覆盖 P2 的截断路径**（`P2_FOLLOWUP_EXCERPT_CHARS` / `（本条已节选）`）：本批**未**为 P2 的截断
   写破坏。P1 的同名性质由 D6 覆盖，**不可互替**（P2 走的是 `excerpt()` 的另一个调用点）。**未证**。
5. **`AiContentController.java` 未被任何一次破坏触及**：它的两道注解（`@RequirePermission` / `@RateLimit`）
   在 P1 批次里由 `AiPermissionGrantIT` 与限流用例看着，P2 端点**复用同样的取值**，但本批**没有**为 P2 单跑
   一次「限流 / 权限在 P2 端点上真的生效」的判据套用例。⇒ 「两个端点共用同一套门」这句话目前是**读注解
   得到的**，不是本批的读数。**未证**。
6. **还原判据是「回写后与备份逐字相等」这个事件，不是下列 sha1 的永久值**：任何后续改写都会让它们失效。
   本批的还原读数（sha1，五个文件）：
   - `AiPromptCatalog.java` `d6a86ee316baaf60d18c10bb1561854f5cff5289`
   - `AiCustomerSummaryService.java` `7b879d1ea0fa34d1e1804d709098da8b9d8d11b1`
   - `AiContentIT.java` `8bc7fc398dcfe3ea2e0445a04d527d44b8e1485f`（**加固后的新基线**；
     加固前是 `dbb2ab061abf2e7313e5f62d184fd11e4cdc592a`）
   - `AiPromptCatalogTest.java` `af92783a8167dce1adf3ce3ce3e52ce95330d4b3`
   - `AiContentController.java` `e524dddc300bdcf6b5d1258ca77328874e8bbaad`（本批**未**破坏它，一并记下
     以免与 E4/E5/E6 的还原混淆）
7. **还原走的是 `cp` 回写 + sha1 相等**，**未使用** `git checkout`（它会吞掉同文件里**有意未提交**的改动）。
8. **加粗的操作教训（供复算）**：破坏态下**不要**用 `mvn verify` 跑 IT——`spotless-check` 会先中止构建，
   让人把「格式的红」读成「判据的红」。用上面那条 `test-compile failsafe:integration-test failsafe:verify`。
9. **收尾读数（还原后、本批最后一次运行）**：单测 `AiPromptCatalogTest` **17/17 绿**、
   IT `AiContentIT` **10/10 绿**，两次 `BUILD SUCCESS`。此读数取自**还原后的树**，非破坏态。
10. **前端段的还原读数（sha1，两个被破坏的文件；判据是"回写后逐字相等"这个事件，不是永久值）**：
    - `frontend/src/components/AiCustomerSummaryButton.tsx` `54a34a92189738aa3a759f6f7d5cce2be5f9740d`
    - `frontend/src/components/AiTextGenerateButton.tsx` `72ad95613ade23374d4629960185e7b74c2323a3`
    - 一并记下**本批未破坏**的判据文件，以免后续与还原混淆：
      `AiCustomerSummaryButton.test.tsx` `d809f62c48195c42bb7a6a1c55febe6359e1a660`（**含上述两次补刀后的新基线**）。
11. **E8-a「不可区分地打红全文件」这件事本身没有被修**：本条**未**把 `openModal` 改成与前缀无关的入口
    （那样它就得靠别的东西定位按钮，而那个东西同样会引入新的耦合）。⇒ 该文件对「前缀写错」的分辨力，
    目前由 **E8-b** 那条路径提供，**不**由入口查找提供。如实记为**未证**的部分。
12. **前端段的门禁读数**（还原后、E8-b 回写之后重跑，全部取自同一次工作区）：`pnpm lint` / `pnpm typecheck` /
    `pnpm i18n:check`（zh-CN **3002** 键 / en **3002** 键）/ `pnpm ui:check` / `pnpm zh:check` **五项全过**；
    定向用例 `AiCustomerSummaryButton` **6/6** + `AiGenerateButton` **6/6** + `CustomerDetailPage.perm` **9/9** +
    `CustomerDetailPage.render` **2/2** = **23/23 绿**。

---

## E 批（C7：P3 跟进润色 / 总结，含本批前端）

> 本节是 **C7** 的破坏读数，编号自 **E9** 起接在 C6（E1–E8-b）之后。两批的编号不重叠，各自的"边界"节也各自独立——**上一节（`## 边界（E 批…）`）只覆盖 E1–E8-b**，本节末尾另有 C7 自己的边界。
>
> **E9 / E10 打后端**（`AiPromptCatalog.renderFollowUpPolishUserPrompt`，即 T064 点名的那一处），**E11–E15 打本批前端**（P3 组件与共用外壳），**E16–E18 打前端宿主接线**（`FollowUpTimeline` 里那颗按钮——G 系列全部看不见这一层）。
>
> ⚠️ **读数一栏照本仓惯例写"哪条用例变红 + 红在什么可观测量上"**，E9 / E10 未附 `Tests run: N` 总行——理由见边界第 1 条。

| # | 破坏 | 它该改变哪条可观察行为 | 点名的判据 | 实测读数 | 结论 |
|---|---|---|---|---|---|
| E9 | `renderFollowUpPolishUserPrompt` 里把 `ctx.content()` 换成 **P1 式的节选**（`excerpt(content, 200).text()`） | 送进提示词的跟进原文从「整段」变成「前 200 字符 + 已节选标记」 | **U10-e** `contentIsSentVerbatimWithoutExcerpting` | 单测 **1 红**（26 例中），红的**唯一**一条正是 U10-e：`AssertionError`「原文必须整段出现」，实际提示词可见地被截在 200 字符处 | **变红 ✓，且只红这一条** ⇒ 「逐字、不节选」这条判据有分辨力 |
| E10 | 同一渲染器里把日期抹掉（`content.replaceAll("\\d+\\s*月\\s*\\d+\\s*日", "")`） | 提示词里的「3 月 5 日」消失 | **U10-e** + **I11** `followUpContentReachesTheModelVerbatim` | 单测 **1 红**（U10-e）；IT **1 红**（I11），且捕获到的提示词里 `3 月 5 日` **出现 0 次** | **变红 ✓，两层各一条** ⇒ **T064（「让润色丢掉日期 ⇒ 该红」）兑现**；这是 US3-AS1「关键要素逐项保留」在**日期**这一项上的可执行判据 |
| E11 | `AiFollowUpPolishButton` 的请求体恒带 `customerId`（`customerId === undefined ? … : …` → `{content, mode, customerId: customerId ?? 0}`） | 宿主**没给**客户时（线索页），请求体从「没有 `customerId` 这个键」变成「声明了 `customerId: 0`」 | **G-b** | 10 例 **1 红 9 绿**，红的正是 G-b（`expected "spy" to be called with arguments: [ { …(2) } ]` / `Number of calls: 1`） | **变红 ✓，且只红这一条**。⚠️ 破坏取 `?? 0` 而不是 `?? null`：`customerId?: number` 不接受 `null`，那会红在**编译**上（形态②）；`0` 是类型合法的"声明了一个不存在的归属" |
| E12 | `AiFollowUpPolishButton` 忽略 `mode` 状态（两处字面量都写死 `DEFAULT_MODE`） | 切成「总结」后出站仍是 `POLISH` | **G-c** | 10 例 **1 红 9 绿**，红的正是 G-c | **变红 ✓，且只红这一条** ⇒ 「模式是请求体的一部分、随选择器变」有判据 |
| E13 | `AiFollowUpPolishButton` 的 `disabled={content.trim() === ''}` → `disabled={false}` | 空原文时按钮从「禁用、点了白点」变成「可点、能打开模态框」 | **G-d**（预测：只有它） | 10 例 **1 红 9 绿**，红的正是 G-d | **变红 ✓，且确实只有它**。⚠️ **正对照 G-e 抓不到这条破坏**（"恒可用"正是 G-e 期望的状态）——G-e 的存在是防**反方向**的错（"恒禁用"），两条不可互替 |
| E14 | 外壳 `AiTextGenerateButton` 的 `ERROR_SUFFIX.BAD_REQUEST` 从 `'InvalidInput'` 改成 `'Failed'` | 400 的文案从「输入不合法、重试无用」变成「生成失败，请稍后重试」 | **G-f**（预测：只有它） | 三文件 22 例 **1 红 21 绿**：红的正是 G-f；`AiGenerateButton`（6 例）与 `AiCustomerSummaryButton`（6 例）**全绿** | **变红 ✓，且只红这一条** ⇒ 该映射此前**零判据**（P1/P2 的用例从没跑过 `BAD_REQUEST`），本批的 G-f 是它的第一个判据 |
| E15 | **作用域破坏**：P3 组件的 `KEY` 改 `'pages.customer.detail'`，**并把 22 条 `aiPolish*` 键原样复制进两份语言文件的 `pages.customer.detail` 组** | 线索页上的 P3 按钮从「读 `pages.followUpTimeline.*`」变成「读客户页的词条」——而**两份语言文件都有那些键** | G-c / G-f / G-g / G-j（预测四条） | 10 例 **9 红 1 绿**（G-d 绿）；⚠️ **`npm run i18n:check` 绿：zh-CN **3048** 键 / en **3048** 键** | **变红 ✓，但靶面比预测宽**：除预测的四条外，G-a/G-b/G-e/G-h/G-i 也红了——因为**测试文件里的 `KEY` 字面量**（`pages.followUpTimeline.aiPolish*`）同样参与断言（按钮名、`Placeholder`、`Truncated`…）。⇒ 如实记：**作用域被 9 条用例看着，红点是"字面量不匹配"，不是 `t` 桩抛缺键**（复制的键让桩不再报错）。**最有价值的那一半是 `i18n:check` 的绿**：它**实证**了"用错作用域在那套门禁下是隐形的"这句话 |
| E16 | `FollowUpTimeline` 的 `canGenerateAiText = hasPerm(PERMS.aiGenerate, user)` → `= true` | 不持码的用户从「看不到这颗按钮」变成「看得到」 | **W-a①** | 3 例 **1 红 2 绿**（此时文件尚无 W-d），红的正是 W-a① | **变红 ✓，且只红这一条** ⇒ 权限门有判据。⚠️ 该破坏同时让 `hasPerm` / `PERMS` 两个 import 变成未使用（lint 会另外报），但**红的不是它**——断言红在先 |
| E17-a | `FollowUpTimeline` 把 `content={contentValue ?? ''}`（`Form.useWatch` 的**当前值**）换成 `content={editing?.content ?? ''}`（**打开时的快照**） | 用户改过原文之后，送出去的仍是**打开编辑时**那一份 | **W-d**（预测） | ⚠️ **预测被证伪一半**：3 例 **1 红 2 绿**，红的是 **W-b/W-c** 且红在 `toBeEnabled()`（新建路径下 `editing` 为 null ⇒ 原文恒空 ⇒ 按钮恒禁用，**W-d 当时还不存在**） | **形态③「红在前提、不在结论」**：这一刀确实红了，但它红在"按钮压根不可用"，**没有**证明"送出去的是旧值"这件事被看着 ⇒ 处置见 E17-b |
| E17-b | **补判据后复跑同一破坏**：新增 **W-d**（编辑既有记录、改过原文之后 ⇒ 送出的必须是改过的那段） | 同上 | **W-d** | 4 例 **2 红 2 绿**：W-d 红在**出站内容的逐字相等**（`expected "spy" to be called with arguments: [ { …(3) } ]`），W-b/W-c 仍红在 `toBeEnabled()` | **变红 ✓，且这回红在点名缺陷的那一行** ⇒ 「实时读表单 vs 存快照」这条缝隙现在**只**由 W-d 看着。**两次读数都记**（先记"红错地方"，再记"补刀后红对地方"），因为"这条破坏当初为什么没被说中"正是本批要留下的信息（同 E7 的形状） |
| E18 | `FollowUpTimeline` 摘掉 `onGenerated={(text) => form.setFieldValue('content', text)}`（**只**摘这一条接线） | 生成成功后，模型返回的文本不再写进跟进表单的 `content` 字段 | **W-b/W-c**（预测：只有它） | 4 例 **1 红 3 绿**，红的正是 W-b/W-c，且落在 `expect(element).toHaveValue('整理后的跟进记录')` | **变红 ✓，且只红这一条** ⇒ 「写回」有判据；而同一条用例里的"**不落库**"两行（`expect(createFollowUp).not.toHaveBeenCalled()` / `updateFollowUp` 同）**在两种实现下都绿**——它们是**反方向**的护栏（防"顺手替你保存了"），本批**没有**为它们做破坏（见边界第 7 条） |

#### E15 的特别说明（**"两份语言文件都有那个键"是这套门禁的结构性盲区**）

`check-i18n.mjs` 只比对 **zh-CN ↔ en 的扁平键集合**（加空值检查），**不扫源码用法**；测试里的 `t` 桩只校验
**zh-CN 一侧**的键存在性。⇒ 把同一组键复制到**另一个作用域**下，两边都"存在"，三条门禁（`i18n:check`、
`t` 桩、`lint`）**没有一条会响**。E15 的实测就是这句话的证据：`3048 == 3048`、`✓ 语言资源一致`。

**处置（已采纳为交付物）**：G-f / G-g 断言的是**完整键名字面量**（含作用域），G-j 则反向断言 P3 的三条
`Mode*` 键**不得**出现在 `pages.customer.detail` 组里。⚠️ 仍如实记为**部分覆盖**：如果**只**复制、不把 `KEY`
改错（即"多了一份没人用的词条"），只有 G-j 的反向断言会响——而那条断言只点名了 3 条 `Mode*` 键，
**不覆盖其余 19 条**。

### 边界（C7 **不可证**或**未证**的项）

1. **E9 / E10 未附 `Tests run: N`**：这两条跑在 `-Dtest` / `-Dit.test` 的**定向运行**里，且**早于**本批的交付
   `verify`。按本仓纪律，**交付 `verify` 之后不再为同一读数单跑 Maven**——`jacoco.exec` 的字节 / mtime 与覆盖率
   正是"那一次运行"的属性，门禁后再单跑测试类会把它冲掉（**自伤**）。⇒ 这两条记的是**哪条用例红、红在什么
   可观测量上**（与总行数无关），总行数由 C7 交付的整次 `verify` 给出（`tasks.md` 的 C7 块）。
2. **E11–E18 的还原判据是"回写后与备份逐字相等"这个事件，不是下列 sha1 的永久值**。本批的还原读数（sha1，五个被破坏的文件）：
   - `backend/.../AiPromptCatalog.java` `793641edb362c702da17651ec6512a7d80ee436a`（E9/E10；与 C6 批记的是同一个值——本批还原后未再改它）
   - `frontend/src/components/AiFollowUpPolishButton.tsx` `115fae3be7fd049878880b95292ca38a747c4133`（E11/E12/E13/E15）
   - `frontend/src/components/AiTextGenerateButton.tsx` `e142d98cffe4e945c2ae7ec91854f686b5b1ece3`（E14）
   - `frontend/src/components/FollowUpTimeline.tsx` `5a6d6017a98b4faa660c381eb7483b3d12776813`（E16/E17/E18）
   - `frontend/src/i18n/zh-CN.ts` `7aef0224e5f4398d4b77d1eae4c83553e951815c`、`frontend/src/i18n/en.ts` `3c601c12b03c2bd20b870a8ad96457eafbd0cc0b`（仅 E15 的复制键）
   - 一并记下**本批未破坏**的判据文件，以免与还原混淆：`AiFollowUpPolishButton.test.tsx` `b51c7300d12620786210d7203ff0a2acb8f98c00`、`FollowUpTimeline.aiPolish.test.tsx` `656b4e71ab695d66fdfb3cdc55d9181a5882009c`、`AiContentIT.java` `ce56dc499cd6fd3fcebfc0594974accd787dce32`、`AiPromptCatalogTest.java` `88f63853108d3c8289b1979c5525c06f87fb72a9`、`AiFollowUpPolishService.java` `25d96c0cca4ea258171b9ca036966028860c765d`。
3. **E15 的复制键是"一次性的破坏态"，不是交付物**：两份语言文件在还原后**不含** `pages.customer.detail.aiPolish*`（还原后用 `aiPolishButton:` 的出现次数自证：每份文件 **1** 次）。
4. **还原走的是 `cp` 回写 + sha1 相等**，**未使用** `git checkout`（同 E 批第 7 条）。⚠️ E15 的键复制是**脚本按字节**做的（该文件是 CRLF；脚本原地重写会把整文件的换行翻成 LF ⇒ `spotless` / diff 全文件变红），故脚本以 `rb`/`wb` 处理并在还原后自证 `bare-LF == 0`。
5. **前端段的门禁读数**（还原后、E18 回写之后重跑，全部取自同一次工作区）：`npx tsc --noEmit` **退出码 0**、
   `npx eslint .` **退出码 0**、`npm run i18n:check`（zh-CN **3026** 键 / en **3026** 键）、`npm run ui:check`（白名单外新增 0）、
   `npm run zh:check`（未登记命中 0）、`npm run menu:check`（56 项）、`npm run perms:check`（69 个权限码）**七项全过**；
   定向用例 **26/26 绿**（`FollowUpTimeline.aiPolish` 4 + `AiFollowUpPolishButton` 10 + `AiGenerateButton` 6 + `AiCustomerSummaryButton` 6）。
6. **G-d 与 G-e 构成的是一对**（"空 ⇒ 禁用"与"非空 ⇒ 可用"），**任一条单独都不够**：只有 G-d，"恒禁用"也是绿的；
   只有 G-e，"恒可用"也是绿的。E13 实测的是前一半。
7. **"写回但不落库"里的"不落库"那一半本批未做破坏**：`expect(createFollowUp).not.toHaveBeenCalled()` 与
   `expect(updateFollowUp).not.toHaveBeenCalled()` 是**反方向**的护栏（防的是"顺手替你保存了"），而
   E18 的破坏（摘掉写回）**不会**让它们变红——**未证**的是"如果有人把写回改成'写回 + 保存'，这两行会响"。
   理由：那需要构造一个"多调一次 `createFollowUp`"的破坏态，而它在本批的实现里**没有对应的单点改动**
   （保存走的是弹窗的 `onSave`，与 `onGenerated` 不相邻），硬造会变成一次"改两处"的破坏（形态②风险）。
8. **`FollowUpTimeline` 的既存行为（与 104 无关）**：该组件**只**在 `openCreate` 与保存后调 `load()`，**挂载路径上没有 `useEffect`**
   ⇒ 两个宿主页（客户详情 / 线索详情）首屏的跟进列表是**空的**，要到用户点开"添加跟进"才拉到数据。⚠️ 这不是本批引入的、
   也**不在本批范围内**（无判据看着它；W-d 的用例因此要先点一次"添加跟进"才能拿到"编辑"链接）。**如实记为既存缺陷，未修**。
