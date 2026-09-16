# 快速验证：五笔登记遗留账的收口（097）

---

## 0 前置

```bash
cd backend  && mvn spotless:apply        # 必先跑：spotless:check 在 verify 相位、早于 failsafe
cd frontend && pnpm install              # 若 node_modules 已就绪可跳过
```

⚠️ 本项**只改一行生产侧代码（`check-ui.mjs` 的注释）**、**只加一条测试**、**不加迁移** ⇒ 前置与 096 相同，无额外准备。

## 1 门禁（唯一权威判据）

```bash
cd backend  && mvn -B verify
cd frontend && pnpm typecheck && pnpm lint && pnpm i18n:check && pnpm menu:check && pnpm perms:check && pnpm ui:check && pnpm test:coverage
```

**判据**：

| 门禁 | 判据 |
|---|---|
| `mvn -B verify` | 退出码 **0**；JaCoCo INSTRUCTION ≥ **0.73**（阈值**未下调**）；surefire 净增 = 本项新增用例数（本项 **1 个测试类 / 2 个 `@Test`**，须以「基线 + 2」对得上） |
| `test:coverage` | 四项覆盖率对阈值 **33.6 / 47.2 / 21.4** 均**高于**且**未改阈值**；**不与上次的小数位比** |
| `ui:check` | 冻结台账**不得增长**（**以实跑读数为准**）；**R6 自身 = 10** 应与订正后的块头注释对得上 |
| `perms:check` | 四项通过（本项**不动前端 `permissions.ts`**） |
| `i18n:check` / `menu:check` | 本项**不改文案、不改菜单** ⇒ 应与 096 交付时逐字相同 |

⚠️ **`mvn -B verify` 的报告目录要先清**（`rm -rf backend/target/failsafe-reports backend/target/surefire-reports`），
否则机器里混着旧报告时净增数会被污染。
⚠️ **覆盖率读数要有「无第二写入者」的工区才能归因**（他人未跟踪的 `*.test.tsx` 会被 vitest 静默计入）。

## 2 定向破坏（**4 条**，逐条转红后**逐字节还原**）

每条都要：① 记录破坏前的字节基准（`git diff` 为空）；② 观测到**具体哪条用例/断言**转红（**点得出名字**）；
③ 逐字节还原并复跑转绿；④ **破坏期间不提交**。

| # | 断言 | 破坏 | 期望转红 |
|---|---|---|---|
| D1 | `UnwiredPermissionCodeTest`：台账**不得增长** | 在 `RoleConstants` 的某组加 `perm("demo:unused", "…")` | 该测试，且失败信息里点名 **`demo:unused`** |
| D2 | 同上：台账**不得减少** | 把白名单里 `system:manage` 一条删掉 | 该测试（**少一个 = 台账在骗人**，接完线忘更新白名单正是这么发生的） |
| D3 | `ui:check` 守的是 `R6_ALLOWED` 的 **count** | 把某条 `count` 改错 1 | `ui:check`。**这条证明「机器守的是 count、不是块头注释」**——即 SC-097-007 的边界 |
| D4 | **SC-097-003 的反向**：裁决未被推翻 | 把 `RequirePermissionCatalogTest` 的反向断言补成「必须为空」 | 该测试转红（22 条未接线码）——**证明本项确实没走那条路**。验完**逐字节还原** |

⚠️ **D4 的期望也是红**：它证明的不是「我的护栏有牙齿」，而是「**另一条路确实走不通**」。
⚠️ **D1/D2 必须两条都做**。只做 D1 的话，一个**单向**台账也会全绿——而单向台账恰恰是 ① 要防的那种骗人。

### 2.1 无断言可观测转红的三项（②/④/⑤）——改用**可核替代**

②/④/⑤ 是**文档订正**，**没有断言看着它们**（机器守的是 `R6_ALLOWED` 的 count，不是块头注释里的数；
`PROJECT_FEATURES.md` 与 081 的工件更没有任何测试）。⇒ 照章程「**不沉默、不假称有护栏**」，
改用**读者可自证的替代**：

```bash
# ② 复算 R6：条目数与 count 合计（读者自己数，不采信注释）
awk '/^const R6_ALLOWED = \[/,/^\]/' frontend/scripts/check-ui.mjs | grep -c "file:"        # 期望 8
awk '/^const R6_ALLOWED = \[/,/^\]/' frontend/scripts/check-ui.mjs \
  | grep -o "count: [0-9]*" | awk '{s+=$2} END{print s}'                                    # 期望 10

# ④/⑤ 订正不静默的判据：旧值仍可 grep 到（**零命中 = 静默改写**）
grep -n "实测 11 处"            frontend/scripts/check-ui.mjs
grep -n "9 处不该翻译"           frontend/scripts/check-ui.mjs
grep -n "88 个（V1–V89"          PROJECT_FEATURES.md
grep -n "94 个（001–095"         PROJECT_FEATURES.md
grep -n "89 / 7"                 PROJECT_FEATURES.md
grep -n "含测试共 174"            PROJECT_FEATURES.md
grep -n "081 仅有 design.md\|design/tasks，无 spec" specs/README.md
```

**这七条命令的结果要进 `falsification-evidence.md`**（② 的两条读数 + 七条旧值命中行）。
⚠️ 注意 grep 里的 `–` 是 **en dash**（原文字符），照抄，别敲成 `-`。

## 3 手工冒烟

**无**。本项不含运行时授权行为变更、不改端点、不加迁移 ⇒ **不需要后端在跑、不需要真库**。
本项**不声称**做过任何手工验证（`spec.md` §6 已如实写明）。

⚠️ 连带的**已知空白**照旧记着：22 个码**在真库里的实际授予行**本项**只按迁移文本核对**
（`V46`/`V75` + `schema-h2.sql` 镜像），**不在真库复核**。若要补，属另一次手工冒烟。

## 4 ③ 的删除（不产生提交，故**不在门禁里**）

`frontend/src/App.tsx.bak` 未跟踪且被 `.gitignore:45`（`*.bak`）覆盖 ⇒ 删除**不影响任何门禁读数**。
⇒ 它的「证据」只能是**删除前**取的读数，两条：

```bash
git check-ignore -v frontend/src/App.tsx.bak                          # 期望命中 .gitignore:45 的 *.bak
git log --all --oneline --find-object=<blob>                          # 期望**空**
for c in $(git log --format=%h --all -- frontend/src/App.tsx); do \
  [ "$(git show $c:frontend/src/App.tsx | wc -l)" = 498 ] && echo "命中 $c"; done   # 期望无输出
```

⚠️ **`git hash-object -w` 会把对象写进对象库**，之后 `git cat-file -e` 必然成功——**那不是「历史里有」的证据**。
判可达性只能用 `--find-object`。本项执行时踩过这一步，`research.md` §3 已写明。
