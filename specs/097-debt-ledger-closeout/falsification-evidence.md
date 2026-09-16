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

```
（待填：命令 / 实际转红的用例名 / 失败信息逐字 / sha1 还原核对）
```

---

## §B D2 —— 台账**不得减少**（同一条断言的另一方向）

**破坏**：把白名单里 `system:manage` 一条删掉。

**期望**：该测试转红（**少一个 = 台账在骗人**：接完线忘更新白名单正是这么发生的）。

**实做读数**：

```
（待填）
```

> ⚠️ **D1 与 D2 必须都做**。只做 D1 的话，一个**单向**台账也会全绿——而单向台账恰恰是 ① 要防的那种骗人。

---

## §C D3 —— `ui:check` 守的是 `R6_ALLOWED` 的 **count**，不是块头注释

**破坏**：把 `R6_ALLOWED` 某条的 `count` 改错 1。

**期望**：`ui:check` 转红。

**这条破坏的用途是「证明边界」**：它证明 SC-097-005 说的是实话——**机器守的是 count**，
块头注释里的两个数**没有任何断言看着它** ⇒ ② 只能订正 + 留复算命令，**不能声称有护栏**。

**实做读数**：

```
（待填）
```

---

## §D D4 —— **反向**：另一条路确实走不通

**破坏**：给 `RequirePermissionCatalogTest` 补一条「字典里未被引用的码必须为空」的反向断言。

**期望**：**转红**（22 条未接线码）。

**⚠️ 这条的期望也是红，但它证明的不是「我的护栏有牙齿」**，而是「**SC-097-003 那条路确实走不通**」——
即本项选择「冻结台账」而非「必须为空」是**被观测支持的**，不是口味问题。

**实做读数**：

```
（待填）
```

**还原核对**：`RequirePermissionCatalogTest` 必须**逐字节**回到原文（`git diff` 为空 + `sha1sum` 相等），
且 SC-097-003 要求它与 `PermissionMatrixIT` **一行未改**。

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
（待填）
```

---

## §G ④/⑤ 的**订正不静默**自查（旧值仍可 grep 到；**零命中 = 静默改写**）

| # | 旧值 | 命令 | 命中行 |
|---|---|---|---|
| 1 | `88 个（V1–V89` | `grep -n "88 个（V1–V89" PROJECT_FEATURES.md` | 待填 |
| 2 | `94 个（001–095` | `grep -n "94 个（001–095" PROJECT_FEATURES.md` | 待填 |
| 3 | `89 / 7`（前端单测 / E2E） | `grep -n "89 / 7" PROJECT_FEATURES.md` | 待填 |
| 4 | `含测试共 174` | `grep -n "含测试共 174" PROJECT_FEATURES.md` | 待填 |
| 5 | `design/tasks，无 spec`（081 行） | `grep -n "design/tasks，无 spec" specs/README.md` | 待填 |
| 6 | 编号说明里 081 缺 spec 的那句 | `grep -n "081 仅有" specs/README.md`（立项时 `:137`，097 登记后 `:138`——**按锚不按行号**） | 待填 |
| 7 | `仍待处理`（§九） | `grep -n "仍待处理" PROJECT_FEATURES.md` | 待填 |

⚠️ 表里第 1/2 列的 `–` 是 **en dash**（原文字符），照抄，别敲成 `-`（敲错会得到「零命中」，
而那**恰好是静默改写的信号**——两种零命中必须分得清）。

**实做读数**：

```
（待填：逐条命中行）
```

---

## §H 门禁实跑读数（交付块的数据来源）

```
（待填：mvn -B verify 退出码 / INSTRUCTION% / surefire 净增
       前端四项覆盖率 / ui:check 冻结台账与 R6 自身 / i18n / menu / perms）
```

⚠️ **覆盖率读数要有「无第二写入者」的工区才能归因**（他人未跟踪的 `*.test.tsx` 会被 vitest 静默计入）。

---

## §I 本项**未**声明的（如实边界）

- **无手工冒烟**：本项不改端点、不加迁移、无运行时授权行为变更 ⇒ 不需要后端在跑，也**不声称**做过。
- **22 个码在真库里的授予行**只按迁移文本核对（`V46`/`V75` + `schema-h2.sql` 镜像），**未在真库复核**。
- **不涉及 `matchMedia`**：本项不改任何渲染分支（前端产品代码零改动），故不触及 jsdom 无布局引擎那条边界。
- **③ 不可回滚**：见 §E，以「删除前核实 + 留痕」代替「可回滚」，**不声称**删错能找回。
