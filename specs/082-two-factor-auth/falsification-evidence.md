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
