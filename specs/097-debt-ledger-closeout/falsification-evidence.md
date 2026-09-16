# 定向破坏与订正留痕（097）

**日期**：2026-09-16 立项；**§0 以下各节的读数留到交付时填**（避免写入未实测的数字）。
**方法**：每条声称是护栏的断言，都要**被观测到具体哪条用例转红**；点不出名字即说明护栏只盖住了判据的一半。
破坏期间**不提交**，每条**逐字节还原**后复跑转绿。

---

## §0 立项阶段的既存读数（已实测，可直接采信）

来自 `research.md`（命令逐条附在那里，此处只汇总结论）：

| 项 | 读数 | 出处 |
|---|---|---|
| 字典码 / 被注解引用 / 差集 | 144 / 122 / **22** | §1.1 |
| 22 码的性质 | **A 类 11**（有操作，由相邻码放行）+ **B 类 11**（全仓无对应操作） | §1.4 |
| 码校验路径数 | **1**（`@RequirePermission` + `PermissionAspect`；`permissionsOf` 只被切面与 `TokenService` 调） | §1.2 |
| R6 台账 | `R6_ALLOWED` **8 条** / count 合计 **10** / `CANDIDATE_READINGS.R6 = 10`；块头注释 **11 / 9** | §2.1 |
| 22 码中已有授权行的 | **20**（`V46`/`V75`）⇒ 删码会撞 `PermissionMatrixIT` | §1.3 |
| `.bak` | 498 行；未跟踪；被 `.gitignore:45` 覆盖；**不在任何可达提交里**；独有差异只有 `/board` → `/data-vision` | §3 |
| 081 | 实际 **13 角色 / 三表 / `permission_code` 码表**；`tasks.md` 写的是**另一套模型**（8 条偏差） | §4 |
| `V72` / `specs/069` | **全历史从未存在**（两条 `--diff-filter=A` 均空） | §5.1 |
| `PROJECT_FEATURES.md` §一 | **4 行**已被 096 作废；其余 6 行逐条重测一致 | §5.2 |

---

## §A D1 —— 台账**不得增长**（`UnwiredPermissionCodeTest`）

**破坏**：在 `RoleConstants` 某组加 `perm("demo:unused", "演示：无人使用")`。

**期望**：`UnwiredPermissionCodeTest` 转红，且失败信息**点名 `demo:unused`**。

**实做读数**：

**破坏**（唯一一处改动，`RoleConstants.java:457`）：

```java
- permGroup("集成中心", perm("integration:manage", "集成中心管理")),
+ permGroup("集成中心", perm("integration:manage", "集成中心管理"), perm("demo:unused", "演示：无人使用")),
```

```
$ cd backend && mvn -B test -Dtest=UnwiredPermissionCodeTest -DfailIfNoSpecifiedTests=false
[INFO] Running com.crm.security.UnwiredPermissionCodeTest
[ERROR] Tests run: 2, Failures: 1, Errors: 0, Skipped: 0 <<< FAILURE!
[ERROR] com.crm.security.UnwiredPermissionCodeTest.everyUnwiredCodeIsInTheFrozenLedger <<< FAILURE!
Expecting empty but was: ["demo:unused"]
	at …UnwiredPermissionCodeTest.everyUnwiredCodeIsInTheFrozenLedger(UnwiredPermissionCodeTest.java:168)
[INFO] BUILD FAILURE
```

⇒ **转红，且失败信息点名 `demo:unused`**（不是「某个集合非空」这种看不出是谁的报错）。

**还原核对**：`git checkout --` 后 `sha1sum` = `0a6f80fca215ea57e8e59be94906e7abbc1bcd4e`
（与破坏前**逐字相等**）、`git diff` 为空。

---

## §B D2 —— 台账**不得减少**（同一条断言的另一方向）

**破坏**：把白名单里 `system:manage` 一条删掉。

**期望**：该测试转红（**少一个 = 台账在骗人**：接完线忘更新白名单正是这么发生的）。

**实做读数**：

**破坏**：把 `LEDGER` 里 `system:manage` 那条整条删掉（前一条的 `),` 补成 `));`）。

```
$ cd backend && mvn -B test -Dtest=UnwiredPermissionCodeTest -DfailIfNoSpecifiedTests=false
[ERROR] Tests run: 2, Failures: 2, Errors: 0, Skipped: 0 <<< FAILURE!
[ERROR] …UnwiredPermissionCodeTest.everyUnwiredCodeIsInTheFrozenLedger <<< FAILURE!
Expecting empty but was: ["system:manage"]
[ERROR] …UnwiredPermissionCodeTest.everyLedgerEntryCarriesItsNatureAndReason <<< FAILURE!
Expected size: 11 but was: 10 in:
[INFO] BUILD FAILURE
```

⇒ **两条断言同时红**，且各自指向不同的错法：① 「未接线却不在台账」点名 `system:manage`；
② A/B 的 **11 → 10**。**这是「双向」不是修辞**：台账少一条会被**两个独立断言**分别按住，
不是同一条断言换个说法。

**还原核对**：`sha1sum` = `8f970a4697f4c04dfd3d055cf275f40327670f32`（与破坏前**逐字相等**）、`git diff` 为空；
复跑 `Tests run: 2, Failures: 0` ⇒ **BUILD SUCCESS**。

> ⚠️ **D1 与 D2 必须都做**。只做 D1 的话，一个**单向**台账也会全绿——而单向台账恰恰是 ① 要防的那种骗人。

---

## §C D3 —— `ui:check` 守的是 `R6_ALLOWED` 的 **count**，不是块头注释

**破坏**：把 `R6_ALLOWED` 某条的 `count` 改错 1。

**期望**：`ui:check` 转红。

**这条破坏的用途是「证明边界」**：它证明 SC-097-005 说的是实话——**机器守的是 count**，
块头注释里的两个数**没有任何断言看着它** ⇒ ② 只能订正 + 留复算命令，**不能声称有护栏**。

**实做读数**：

**破坏**：把 `R6_ALLOWED` 里 `src/pages/mail/MailSyncPage.tsx` 的 `count: 3` 改成 `count: 2`。

```
$ cd frontend && pnpm -s ui:check
扫描 272 个产品文件（其中 126 个 tsx）、304 个 Form.Item

✗ UI 规范校验失败：1 处问题

【R6 白名单陈旧】1 处
  src/pages/mail/MailSyncPage.tsx
      白名单登记 2 处，实际命中 3 处。
      第 161 行：placeholder="sales@corp.com"
      第 167 行：placeholder="imap.corp.com"
      第 173 行：placeholder="smtp.corp.com"
    修复：该文件新增了违规。请修掉，或更新白名单的 count 与理由。

---- exit=1 ----
```

⇒ **红，且逐行点名**。**机器守的确实是 `count`**。

### §C(ii) D3-ii —— 反向对照：块头注释里那个数**没有**断言看着它

**破坏**：把块头注释里 `grep -c "file:"` 的期望值 **`8` 改成 `999`**（`check-ui.mjs:425`）。

```
$ cd frontend && pnpm -s ui:check
✓ UI 规范校验通过（白名单内冻结的既存债 56 处，未新增违规）
---- exit=0 ----
```

⇒ **注释被改成明显错的值，门禁仍然绿**。**这才是 SC-097-005 的证据形态**——它不是「我们没找到断言」，
而是「**把注释改错，观测不到任何红**」。故 ② 的处置**只能是**：订正原文 + 留**读者可自证的复算命令**
（§F），**不声称此处有护栏**。

**还原核对（两半各自的读数不同，如实记）**：
- 两次还原都用 `git checkout -- frontend/scripts/check-ui.mjs`，还原后 `git status` / `git diff` **均为空**；
- **但 `sha1sum` 不相等**：破坏前 `d2b06abb…`、还原后 `06d545c5…`。**原因已查明**：本仓 `core.autocrlf` 为真，
  `git checkout` 会把该文件的工作区副本写成 **CRLF**（`file` 报 "with CRLF line terminators"），
  而破坏前的工作区副本是 **LF**（此前由工具写入，与 blob 同形）。
- **判据改用内容级**：`git cat-file -p HEAD:frontend/scripts/check-ui.mjs | sha1sum` = `d2b06abb…`
  ⇒ **归一化后与 HEAD 逐字节相同**，仓库里存的内容**一个字节都没变**。
- ⚠️ **本节不写「逐字节一致」**：对 Java 那三件成立（`sha1sum` 相等），对这一件**只在归一化意义上成立**。
  两者混为一谈会被后来的读者当成同一种还原强度。

---

## §D D4 —— **反向**：另一条路确实走不通

**破坏**：给 `RequirePermissionCatalogTest` 补一条「字典里未被引用的码必须为空」的反向断言。

**期望**：**转红**（22 条未接线码）。

**⚠️ 这条的期望也是红，但它证明的不是「我的护栏有牙齿」**，而是「**SC-097-003 那条路确实走不通**」——
即本项选择「冻结台账」而非「必须为空」是**被观测支持的**，不是口味问题。

**实做读数**：

**破坏**：在 `everyAnnotatedCodeIsGrantableFromTheDictionary` 的 `orphans` 断言之后补三条：

```java
    Set<String> unused = new TreeSet<>(dictionary);
    unused.removeAll(usages.keySet());
    assertThat(unused).as("D4 反向：字典里有、注解没用的码必须为空").isEmpty();
```

```
$ cd backend && mvn -B test -Dtest=RequirePermissionCatalogTest -DfailIfNoSpecifiedTests=false
[ERROR] Tests run: 1, Failures: 1, Errors: 0, Skipped: 0 <<< FAILURE!
[ERROR] …RequirePermissionCatalogTest.everyAnnotatedCodeIsGrantableFromTheDictionary <<< FAILURE!
java.lang.AssertionError:
[D4 反向：字典里有、注解没用的码必须为空]
Expecting empty but was: ["approval:approve", "approval:create", "approval:delete", "approval:update",
    "campaign:export", "contract:delete", "customer:transfer", "email:create", "email:delete",
    "email:send", "email:update", "export:delete", "follow_up:delete", "invoice:create",
    "invoice:delete", "invoice:update", "opportunity:export", "quota:delete", "quote:delete",
    "segment:manage", "system:manage", "ticket:approve"]
[INFO] BUILD FAILURE
```

⇒ **转红，失败信息逐条列出全部码**。条数由失败文本本身复算（`sort -u | wc -l` 于括注内的码字面量）：
**22 个**，与冻结台账（`UnwiredPermissionCodeTest.LEDGER`）**逐字吻合**。
**这条读数的价值**：22 这个数**不是**只由 grep 得出的——它由**真代码的差集**独立复现了一次，
且顺序、成员完全一致。⇒ SC-097-003「必须为空走不通」是**被观测支持的结论**，不是口味。

**还原核对**：`RequirePermissionCatalogTest` 逐字节回到原文——
`sha1sum` = `07dd26d4cd00e4da0f0a41cd3ddba9f483b4927a`（与破坏前**逐字相等**）、`git diff` 为空，
复跑 `Tests run: 1, Failures: 0` ⇒ BUILD SUCCESS。
且 SC-097-003 要求它与 `PermissionMatrixIT` **一行未改**——以 096 交付时的提交为基线对照：

```
$ git diff --stat 81eefac..HEAD -- backend/src/test/java/com/crm/security/RequirePermissionCatalogTest.java \
                                        backend/src/test/java/com/crm/integration/PermissionMatrixIT.java
（空）
```

---

## §E ③ 的删除（**不产生提交**，留痕是唯一凭据）

`frontend/src/App.tsx.bak` 未跟踪且被 `.gitignore:45`（`*.bak`）覆盖 ⇒ 删除**不影响任何门禁读数**，
也无法用 `git` 回滚。**删除不可逆**这件事**明写在此**。

**删除前**取的两条读数（照 `quickstart.md` §4 的命令）：

```
$ ls -l frontend/src/App.tsx.bak
-rw-r--r-- 1 Administrator 197121 22354 Aug 23 23:55 frontend/src/App.tsx.bak     # 498 行
$ git check-ignore -v frontend/src/App.tsx.bak
.gitignore:45:*.bak	frontend/src/App.tsx.bak
$ git ls-files --error-unmatch frontend/src/App.tsx.bak
error: pathspec 'frontend/src/App.tsx.bak' did not match any file(s) known to git
$ git log --all --oneline -- frontend/src/App.tsx.bak | wc -l
0
$ git hash-object frontend/src/App.tsx.bak              # 注意：不带 -w
f3e8c6013f169b2f44ace54276a8eea06a957d43
$ git log --all --oneline --find-object=f3e8c6013f169b2f44ace54276a8eea06a957d43 | wc -l
0
```

⚠️ **本次读数里有一项会被误读，必须先说明**：

```
$ git cat-file -e f3e8c6013f169b2f44ace54276a8eea06a957d43 && echo 存在
存在
$ git rev-list --objects --all | grep -c f3e8c60…      # 从任何 ref 可达吗
0
$ git fsck --unreachable --no-progress | grep -c f3e8c60…
1
$ ls -l .git/objects/f3/e8c6013f169b2f44ace54276a8eea06a957d43
-r--r--r-- 1 Administrator 197121 6732 Sep 16 19:03 …/f3/e8c6013f169b2f44ace54276a8eea06a957d43
```

**这个 blob 确实在对象库里，但那是「本项调研自己写进去的」**：mtime `2026-09-16 19:03`（今天）、
`fsck` 判 **unreachable**、`rev-list --objects --all` **0 命中**。成因是 `research.md` §3 记的那个坑——
立项调研时先跑了 `git hash-object -w`（**带 `-w`**），对象因此落库。
⇒ **判「历史里有没有」只能用 `--find-object`（查可达性）**；用 `cat-file -e`（查对象存在性）会得到
**由本次调研自己制造的假阳性**。上面那条 `cat-file` 读数是**故意留下的反面样本**。

（尺寸也对得上：磁盘 22354 字节 − 498 行 × 1 字节 CRLF = 21856 = `git cat-file -s` 的读数。）

**第三条读数（删除当时做的更严复核，见 `research.md` §3 末尾的 ⚠️）**：
按**行**粒度比「`.bak` 独有的行 vs 全历史 + 现行」⇒ **19 行**，**全部是静态 `import` 句**；
18 个落点今天仍在同一路径，第 19 个 `pages/board/KpiBoardPage.tsx` 由 `68a14d5` 删除、可从其父版本取回。
⇒ **无独有信息**在更严的粒度上依然成立。

> 立项期已取过一次**同形**读数（记在 `research.md` §3，含那次踩到的 `hash-object -w` 循环论证坑）；
> 这里要的是**删除当时**的一份，两份并存、互不替代。

**删除后**：`ls frontend/src/App.tsx.bak` 应报 No such file；`git status --porcelain` **不应多出任何条目**
（这正是「不产生提交」的证据）。

```
$ rm frontend/src/App.tsx.bak
$ ls frontend/src/App.tsx.bak
ls: cannot access 'frontend/src/App.tsx.bak': No such file or directory
$ git status --porcelain        # 删除前 3 条 = 删除后 3 条（且 3 条都是本次要提交的文件）
 M PROJECT_FEATURES.md
 M specs/097-debt-ledger-closeout/falsification-evidence.md
 M specs/097-debt-ledger-closeout/research.md
```

⇒ **条目数无增减**：`.bak` 从未被跟踪、又被 `.gitignore:45` 覆盖，删它**不产生任何提交**。
`App.tsx.bak` 的全部凭据就是本节的读数——**它已不存在于任何地方，包括 git 历史**。

---

## §F ② 的**可核替代**（无断言可观测转红，故改用读者可自证）

```bash
awk '/^const R6_ALLOWED = \[/,/^\]/' frontend/scripts/check-ui.mjs | grep -c "file:"          # 8
awk '/^const R6_ALLOWED = \[/,/^\]/' frontend/scripts/check-ui.mjs \
  | grep -o "count: [0-9]*" | awk '{s+=$2} END{print s}'                                      # 10
grep -n "实测 11 处"  frontend/scripts/check-ui.mjs       # 订正后仍应命中（原文留痕）
grep -n "9 处不该翻译" frontend/scripts/check-ui.mjs       # 同上
```

**实做读数**：

```
$ awk '/^const R6_ALLOWED = \[/,/^\]/' frontend/scripts/check-ui.mjs | grep -c "file:"
8
$ awk '/^const R6_ALLOWED = \[/,/^\]/' frontend/scripts/check-ui.mjs | grep -o "count: [0-9]*" | awk '{s+=$2} END{print s}'
10
$ grep -n "实测 \*\*11 处" frontend/scripts/check-ui.mjs
408: * <p>⚠️ 这条规则实测 **11 处，其中 9 处不该翻译**——它们是**格式/单位示例**
$ grep -n "9 处不该翻译" frontend/scripts/check-ui.mjs
408: * <p>⚠️ 这条规则实测 **11 处，其中 9 处不该翻译**——它们是**格式/单位示例**
```

⇒ 条目数 **8** / count 合计 **10**（与 `CANDIDATE_READINGS.R6 = 10` 一致）；旧值 `11 / 9` **仍在 408 行**
（原文留痕，不是静默改写，也不是「被删掉了所以 grep 不到」）。

---

## §G ④/⑤ 的**订正不静默**自查（旧值仍可 grep 到；**零命中 = 静默改写**）

| # | 旧值 | 命令 | 命中行 |
|---|---|---|---|
| 1 | `88 个（V1–V89` | `grep -n "88 个（V1–V89" PROJECT_FEATURES.md` | **56**（留痕行） |
| 2 | `94 个（001–095` | `grep -n "94 个（001–095" PROJECT_FEATURES.md` | **56**（留痕行） |
| 3 | `89 / 7`（前端单测 / E2E） | `grep -n "89 / 7" PROJECT_FEATURES.md` | **56**（留痕行） |
| 4 | `含测试共 174` | `grep -n "含测试共 174" PROJECT_FEATURES.md` | **44**（变动表）与 **57**（留痕行） |
| 5 | `design/tasks，无 spec`（081 行） | `grep -n "design/tasks，无 spec" specs/README.md` | **111** |
| 6 | 编号说明里 081 缺 spec 的那句 | ``grep -n '`081` 仅有' specs/README.md``（立项时 `:137`，097 登记后 `:138`——**按锚不按行号**） | **138** |
| 7 | `仍待处理`（§九） | `grep -n "仍待处理" PROJECT_FEATURES.md` | **254**（原标题被引在处置标题里） |

⚠️ 表里第 1/2 列的 `–` 是 **en dash**（原文字符），照抄，别敲成 `-`（敲错会得到「零命中」，
而那**恰好是静默改写的信号**——两种零命中必须分得清）。

**实做读数**：

| # | 命中行 |
|---|---|
| 1 | `PROJECT_FEATURES.md:56` |
| 2 | `PROJECT_FEATURES.md:56` |
| 3 | `PROJECT_FEATURES.md:56` |
| 4 | `PROJECT_FEATURES.md:44` 与 `:57` |
| 5 | `specs/README.md:111` |
| 6 | `specs/README.md:138` |
| 7 | `PROJECT_FEATURES.md:254` |

（1/2/3 落在同一行不是巧合——那行就是**逐字引用这四个旧值**的那行留痕；
4 有两个命中：一处是**引用**、一处是**表格里那一格已按新值改**后仍留的原文引用。）

> ⚠️ **立项期本表第 6 行的命令写错了，此处订正（原文在上一句里逐字保留）**：
> 立项时写的是 `grep -n "081 仅有" specs/README.md` ——**这个模式永远零命中**，
> 因为原文是 `` `081` 仅有 ``（`081` 两侧有反引号）。零命中在**本节自己的口径**下会被读成「静默改写」，
> 而实际上**改的是 grep 模式、不是文件**。正确写法：``grep -n '`081` 仅有' specs/README.md`` ⇒ **138**。
> **教训与本节第 1 条同源**：`grep` 的**模式边界要先自证**（先 `grep` 一个必然命中的更宽模式，
> 再收紧）——否则「我没搜到」会被当成「它不存在」。**这一条是本次真被咬到的一次**，故在此写明。

---

## §H 门禁实跑读数（交付块的数据来源）

**全部读数为实跑取值，不沿用任何历史数字**（见仓规「一个数字住在好几个地方」）。
取值日期 **2026-09-16**，工区即本次交付的工区。

### 归因前提：工区**无第二写入者**

```
$ git status --porcelain
 M specs/097-debt-ledger-closeout/falsification-evidence.md
 M specs/README.md
 M specs/roadmap.md
```

三个改动**均为本项工件**；**无未跟踪的 `*.test.tsx`**（他人未跟踪的测试文件会被 vitest **静默计入**
总数 ⇒ 制造假绿）。故下面的 `91 / 464` 可归因到本项工区。

### 后端（`cd backend`）

```
$ mvn -B verify            # exit=0
[INFO] Tests run: 697, Failures: 0, Errors: 0, Skipped: 0     ← surefire（:1319）
[INFO] Tests run: 325, Failures: 0, Errors: 0, Skipped: 0     ← failsafe（:4772）
[INFO] Spotless.Java is keeping 779 files clean - 0 needs changes to be clean …（:4780）
[INFO] All coverage checks have been met.                      ← jacoco:check（:4787）
[INFO] BUILD SUCCESS                                           ← :4789
[INFO] Total time:  01:58 min
```

| 项 | 读数 | 判据 / 对照 |
|---|---|---|
| `mvn -B verify` | **exit 0 / BUILD SUCCESS** | 硬门禁 |
| surefire | **697**，Failures·Errors·Skipped **全 0** | 096 基线 **695** + 本项 `UnwiredPermissionCodeTest` **2 个用例** = 697，**对得上** |
| failsafe | **325**，全 0 | 本项**不新增 IT** ⇒ 与 096 **相同**，符合预期 |
| JaCoCo INSTRUCTION | **0.8109** | ≥ 阈值 **0.73**；由 `target/site/jacoco/jacoco.csv` 的 `INSTRUCTION_MISSED/COVERED` 汇总复算（非只读日志） |
| spotless | 779 files clean | ⚠️ `spotless:check` 在 `verify` 相位、**早于 failsafe**；本项改的是文档与测试，无 Java 格式改动 |
| JaCoCo 覆盖门禁 | **被判定过** | `All coverage checks have been met.` 是成功判据；**没有这行 = 门禁根本没被判定**，不能把「没搜到某串」当成「不存在结论」 |

⚠️ **`-DargLine` 会静默废掉 JaCoCo**（代理被挤掉 ⇒ `jacoco.exec` 不生成、覆盖率门禁**空过**而构建全程成功无报错）。
本项**未传** `-DargLine`；`jacoco:prepare-agent` 的 `argLine set to -javaagent:…jacoco.agent…` 在日志 **:9** 可见，
`Loading execution data file …\target\jacoco.exec`（:4776 / :4785）确认数据文件真的被读到 ⇒ 上表的 INSTRUCTION 是**被测出来的**，不是空过。

### 前端（`cd frontend`，**七道门禁逐条 exit 0**）

| 命令 | exit | 实跑输出 |
|---|---|---|
| `pnpm typecheck` | 0 | （无输出） |
| `pnpm lint` | 0 | （无输出） |
| `pnpm i18n:check` | 0 | `✓ 语言资源一致：zh-CN 2935 键 / en 2935 键；菜单路由与清单双向对齐（路由 58 条 / 清单 56 项，粗粒度别名 3 条）` |
| `pnpm menu:check` | 0 | `✓ 菜单清单是最新的（56 个菜单项，来源：RoleConstants.MENU_TREE）` |
| `pnpm perms:check` | 0 | `✓ 权限判定接线校验通过（68 个权限码；8 个文件含已登记的 ADMIN 判断，共 9 处）` |
| `pnpm ui:check` | 0 | `扫描 272 个产品文件（其中 126 个 tsx）、304 个 Form.Item` / `✓ UI 规范校验通过（白名单内冻结的既存债 56 处，未新增违规）` |
| `pnpm test:coverage` | 0 | `Test Files 91 passed (91)` / `Tests 464 passed (464)` |

**覆盖率四项（`All files` 行）对阈值**：

| | Stmts | Branch | Funcs | Lines |
|---|---|---|---|---|
| **实测** | **71.28** | **75.25** | **39.24** | **71.28** |
| 阈值（**未改**） | 33.6 | 47.2 | 21.4 | 33.6 |
| | ✅ | ✅ | ✅ | ✅ |

- **阈值一个都没动**（96 / 95 的先例一致）；判据是**与阈值比**，**不与上一次的小数位比**。
- ⚠️ **Branch 会差 0.01**：同一棵树同一条命令两次跑，**只有 Branch** 会抖（其余三项逐字相同）。
  上表取**交付时那一次**；这一项**原因未查明**，若与别的记录差 0.01 **不代表有人改过代码**。
- **`ui:check` 冻结台账 = 56，R6 自身基线 = 10** —— 本项**不碰** `check-ui.mjs` 的 `R6_ALLOWED`（只改块头注释，见 §C/§C(ii)）；
  台账**未增长**（输出逐字为「未新增违规」）。

### 破坏还原后**复跑**了一次后端全量

D1–D4（含 D3-ii，共 **5 个观测**）全部还原之后，**又跑了一次 `mvn -B verify`**：
**exit 0 / surefire 697 / failsafe 325 / INSTRUCTION 0.8109 / BUILD SUCCESS** ——
与破坏前**读数相同**。故上表不是「破坏期间的读数」，也不是「还原了一半的读数」。

⚠️ **本节不声称的**：`e2e` **没跑**（本项不改端点、不改后端行为，且 `e2e` 打的是**已在跑的后端**，
跑绿也不等于本次改动被端到端验证）⇒ 见 §I。

---

## §I 本项**未**声明的（如实边界）

- **无手工冒烟**：本项不改端点、不加迁移、无运行时授权行为变更 ⇒ 不需要后端在跑，也**不声称**做过。
- **22 个码在真库里的授予行**只按迁移文本核对（`V46`/`V75` + `schema-h2.sql` 镜像），**未在真库复核**。
- **不涉及 `matchMedia`**：本项不改任何渲染分支（前端产品代码零改动），故不触及 jsdom 无布局引擎那条边界。
- **③ 不可回滚**：见 §E，以「删除前核实 + 留痕」代替「可回滚」，**不声称**删错能找回。
