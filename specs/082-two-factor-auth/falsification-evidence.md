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
