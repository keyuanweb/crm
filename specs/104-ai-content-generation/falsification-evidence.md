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
