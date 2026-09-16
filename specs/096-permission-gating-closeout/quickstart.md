# 快速验证：086「不可收口清单」收尾（096）

---

## 0 前置

```bash
cd backend  && mvn spotless:apply        # 必先跑：spotless:check 在 verify 相位、早于 failsafe
cd frontend && pnpm install              # 若 node_modules 已就绪可跳过
```

## 1 门禁（唯一权威判据）

```bash
cd backend  && mvn -B verify
cd frontend && pnpm typecheck && pnpm lint && pnpm i18n:check && pnpm menu:check && pnpm perms:check && pnpm ui:check && pnpm test:coverage
```

**判据**：

| 门禁 | 判据 |
|---|---|
| `mvn -B verify` | 退出码 **0**；JaCoCo INSTRUCTION ≥ **0.73**（阈值**未下调**）；surefire/failsafe 净增数与本项新增用例数**对得上** |
| `test:coverage` | 四项覆盖率对阈值 **33.6 / 47.2 / 21.4** 均**高于**且**未改阈值**；**不与上次的小数位比** |
| `ui:check` | 冻结台账**不得增长**（**以实跑读数为准**） |
| `perms:check` | 四项通过（白名单 count 未变、理由已更新） |
| `i18n:check` | 双语双向一致 |
| `menu:check` | 菜单双射 |

⚠️ **`mvn -B verify` 的报告目录要先清**（`rm -rf backend/target/failsafe-reports backend/target/surefire-reports`），
否则机器里混着旧报告时净增数会被污染。
⚠️ **覆盖率读数要有「无第二写入者」的工区才能归因**（他人未跟踪的 `*.test.tsx` 会被 vitest 静默计入）。

## 2 定向破坏（**9 条**，逐条转红后**逐字节还原**）

每条都要：① 记录破坏前的字节基准（`sha1sum` 或 `git stash` 前的 `git diff` 为空）；
② 观测到**具体哪条用例/断言**转红（**点得出名字**，点不出名字说明护栏只盖住判据的一半）；
③ 逐字节还原并复跑转绿；④ **破坏期间不提交**。

| # | 断言 | 破坏 | 期望转红 |
|---|---|---|---|
| D1 | `RequirePermissionCatalogTest`（注解用码 ⊆ 字典） | 给某端点挂一个**不在字典**的码 | 该测试，且报出 `orphans` |
| D2 | `PermissionMatrixIT`（已授予码 ⊆ 字典） | `V90` 里 INSERT 一个字典里没有的码名 | 该 IT |
| D3 | 评论：SALES_REP **无码** ⇒ `PERMISSION_DENIED` | `V90` 把 `comment:*` **也授给 SALES_REP** | 零行为变化断言（**这是「不扩权」的正面证据**） |
| D4 | 记录面：ANALYST **仍被拒** | `V90` 把 `custom_object_record:*` 授给 ANALYST | 同上 |
| D5 | 两层 403 分离 | 让数据范围拒绝抛 `PERMISSION_DENIED`（或改断言只判 403） | 分离断言 |
| D6 | `SchemaParityIT`：迁移须被镜像 | 加 `V90` 但**不**加进 `MIRRORED_MIGRATIONS` | 该 IT |
| D7 | `SchemaParityIT`：无 DDL 迁移须有段头 | 加进集合但**不**在 `schema-h2.sql` 落段头 | 该 IT |
| D8 | `CustomerDetailPage.perm.test.tsx`（评论删除） | 删掉 `canDelete` 的**码**判据、只留归属判断 | 无码作者仍能看见删除 ⇒ 该用例 |
| D9 | `ApprovalCenterPage` 归属用例 | 渲染条件改回 `row.status === 'PENDING'` | 非被分配人又看见按钮 ⇒ 该用例。⚠️ 用例**必须桩住取数**（mock `fetchApprovalTodos` 返回一条 `approverId != 我` 的 PENDING 任务）：真端点是按 `approverId` 过滤的（`ApprovalEngineService.todos`），不桩的话这条断言在今天的行为下**打不出红**——见 `spec.md` US3 的 2026-09-16 订正 |

**可选补做**（若 T016 登记了记录面的码）：把 `CustomObjectRecordPage` 的 `usePerms` 判据去掉，
删除链接恒渲染 ⇒ 该用例转红。

## 3 手工冒烟（**需用户放行**，未放行则不做、也不声称做过）

⚠️ 按仓规**不得擅自重启可能归并行会话所有的共享后端**（8081）。需要 8081 上有后端时**先问用户**。

分角色令牌打两族真端点：

| 角色 | `GET /api/v1/comments` | `DELETE /api/v1/comments/{id}` | `GET /api/v1/custom-objects/{id}/records` |
|---|---|---|---|
| ADMIN | 200 | 200 | 200 |
| SALES | 200 | 200（作者） | 200 |
| SUPPORT | 200 | 200（作者） | **403 `PERMISSION_DENIED`** |
| SALES_REP | **403 `PERMISSION_DENIED`** | 403 | **403 `PERMISSION_DENIED`** |
| ANALYST / VIEWER | **403 `PERMISSION_DENIED`** | 403 | **403 `PERMISSION_DENIED`** |

**三层判据必须分清**：

1. **401** = 无令牌；
2. **403 + `PERMISSION_DENIED`** = 权限码拒绝（本项新增的闸门）——**这才是「码生效」的证据**；
3. **403 + `FORBIDDEN`** = 数据范围/归属拒绝（服务层，如「删别人的评论」）——**不是码的证据**。

⇒ 只断言 HTTP 403 会让「码没生效、恰好被范围兜住」与「码正常工作」**看起来一样**。

**另外必须试**：拿 ADMIN 令牌进角色页，确认这 **7 个新码在页面上勾得出来**（这是本项对「锁死」的兑现：
新增的**只是可勾选性**，不是权限本身）。
