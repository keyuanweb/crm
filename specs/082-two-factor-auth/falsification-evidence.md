# 定向破坏留痕：082-two-factor-auth

**用途**：本项目不把「新增了 N 条用例、全绿」当证据。**每一条声称是护栏的断言，都必须被观测到
在定向破坏下转红**，破坏逐字节还原（sha1 复现基准值、`git diff` 为空），且**破坏期间不提交**。

**纪律**（照 095 的先例）：

- 逐条做、逐条还原，不批量做。
- 记录**逐字的失败信息**，不转述。
- 还原用 **sha1 校验**，不靠肉眼。
- 若某条破坏**没让断言转红**，那就是**断言不够**或**破坏了别的东西** —— 两种都要如实写下来。

⚠️ **本文件的覆盖边界（写在前面的免责）**：MockMvc 是单线程的，
**任何集成测试都区分不出 `getAndDelete` 与 `get`+`delete`**
（两者在单线程下可观测行为相同）。故**原子性只能靠调用形状单测**（surefire，`MfaStateStoreTest`）
钉住，集成测试钉的是**可观测行为**。这个分工在下面每一行里都注明。

---

## §A `User.last2faVerifiedAt` 的列名映射（第 1 步，2026-09-16）

**被守护的断言**：`TwoFactorSchemaMappingIT.userTwoFactorColumnsRoundTrip` 的
`assertEquals(verifiedAt, reloaded.getLast2faVerifiedAt(), ...)`。

**为什么需要单独守护**：`SchemaParityIT` 自己写明它**不覆盖列级漂移**（只核表名）。
而属性名 → 列名的推导是 MyBatis-Plus 按 camelToUnderline 做的，**只在「大写字母」前插下划线**
⇒ `last2faVerifiedAt` 推出的是 `last2fa_verified_at`（"last" 与 "2fa" 之间**没有**下划线），
而真实列名是 `last_2fa_verified_at`。**编译期无任何提示，只在运行时炸**。
段首为数字的列名在本仓仅此一处，没有先例可参照 —— 所以这个映射是否正确**不能靠读代码判断**。

**基准**：`backend/src/main/java/com/crm/entity/User.java` 的 sha1
```
e67f663e5dbed90a76959f6c650e7aa5efc3d60d
```

**破坏**：删掉第 57 行上方那一条 `@TableField("last_2fa_verified_at")`，其余一字不动。

**观测到的失败（逐字）**：
```
[ERROR] Tests run: 3, Failures: 0, Errors: 1, Skipped: 0  <<< FAILURE!
     -- in com.crm.integration.TwoFactorSchemaMappingIT
### Error updating database.  Cause: org.h2.jdbc.JdbcSQLSyntaxErrorException:
    Column "last2fa_verified_at" not found; SQL statement:
[INFO] BUILD FAILURE
```

**结论**：破坏被观测到转红，且**红得与预测逐字一致** ——
失败信息里的列名正是 `last2fa_verified_at`（无下划线），即 MyBatis-Plus 推导出的错列名。
⇒ 两件事同时被证实：① 该映射**确实是承重的**（不是一段防患于未然的冗余注释）；
② 本用例**确实覆盖**它（换成「本该正确」的写法会红）。

**还原**：重新加回该注解 → `sha1sum -c` 输出 `OK`，基准值逐字节复现。

⚠️ **过程中的一次真实事故，如实记**：还原是用 Python 读写做的，而
`io.open(p, encoding='utf-8')` 的**通用换行**把 CRLF 折成了 LF，写完 sha1 立刻不符。
定位方式是用 `io.open(p,'rb')` 数 `\r\n` 与裸 `\n`（得 0 / 57），再用
`d.replace(b'\n', b'\r\n')` 转回。**这条记下来的理由是：它差一点被当成「还原成功」**——
查 sha1 时若只看「文件内容看起来对」，就会把一个行尾被改写的文件提交进去，
而这个改动**不会让任何门禁转红**（spotless 只覆盖 `<java>`，且它恰恰要求 CRLF，
所以它反而会「帮忙」把文件格式化回去，掩盖了这次改写）。
**教训**：用脚本改文件时，读要带 `newline=''` 或二进制，写要显式指定行尾。

**同行尾的实测事实**（顺带核实，供后续批次参照）：
本仓 `backend/src/main/java/**` 的 `.java` 是 **CRLF**；
而 `backend/src/main/resources/db/migration/*.sql` 是 **LF**（V82~V85 实测皆然）。
`V89__two_factor_auth.sql` 按既有迁移的 **LF** 落库 —— 与邻居一致，不是疏漏。

---

## §B FR-M14「非 2FA 响应逐字节不变」的两条守卫（第 2 步，2026-09-16）

**被守护的断言**（两条，缺一不可）：
`AuthResponseSerializationTest.normalLoginSerializesByteIdentically`（surefire，自建 ObjectMapper）
与 `LoginResponseShapeIT.normalLoginResponseShapeIsUnchanged`（failsafe，真上下文 + Spring 装配的 ObjectMapper）。

**为什么是两条而不是一条**：本批给 `AuthResponse` 加了三个可空字段，FR-M14 靠
`spring.jackson.default-property-inclusion: non_null`（`application.yml`）让 `null` 不出现在 JSON 里才成立。
这里有两个**独立**的失效路径，各自的可见面不同：

| 失效路径 | 谁看得见 |
|---|---|
| 给新字段加默认值 / 写成 `Boolean.FALSE` / 新增第 4 个非空字段 | 两条都看得见（见破坏 A） |
| `application.yml` 那条 `non_null` 被改成 `always` | **只有 IT 看得见**（见破坏 B） |

破坏 B 是**专门用来证伪"单测够用"这个想法**的 —— 它同时给出「单测仍绿」与「IT 转红」两个观测，
从而证明两条断言确实互补，而不是同一条断言写了两遍。

**基准**（`sha1`）：
```
db4f7416880ba0960a28de5d461b639892c0a3f7  backend/src/main/java/com/crm/dto/auth/AuthResponse.java
9592b53d342e26e9e325116be6ace09aecca5406  backend/src/main/resources/application.yml
```

### 破坏 A：给 `mfaRequired` 加 Lombok 默认值

**破坏**：`private Boolean mfaRequired;` → `private Boolean mfaRequired = Boolean.FALSE;`（其余一字不动）。

**观测（逐字）**：

`surefire` —— 2 of 3 转红：
```
Tests run: 3, Failures: 2, Errors: 0, Skipped: 0 <<< FAILURE! -- in com.crm.dto.auth.AuthResponseSerializationTest
org.opentest4j.AssertionFailedError: 登录响应形状变了 …… ==> expected: <{"accessToken":"access-abc","refreshToken":
"refresh-xyz","user":{…}}> but was: <{"accessToken":"access-abc","refreshToken":"refresh-xyz","user":{…},
"mfaRequired":false}>
com.crm.dto.auth.AuthResponseSerializationTest.newFieldsAreNullOnNormalPath ……
AssertionFailedError: mfaRequired 必须是 null —— 写成 Boolean.FALSE 会多出一个键 ==> expected: <null> but was: <false>
```

`failsafe` —— 2 of 2 转红：
```
Tests run: 2, Failures: 2, Errors: 0, Skipped: 0 <<< FAILURE! -- in com.crm.integration.LoginResponseShapeIT
AssertionFailedError: 登录响应的 data 键集/顺序变了 …… ==> expected: <[accessToken, refreshToken, user]>
but was: <[accessToken, refreshToken, user, mfaRequired]>
```

**还原**：改回无默认值 → `sha1sum` 输出与基准一致（逐字节复现）。

### 破坏 B：把 yml 的 `non_null` 改成 `always`（**本条是"证伪单测够用"的那一条**）

**破坏**：`default-property-inclusion: non_null` → `always`（其余一字不动）。`AuthResponse.java` **不动**。

**观测（逐字）**：

`surefire` —— **仍然全绿**，这是**预期**结果，也正是本条破坏的价值所在：
```
Tests run: 3, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```
⇒ 实测证实：**单测看不见 yml**。它的 ObjectMapper 是自建的（只镜像了那一条配置），
所以"改 yml"这件事在它眼里从未发生。若只有它一条守卫，这次破坏会**全仓绿**地溜过去。

`failsafe` —— 2 of 2 转红。**首次运行时只有 1 条断言报出来**（逐字）：
```
Tests run: 2, Failures: 2, Errors: 0, Skipped: 0 <<< FAILURE! -- in com.crm.integration.LoginResponseShapeIT
AssertionFailedError: 成功响应的顶层键应恰为 success/data …… ==> expected: <[data, success]>
but was: <[success, data, error]>
```

⚠️ **如实记一处与预期的偏差，以及它的处置**：上面这条转红的是**信封**断言
（`ApiResponse` 顶层多出 `"error":null`），**不是** `data` 键集那条。原因是 `assertEquals` 在第一条
失败处即中止，`data` 那条**根本没跑到**。⇒ 首次破坏**只证明了**"IT 能看见 yml 的变化"，
**没有**单独证明 `data` 键集断言对 `always` 敏感；当时那一点只是**推断**（`always` 下三个 `null`
字段会出现，键集必然变）。

**处置 —— 把推断变成观测**：把该用例的 5 条断言改用 JUnit 的 `assertAll` 包起来
（`all` 会跑完全部再汇总），**重做同一次破坏**。第二次的逐字结果：
```
org.opentest4j.MultipleFailuresError: 登录响应形状（未启用 2FA 的账号） (4 failures)
  AssertionFailedError: 成功响应的顶层键应恰为 success/data …… ==> expected: <[success, data]>
      but was: <[success, data, error]>
  AssertionFailedError: 登录响应的 data 键集/顺序变了 …… ==> expected: <[accessToken, refreshToken, user]>
      but was: <[accessToken, refreshToken, user, mfaRequired, mfaToken, expiresIn]>
  AssertionFailedError: 非 2FA 登录响应里不得出现任何 mfa 标识；命中处：…"expiresIn":null},"error":null}…
  AssertionFailedError: expiresIn 只在 mfaRequired 分支出现；命中处：…"expiresIn":null},"error":null}…
```
⇒ `data` 那条**确实**对 `always` 敏感，实测键集为 `[accessToken, refreshToken, user, mfaRequired,
mfaToken, expiresIn]`，与推断逐字一致。

**顺带修掉的第二个问题**：第一次的失败信息把**整个响应体**（admin 的 55 个菜单 + 140+ 个权限码，
约 4KB）打进了消息里，失败报告基本不可读。已改为只回显命中处 ±40 字符（`excerpt(...)`）。

**还原**：改回 `non_null` → `sha1sum` 输出与基准一致（逐字节复现）。
`assertAll` 与 `excerpt` 是**还原之后**才落的，属本批正式改动，不在破坏范围内。

**两条破坏都未提交**，破坏期间工作区只含本批自己的改动。

---

## §C TOTP 与 AES-GCM 纯逻辑（第 4 步，2026-09-16）

**被守护的断言**：`TotpGeneratorTest`（RFC 6238 附录 B 六条 + RFC 4226 附录 D 十条官方向量）
与 `AesGcmCipherTest`（篡改必须被发现 / IV 长度 / 新 IV）。

**为什么门禁抓不住它们**：`pom.xml` 的 JaCoCo 配置排除了 `com/crm/common/**`，
所以**删掉这两个测试文件，`mvn -B verify` 仍然 BUILD SUCCESS、覆盖率一格不掉**。
两个类的 javadoc 都写明了这件事（照 `OutboundUrlValidatorTest` 的先例）。

**基准**（`sha1`）：
```
e90bef16df30dd7659d9766a0c50e0b31c09a7c3  backend/src/main/java/com/crm/common/TotpGenerator.java
0a837ad658037ed3017f756c77bbf9d779d5bb14  backend/src/main/java/com/crm/common/AesGcmCipher.java
```

### ⚠️ 先说一件**在开发中真的发生过**的事故 —— 断言当场证伪了我的实现

`decodeSecret` 的第一版把"拒绝非 Base32 字符"**托付给了 `commons-codec` 的 `Base32.decode`**，
并在 javadoc 里写下了"非 Base32 字符仍然抛 `IllegalArgumentException`"。

`decodeSecretRejectsNonBase32` 直接把这句话证伪了 —— 首次运行 39 例里红 2 例：
```
Tests run: 39, Failures: 2, Errors: 0, Skipped: 0
[ERROR] TotpGeneratorTest.decodeSecretRejectsNonBase32:142
    Expected java.lang.IllegalArgumentException to be thrown, but nothing was thrown.
```
原因是 **commons-codec 的 `Base32` 默认静默丢弃字母表之外的字符**：
`decode("JBSW-Y3DP")` 跳过 `-` 解出 5 个字节、`decode("JBSWY3DP!")` 同理，都不报错。

**为什么这条值得单独记**：它正是本方法声称要防的那种失败形态 ——
密钥被静默换成另一把，调用方拿到一个**形状完全正常的字节数组**，表现为"用户的码永远不对"，
没有任何线索指向"你抄错了一个字符"。而第一版实现把这道防线交给了外部库的默认行为，
**读代码看不出来**（`decode` 抛异常这件事看起来天经地义），只有断言能看出来。
处置：在 `decodeSecret` 里加了一道**自己写的**字母表校验（`[A-Z2-7]+`），
并把字母表**抄成字面量而不是从 `Base32` 反射**——从被怀疑的对象那里取判据，这道校验就与自己要防的东西同源。

### 破坏 C1：动态截断丢掉最高位抹零（`& 0x7F` → `& 0xFF`）

**破坏**：`TotpGenerator.codeAt` 的 `((hash[offset] & 0x7F) << 24)` 改成 `& 0xFF`，其余一字不动。

**观测（逐字，8 of 39 转红）**：
```
Tests run: 29, Failures: 8, Errors: 0, Skipped: 0 <<< FAILURE! -- in com.crm.common.TotpGeneratorTest
AssertionFailedError: counter=0 ==> expected: <755224> but was: <-728424>
AssertionFailedError: counter=1 ==> expected: <287082> but was: <-196566>
AssertionFailedError: T=59    ==> expected: <94287082> but was: <-53196566>
AssertionFailedError: T=1111111111 ==> expected: <14050471> but was: <-33433177>
```
失败形态正是**符号扩展**（`hash[offset]` 为负时把符号位带进左移）。还原：`sha1sum -c` 输出 `OK`。

⚠️ **如实记一个细节**：`0x7F` → `0xFF` 只让 16 条官方向量里的 8 条转红（RFC 6238 的 6 条里红 4 条、
RFC 4226 的 10 条里红 6 条）——只有最高位恰好为 1 的那些被影响。
⇒ **只挑一条向量来"证明"实现正确是不够的**，那 8 条绿的会让人以为没事。
这组向量必须保留完整的 16 条。

### 破坏 C2：IV 从 12 字节改成 16 字节

**破坏**：`AesGcmCipher.IV_BYTES = 12` → `16`，其余一字不动。

**观测（逐字，1 of 10 转红）**：
```
AssertionFailedError: IV 必须是 96 位 ==> expected: <12> but was: <16>
```
⚠️ **16 字节的 IV 对 GCM 是完全合法的**（GCM 接受任意长度，非 96 位只是走额外的 GHASH 派生路径），
所以往返、篡改、新 IV 那 9 条**全绿**——**只有形状断言看得见这件事**。
即"必须 96 位"这条要求**只由这一条断言承载**。

**顺带观测到的一个事实**：正因为 IV 被钉成 12 字节，
**把 GCM 直接换成 CBC/CTR 的破坏做不出来**——那些模式要求 16 字节 IV，
`IvParameterSpec(12 字节)` 会当场抛 `InvalidAlgorithmParameterException`。
所以"换成非 AEAD 模式"这个改动在这个类里是**窄**的（会在初始化处就失败），
真正需要防的是下面 C3 那种"保留 GCM 但把失败吞掉"的写法。

### 破坏 C3：认证失败时 fail-open（`throw` → `return input`）——**本条是这三条里最要紧的**

**破坏**：`AesGcmCipher.run` 的 catch 分支由
`throw new IllegalStateException(...)` 改成 `return input;`（即"吞掉 `AEADBadTagException`，把原文还回去"），
其余一字不动。这正是该类 javadoc 里点名要防的写法。

**观测（逐字，3 of 10 转红）**：
```
Tests run: 10, Failures: 3, Errors: 0, Skipped: 0 <<< FAILURE! -- in com.crm.common.AesGcmCipherTest
AesGcmCipherTest.tamperedCiphertextIsRejected
  AssertionFailedError: 密文第 0 位被翻转后仍解密成功 —— 认证标签没起作用
  ==> Expected java.lang.IllegalStateException to be thrown, but nothing was thrown.
AesGcmCipherTest.tamperedIvIsRejected  ==> Expected ... but nothing was thrown.
AesGcmCipherTest.wrongKeyIsRejected    ==> Expected ... but nothing was thrown.
```
⇒ 转红的**恰好是三条真实性断言**，而 `roundTrips` / `payloadShapeIsIvColonCiphertext` /
`encryptUsesFreshIvEachTime` / `ciphertextDoesNotContainPlaintext` **全绿**。
这实测证实了 `AesGcmCipherTest` 类 javadoc 里那句话：**集成测试里那列的断言（"不含明文、能解回来"）
在认证被摘掉之后照样全绿** —— 所以这三条断言不是冗余，它们是"篡改必须被发现"的唯一防线。

**还原**：`sha1sum -c` 输出 `OK`，两个基准值逐字节复现。

**三次破坏都未提交**，破坏期间工作区只含本批自己的改动。
`TotpGenerator` 的字母表校正是**还原之后**才落的正式改动，不在破坏范围内。

### ⚠️ 追加订正（2026-09-16，第 5 步）：§C 里 `AesGcmCipher` 的 sha1 基准已作废

原文（上面每条破坏的「还原」判据里引用的）基准是 `0a837ad658…`。**该基准不再成立**：
第 5 步把 `AesGcmCipher.KEY_BYTES` 由包内可见改为 `public`，供
`MfaSecretEncryptionService` 复用「解码后必须恰好 32 字节」这个判据——否则 `32` 这个数字会有
**两个出处**（一处改、另一处不改就会静默漂移）。

这是一次**正式改动，不是还原失败**：§C 的三次破坏在**其记录时点**都逐字节还原成功（当时 `sha1sum -c`
输出 `OK`），改动发生在**那之后**。原文保留不改，新基线如下：

| 文件 | 第 5 步提交时的 sha1 |
|---|---|
| `common/AesGcmCipher.java` | `66592dc4a54119f076f1562f9adaa38d4a55e70b` |

---

## §D 配置接缝、启动守卫与 TOTP 时间侧（第 5 步，2026-09-16）

**本步新增 27 条用例**：`TotpServiceTest` 10、`MfaSecretEncryptionServiceTest` 10、
`SecurityDefaultsGuardTest` 7（surefire 601 → **628**，与「基线 + 本项 N」对得上）。

**破坏基准**（破坏前先记，逐字节还原后 `sha1sum -c` 必须全 `OK`）：

```
f42bb68e73d7f0ddbdca1648804e112e8bdb1ac1 *src/main/java/com/crm/config/SecurityDefaultsGuard.java
030150b4cc999f1e04d7030a09d0e0e157a4eeb3 *src/main/java/com/crm/service/TotpService.java
a49ee0e6854b8b692b35c995c0820b809ccefb64 *src/main/java/com/crm/service/MfaSecretEncryptionService.java
5fac788166fa43f8c0e85290376646be72ef0ed3 *src/main/java/com/crm/config/ClockConfig.java
```

四条破坏**全部**观测到转红，且**每条都逐字节还原**（四次 `sha1sum -c` 全 `OK`）。
还原之后才对 `SecurityDefaultsGuard` 做了一处**引用订正**（陈旧地指向一个**刻意未创建**的
`MfaStartupGuardIT`，见 §D 末），因此该文件的**提交态** sha1 是
`54a0a7c4fba75bc7c9aefa501714f4c0547224d7`，不再是上表里的 `f42bb68e…`。

### 破坏 D1：把 MFA 密钥检查挪到 dev 的 `return` **之后**（位置性质）

**被守护的断言**：`SecurityDefaultsGuardTest.checkRunsBeforeTheDevEarlyReturn`。

**为什么需要单独守护**：`run()` 第 1 步在 dev 环境走的是 **`return`**（不是"检查完继续往下"），
所以**任何追加在它之后的检查在 dev 里永远不会执行**——而 dev 恰恰是"本机没配 MFA 密钥"最常见的地方。
失效方向是「**看起来检查过了**」。这条性质**靠注释保证不了**，`run()` 上方那段注释就是为此写的。

**破坏**：删掉 `run()` 开头的 `checkMfaSecretKey();`，追加到第 3 步（生产环境提示）之后。

**观测（逐字，7 条里恰好 1 条转红）**：
```
Tests run: 7, Failures: 1, Errors: 0, Skipped: 0 -- in com.crm.config.SecurityDefaultsGuardTest
SecurityDefaultsGuardTest.checkRunsBeforeTheDevEarlyReturn
  AssertionFailedError: 在会提前 return 的 dev 路径上，畸形密钥仍必须让启动失败
  ==> Expected java.lang.IllegalStateException to be thrown, but nothing was thrown.
```
⇒ **对照组 `devPathPassesWithAValidKey` 保持绿**（同一条 dev 路径、同样的 `return`，只是密钥合法），
故这次转红可归因于「检查的位置」这一件事，而不是 dev 分支本身。

**还原**：`sha1sum -c` 输出 `OK`。

### 破坏 D2：`matchTimeStep` 返回"当前步"而不是"命中那一步"

**被守护的断言**：`TotpServiceTest.returnsTheMatchedStepNotTheCurrentOne`。

**为什么需要单独守护**：返回值是**防重放的键**。若实现图省事返回 `currentTimeStep()`，
单次验证的表现**完全正常**（窗口内的码照样通过），只有重放时才出问题：用户提交上一步的码、
却被按当前步记下来 ⇒ **上一步没被标记 ⇒ 同一个码还能再用一次**。
这个缺陷**不会让任何"能登录"的测试转红**。

**破坏**：`matchTimeStep` 的两处 `return OptionalLong.of(step);` → `return OptionalLong.of(now);`
（即"命中的就是当前步"这个错误假设，其余一字不动）。

**观测（逐字，10 条里恰好 1 条转红）**：
```
Tests run: 10, Failures: 1, Errors: 0, Skipped: 0 -- in com.crm.service.TotpServiceTest
TotpServiceTest.returnsTheMatchedStepNotTheCurrentOne:72
  AssertionFailedError: 上一步的码应命中上一步
  ==> expected: <OptionalLong[59650799]> but was: <OptionalLong[59650800]>
```
⇒ `59650799` = `now - 1`，`59650800` = `now`：期望与实得的**差恰好是一步**，
这就是"返回了当前步"的签名。同类的 `matchesCurrentStep`（当前步的码 ⇒ 命中当前步，
此时 `step == now` 两者无法区分）、`rejectsOutsideTheWindow`、
`zeroToleranceAcceptsOnlyCurrentStep` **全绿** —— 说明转红的归属是干净的。

**还原**：`sha1sum -c` 输出 `OK`。

### 破坏 D3：`timeStepRetention()` 写死 90 秒

**被守护的断言**：`TotpServiceTest.retentionIsDerivedFromTheWindow` 的第二条
（`tolerance = 2` ⇒ 150 秒）。

**为什么需要单独守护**：保留期必须**由窗口参数推出**。写死 90 的后果是：容错窗口一旦被调宽，
被标记的步会**在它仍可被接受时提前解禁** ⇒ 同一个码能在两条票据上各用一次，正是 FR-M08 要防的重放。

**破坏**：`Duration.ofSeconds((2L * tolerance + 1) * timeStepSeconds)` → `Duration.ofSeconds(90)`。

**观测（逐字，10 条里恰好 1 条转红）**：
```
Tests run: 10, Failures: 1, Errors: 0, Skipped: 0 -- in com.crm.service.TotpServiceTest
TotpServiceTest.retentionIsDerivedFromTheWindow:135
  AssertionFailedError: 容错窗口变宽时保留期必须跟着变
  ==> expected: <PT2M30S> but was: <PT1M30S>
```
⇒ 值得记下来的一点：**同一条用例里 `tolerance = 1` 那半仍然是绿的**（写死 90 与推导出的 90 相等）。
即「写死 90」这个缺陷**只用默认参数是验不出来的**，必须有一条**非默认参数**的用例才抓得住 ——
这就是那条 `tolerance = 2 ⇒ 150s` 断言存在的全部理由。

**还原**：`sha1sum -c` 输出 `OK`。

### 破坏 D4：把配置内容回显进问题描述

**被守护的断言**：`MfaSecretEncryptionServiceTest.messagesNeverContainTheConfiguredValue`
与 `SecurityDefaultsGuardTest.failureMessageDoesNotEchoTheKey`。

**为什么需要单独守护**：配置值可能是运维**误粘的别的东西**（私钥、口令）。它一旦进了异常消息，
就会跟着错误报告、日志、工单一路扩散出去——而这条路径**只在配置出错时才走**，
正是没有人会去读日志的时刻。

**破坏**：在 `configurationProblem()` 的**非 Base64** 分支与**长度不对**分支各自追加
`+ "（当前值：" + configuredKey + "）"`。

**观测（逐字，两个类合计 17 条里 2 条转红）**：
```
Tests run: 7, Failures: 1 -- in com.crm.config.SecurityDefaultsGuardTest
SecurityDefaultsGuardTest.failureMessageDoesNotEchoTheKey
  AssertionFailedError: 消息里出现了配置内容：SECURITY: crm.security.mfa.secret-key 不是合法的 Base64。
  应为 `openssl rand -base64 32` 的输出（44 字符，末尾一个 =）（当前值：!!!这不是 Base64!!!）

Tests run: 10, Failures: 1 -- in com.crm.service.MfaSecretEncryptionServiceTest
MfaSecretEncryptionServiceTest.messagesNeverContainTheConfiguredValue
  AssertionFailedError: 描述里出现了配置内容的片段 [!]：…（当前值：!!!这不是 Base64!!!）
```

#### 破坏 D4b：**同一条破坏证明不了两个分支** —— 补一次只坏长度分支的观测

`messagesNeverContainTheConfiguredValue` 是**逐样本循环**的，它在**第一个样本**
（`NOT_BASE64`，片段 `!`）就中止了。所以上面那次转红**证明不了**长度分支的回显也会被抓住。
把非 Base64 分支先还原、只留长度分支的回显，再观测一次：

**观测（逐字，`SecurityDefaultsGuardTest` 7/7 全绿，只有循环那条转红）**：
```
Tests run: 7, Failures: 0 -- in com.crm.config.SecurityDefaultsGuardTest
Tests run: 10, Failures: 1 -- in com.crm.service.MfaSecretEncryptionServiceTest
MfaSecretEncryptionServiceTest.messagesNeverContainTheConfiguredValue
  AssertionFailedError: 描述里出现了配置内容的片段 [MDEy]：crm.security.mfa.secret-key 解码后为 16 字节，
  AES-256 要求恰好 32 字节（…）。注意长度是**解码后**算的：44 个字符的 Base64 串才是 32 字节
  （当前值：MDEyMzQ1Njc4OWFiY2RlZg==）
```
⇒ 这次红的样本是 `SHORT_16`、片段 `MDEy`，走的正是**长度分支**；而守卫那边因为样本走非 Base64 分支
（已还原）**全绿**。**两个分支各自被观测到一次**，归属干净。

**还原**：`sha1sum -c` 输出 `OK`（两处回显各自还原后复验）。

### 本步同时做的一处**引用订正**（不是破坏）

`checkMfaSecretKey()` 的 javadoc 原先写着「'它在 `run()` 里被调到、且在 dev 的 `return` 之前'
是另一条性质——**那条只有真起一个上下文才看得见（`MfaStartupGuardIT`）**」。
**`MfaStartupGuardIT` 是刻意不创建的**：要真起一个 dev 上下文得连 MySQL，而这条性质本身只是
`run()` 内两步的**先后**，与容器无关。该性质已由
`SecurityDefaultsGuardTest.checkRunsBeforeTheDevEarlyReturn`（反射设好两个 `@Value` 字段后直接调 `run()`）承担。
javadoc 与 `MfaSecretEncryptionServiceTest` 类注释里对它的引用一并订正为指向那个用例。

### ⚠️ 本次门禁期间观测到**一次与本批无关的间歇失败**（如实记，未修）

第 5 步的首次与第二次全量 `mvn -B verify` 都**不是全绿**：`failsafe 291 条里 1 条失败`，
稳定落在 `WebhookRedirectIT.legitimateRedirectIsFollowedHopByHop`（两次都是同一条，报
`expected: "SUCCESS" but was: "FAILED"`）。此后：

| 第几次全量 `verify` | 工作区状态 | failsafe |
|---|---|---|
| 第 1 次 | 干净 | 291 / **1 失败** |
| 第 2 次 | 干净 | 291 / **1 失败** |
| 第 3 次 | 测试文件里**带着临时诊断断言**（生产代码未变） | 291 / 0 绿 |
| 第 4 次 | 诊断已逐字节还原（`sha1sum -c` 输出 `OK`） | 291 / 0 绿 |

⇒ 它是**间歇的**，不是本批引入的确定性回归；第 4 次是**干净工作区**上的门禁跑，
surefire **628 / 0**（= 基线 601 + 本步 27）、failsafe **291 / 0**、
`All coverage checks have been met.`、`BUILD SUCCESS`。

**取证过程**（给一条只改消息、不改逻辑的临时诊断，跑完逐字节还原，`sha1sum -c` 复验 `OK`）：
失败时该投递记录的真实内容是
```
error = I/O error on POST request for "http://localhost:9999/hook": Connection refused: connect
retryCount = 3        httpStatus = 200
回环服务命中： /hook 命中=1, /final 命中=1
```

**机制**：`localhost:9999` **在 `WebhookRedirectIT` 里从不出现**（它用的是 `HttpServer` 随机回环端口）。
它属于**别的 IT 类**（`IntegrationHubIT` / `OpenPlatformIT` 用 `localhost:9999/hook`，而 9999 上无人监听
⇒ 该投递要走完 1s／5s／30s 三次退避、约 36 秒才落库）。于是：
**前一个测试类的在飞投递线程，在库被 `resetDatabase()` 重放之后才写下它的终态行**；
而重放会让自增 id 重新开始，于是那条行的 `subscription_id` 与**本用例新建的订阅**撞号，
被 `awaitDelivery` 的 `items.path(0)` 读成了本次的结果。本用例自己的两跳**其实都成功了**
（各命中 1 次、落点 200），这也与"读到的是别人的行"一致。

**这是本仓既有的跨测试类异步泄漏 + 主键重用的组合，不在 082 的改动面内**
（本批没有碰 webhook／投递／异步调度的任何代码）。**本步不修**：它属于另一个特性的测试隔离缺陷，
值得单独立项，而不该在 082 里顺手改掉（那会让 082 的门禁证据里混进一次无关的行为变更）。

**同时订正我自己在排查中的一条错误推断**：我曾据「失败那次整类只跑 1.68s、隔离跑 10.42s」
推断"跑得快即失败"。**不成立**——后来两次全绿里 `WebhookRedirectIT` 是 1.627s／1.647s，
以及失败那两次本身就是 1.669s／1.680s：**耗时并不区分成败**，该推断作废（此处留痕以免后人重蹈）。

**排查中另有一处工具陷阱值得记**：给测试加诊断后头两次跑都读到**旧报告**，原因是
① `spotless:check` 在 `verify` 相位**早于 failsafe**，格式违规会让构建在跑测试**之前**就中止；
② Maven 的增量编译判定 "Nothing to compile - all classes are up to date"，改动**根本没被编进去**
（那份 `.class` 被判为比源码新）。故诊断类改动要生效，必须**先 `spotless:apply`**、
并**删掉对应的陈旧 `target/test-classes/**.class`** 强制重编，否则会拿着上一轮的结论继续推理。
