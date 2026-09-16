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

---

## §E 测试替身与 `MfaStateStore`（第 6 步，2026-09-16）

**本步新增 26 条用例**：`MfaStateStoreTest` 15、`InMemoryRedisTestSupportTest` 11
（surefire 628 → **654**，与「基线 + 本项 N」对得上；failsafe **291** 不变）。
全量 `mvn -B verify` 退出码 **0**、`Tests run: 654 / 291` 各自 0 失败、
`All coverage checks have been met.`、`BUILD SUCCESS`。

### ⚠️ 先说明本步的破坏是**重做过的**，以及为什么

第 6 步存储侧的破坏最初是在**修订之前**的字节上观测的。此后 `lockRemainingSeconds`
因毫秒/秒精度缺陷被改（见破坏 E7 下方的「本条是实测推出来的」）。按仓规
**「改完定向破坏必须重做」**，故本文件记录的全部观测都是在**提交态字节**上重做的 ——
不是把旧结论抄一遍，而是在当前这份源码上重新看它们转红。
（这也是本节所有 `sha1` 基准都取自提交态、而 §D 里那条基准需要事后订正的原因。）

**破坏基准**（破坏前先记，逐字节还原后 `sha1sum -c` 必须全 `OK`）：

```
59a1a1acc5588f7257c194c828019f3f551d0d67 *src/main/java/com/crm/service/MfaStateStore.java
bfceb806553f90862b657bd8b48c13a8bca5bd32 *src/test/java/com/crm/service/MfaStateStoreTest.java
```

替身侧（`InMemoryRedisTestSupport` 与它自己的用例）：

```
68faafe5bb7ddcd9e32711349858c869042f4eef *src/test/java/com/crm/support/InMemoryRedisTestSupport.java
e4e32c77b2c23e386777b60438832bd4001eab7d *src/test/java/com/crm/support/InMemoryRedisTestSupportTest.java
```

共 **14 次破坏**（存储侧 8、替身侧 6），**每次单独观测、每次逐字节还原**，
全部 `sha1sum -c` 输出 `OK`；破坏期间**未提交**；结束后 `git status --porcelain` 为空，
两类 26/26 复跑全绿。

### 破坏 E1：`consumeTicket` 换成 `get` + `delete`（TOCTOU）

**被守护的断言**：`MfaStateStoreTest.consumeTicketUsesTheAtomicPrimitiveOnly`。

**为什么需要单独守护**：这是**本批唯一能钉住原子性的地方** ——
`getAndDelete`（`GETDEL`）与 `get`+`delete` 在单线程下可观测行为**完全相同**，
所以 §开头那条免责说的正是这件事：**任何 IT 都区分不出正解与劣解**。
劣解的后果是「一张票据 ⇒ 一个会话」在并发下失效。

**破坏**：`raw = redisTemplate.opsForValue().getAndDelete(ticketKey(ticket));`
→ `raw = redisTemplate.opsForValue().get(ticketKey(ticket)); redisTemplate.delete(ticketKey(ticket));`

**观测（逐字，15 条里恰好 1 条转红）**：
```
Wanted but not invoked: valueOperations.getAndDelete("auth:2fa-ticket:T"); ...
However, there was exactly 1 interaction with this mock:
valueOperations.get("auth:2fa-ticket:T"); -> at com.crm.service.MfaStateStore.consumeTicket(MfaStateStore.java:138)
```

**还原**：`sha1sum -c` 输出 `OK`。

### 破坏 E2：`markTimeStepUsed` 换成 `get` + `set`（防重放变竞态）

**被守护的断言**：`markTimeStepUsedUsesSetIfAbsentWithTheRetention`（调用形状）
与 `markTimeStepUsedFailsClosedOnAmbiguity`（`null` ⇒ 按已占用处理）。

**为什么需要单独守护**：同 E1，`SET NX` 与 `get`+`set` 在单线程下不可区分；
劣解让「同一个动态码用两次」成为可能 —— 正是 FR-M08 要防的。

**破坏**：`first = ...setIfAbsent(usedKey(...), "1", retention);`
→ 先 `get`，非空则 `first = Boolean.FALSE`，否则 `set(...)` 后 `first = Boolean.TRUE`。

**观测（逐字，15 条里 2 条转红）**：
```
Tests run: 15, Failures: 2 -- in com.crm.service.MfaStateStoreTest
MfaStateStoreTest.markTimeStepUsedFailsClosedOnAmbiguity
  AssertionFailedError: expected: <false> but was: <true>
MfaStateStoreTest.markTimeStepUsedUsesSetIfAbsentWithTheRetention
  NeverWantedButInvoked:
  valueOperations.get(<any string>);
  Never wanted here: -> at MfaStateStoreTest.markTimeStepUsedUsesSetIfAbsentWithTheRetention(MfaStateStoreTest.java:134)
  But invoked here: -> at com.crm.service.MfaStateStore.markTimeStepUsed(MfaStateStore.java:245) with arguments: [auth:2fa-used:7:59650800]
```
⇒ 两条各自命中一处：一条钉**原语形状**，一条钉**歧义时的 fail-closed 方向**。
**这两条不重复**，故两条都留着。

**一处编译事故（如实记）**：这条破坏第一次是写成三元表达式
（`... get(k) != null ? Boolean.FALSE : set(k, "1", retention)`）的，
而 `ValueOperations.set(K,V,Duration)` 返回 **`void`** ⇒ 三元里没有可用的值、**根本编译不过**。
最终写成上面那个 if/else。记下来是因为它说明：**"把原子原语换回两次调用"这件事在 Java 里
不是一处签名兼容的替换**，改写时容易顺手改出另一个语义。

**还原**：`sha1sum -c` 输出 `OK`。

### 破坏 E3：`recordFailure` 只在**第 1 次**失败时 `expire`（锁窗起点错位）

**被守护的断言**：`lockWindowStartsAtTheFailureReachingTheThreshold`。

**为什么需要单独守护**：仓内既有的计数惯例（`AuthService` 的 `auth:fail:`）是
**每次失败都续一整窗**；照抄过来，锁定窗口的**起点**就从"达到阈值那次"移到"第 1 次失败"，
等于把 5 次尝试压缩进一个已经在倒计时的窗口里 —— 用户实际能试的时间远少于 15 分钟。
这条差别**只体现在"第几次失败时才 expire"**，没有任何可观测的返回值能区分。

**破坏**：`if (count != null && count == maxAttempts)` → `if (count != null && count == 1)`。

**观测（逐字，15 条里 1 条转红）**：
```
Tests run: 15, Failures: 1 -- in com.crm.service.MfaStateStoreTest
MfaStateStoreTest.lockWindowStartsAtTheFailureReachingTheThreshold
  NeverWantedButInvoked:
  redisTemplate.expire(<any string>, <any java.time.Duration>);
  Never wanted here: -> at org.springframework.data.redis.core.RedisOperations.expire(RedisOperations.java:327)
  But invoked here: -> at com.crm.service.MfaStateStore.recordFailure(MfaStateStore.java:175) with arguments: [auth:2fa-fail:7, PT15M]
```

**还原**：`sha1sum -c` 输出 `OK`。

### 破坏 E3b：**计划预期两条转红，实测只有一条** —— 补一次可达第二条的破坏

计划的表里这一行写着「换成只在第 1 次失败时 expire ⇒ 预期给
`lockWindowStartsAtTheFailureReachingTheThreshold` **和** `failuresBeyondTheThresholdDoNotExtendTheLock` 标红」。
**实测只红了前者。** 原因不是断言写错，而是**那次破坏根本没有覆盖后者的失效方向**：
`count == 1` 依然只 `expire` 一次，"阈值之后不再续窗"这条**仍然是成立的**
（`failuresBeyondTheThresholdDoNotExtendTheLock` 判的是 `times(1)`）。

⇒ 若就此收工，第二条断言就是**一条没有被任何破坏触及过的护栏**（它可能永远绿着，
包括在一个把它变成空操作的改动下）。故补一次针对它的破坏：

**破坏**：`recordFailure` 里去掉条件、**每次失败都 `expire`**（即照抄 `AuthService` 的惯例）。

**观测（逐字，15 条里 2 条转红）**：
```
Tests run: 15, Failures: 2 -- in com.crm.service.MfaStateStoreTest
MfaStateStoreTest.lockWindowStartsAtTheFailureReachingTheThreshold
  NeverWantedButInvoked: redisTemplate.expire(<any string>, <any java.time.Duration>); ...
  But invoked here: -> at com.crm.service.MfaStateStore.recordFailure(MfaStateStore.java:174) with arguments: [auth:2fa-fail:7, PT15M]
MfaStateStoreTest.failuresBeyondTheThresholdDoNotExtendTheLock
  TooManyActualInvocations:
  redisTemplate.expire("auth:2fa-fail:7", PT15M);
  Wanted 1 time: ... But was 6 times:
  -> at com.crm.service.MfaStateStore.recordFailure(MfaStateStore.java:174)  (×6)
```
⇒ 两条断言**各自被观测到一次转红**，归属干净。这条是本文件**自己加的破坏**（计划的表里没有）：
不加上它，计划里那一格就是一句**没被验证过的预期**。

**还原**：`sha1sum -c` 输出 `OK`。

### 破坏 E4：把「计数已满但 TTL ≤ 0」读成「没锁」（永久锁定洞）

**被守护的断言**：`aFullCounterWithoutATtlIsReArmedInsteadOfLockingForever`。

**为什么需要单独守护**：仓内的 `increment` + `expire` 是**两次**调用，中间断掉就会留下
一个"计数满着、但没有过期时间"的键，而 `INCR` 会一直让它满着。把这种键读成"没锁"
⇒ 那个人的 2FA **永久**不可用（键在 Redis 里，重启进程也没用），
且症状是"这个人怎么试都不行"，排查方向会整个跑偏。
补窗的代价是一个 TTL，收益是从"永久"变回"15 分钟"。

**破坏**：`if (ttlMillis == null || ttlMillis <= 0) { expire + warn + return lockWindow }`
→ `if (ttlMillis == null || ttlMillis <= 0) { return 0; }`。

**观测（逐字，15 条里 1 条转红）**：
```
Tests run: 15, Failures: 1 -- in com.crm.service.MfaStateStoreTest
MfaStateStoreTest.aFullCounterWithoutATtlIsReArmedInsteadOfLockingForever
  AssertionFailedError: 应报「已锁」，而不是「没锁」 ==> expected: <900> but was: <0>
    at com.crm.service.MfaStateStoreTest.aFullCounterWithoutATtlIsReArmedInsteadOfLockingForever(MfaStateStoreTest.java:241)
```

**还原**：`sha1sum -c` 输出 `OK`。

### 破坏 E4b：返回值改成向下取整（另一侧的精度方向）

**被守护的断言**：`aWindowInItsLastSecondIsStillReportedAsLocked`。

**为什么需要单独守护**：同一次 `getExpire` 读出的毫秒值，**两个用途要求的精度相反** ——
"要不要补窗"问的是「键上到底有没有 TTL」，"还锁不锁着"问的是「还剩多久」。
直接 `ttlMillis / 1000` 会把"还剩 400ms"报成 `0`，而调用方（`MfaVerificationService`）
判的是 `> 0` ⇒ **还锁着的人被放行一次尝试**。故返回必须向上取整。
E4 与 E4b 是**同一条读的两个相反失效方向**，各自需要一次破坏 ——
一次只坏一侧的观测证不了另一侧（同 §D 的 D4/D4b）。

**破坏**：`return (ttlMillis + 999) / 1000;` → `return ttlMillis / 1000;`

**观测（逐字，15 条里 1 条转红）**：
```
Tests run: 15, Failures: 1 -- in com.crm.service.MfaStateStoreTest
MfaStateStoreTest.aWindowInItsLastSecondIsStillReportedAsLocked
  AssertionFailedError: 还剩 400ms 也是「锁着」——报 0 会让调用方放行一次尝试 ==> expected: <1> but was: <0>
    at com.crm.service.MfaStateStoreTest.aWindowInItsLastSecondIsStillReportedAsLocked(MfaStateStoreTest.java:223)
```

**还原**：`sha1sum -c` 输出 `OK`。

### 破坏 E5a：`findTicketUserId` 的 `catch` 改成 `return Optional.empty()`（静默 fail-open）

**被守护的断言**：`everyMethodFailsCloseWhenTheStoreIsDown`。

**为什么需要单独守护**：这一行就是**"静默降级为单因素"**。`catch { return Optional.empty(); }`
读起来像"没有票据"，语义是**跳过二次验证**；此时用户正常登录、审计没有异常、
监控没有报错 —— 响应里**看不出来**。全仓只有这一个类能写错，而它写错的代价是
Redis 一抖、**全体已启用 2FA 的账号变回单因素**。

**破坏**：`catch (Exception ex) { throw unavailable("读取二次验证票据", ex); }`
→ `catch (Exception ex) { log.warn(...); return Optional.empty(); }`。

**观测（逐字，15 条里 1 条转红）**：
```
Tests run: 15, Failures: 1 -- in com.crm.service.MfaStateStoreTest
MfaStateStoreTest.everyMethodFailsCloseWhenTheStoreIsDown
  AssertionFailedError: Expected com.crm.common.BusinessException to be thrown, but nothing was thrown.
    at com.crm.service.MfaStateStoreTest.assertUnavailable(MfaStateStoreTest.java:280)
    at com.crm.service.MfaStateStoreTest.everyMethodFailsCloseWhenTheStoreIsDown(MfaStateStoreTest.java:266)
```
⇒ `MfaStateStoreTest.java:266` 是**逐个方法**写的断言列表里 `findTicketUserId` 那一行
（类 javadoc 说明了为什么不只挑一个代表：漏掉的那一处不会让别的用例转红）。

**还原**：`sha1sum -c` 输出 `OK`。

### 破坏 E5b：`consumeTicket` 的 `catch` 改成 `return false`（另一个方向的静默 fail-open）

**被守护的断言**：同上，`everyMethodFailsCloseWhenTheStoreIsDown`。

**为什么需要单独守护**：E5a 与 E5b 落在**同一个用例的不同行**上，方向也相反 ——
`Optional.empty()` 是"没有票据"，`false` 是"票据无效"，两者都**看起来像正常业务结果**
（用户会看到 `MFA_TICKET_INVALID`，而不是"服务不可用"）。所以这不是同一条断言的重复执行，
而是同一道防线上**两个不同的入口**。

**破坏**：`catch (Exception ex) { throw unavailable("消费二次验证票据", ex); }`
→ `catch (Exception ex) { log.warn(...); return false; }`。

**观测（逐字，15 条里 1 条转红）**：
```
Tests run: 15, Failures: 1 -- in com.crm.service.MfaStateStoreTest
MfaStateStoreTest.everyMethodFailsCloseWhenTheStoreIsDown
  AssertionFailedError: Expected com.crm.common.BusinessException to be thrown, but nothing was thrown.
    at com.crm.service.MfaStateStoreTest.assertUnavailable(MfaStateStoreTest.java:280)
    at com.crm.service.MfaStateStoreTest.everyMethodFailsCloseWhenTheStoreIsDown(MfaStateStoreTest.java:267)
```
⇒ 行号从 `:266` 变成 `:267`，**这就是两次观测的区分点**（同一个用例、下一行）。

**还原**：`sha1sum -c` 输出 `OK`。

### 替身侧为什么也要有自己的破坏

`InMemoryRedisTestSupport` 的定位是「让 IT 能真跑通 Redis 路径」，
于是它一旦有偏差，结果是**别处的 IT 因为错误的原因变绿** —— 那比一条失败的用例糟得多，
因为它不报错，只让人相信一条不成立的结论。它的 11 条用例是**唯一的**纠错装置，
故同样逐条做破坏。下面 6 次，破坏基准见本节开头的替身侧 sha1 块。

### 破坏 E6：`live()` 不再检查过期（TTL 变成装饰）

**被守护的断言**：`aKeyDisappearsExactlyWhenItsTtlElapses` 等 —— 实测 **11 条里 5 条转红**。

**为什么需要单独守护**：这是替身最核心的保真度。TTL 若不真的过期，
「票据 300 秒后失效」「锁定 900 秒后自动恢复」「保留期取短了会提前解禁」这三类断言
**全部变成恒真的空断言**，而它们全都依赖替身真的会删掉过期项。

**破坏**：`if (entry.expiresAtMillis() != null && now() >= entry.expiresAtMillis())` 的条件前加 `false &&`。

**观测（逐字，5 条转红；取第一条的原文）**：
```
Tests run: 11, Failures: 5 -- in com.crm.support.InMemoryRedisTestSupportTest
  deleteReportsWhetherTheKeyWasThere
  aKeyDisappearsExactlyWhenItsTtlElapses
  incrementKeepsAnExistingTtl
  setIfAbsentKeepsTheFirstValueAndHonoursRetention
  expiredKeysAreNotInTheSnapshot

aKeyDisappearsExactlyWhenItsTtlElapses
  AssertionFailedError: 到期即不可读——否则『票据 TTL』『锁定 900 秒』这类断言全是假的 ==> expected: <null> but was: <v>
```

**还原**：`sha1sum -c` 输出 `OK`。

### 破坏 E7：`getExpire` 把 `-2`（键不存在）报成 `-1`（有键无 TTL）

**被守护的断言**：`getExpireDistinguishesMissingFromNoTtl`。

**为什么需要单独守护**：`MfaStateStore.lockRemainingSeconds` 正是靠这个区分判断
"计数已满但窗口丢了"要不要补窗。替身若一律返回 `-1`，
那条"永久锁定洞"的用例就**永远看不到补窗分支** —— 而它在真 Redis 上会走到。

**破坏**：`if (entry == null) { return -2L; }` → `if (entry == null) { return -1L; }`。

**观测（逐字，11 条里 1 条转红）**：
```
Tests run: 11, Failures: 1 -- in com.crm.support.InMemoryRedisTestSupportTest
InMemoryRedisTestSupportTest.getExpireDistinguishesMissingFromNoTtl
  AssertionFailedError: 键不存在 ==> expected: <-2> but was: <-1>
```

**还原**：`sha1sum -c` 输出 `OK`。

### 破坏 E8：`INCR` 清掉键上已有的 TTL

**被守护的断言**：`incrementKeepsAnExistingTtl`。

**为什么需要单独守护**：真 Redis 的 `INCR` **明确不修改 TTL**。
而 `AuthService` 的失败计数正是 `increment` + **另一次** `expire`（两次调用，不原子）；
替身若在 `INCR` 时把 TTL 清掉，那"两次调用之间"的窗口在替身里**永远不存在**，
于是这个惯用法留下的洞在被测路径上**不可观测**。

**破坏**：`values.put(key, new Entry(base + delta, current == null ? null : current.expiresAtMillis()));`
→ `values.put(key, new Entry(base + delta, null));`

**观测（逐字，11 条里 1 条转红）**：
```
Tests run: 11, Failures: 1 -- in com.crm.support.InMemoryRedisTestSupportTest
InMemoryRedisTestSupportTest.incrementKeepsAnExistingTtl
  AssertionFailedError: 真 Redis 的 INCR 不修改 TTL；替身若在这里续期或清 TTL，『increment 后紧跟 expire』那种写法就不会被发现有问题 ==> expected: <900> but was: <-1>
```

**还原**：`sha1sum -c` 输出 `OK`。

### 破坏 E9a：`delete` 无条件返回 `true`

**被守护的断言**：`deleteReportsWhetherTheKeyWasThere`。

**为什么需要单独守护**：真 Redis 的 `DEL` 报的是"是否真的删掉了一个键"。
无条件 `true` 会让"删一个已经不在了的键"看起来成功 —— 将来任何**用返回值判断
"这次是我清掉的吗"**的代码（清理/竞争检测）都会因此写错，且现场看不出来。

**破坏**：`return present;` → `return true;`（`present` 计算保留，故是个"只坏返回值"的破坏）。

**观测（逐字，11 条里 1 条转红）**：
```
Tests run: 11, Failures: 1 -- in com.crm.support.InMemoryRedisTestSupportTest
InMemoryRedisTestSupportTest.deleteReportsWhetherTheKeyWasThere
  AssertionFailedError: 已经不在了 ==> expected: <false> but was: <true>
```

**还原**：`sha1sum -c` 输出 `OK`。

### 破坏 E9b：`expire` 对不存在的键返回 `true`

**被守护的断言**：`expireReportsFalseForAMissingKey`。

**为什么需要单独守护**：与 E9a 同源但**另一个方法**（本类此前正是无条件返回 `true` 的）。
真 Redis 对不存在的键返回 0/false；无条件成功会让"给一个不存在的键补 TTL"看起来成功了 ——
而 `lockRemainingSeconds` 的补窗路径**正是**给一个可能不存在的键补 TTL。
一次只坏一个方法的返回值，两条断言各得一次观测（同 E4/E4b 的理由）。

**破坏**：`if (live(key) == null) { return false; }` → `return true;`。

**观测（逐字，11 条里 1 条转红）**：
```
Tests run: 11, Failures: 1 -- in com.crm.support.InMemoryRedisTestSupportTest
InMemoryRedisTestSupportTest.expireReportsFalseForAMissingKey
  AssertionFailedError: expected: <false> but was: <true>
```

**还原**：`sha1sum -c` 输出 `OK`。

### 破坏 E10：故障注入不再按前缀过滤（放之四海皆抛）

**被守护的断言**：`failureInjectionIsScopedToThePrefix`。

**为什么需要单独守护**：**这条替身性质直接决定了 `MfaFailClosedIT` 的断言有没有意义**。
`MfaFailClosedIT` 要在**同一次故障注入、同一份替身**里同时断言
「2FA 键全抛 ⇒ 503」与「非 2FA 键照常 ⇒ 200」。若注入其实不分前缀，那么后一半
**一定失败**（而不是"因为隔离性成立而通过"）—— 换句话说，
误报的方向不是"假绿"而是"用例炸"，但**同一种误报也可能反向发生**：
一个把前缀当成 `""` 的注入会让所有键一起挂，于是"非 2FA 不受影响"这条
在**一个本来就不该受影响的替身上**被验证，等于没验证。
两种情况都必须让这条断言自己先红。

**破坏**：`guard` 里去掉 `key.startsWith(prefix)` 判断，命中任一前缀即抛。

**观测（逐字，11 条里 1 条 ERROR —— 异常直接抛出测试方法，故计 ERROR 而非 FAILURE）**：
```
Tests run: 11, Failures: 0, Errors: 1 -- in com.crm.support.InMemoryRedisTestSupportTest
InMemoryRedisTestSupportTest.failureInjectionIsScopedToThePrefix
  org.springframework.data.redis.RedisConnectionFailureException: 注入的故障：键 auth:fail:1 命中前缀 auth:2fa-
    at com.crm.support.InMemoryRedisTestSupport.guard(InMemoryRedisTestSupport.java:299)
    at com.crm.support.InMemoryRedisTestSupport.lambda$4(InMemoryRedisTestSupport.java:156)
    at org.mockito.internal.stubbing.StubbedInvocationMatcher.answer(StubbedInvocationMatcher.java:42)
```
⇒ 注意键名 `auth:fail:1`（**非** 2FA 的键）**命中了 2FA 的前缀** ——
这正是被守护的那条性质，红色证据同时也是它的反例说明。

**还原**：`sha1sum -c` 输出 `OK`。

### 本节两处**如实记**的观察

1. **计划预期与实测不一致之处已在 E3b 写明**：E3 那条破坏只触及两条断言中的一条，
   补了一次 E3b 才让第二条有可达的证伪者。**计划的表不等于已验证的结论**。
2. **`everyMethodFailsCloseWhenTheStoreIsDown` 被两次破坏各命中一次（E5a/E5b）**：
   同一个用例、相邻的两行。这两次观测**不构成"该断言被重复验证"**，
   而是同一条防线上的两个入口各被打开过一次 —— 见各条的逐字行号（`:266` / `:267`）。


---

## §F 恢复码 `RecoveryCodeService`（第 7 步，2026-09-16）

**本步新增 23 条用例**：`RecoveryCodeServiceTest` 17（surefire）、`RecoveryCodeServiceIT` 6（failsafe）
—— surefire 654 → **671**、failsafe 291 → **297**，与「基线 + 本项 N」对得上。

**门禁**（三份文件逐字节还原之后，在**冻结的提交态字节**上完整跑一次 `mvn -B verify`）：
surefire **671 / 0**、failsafe **297 / 0**、`All coverage checks have been met.`、
`BUILD SUCCESS`（退出码 **0**）。

⚠️ **如实记：这次门禁是跑了两次才绿的。** 第一次复跑（同样在提交态字节上、工作区干净）
surefire **671 / 0**，而 failsafe **297 里 1 条失败**，失败的正是 §D 已记录的那条既知间歇失败
`WebhookRedirectIT.legitimateRedirectIsFollowedHopByHop`；紧接着的第二次复跑即 **297 / 0**。
本批没有碰 webhook／投递／异步调度的任何代码，**该间歇失败不在 082 的改动面内**；
处理方式与 §D 一致：**如实记、不修**（它值得单独立项）。

### 先说清楚本步为什么**必须**有"调用形状"断言 —— 而这一点在同一节里被**实测**证明了

第 7 步要守的安全性质是 SC-M05「并发下同一个恢复码只能被消费一次」。实现它的唯一手段是**条件 UPDATE**
（`SET used=1, used_at=? WHERE id=? AND used=0`，且要求影响行数恰为 1）；等价的劣解是
「`selectOne` 查到未使用行 → 比对哈希 → `updateById`」——两步之间开着一个 TOCTOU 窗口。

两者在**单线程**下的可观测行为**完全相同**，而 `MockMvc` 就是单线程的。所以
**任何 IT 在结构上都区分不出这两者**。这句话不是免责声明：下面的破坏 F6 把它变成了实测结论 ——
**单测恰好 1 条转红，而 `RecoveryCodeServiceIT` 6/6 全绿**。

同理，`RecoveryCodeServiceIT` 的类 javadoc 也把"本类证明不了 SC-M05"写在开头，
以免下一位评审以为 IT 覆盖了原子性。

**破坏基准**（破坏前先记，逐字节还原后 `sha1sum -c` 必须全 `OK`）：

```
0670c8566dd65fdda5fd1ff4e7444926be689d1d *src/main/java/com/crm/service/RecoveryCodeService.java
b5b2b1be19eb8134baad363e67cd01eefb9732a1 *src/test/java/com/crm/service/RecoveryCodeServiceTest.java
906aa7992479d0f3b41d8c26ea784abee72c77b5 *src/test/java/com/crm/integration/RecoveryCodeServiceIT.java
```

共 **7 次破坏**（F1、F2、F2b、F3、F4、F5、F6。其中 **F2b 是把 F2 拆成两半后的补充**，
理由见 F2 条目）**每次单独观测、每次逐字节还原**，全部 `sha1sum -c` 输出 `OK`；
破坏期间**未提交**。

### 破坏 F1：去掉条件 UPDATE 谓词里的 `used=0`

**被守护的断言**：`RecoveryCodeServiceTest.consumeUsesConditionalUpdateWithUnusedPredicate`。

**为什么需要单独守护**：`used=0` 既是判据也是守卫 —— 它是"并发下第二个请求的 UPDATE
影响行数为 0"的唯一来源。删掉它，两个请求会**都**改到这一行、**都**拿到影响行数 1，
于是同一个恢复码换出两个会话。这就是 SC-M05 的直接违反。

**破坏**：删掉 `.eq(UserRecoveryCode::getUsed, false)` 整行。

**观测（逐字，17 条里恰好 1 条转红）**：
```
[ERROR] Tests run: 17, Failures: 1, Errors: 0, Skipped: 0 -- in com.crm.service.RecoveryCodeServiceTest
[ERROR]   RecoveryCodeServiceTest.consumeUsesConditionalUpdateWithUnusedPredicate:100
  条件 UPDATE 缺少 used=0 谓词 —— 参数值里应同时出现 set 的 true 与谓词的 false，实际=[1234, 2026-09-16T10:00, true] ==> expected: <true> but was: <false>
```
⇒ 参数表里 `false` **消失**了，只剩 `[主键, used_at, true]`。断言用的是 `contains`（对内容、不对顺序），
故它钉的是"谓词在不在"，不依赖 MP 往 `paramNameValuePairs` 里塞参数时的次序。

**还原**：`sha1sum -c` 输出 `OK`。

### 破坏 F2：改回 `selectOne` + `updateById`（**本条归属不纯，故另补 F2b**）

**被守护的断言**：同上一条 + `consumeAcceptsNormalizedInput` + `generatedCodeRoundTripsThroughConsume`。

**破坏**：把条件 UPDATE 换成"读到的那行已经比对成功 ⇒ `mapper.updateById(row)`"，
返回 `true`（即回到"先判断后写入"）。

**观测（逐字，17 条里 3 条转红）**：
```
[ERROR] Tests run: 17, Failures: 3, Errors: 0, Skipped: 0 -- in com.crm.service.RecoveryCodeServiceTest
[ERROR]   RecoveryCodeServiceTest.consumeAcceptsNormalizedInput:161 expected: <true> but was: <false>
[ERROR]   RecoveryCodeServiceTest.consumeUsesConditionalUpdateWithUnusedPredicate:91 expected: <true> but was: <false>
[ERROR]   RecoveryCodeServiceTest.generatedCodeRoundTripsThroughConsume:238 刚生成的码必须能用 ==> expected: <true> but was: <false>
```

**⚠️ 如实记：这条破坏的**归属不纯**，所以它证明力有限。** 三条红都是
「该 `true` 而实 `false`」，而**这正是 mock 的默认行为造成的**：纯 Mockito 里
`updateById` 没有打桩 ⇒ 返回 `0`，于是劣解在这套用例里"消费失败"。也就是说，
**这 3 条红钉的是"这条路还能不能消费"，不是"用的是哪种写法"**。把 `updateById` 打桩成返回 1，
它们就会全部回到绿 —— 而那恰恰是"劣解在单线程下不可观测"的本来面目。

⇒ 真正钉住写法的是下一条 F2b，它**保留了正确的条件 UPDATE**、另外**多加一次整行回写**，
所以只有"多余的那次调用"会被看到，与 mock 的返回值无关。

**还原**：`sha1sum -c` 输出 `OK`。

### 破坏 F2b：保留条件 UPDATE，另加一次整行回写（**本条才钉住"写法"**）

**被守护的断言**：`consumeUsesConditionalUpdateWithUnusedPredicate` 里的
`verify(mapper, never()).updateById(any())`。

**为什么需要单独守护**：`updateById` 会把**整行实体**写回去 —— 既绕开定向更新的语义
（`WHERE` 不再约束"改哪一行"，等于回到先判断后写入），也和 085 那条"`user` 表的写入必须定向"
是同一类风险的邻居。这条断言**只在"两种写法同时存在"时才可能红**，故 F2 抓不到它。

**破坏**：在条件 UPDATE 之后追加 `mapper.updateById(row);`。

**观测（逐字，17 条里恰好 1 条转红）**：
```
[ERROR] Tests run: 17, Failures: 1, Errors: 0, Skipped: 0 -- in com.crm.service.RecoveryCodeServiceTest
org.mockito.exceptions.verification.NeverWantedButInvoked:

userRecoveryCodeMapper.updateById(<any>);
Never wanted here:
-> at com.crm.service.RecoveryCodeServiceTest.consumeUsesConditionalUpdateWithUnusedPredicate(RecoveryCodeServiceTest.java:110)
But invoked here:
-> at com.crm.service.RecoveryCodeService.consume(RecoveryCodeService.java:149) with arguments: [com.crm.entity.UserRecoveryCode@6629643d]
```

**⚠️ 一处必须说明的行号现象**：这条 `RecoveryCodeService.java:149` 是**破坏态字节**上的行号，
与提交态文件的行号**不重合**（同一破坏的两次观测里，另一次报的是 `:148` —— 两次注入点差一行）。
这也正解释了为什么本文件里的引用一律**同时给出符号名**：`Mockito` 报的是"当时代码"的坐标，
而源码一改，所有指向它的坐标同时失效。

**还原**：`sha1sum -c` 输出 `OK`。

### 破坏 F3：存明文、比明文（**唯一一次两层各自抓到**）

**被守护的断言**：单测 5 条（`regenerateRevokesThenInserts` 的 `库里不得出现明文`、
`regenerateSaltsEachRowIndependently` 的盐形态、以及三条消费路径）+ IT 5 条。

**为什么需要单独守护**：库里的形态是"暴露即定罪"的东西 —— 一旦落库是明文，任何一次库导出、
备份、慢查询日志都会把 10 个"能换会话的字符串"摊开。而它**在单测与 IT 上都能被观测**，
这是本步少见的、两层都有效的破坏（对比 F6）。

**破坏**：`hashOf` 返回明文本身、`matches` 改成明文相等比较。

**观测（逐字，单测 5 红 + IT 5 红）**：
```
[ERROR] Tests run: 17, Failures: 5, Errors: 0, Skipped: 0 -- in com.crm.service.RecoveryCodeServiceTest
[ERROR]   RecoveryCodeServiceTest.consumeAcceptsNormalizedInput:161 expected: <true> but was: <false>
[ERROR]   RecoveryCodeServiceTest.consumeUsesConditionalUpdateWithUnusedPredicate:91 expected: <true> but was: <false>
[ERROR]   RecoveryCodeServiceTest.generatedCodeRoundTripsThroughConsume:239 刚生成的码必须能用 ==> expected: <true> but was: <false>
[ERROR]   RecoveryCodeServiceTest.regenerateRevokesThenInserts:201 库里不得出现明文 ==> expected: <false> but was: <true>
[ERROR]   RecoveryCodeServiceTest.regenerateSaltsEachRowIndependently:223 盐应为 16 字节 base64（24 字符） ==> expected: <24> but was: <-1>

[ERROR] Tests run: 6, Failures: 5, Errors: 0, Skipped: 0 -- in com.crm.integration.RecoveryCodeServiceIT
  codesAreScopedToTheirUser:117                        Expecting value to be true but was false
  consumedCodeIsRejectedButTheNextOneStillWorks:44     Expecting value to be true but was false
  normalizedInputIsAccepted:84                         Expecting value to be true but was false
  regenerateRevokesThePreviousBatch:106                Expecting value to be true but was false
  storedValueIsASaltedHashAndConsumptionIsPersisted:68 Expecting actual: ... (形态断言)
```
⇒ 注意 IT 那 5 条的行号（44/68/84/106/117）与**提交态**一致（本次破坏只动服务侧），
而单测那 5 条的行号取自破坏态。两层各自独立抓到同一件事，**互不替代**。

**⚠️ 如实记：这次观测**当场暴露了我自己的一处测试缺陷**（论点顺序写反）。
第一次观测时 `regenerateSaltsEachRowIndependently` 的失败信息印的是
`expected: <-1> but was: <24>` —— 而我写的是 `assertEquals(indexOf(':'), 24, ...)`，
JUnit 的签名是 `assertEquals(expected, actual)`，于是 expected/actual 被印反了。
这正是**仓规「改完定向破坏必须重做」**要防的那类事：修正论点顺序**改动了单测的字节**
（`222`→`223`、`238`→`239` 两处行号位移即来自那次修正），所以 F3 **整条在最终字节上重跑过**，
而不是把第一次的结论抄一遍。

**还原**：`sha1sum -c` 输出 `OK`。

### 破坏 F4：盐写成常量（**计划表外的补充破坏**）

**被守护的断言**：`regenerateSaltsEachRowIndependently`。

**为什么需要单独守护**：盐的作用是**挡住预计算与跨行复用**。若盐是常量，
拿库的人可以用一张彩虹表同时命中所有用户的所有码（每行不再需要单独穷举），
而**明文码本身仍是随机的** —— 所以"哈希不同"这件事**证明不了盐**：
必须比 `:` **之前的那一段盐**本身。这条破坏是我在写用例时意识到"比哈希是在证一个不成立的东西"
之后补的（计划的破坏表里没有它）。

**破坏**：`hashOf` 里改成 `byte[] salt = new byte[SALT_BYTES]`（全零、不复用 `random`）。

**观测（逐字，17 条里恰好 1 条转红）**：
```
[ERROR] Tests run: 17, Failures: 1, Errors: 0, Skipped: 0 -- in com.crm.service.RecoveryCodeServiceTest
[ERROR]   RecoveryCodeServiceTest.regenerateSaltsEachRowIndependently:224 两批之间盐也不应相同 ==> expected: not equal but was: <AAAAAAAAAAAAAAAAAAAAAA==>
```
⇒ 打印出来的正是全零 16 字节的 base64（`AAAAAAAAAAAAAAAAAAAAAA==`，与测试里那条
"盐应为 16 字节 base64（24 字符）"的形态断言互相印证）。

**还原**：`sha1sum -c` 输出 `OK`。

### 破坏 F5：`regenerate` 不再先 `revokeAll`（**两层各自独立抓到**）

**被守护的断言**：单测 `regenerateRevokesThenInserts` 的 `verify(mapper).delete(any())`；
IT `regenerateRevokesThePreviousBatch`。

**为什么需要单独守护**：恢复码是**打印在纸上**的东西。换批而不作废旧批，等于那张纸**永久有效** ——
用户以为"重新生成"收回了自己丢过的码，实际旧码一条没少，而且 `countRemaining` 会**虚高**
（用户看到 20 个可用码，其中 10 个是他想要废掉的）。

**破坏**：`regenerate` 里去掉 `revokeAll(userId);` 一行。

**观测（逐字，单测 1 红 **且** IT 1 红）**：
```
[ERROR] Tests run: 17, Failures: 1, Errors: 0, Skipped: 0 -- in com.crm.service.RecoveryCodeServiceTest
[ERROR]   RecoveryCodeServiceTest.regenerateRevokesThenInserts:208
Wanted but not invoked:
userRecoveryCodeMapper.delete(<any>);
-> at com.crm.service.RecoveryCodeServiceTest.regenerateRevokesThenInserts(RecoveryCodeServiceTest.java:208)

However, there were exactly 10 interactions with this mock:
userRecoveryCodeMapper.insert(com.crm.entity.UserRecoveryCode@3fb450d7);
-> at com.crm.service.RecoveryCodeService.regenerate(RecoveryCodeService.java:109)  (×10)

[ERROR] Tests run: 6, Failures: 1, Errors: 0, Skipped: 0 -- in com.crm.integration.RecoveryCodeServiceIT
[ERROR]   RecoveryCodeServiceIT.regenerateRevokesThePreviousBatch:102
expected: 10
 but was: 20
```
⇒ 两层抓的是同一件事的两面：单测钉**调用形状**（`delete` 根本没发生），
IT 钉**可观测后果**（`countRemaining` 从 10 变 20，两批并存）。

**还原**：`sha1sum -c` 输出 `OK`。

### 破坏 F6：不看影响行数、命中即 `true`（**单测 1 红而 IT 6/6 全绿**）

**被守护的断言**：`consumeReturnsFalseWhenTheConditionalUpdateTouchesNoRow`。

**为什么需要单独守护**：这是本步**最容易被顺手改错**的一行。命中比对之后改为"直接返回 `true`"
（不读影响行数），在单线程下**完全等价**，但其语义已经变回了"先判断后写入"：
并发下两个请求都命中、都返回 `true`，而只有一个真的改到了行。
**本节开头那句"IT 结构上区分不出两者"，由这一条破坏单独作为证据。**

**破坏**：`return rows == 1;` → `return true;`（命中即返回）。

**观测（逐字，单测恰好 1 条转红，且**同一次运行的 IT 全绿**）**：
```
[ERROR] Tests run: 17, Failures: 1, Errors: 0, Skipped: 0 -- in com.crm.service.RecoveryCodeServiceTest
[ERROR]   RecoveryCodeServiceTest.consumeReturnsFalseWhenTheConditionalUpdateTouchesNoRow:122
  命中不等于消费成功：必须等 UPDATE 的影响行数说话，否则又成了先判断后写入 ==> expected: <false> but was: <true>

[INFO] Tests run: 6, Failures: 0, Errors: 0, Skipped: 0 -- in com.crm.integration.RecoveryCodeServiceIT
```
⇒ **同一份字节、同一次构建**：单测红、IT 全绿。这就是"原子性只能在调用形状断言里钉住"的实证，
也是本步把"为什么必须断言 SQL 怎么被拼的"写进单测 javadoc 的直接原因。

**还原**：`sha1sum -c` 输出 `OK`。

### 本节四处**如实记**的观察

1. **F2 的归属不纯**（见其条目）：3 条红全部来自 mock 未打桩的默认返回值，
   钉的是"能不能消费"而不是"怎么写"。**只有 F2b 钉写法**。计划的破坏表里写的是
   「改回 `selectOne`+`updateById` 一条」，实测表明那一条**不足以**证明"写法被钉住"。
2. **F4 是计划表外的破坏**：因为"哈希不同"**证不了盐**（明文随机就足以让哈希不同），
   原计划里没有一条破坏能让"盐"这个断言转红 —— 补 F4 才使它有可达的证伪者。
3. **F6 单测红 / IT 全绿**（见其条目）：本批最要紧的一处"测不到"，在此被观测而非被声称。
4. **一处工具陷阱（与 §D 同源，但这次是行尾风格）**：F6 那次观测的构建**尾段 `spotless:check` 是失败的**，
   违规报告对 `RecoveryCodeService.java` 给出 `@@ -1,279 +1,279 @@` —— **整个文件逐行同时出现 `-`/`+`**，
   即行尾风格层面的差异（该次注入把文件的换行写成了另一种风格），与破坏的语义无关。
   随后按仓规先 `spotless:apply`（**限定到本文件**
   `-DspotlessFiles='.*RecoveryCodeService.*\.java'`，避免全仓 apply 动到并行会话的文件）再复跑，
   `spotless:check` 退出码 **0**。记下来是因为它会让"测试结果已经拿到、构建却是 FAILURE"看起来自相矛盾
   —— 而在这个仓库里 `spotless` 位于 `verify` 相位的**测试之后**，两者本来就会一起出现在同一份日志里。

---

## §G 注册生命周期与端点（第 8 步，2026-09-16）

**本步新增用例 33 条**：`MfaQrCodeServiceTest` 6（surefire）、`MfaServiceTest` 18（surefire）、
`MfaLifecycleIT` 9（failsafe）—— surefire 671 → **695**、failsafe 297 → **306**，
与「基线 + 本项 N」对得上（24 + 9，逐条可数）。

**门禁**（五份文件逐字节还原之后，在**冻结的提交态字节**上完整跑一次 `mvn -B verify`）：
surefire **695 / 0**、failsafe **306 / 0**、`All coverage checks have been met.`、
`BUILD SUCCESS`（退出码 **0**）。

### ⚠️ 先说三件**在开发中真的发生过**的事故

这一节与 §C 同源：**是失败先把我的假设证伪，才有的这些断言/实现**。它们比破坏观测更有价值，
因为破坏是我设计的，事故是环境给的。

#### 事故 1：`getSqlSegment()` 只返回 WHERE —— 断言"改了哪几列"必须读 `getSqlSet()`

第 8 步的第一版用例用 `wrapper.getSqlSegment()` 断言 SET 里出现了 `totp_secret_encrypted`，
实测拿到的是 `(id = #{ew.paramNameValuePairs.MPGENVAL2})` —— **`getSqlSegment()` 只给 WHERE 那一段**，
SET 子句在 `Update` 接口的 `getSqlSet()` 上。三处断言当场全红。
若当初"顺手"把断言改成 `contains("id")` 让它变绿，那么**"写没写那一列"这条性质就再没有护栏**了 ——
而它正是 §G 破坏 G1 要钉的东西（也说明这类"测试写错了、实现没错"的红，修测试是唯一正确的方向）。
两段都是惰性求值，故 `capturedUpdate()` 辅助方法先 `getSqlSet()` 再 `getSqlSegment()`。

#### 事故 2：zxing 对空内容抛的是 `IllegalArgumentException`，不是 `WriterException`

`pngDataUrl` 只 `catch (WriterException)`；而 `QRCodeWriter.encode("")` 抛的是
`IllegalArgumentException("Found empty contents")`（见下面破坏 G5 的逐字栈）。
它**绕过**了那个 catch 直接冒到调用方，消息里也看不出这是"本服务拼错了 URL"还是"用户输入的"。
故在入口显式拦成本服务自己的 `IllegalStateException`。这条守卫是被一条真实红逼出来的，不是预防性代码。

#### 事故 3：`FixedClockTestSupport` 写好了、也提交了，但**从未被执行过** —— 第一次被继承时 9 条用例 9 个 error

`MfaLifecycleIT` 是本批第一个 `extends FixedClockTestSupport` 的类，随后 9 条用例全部：

```
Caused by: org.springframework.beans.factory.NoSuchBeanDefinitionException:
No qualifying bean of type 'com.crm.support.MutableClock' available:
expected at least 1 bean which qualifies as autowire candidate.
Dependency annotations: {@org.springframework.beans.factory.annotation.Autowired(required=true)}
```

根因是测试基建的**机制**，不是被测代码（被测代码一行没错）：

- `javap -p -c` 反编译 spring-test 6.1.1 的
  `AnnotationConfigContextLoaderUtils.detectDefaultConfigurationClasses`，字节码里**只有**
  `Class.getDeclaredClasses()` 一次调用，**没有任何 `getSuperclass()` 递归**；
  它自己的日志文案亦为「…does not declare any static, non-private, non-final, nested classes
  annotated with @Configuration」。⇒ **嵌在抽象父类里的 `@TestConfiguration` 不会被自动探测**。
- 另一侧的 `SpringBootTestContextBootstrapper` 反编译确认：`containsNonTestComponent` 只要**任一**
  候选类上直接有 `@TestConfiguration` 就返回 `false`，于是 `merge(找到的 @SpringBootConfiguration, 候选)` 照常执行
  —— 即"显式点名一个 `@TestConfiguration`"这条路是通的。

修法：在基类上写 `@ContextConfiguration(classes = FixedClockTestSupport.FrozenClockConfig.class)`
（`@ContextConfiguration` 的查找是 `TYPE_HIERARCHY` 语义，写在抽象基类上子类能继承到），
嵌套类留在原处。修完 9/9 绿。**这条教训与 `InMemoryRedisTestSupport` 同样适用于第 9 步的
`AuthMfaIT` / `MfaFailClosedIT`，故已写进 `FixedClockTestSupport` 的 javadoc（"四个必须写明的坑"第 4 条）**。

### 破坏基准

破坏前先记，逐条还原后 `sha1sum -c` 必须全 `OK`：

```
f3d639b53541c4a3a118e15bcb783f4a0c3b85a0 *src/main/java/com/crm/service/MfaService.java
b43440a60965cfb315f3d3986eac3e652abbbcd4 *src/main/java/com/crm/service/MfaQrCodeService.java
ee51890ead287cadef3e94e526c2e0e73a34f3f3 *src/test/java/com/crm/service/MfaServiceTest.java
21e2ad123fd15a260e5b76d17b9fe6ac58f1e19e *src/test/java/com/crm/service/MfaQrCodeServiceTest.java
a83996644c54f46272e91b688334789005c04395 *src/test/java/com/crm/integration/MfaLifecycleIT.java
```

共 **5 次破坏**（G1–G5）**每次单独观测、每次逐字节还原**，全部 `sha1sum -c` 输出 `OK`；破坏期间**未提交**。

⚠️ **如实记：G1 做过两次。** 第一次观测之后、G2 之前，我给 `MfaLifecycleIT` 补了 `version` 守卫
（理由见 G2），`MfaLifecycleIT.java` 的 sha1 因此变了 —— 于是**在最终基准上把 G1 重做了一遍**，
下面记的是重做那一次的输出。附带说明：`MfaService.java` 在两个基准上 sha1 相同，
G1 的破坏点没有被那次补守卫动过。

### 破坏 G1：`clearTwoFactor` 漏掉密钥列（"没启用但密钥还留着"）

**被守护的断言**：`MfaServiceTest.disableClearsAllThreeColumns` 与
`MfaLifecycleIT.assertSecretColumnIsCleared` —— **两层各抓一次**，这正是本批刻意保留两条断言的理由。

**破坏**：从 `clearTwoFactor` 的 SET 里删掉 `.set(User::getTotpSecretEncrypted, null)` 一行。

**surefire**：`Tests run: 24, Failures: 1`（`MfaQrCodeServiceTest` 仍 6/6 —— 与二维码无关，符合预期）

```
com.crm.service.MfaServiceTest.disableClearsAllThreeColumns -- Time elapsed: 0.021 s <<< FAILURE!
java.lang.AssertionError:

Expecting actual:
  "two_factor_enabled=#{ew.paramNameValuePairs.MPGENVAL1},two_factor_enabled_at=#{ew.paramNameValuePairs.MPGENVAL2}"
to contain:
  "totp_secret_encrypted"
	at com.crm.service.MfaServiceTest.disableClearsAllThreeColumns(MfaServiceTest.java:338)
```

**failsafe**：`Tests run: 9, Failures: 3`（`fullLifecycle:79`、`adminResetClearsEverythingAndIsAudited:174`、
`recoveryCodeDisablesAndIsConsumedOnce:134`）

```
org.opentest4j.AssertionFailedError:

expected: null
 but was: "J3V7OUIPK0KC17bc:Uier0AvJbPZb8qMaFqxcohZnhhvef7tLLFcuAsifbNFvQ+EJMZ3xYJ77u2A2CAWO"
	at com.crm.integration.MfaLifecycleIT.assertSecretColumnIsCleared(MfaLifecycleIT.java:219)
```

失败信息里那一串是**真实的密文**（`b64(iv):b64(ct||tag)`）—— 它同时证明了"残留的确实是一把能用的密钥"
而不是某种占位空串。**还原**：`sha1sum -c` 输出 `OK`。

### 破坏 G2：把 `enable` 的定向 update 回退成 `updateById(entity)`（**085 回归，本批最高风险项**）

**被守护的断言**：`MfaServiceTest` 的 `verify(userMapper, never()).updateById(any())`（调用形状），
以及本步**新补的** `MfaLifecycleIT` 里两处 `version` 前后值比较（可观测行为）。

**破坏**：把 `enable` 里那段

```java
userMapper.update(null, new LambdaUpdateWrapper<User>()
    .eq(User::getId, userId).set(User::getTwoFactorEnabled, true).set(User::getTwoFactorEnabledAt, now));
```

换成"造一个实体、`updateById`"。写入的**两列与取值完全相同** —— 差别只在乐观锁插件会不会
`SET version = version + 1`、以及会不会整行回写。

**surefire**：`Tests run: 24, Failures: 1`

```
Wanted but not invoked:
userMapper.update(
    isNull(),
    <Capturing argument: LambdaUpdateWrapper>
);
-> at com.crm.service.MfaServiceTest.capturedUpdate(MfaServiceTest.java:474)

However, there were exactly 2 interactions with this mock:
userMapper.selectById(42L);
-> at com.crm.service.MfaService.requireUser(MfaService.java:200)

userMapper.updateById(
    com.crm.entity.User@ab327c
);
-> at com.crm.service.MfaService.enable(MfaService.java:136)
```

**failsafe**：`Tests run: 9, Failures: 4`

```
com.crm.integration.MfaLifecycleIT.fullLifecycle -- Time elapsed: 0.247 s <<< FAILURE!
org.opentest4j.AssertionFailedError:

expected: 0
 but was: 1
	at com.crm.integration.MfaLifecycleIT.fullLifecycle(MfaLifecycleIT.java:95)
```

⚠️ **4 红里只有 2 红是直接证据**，另外 2 红是**同一根因的下游症状**，如实记下以免被当成独立证据：
`canReEnableAfterDisabling:174` 报 `Expecting value to be true but was false`、
`recoveryCodeDisablesAndIsConsumedOnce:157` 报 `expected: RECOVERY_CODE_INVALID but was: MFA_NOT_ENABLED`
—— 两者的机制相同：`updateById` 带乐观锁生成 `WHERE id = ? AND version = ?`，
而实体是新造的（`version = 0`），于是**第二次 `enable` 在 `version` 已变成 1 之后静默地一行都没改**，
账号实际上没被启用。这条症状比"管理员将来遇到 409"更早、更直白地暴露了同一处错误。

⚠️ **这也解释了为什么本步要补 `version` 守卫**：破坏 G2 第一次观测时，
surefire **1 红**而 failsafe **9/9 全绿** —— 调用形状断言看得见"调了哪个方法"，
看不见"写进去之后那行数据变成了什么"。补守卫后两层各自独立转红。

**还原**：`sha1sum -c` 输出 `OK`。

### 破坏 G3：`setup` 顺手把 `two_factor_enabled` 也写进去（FR-M04 的边界）

**被守护的断言**：`MfaServiceTest.setupWritesOnlyTheSecretColumnAndStaysDisabled` 与
`MfaLifecycleIT.fullLifecycle` 的 `assertThat(...getTwoFactorEnabled()).isFalse()`。

**破坏**：在 `writeSecret` 的 SET 里加 `.set(User::getTwoFactorEnabled, true)`。

**surefire**：`Tests run: 24, Failures: 1`

```
com.crm.service.MfaServiceTest.setupWritesOnlyTheSecretColumnAndStaysDisabled -- ... <<< FAILURE!
java.lang.AssertionError:

Expecting actual:
  "two_factor_enabled=#{ew.paramNameValuePairs.MPGENVAL1},totp_secret_encrypted=#{ew.paramNameValuePairs.MPGENVAL2}"
not to contain:
  "two_factor_enabled"
	at com.crm.service.MfaServiceTest.setupWritesOnlyTheSecretColumnAndStaysDisabled(MfaServiceTest.java:146)
```

**failsafe**：`Tests run: 9, Failures: 1, Errors: 7`：

```
com.crm.integration.MfaLifecycleIT.adminResetDoesNotTouchThePassword -- ... <<< ERROR!
com.crm.common.BusinessException: 账号已启用双因素认证，不可重复启用
	at com.crm.service.MfaService.enable(MfaService.java:122)
```

⚠️ 那 7 个 error 是**级联**（用例的第二步 `enable` 被 `MFA_ALREADY_ENABLED` 拒掉），
只有 `fullLifecycle:74` 的 `twoFactorEnabled isFalse` 是本条性质自己的断言。再次如实记。
**还原**：`sha1sum -c` 输出 `OK`。

### 破坏 G4：二维码渲染成**全白图**（把"图能不能被扫出内容"从声称变成实测）

**被守护的断言**：`MfaQrCodeServiceTest.pngDecodesBackToTheExactContent`
（用 zxing 自己的 `QRCodeReader` 把 PNG 解回文本，与拼进去的那串逐字比）。

**破坏**：把像素循环从 `image.setRGB(x, y, matrix.get(x, y) ? 0x000000 : 0xFFFFFF)`
改成恒写白 `image.setRGB(x, y, 0xFFFFFF)` —— 一张合规的、200×200 的、纯白的 PNG。

**surefire**：`Tests run: 24, Failures: 1, Errors: 1`

```
MfaQrCodeServiceTest.pngDecodesBackToTheExactContent -- ... <<< ERROR!
com.google.zxing.NotFoundException

MfaQrCodeServiceTest.pngDataUrlIsAnInlinePng:84 -- ... <<< FAILURE!
java.lang.AssertionError:
Expecting actual:
  "data:image/png;base64,iVBORw0KGgo…（与下面完全相同的串）"
not to be equal to:
  "data:image/png;base64,iVBORw0KGgo…（同一个串）"
```

⚠️ **这条破坏的要点是"哪几条断言没红"**：`pngDataUrlIsAnInlinePng` 里
「`data:image/png;base64,` 前缀」「PNG 魔数」「边长 200×200」三条**在全白图上全部通过**，
该用例里唯一红的是第 84 行那条"两张不同内容的图不能逐字相同"。
这正是 `MfaQrCodeServiceTest` 类 javadoc 里那句话的实证：
**只断魔数与边长的用例，在一张全白图上同样全绿** —— 而全白图意味着**每个用户都扫不上码**，
且失败现场（"App 说二维码无效"）离"像素画反了"很远。

**还原**：`sha1sum -c` 输出 `OK`。

### 破坏 G5：删掉 `pngDataUrl` 的空内容守卫（第三方异常直接冒到调用方）

**被守护的断言**：`MfaQrCodeServiceTest.emptyContentIsRejectedWithOurOwnException`。

**破坏**：删掉 `if (content == null || content.isEmpty()) { throw new IllegalStateException(...); }` 整块。

**surefire**：`Tests run: 24, Failures: 1`

```
com.crm.service.MfaQrCodeServiceTest.emptyContentIsRejectedWithOurOwnException -- ... <<< FAILURE!
java.lang.AssertionError:

Expecting actual throwable to be an instance of:
  java.lang.IllegalStateException
but was:
  java.lang.IllegalArgumentException: Found empty contents
	at com.google.zxing.qrcode.QRCodeWriter.encode(QRCodeWriter.java:55)
	at com.crm.service.MfaQrCodeService.pngDataUrl(MfaQrCodeService.java:89)
	at com.crm.service.MfaQrCodeServiceTest.lambda$0(MfaQrCodeServiceTest.java:119)
```

**还原**：`sha1sum -c` 输出 `OK`；随后复跑 `Tests run: 24, Failures: 0, Errors: 0` / `BUILD SUCCESS`。

### 本节两处**如实记**的观察

1. **G2 是本批唯一一条"先有声称、后有护栏"的破坏**（第一次观测单测红 / IT 全绿），
   而它守护的恰好是计划风险表里排第一的 085 回归。这说明"最高风险项在行为层没有观测点"
   这件事本身不会被任何一次全绿发现 —— 只能靠**主动去问"集成测试能不能看见它"**。
2. **两次"看起来像并行会话改了我的文件"的假警报**（IDE / m2e 的陈旧 `spotless:check` 快照）：
   其 diff 的 `-` 侧是**改守卫之前**的旧草稿内容，而 `Edit` 报的"文件已在磁盘上被修改"来自
   我先前那次限定范围的 `spotless:apply` 重排 javadoc。**判据是 `grep` 磁盘实际内容 + `sha1sum`**，
   不是诊断面板；整仓 `mvn -B spotless:check` 当时退出码为 **0**，即当时并不存在真实的格式违规。

---

## §H 二次验证（第 9 步，2026-09-16）

**本步新增用例 17 条**：`AuthMfaIT` 12（failsafe）、`MfaFailClosedIT` 4（failsafe）、
`UserIT.mfaVerifyDoesNotBumpVersion` 1（failsafe）—— surefire 695 → **695**（+0）、
failsafe 306 → **323**，与「基线 + 本项 N」对得上（12 + 4 + 1 = 17，逐条可数）。

**门禁**（八份文件逐字节还原之后，在**冻结的提交态字节**上完整跑一次 `mvn -B verify`）：
surefire **695 / 0**、failsafe **323 / 0**、`All coverage checks have been met.`、
`BUILD SUCCESS`（退出码 **0**）。

### ⚠️ 先说三件**在开发中真的发生过**的事故

与 §C、§G 同源：**是失败先把我的假设证伪，才有的这些实现/断言**。

#### 事故 1：`AuthServiceTest` 单跑必红、全量跑必绿 —— lambda 缓存是**进程级静态**

第 9 步给 `AuthService` 注入 `MfaChallengeService` 之后，`mvn test -Dtest=AuthServiceTest` 在
`successClearsIpFailures` 上报：

```
MybatisPlusException: can not find lambda cache for this entity [com.crm.entity.User]
```

一个**与被测逻辑毫无关系**的红。根因在测试基建的机制：`AuthService.login` 的成功路径上会构造
`new LambdaUpdateWrapper<User>()...set(User::getLastLoginAt, ...)`，而该缓存由**某个测试类首次
`initTableInfo` 时写入、且是进程级的** —— 于是本类的结果取决于「同 JVM 里有没有别的类先跑过」。
修法是本仓既有惯例（`CommentServiceTest` 等 40 余处都在做）：`@BeforeAll` 里
`TableInfoHelper.initTableInfo(assistant, User.class)`。加完单跑 6/6 绿。

⚠️ 这条**不是代码缺陷**，而是"零回归检查点"的采样错误：若当时按"全量绿 ⇒ 没问题"收工，
下一次有人单跑这个类就会撞上一个看起来像 `AuthService` 坏了的红。

#### 事故 2：`verifyData` 返回的是**根节点**，不是 `data`

第一版 `verifyData` 直接返回 `data` 节点，于是失败分支（要从根上取 `error.code`）全部取到
`MissingNode`，断言报的是 `expected: "MFA_TICKET_INVALID" but was: ""` —— **看起来像实现没写错误码**，
实际是辅助方法返回错了层。修完返回根节点，并把这个形状写进 `verifyData` 的 javadoc
（`AuthMfaIT` 成功分支里那句 `verifyData(...).path("data")` 就是留下的痕迹）。

同源的一次小事故：`verifyData` 的期望状态码是**显式参数**（`verifyData(ticket, code, null, 401)`），
而不是靠"断言失败即非 200"推断 —— 后者会让"本该 200 却 401"与"本该 401 却 500"这两种
方向相反的错误**报出同一句话**。

#### 事故 3：管理员重置之后继续验码 ⇒ `IllegalStateException` ⇒ **500**

第一版 `MfaVerificationService.verify` 没有"读到 user 后立刻判 `twoFactorEnabled`"这一步，
而 `resetByAdmin` 会**同时清空密钥列**。于是"票据取得之后、提交之前被管理员重置"这条路径走到
验码分支时会看到「已启用却没有密钥」，抛出：

```java
throw new IllegalStateException("账号已启用 2FA 但库中没有密钥：userId=" + user.getId());
```

⇒ 用户拿到 **500**。这是一次**完全正常的并发操作被报成"数据坏了"**，而用户不知道该做什么
（真实场景：管理员刚给他重置了 2FA，他拿着几分钟前的票据提交）。

修法是在 ① 与 ② 之间加一段早退判据（401 `MFA_TICKET_INVALID`），**但它不能替代 ⑤** ——
见下面 H7 与 H6 的对照：早退判据管的是"验码之前就已经改了"，⑤ 管的是"验码与消费票据期间改了"，
两处判据相同、**都必须在**。这一段实测证据在 H7（把它删回去，500 复现）与 H6（只留早退判据时，
⑤ 的重读**没有任何用例钉得住**）。

### 破坏基准

破坏前先记。八份文件，逐条还原后 `sha1sum -c` 必须全 `OK`：

```
2af8d551a2829fa727d402258bada8bdf6d18f37 *src/main/java/com/crm/config/SecurityConfig.java
8207e5f1a896ddc1a8f087775c8f069ffd9d5a92 *src/main/java/com/crm/service/MfaStateStore.java
0e5607741f8f212c946767996dc2322f215ef7d9 *src/main/java/com/crm/service/MfaVerificationService.java
ee55f6c889be7ee81fcebf92bd1670858d42ba7b *src/main/java/com/crm/service/AuthService.java
8f69ca870317aadca395926c483461d46be7097a *src/main/java/com/crm/service/MfaChallengeService.java
3289d6020de7000b32bea8db89bff4355c55f1f9 *src/test/java/com/crm/integration/AuthMfaIT.java
203a489b77e34904544f1cd2a97bbd050a12d5e8 *src/test/java/com/crm/integration/MfaFailClosedIT.java
1a64afcf17672afdc0d1aec1aeae39fff2d00c6c *src/test/java/com/crm/integration/UserIT.java
```

共 **13 次破坏**（H1–H13），其中 H2b / H4b / H9b 是**计划表外的补充破坏**（各自理由见对应小节）。
每次单独观测、每次逐字节还原；破坏期间**未提交**。

⚠️ **两份基准要分开看（这是本步最容易读错的地方）**：上面 `AuthMfaIT.java` 的
`3289d602...` 是 **H1–H6 观测时**的版本（11 条用例）；H6 之后我为了堵一个断言缺口给该文件**加了
一条用例**，它随之变成 `75cc90a77584bdc078bf6e432655c01dfd3eff43`（12 条），
`InMemoryRedisTestSupport.java` 同时变成 `51959e0c6d91b2d03f7fb992e3b2adaf64d2d889`。
⇒ **H1–H6 的观测是在旧基准上做的，H7–H13 是在新基准上做的**，两组的行号因此**不可互相换算**；
下面每条都记了它当时的行号，引用时请以**断言所在的用例名**为锚，别以行号为锚。
H1–H6 之所以**没有在新基准上重做**：追加的那条用例只覆盖 ①↔⑤ 窗口，不触及它们任何一条的判据路径
（如实记，不声称"已在新基准上复核"）。

### 破坏 H1：删掉 `SecurityConfig` 里 `/api/v1/auth/2fa/verify` 的 `permitAll`

**被守护的断言**：`AuthMfaIT.verifyIsReachableWithoutJwt` —— 它断言**不带 `Authorization`** 调
`verify` 拿到的是**控制器**的 401（带 `{"code":"MFA_TICKET_INVALID"}` JSON），而不是过滤器链的
空体 401。这是本仓唯一能区分「精确放行这一条路径」与「通配放行整个 `/2fa/**`」的可观测差异。

**failsafe**：11 条中 **9 条变红**。

```
[ERROR] AuthMfaIT.verifyIsReachableWithoutJwt:301 expected: "MFA_TICKET_INVALID" but was: ""
```

⚠️ **同一批里 `otherMfaEndpointsStillRequireJwt` 保持绿色** —— 这正是这条断言的**配对设计**：
它断言未带令牌调 `/2fa/status` 得到的是**空体** 401（`HttpStatusEntryPoint` 不写 body）。
两条一起看，才能把"放行了正确的端点"与"放行了所有端点"分开；只看前者的话，通配化会让它
**因为错误的原因而变绿**（通配化之后 verify 确实能到达控制器，但 status 也一起被放出去了）。

**还原**：`sha1sum -c` 输出 `OK`。

### 破坏 H2：`consumeTicket` 改成只 `get`（**完全不删**）

**被守护的断言**：`MfaStateStoreTest.consumeTicketUsesTheAtomicPrimitiveOnly`（surefire，调用形状）
与 `AuthMfaIT.ticketIsSingleUse`（failsafe，可观测行为）。

**surefire**：

```
Wanted but not invoked:
valueOperations.getAndDelete("auth:2fa-ticket:T");
...
However, there was exactly 1 interaction with this mock:
valueOperations.get("auth:2fa-ticket:T");
```

**failsafe**：`AuthMfaIT.ticketIsSingleUse:141 Response status expected:<401> but was:<200>`

⚠️ **本条的归属一开始记错了。** 我原以为它就是计划表里那条"换成 `get` + `delete`（TOCTOU）"，
但两层**都**抓到了它 —— 而"IT 在结构上区分不出原子性"这条声明若成立，IT 就不该抓得到。
差别在语义：**"完全不删"是行为缺陷**（票据永不过期、能换无限个会话），IT 当然看得见；
**"读了又删"才是 TOCTOU**，两者不可混为一谈。故补做 H2b。

**还原**：`sha1sum -c` 输出 `OK`。

### 破坏 H2b：`get` **+** `delete`（真正的 TOCTOU，**计划表外**）

**被守护的断言**：只有 `MfaStateStoreTest.consumeTicketUsesTheAtomicPrimitiveOnly`。

**failsafe**：**11/11 全部通过，`BUILD SUCCESS`**。
**surefire**：调用形状断言红（同 H2 的报错形状，`get` 与 `delete` 各一次交互）。

这是证据文件顶部那句免责声明的**直接实证**：**MockMvc 集成测试在结构上区分不出
`getAndDelete` 与 `get`+`delete`** —— 单线程下两者的可观测行为逐字相同，只有"调用了哪个原语"
能分开它们，而那只存在于调用形状断言里。⇒ 本条的价值不在"抓到了缺陷"，而在
**"证明了另一个层次抓不到它"**。

**还原**：`sha1sum -c` 输出 `OK`。

### 破坏 H3：`markTimeStepUsed` 恒返回 `true`

**被守护的断言**：`MfaStateStoreTest.everyMethodFailsClosedWhenTheStoreIsDown`、
`MfaStateStoreTest.markTimeStepUsedFailsClosedOnAmbiguity`（surefire）；`AuthMfaIT.sameCodeCannotBeReplayedOnANewTicket`、
`MfaFailClosedIT.verifyFailsClosedWhenReplayMarkerCannotBeWritten`（failsafe）。

**surefire**：上述两条红。
**failsafe**：

```
[ERROR] AuthMfaIT.sameCodeCannotBeReplayedOnANewTicket:162 expected:<401> but was:<200>
[ERROR] MfaFailClosedIT.verifyFailsClosedWhenReplayMarkerCannotBeWritten:94 expected:<503> but was:<200>
```

⚠️ 第二条是**静默 fail-open** 的原型：Redis 写不进去（本该 503 拒绝），实现却当成"标记成功"、
照常签发令牌 —— 而响应、审计、监控里**都看不出异常**。这正是 `MfaStateStore` 类 javadoc 里
"一个'顺手'的 catch 就是一次静默的单因素降级"那句的实测版本。

**还原**：`sha1sum -c` 输出 `OK`。

### 破坏 H4：把锁定检查**挪到验码之后**（但保留它）

**被守护的断言**（计划表声称）：`AuthMfaIT.lockoutAfterFiveFailuresAndAutoRecovery`。

**failsafe**：**11/11 全绿，不转红。**

**如实记**：仅**重排**不构成可观测缺陷。因为锁检查挪到验码之后、**仍在消费票据与签发令牌之前**，
于是"锁定期内即使码正确也被拒"这条性质**依然成立** —— 而它正是锁定的全部意义。
⇒ 计划表里"把锁定检查挪到验码之后"这条破坏**写得不忠实**：它描述的顺序变化不改变任何可观测行为。
补做 H4b。

**还原**：`sha1sum -c` 输出 `OK`。

### 破坏 H4b：**彻底删掉**锁定检查（**计划表外**）

**failsafe**：

```
[ERROR] AuthMfaIT.lockoutAfterFiveFailuresAndAutoRecovery:199 expected:<429> but was:<200>
```

即"锁定形同虚设"缺陷：第 6 次即使码正确也放行。

**还原**：`sha1sum -c` 输出 `OK`。

### 破坏 H5：给 `verify` 加 `@org.springframework.transaction.annotation.Transactional`

**被守护的断言**：`AuthMfaIT.failedVerificationIsAuditedAsSystem`、
`AuthMfaIT.lockoutAfterFiveFailuresAndAutoRecovery`。

**failsafe**：

```
[ERROR] AuthMfaIT.failedVerificationIsAuditedAsSystem:225 Expected size: 1 but was: 0 in: []
[ERROR] AuthMfaIT.lockoutAfterFiveFailuresAndAutoRecovery:204
```

⚠️ **HTTP 状态码仍然是 401** —— 失败的二次验证在接口层"正常"拒绝，只有审计表里那条
`MFA_VERIFY_FAILED` **消失了**。这就是类 javadoc 里那句"它会消失得毫无痕迹"的实测：
若测试只断言状态码，这个缺陷**在任何层次都看不见**。⇒ "本方法没有事务"是一个**承重决定**，
不是风格选择。

**还原**：`sha1sum -c` 输出 `OK`。

### 破坏 H6：⑤ 用 ① 的 `user` 对象代替重读（**计划表外，且不转红**）

**破坏**：`User fresh = userMapper.selectById(userId);` → `User fresh = user;`

**failsafe**：**11/11 全绿，不转红。**

**根因不是破坏写错了，而是断言有缺口**：事故 3 里加的那段**早退判据**（① 与 ② 之间判
`twoFactorEnabled`）使得当时**两个**候选用例都在 ① 就被拦下 —— 而它们都在**请求之前**就改了状态：

| 用例 | 状态变更发生的时刻 | 谁拦下它 |
|---|---|---|
| `adminResetBetweenChallengeAndVerifyIsRejected` | 请求**之前** | ① 之后的早退判据 |
| （当时没有第二条） | — | — |

⇒ ⑤ 的重读**没有被任何用例固定住**，`MfaUserResetWindow` 那类场景只在理论上成立。

**处理方式：补断言，而不是把这条记成"破坏无效"**（照 §G 事故 1 的先例：红指向测试时，修测试）。
两处新增：

1. `InMemoryRedisTestSupport` 加一个**一次性副作用钩子** `onGetAndDeleteKeyPrefix(prefix, action)`
   —— 在命中前缀的键被 `getAndDelete` 取走之后、返回之前执行一次。钩在 `getAndDelete` 上是因为
   ④（`consumeTicket`）**正好落在 ①↔⑤ 那个窗口里**，且它是这条流程里唯一一次票据读删。
   MockMvc 是**同步**的，用例没有任何别的办法从外面插进这个窗口。
2. `AuthMfaIT` 加 `adminResetInsideTheVerificationWindowIsCaughtByTheReRead`：把
   `mfaService.resetByAdmin(userId)` 挂到那个钩子上，断言 401 `MFA_TICKET_INVALID`，
   **外加反方向的守卫** `assertThat(redis.containsKey("auth:2fa-ticket:" + ticket)).isFalse();`
   —— 否则一个"恒返回 401"的实现也能让本用例变绿。

**在 H6 仍然生效的情况下复跑**：

```
[ERROR] AuthMfaIT.adminResetInsideTheVerificationWindowIsCaughtByTheReRead:287->verifyData:418
Response status expected:<401> but was:<200>
```

⇒ 这个新护栏**有牙齿**。还原 H6 之后 `AuthMfaIT` 12 条 + `MfaFailClosedIT` 4 条 = **16/16 绿**。

⚠️ **由此产生的新基准**：`AuthMfaIT.java` → `75cc90a7...`、`InMemoryRedisTestSupport.java` →
`51959e0c...`（两份都记在 `SHA1SUMS.test` 里）。**H1–H5 的观测取自旧基准**，见上面「两份基准」。

**还原**：`sha1sum -c` 输出 `OK`。

### 破坏 H7：删掉 ① 与 ② 之间的早退判据（**事故 3 的复现**）

**被守护的断言**：`AuthMfaIT.adminResetBetweenChallengeAndVerifyIsRejected`。

**failsafe**：

```
[ERROR] AuthMfaIT.adminResetBetweenChallengeAndVerifyIsRejected:269->verifyData:418
Response status expected:<401> but was:<500>
```

**500 的来源就是事故 3 里那个 `IllegalStateException`**（`resetByAdmin` 清空了
`totp_secret_encrypted`，验码分支看到"已启用却没有密钥"）⇒ 这一段早退判据**必要**。

⚠️ **同一次观测里，`adminResetInsideTheVerificationWindowIsCaughtByTheReRead`（H6 新增那条）
保持绿色** —— 它的重置挂在 ④ 那个钩子上，③ 执行时密钥还在。这一绿一红**合起来**才说明了
两处判据的分工：早退判据管"验码之前就已经改了"，⑤ 管"验码与消费票据期间改了"。
若只看 H7 这一条，很容易得出"有了早退判据、⑤ 就多余了"的错误结论 —— 而 H6 证明了 ⑤ 是**最终判据**。

**还原**：`sha1sum -c` 输出 `OK`。

### 破坏 H8：`MfaVerificationService` 的收尾改成 `updateById(fresh)`（**085 回归，本批最高风险项**）

**被守护的断言**：`UserIT.mfaVerifyDoesNotBumpVersion`（HTTP 层）与
`AuthMfaIT.verifyIssuesTokensAndStampsBothTimestamps` 的 `version` 断言（库层）。

**failsafe**：`UserIT` 7 条中 1 条红。

```
[ERROR] UserIT.mfaVerifyDoesNotBumpVersion:219
expected: 0
 but was: 1
```

即 `@Version` 被推进了 —— 管理端"读过某用户 → 该用户完成了一次二次验证 → 管理端编辑该用户"
必然收到 **409 VERSION_CONFLICT**，而错误信息指向一个根本不存在的原因（"他人修改"）。

⚠️ 本条的**层次**与 §G 的 G2 相同：这是**纯库层/HTTP 层**的性质，`AuthMfaIT` 那类"从 HTTP 进、
断言 Redis 与响应"的用例**看不见它**。这也是为什么它在 `UserIT` 里另有一层 HTTP 断言
（拿**流程之前**的版本号提交编辑必须成功）—— 库层断言与 HTTP 层断言各钉一半。

**还原**：`sha1sum -c` 输出 `OK`。

### 破坏 H9：两个时间戳拆成**两条**语句（各自调 `LocalDateTime.now(clock)`）

**被守护的断言**（计划表声称）：`AuthMfaIT.verifyIssuesTokensAndStampsBothTimestamps` 里
`assertThat(after.getLastLoginAt()).isEqualTo(after.getLast2faVerifiedAt())`。

**failsafe**：**12/12 全绿，不转红。**

**如实记，且这条负结果比它想证明的东西更重要**：本类用**冻结时钟**
（`FixedClockTestSupport`），`LocalDateTime.now(clock)` 恒返回同一瞬间 ⇒「拆成两条」
与「一条语句」在时间戳一致性上**在结构上不可区分**。这与 H2b 是**同一类**：
**"两条语句"与"一条语句"的差别是原子性，而原子性只能靠调用形状断言或真实并发来钉，
MockMvc 单线程里没有任何可观测差异。**

**⇒ 进一步：`AuthMfaIT.java:112` 那条"两者逐字相等"的断言是冗余的。** 因为同用例前两条
（`getLastLoginAt() == now(clock)` 与 `getLast2faVerifiedAt() == now(clock)`）已经**蕴含**了它：
两条各自等于同一个值 ⇒ 必然相等。任何能让它红的破坏，都会先让前两条红（见 H9b）。
如实记，不为了"让断言看起来有用"而保留一个不会独立转红的断言。

**还原**：`sha1sum -c` 输出 `OK`。

### 破坏 H9b：两条语句里的一条改用**系统时钟**（计划表外）

**破坏**：`.set(User::getLastLoginAt, LocalDateTime.now())`（无 `clock` 参数）。

**failsafe**：

```
[ERROR] AuthMfaIT.verifyIssuesTokensAndStampsBothTimestamps:111
expected: 2026-09-16T18:00 (java.time.LocalDateTime)
 but was: 2026-09-16T10:03:00.296179 (java.time.LocalDateTime)
when comparing values using 'ChronoLocalDateTime.timeLineOrder()'
```

⚠️ **先红的是第 111 行（`getLastLoginAt() == now(clock)`），不是"两者相等"那条** ——
把 H9 的结论从推理变成了实测：**时间戳这一族的牙齿在"值必须等于注入时钟的当前瞬间"
（即"不许偷偷用系统时钟"），而不在"两者相等"。** 冻结时钟下 18:00 与真实的 10:03 相差 8 小时，
一眼可辨；而若两条都用了系统时钟、只是相差几毫秒，那两条断言就都抓不到了。

**还原**：`sha1sum -c` 输出 `OK`。

### 破坏 H10：把**提交的码**写进失败审计的 detail

**被守护的断言**：`AuthMfaIT.failedVerificationIsAuditedAsSystem` 的最后一条 ——
`assertThat(row.getDetail()).doesNotContain(wrong);`

**破坏**：`failVerification` 增加一个 `String submitted` 形参，detail 拼成
`"二次验证失败（第 N/5 次），提交的码：" + submitted`。

**failsafe**：

```
[ERROR] AuthMfaIT.failedVerificationIsAuditedAsSystem:236
not to contain:
  "588591"
```

**为什么要守**：动态码是短时效的，而**恢复码是长期有效的凭证** —— 任何一条把它写进审计/日志的
实现，都等于把"能换一个会话的字符串"递给了能读审计表或日志文件的人。这条断言用的是
**提交的错误码**作为探针：它能抓到，意味着**正确码同样跑不掉**（同一条拼接语句）。

**还原**：`sha1sum -c` 输出 `OK`。

### 破坏 H11：失败审计的 `recordAsSystem` 改回 `record`

**被守护的断言**：`AuthMfaIT.failedVerificationIsAuditedAsSystem` 的 `actorId` / `actorName` 两条。

**failsafe**：

```
[ERROR] AuthMfaIT.failedVerificationIsAuditedAsSystem:230
Expecting actual not to be null
```

即 `row.getActorId()` 是 `null`（`record(...)` 取 `SecurityUtil.currentUserId()`，而本路径
已被 `SecurityFilterChain` 放行、`SecurityContext` 是空的）。

⚠️ **这正是 `AuditService` 自己的 javadoc 明确要避免的"无主体审计行"**：既归因不到人，
也无法与"用户被删除后 `actor_id` 悬空"区分 —— 事后审计时看到一行 `actor_id IS NULL` 的
`MFA_VERIFY_FAILED`，没有任何办法判断它是"系统写的"还是"数据坏了"。

**还原**：`sha1sum -c` 输出 `OK`。

### 破坏 H12：`lastLoginAt` 挪回 `AuthService` 的密码阶段

**被守护的断言**：`AuthMfaIT.loginReturnsChallengeInsteadOfTokens` 的最后一条 ——
`assertThat(userMapper.selectById(userId).getLastLoginAt()).isEqualTo(loginAtBefore);`

**破坏**：把 `challengeFor` 分支从 `lastLoginAt` 更新**之前**挪到**之后**。

**failsafe**：

```
[ERROR] AuthMfaIT.loginReturnsChallengeInsteadOfTokens:90
expected: 2026-09-16T10:04:21.144169 (java.time.LocalDateTime)
 but was: 2026-09-16T10:04:21.207373 (java.time.LocalDateTime)
```

**为什么要守**：`last_login_at` 常被用来判断"某账号最近活没活跃"。若在密码阶段就写，
**一次失败的（或从未完成的）二次验证会在库里看起来像一次成功登录** —— 而它在接口层
只返回了一个 `mfaRequired` 挑战，看不出差别。

⚠️ 同上，**第 90 行先红**（而不是 `AuthMfaIT` 成功分支里的时间戳相等断言）—— 又一次印证 H9 的结论。

**还原**：`sha1sum -c` 输出 `OK`。

### 破坏 H13：`MfaChallengeService.challengeFor` 恒返回 `Optional.empty()`

**被守护的断言**：整条 2FA 登录链 —— 这是"已启用 2FA 的账号**必须**走二次验证"的**唯一**判定点。

**failsafe**：12 条中 **8 条红**。

```
[ERROR] AuthMfaIT.disabledAccountCannotCompleteVerification:304->verifyData:418 Response status expected:<403> but was:<401>
[ERROR] AuthMfaIT.failedVerificationIsAuditedAsSystem:225
[ERROR] AuthMfaIT.loginReturnsChallengeInsteadOfTokens:80
[ERROR] AuthMfaIT.recoveryCodeIsSingleUseAndTheNextStillWorks:247->verifyData:418 Response status expected:<200> but was:<401>
[ERROR] AuthMfaIT.sameCodeCannotBeReplayedOnANewTicket:156->verifyData:418 Response status expected:<200> but was:<401>
[ERROR] AuthMfaIT.ticketIsSingleUse:136->verifyData:418 Response status expected:<200> but was:<401>
[ERROR] AuthMfaIT.verifyIssuesTokensAndStampsBothTimestamps:103->verifyData:418 Response status expected:<200> but was:<401>
[ERROR] AuthMfaIT.lockoutAfterFiveFailuresAndAutoRecovery:186 [第 1 次失败应当是码错误而不是锁定]
```

（`Tests run: 12, Failures: 8, Errors: 0` —— 未红的 4 条是
`verifyIsReachableWithoutJwt`、`otherMfaEndpointsStillRequireJwt`、
`adminResetBetweenChallengeAndVerifyIsRejected`、`adminResetInsideTheVerificationWindowIsCaughtByTheReRead`：
它们断言的都是**没有票据时应当被拒**，而本破坏恰好让所有账号都变成"没有票据"。
⚠️ 这 4 条在"票据根本不签发"这个错误世界里**因为错误的原因而变绿** —— 是本节观察 1 的又一例。）

⚠️ **红得最多的一条，缺陷方向却最"安静"**：绝大多数红说的是"本该 200 却 401"，
即**已启用 2FA 的账号拿不到票据、登不进来**（fail closed，用户会立刻报障）。
真正危险的方向是**反过来**：若 `challengeFor` 对已启用 2FA 的账号返回了空**而登录照常签发令牌**，
那就是"**静默降级为单因素**" —— 用户正常登录、审计没有异常、监控没有报错。
本仓没有一条断言能直接区分这两种方向（它们都表现为"某条 MFA 用例红了"），
**这是一个已知的、如实记下的缺口**：它由 `MfaChallengeService` 的类 javadoc +
`LoginResponseShapeIT`（非 2FA 响应逐字节不变）从**另一侧**守着，而不是由本步的破坏留痕守着。

**还原**：`sha1sum -c` 输出 `OK`；随后复跑完整 `mvn -B verify`：
surefire **695 / 0**、failsafe **323 / 0**、`BUILD SUCCESS`。

### 本节五处**如实记**的观察

1. **两次"破坏不转红"，两次都指向断言而不是破坏**（H6、H9），但**修法不同**：
   - H6 的缺口**可以堵**（加一条用例 + 替身的一次性钩子），因为"⑤ 的重读发生了"终究是可观测的
     ——它只需要一个能从外面插进 ①↔⑤ 窗口的手段。
   - H9 的缺口**在 MockMvc 里堵不上**（与 H2b 同源）：单线程下"两条语句"与"一条语句"没有
     任何可观测差异。能钉它的只有调用形状断言；而这一次**没有补**，理由是补了也只是把
     同一条冗余断言换个写法 —— 于是改为**删掉冗余断言、并把结论写进 H9**。
   「不转红」不是"破坏没做对"的同义词：**先分清是断言缺了、还是性质本身不可观测**，
   两者的处理方式相反。
2. **H2 与 H2b 的分野值得单独记**：我第一版把"完全不删"当成了 TOCTOU。两者在**代码形态上**
   只差一个 `delete`，在**缺陷分类上**一个是"行为错"、一个是"并发错"，
   在**可观测性上**一个被 IT 抓到、一个抓不到。⇒ 写破坏之前先问一句"这个缺陷的**受害者是谁**"，
   比"这行代码看起来像错的"更能定出它该在哪一层被抓到。
3. **H4 与 H4b、H9 与 H9b、H2 与 H2b 三对**说明：计划表里的破坏描述**本身就可能是错的**
   （"挪到之后"不构成缺陷、"拆成两条"不可观测、"换 get+delete"被误当成"只 get"）。
   破坏留痕的价值一半在"证明护栏有牙齿"，另一半在**"把计划表里想当然的那句话证伪"**。
4. **本步有两条护栏是被真实事故逼出来的、而不是设计出来的**：事故 3 → ① 后的早退判据（H7 守），
   H6 → ⑤ 的用例与替身钩子。两条都在**没有它们时**由一次真实的红暴露出来；
   而 H5（`@Transactional` 吞审计）说明**"少写一个注解"这类缺陷在行为层是看不见的**，
   只能靠"审计表里那条行数"这种**跨表断言**。
5. **IDE / m2e 的陈旧 `spotless:check` 快照在本步又出现了多次**（§G 观察 2 的同一回事）。
   本步多了一个新变体值得记：**破坏期间**它报的 diff 是**真实**的（H8 删掉了唯一使用
   `LambdaUpdateWrapper` 的那行 ⇒ import 真的变成未使用；H13 同理 `Duration`）。
   ⇒ 判据不是"忽略一切 spotless 诊断"，而是**`sha1sum` + 磁盘实际内容**：
   文件处于**基线态**时它的 diff 一律过期，文件处于**破坏态**时它的 diff 可能是真的。
   本步所有破坏都是直接调 `failsafe:integration-test` / `surefire:test` goal
   （绕过 `verify` 生命周期），故 `spotless:check` 在破坏期间从未参与，格式违规不影响观测；
   **还原之后**的完整 `mvn -B verify` 里 `spotless:check` 通过（退出码 0）。
