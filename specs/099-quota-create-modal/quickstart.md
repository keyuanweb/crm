# 快速验证：配额创建表单改成列表页内弹窗（099）

---

## 0 前置

```bash
cd frontend && pnpm install        # 若 node_modules 已就绪可跳过
```

⚠️ 本项**零后端改动**（无端点、无 DTO、无迁移、无权限码）⇒ **不跑 `mvn`**。
只读冒烟与单跑的 e2e 需要 8081 上有后端（见 §4、§5）。

## 1 门禁（唯一权威判据）

```bash
cd frontend && pnpm typecheck && pnpm lint && pnpm i18n:check && pnpm menu:check \
  && pnpm perms:check && pnpm ui:check && pnpm zh:check && pnpm test:coverage && pnpm build
```

**逐道判据**：

| 门禁 | 判据（改动前基线见 `falsification-evidence.md` §0） |
|---|---|
| `i18n:check` | **exit 0**；`zh-CN` 与 `en` **键数相等**且 = **2962**（删 `btnBack` 后）；**路由仍 58 条 / 清单仍 56 项** |
| `menu:check` | **exit 0**；**56 项不变**（本项不动菜单与后端） |
| `perms:check` | **exit 0**；**68 码 / 8 文件 9 处不变**（本项不加权限码） |
| `ui:check` | **exit 0**；**冻结台账 54 不得增长**；`R2` 候选**仍 55**（用原语 ⇒ 候选池不变）；文件数 **271**、`Form.Item` **303**（⚠️ **实做订正 2026-09-16**：立项期推算写的 `304 → 298` **有误**，提交 2 后实跑为 **303**；理由见 `falsification-evidence.md` §0）（**以实跑为准**） |
| `zh:check` | **exit 0**；命中 **0 未登记**、台账 **266 处 / 4 条不变**；文件数 **268**（只印不判） |
| `test:coverage` | **exit 0**；四项覆盖率对 **33.6 / 47.2 / 21.4** 均高于且**未改阈值**；**不与上次的小数位比** |
| `typecheck` / `lint` / `build` | **exit 0**；⚠️ `build` **不证明**「按钮指向的路由存在」（见 §3 的 D5） |

⚠️ **覆盖率读数要有「无第二写入者」的工区**才能归因（他人未跟踪的 `*.test.tsx` 会被 vitest 静默计入）——
判据：`git status --porcelain` 只有本项工件。

### 1.1 单测（本项唯一的行为层证据）

```bash
cd frontend && pnpm exec vitest run src/pages/quotas/QuotaListPage.form.test.tsx
```

5 组断言见 `plan.md` §验证。⚠️ 若整文件跑得慢，**用文件内的 `it(name, fn, 60_000)` 放宽耐心，不动断言**。

## 2 只读冒烟（**不写库**）

前端已在跑时（Vite 5173）：

1. 打开 `http://localhost:5173/quotas`；
2. 点工具栏「**新建**」⇒ **弹窗**应打开（宽度约 **800px**）、标题为原「创建配额」文案；
3. 依次确认 **6 个字段**：年份（默认当前年）/ 季度（Q1–Q4）/ 团队 ID / 销售 ID / 配额金额 / 期间；
4. **必填留空点「创建」** ⇒ 应出现字段级错误提示（年份 / 金额 / 期间三条）；
5. 「取消」或 Esc ⇒ 弹窗关闭、**留在列表页**；
6. ⚠️ **不点提交**（提交会往共享开发库写入一条配额，**按仓规需用户明确同意**）。

**判据**：弹窗开合正常、6 个字段与文案与改造前一致、校验拦截生效。
⚠️ 这一条证明的是「**界面可用**」；它**不**证明「提交后刷新」——后者由 §1.1 的单测 4 承担。

## 3 定向破坏清单（逐条做、逐条还原；破坏期间不提交）

见 `plan.md` §验证的 D1–D7 表。**还原判据**：
本应等于 HEAD 的文件用 `git hash-object <file>` == `git rev-parse HEAD:<path>`（内容级相等，**不称逐字节一致**）；
本项**有意未提交**的工件用「还原后复跑读数与破坏前**逐字相同**」。
**一律 `cp` 备份回写，禁用 `git checkout`**（它会吞掉同文件里本项有意未提交的订正）。

⚠️ **D5 的预期是「仍绿」** —— 它不是护栏验证，而是**空档的实测**，结论登记在 `research.md` §5.3。

## 4 e2e（只跑受影响的一条，不跑整套）

```bash
cd frontend && pnpm exec playwright test e2e/module-page-auth.spec.ts
```

**前置**：8081 上有后端。**判据**：全绿，且清单里**不再有** `'/quotas/create'`。
⚠️ **不跑整套**（其余 spec 可能写共享开发库）。没跑就**如实写明未跑**。

## 5 删除的**可核**判据（替代「没有门禁」的那条）

```bash
cd /e/code/crm
# ①a 旧路由的**活引用**零命中（判据只认**代码位置**：导航目标 / 路由定义 / 页面导入 / 元素使用）
grep -rnE "navigate\('/quotas/create'|path=\"quotas/create\"|import\('\./pages/quotas/QuotaCreatePage'\)|<QuotaCreatePage" \
  frontend/src frontend/e2e && echo "❌ 仍有活引用" || echo "✓ 活引用零命中"
# ①b 而**注释里的历史提及是允许且预期的**（订正不静默要求旧值仍可 grep 到）——
#     本条**不是**判据，是「命中都在注释里」的核对：期望恰好 2 个文件、逐条确认落在注释内
grep -rn "quotas/create" frontend/src frontend/e2e
# ② 旧文件已不存在
test -f frontend/src/pages/quotas/QuotaCreatePage.tsx && echo "❌ 文件还在" || echo "✓ 已删除"
# ③ 列表页里只剩「对比」那一条 navigate
grep -n "navigate('/quotas" frontend/src/pages/quotas/QuotaListPage.tsx
# ④ 死键已从两侧同批删除
grep -rn "btnBack" frontend/src/i18n/ | grep -i quota && echo "❌ 仍在" || echo "✓ 已删"
```

## 6 订正不静默自查（交付时必跑）

```bash
cd /e/code/crm
grep -rn "001–098\|001~098" specs/roadmap.md specs/README.md README.md   # 期望非零
grep -rn "97 个功能模块\|97（001–098" README.md PROJECT_FEATURES.md      # 期望非零
grep -rn "quotas/create" specs/083-engineering-consolidation/quickstart.md  # 期望非零（原文保留）
grep -rn "QuotaCreatePage" PROJECT_FEATURES.md                           # 期望非零（原文保留 + ⚠️ 块）
grep -rn "2963" PROJECT_FEATURES.md                                      # 期望非零（原文保留 + ⚠️ 块）
```

**零命中 = 静默改写**，须逐条确认非零并记入 `falsification-evidence.md` §I。
