# 实施计划：086「不可收口清单」收尾（096）

**Branch**: `appmod/java-upgrade-20260912235322` | **Date**: 2026-09-16 | **Spec**: [spec.md](./spec.md)
**调研**: [research.md](./research.md) | **验证**: [quickstart.md](./quickstart.md) | **留痕**: [falsification-evidence.md](./falsification-evidence.md)

---

## 1 摘要

把 086 的「不可收口清单」**逐行判到底**：2 行真缺口 → **建两族码并按 V81 判据① 补授**（零扩权、
零收窄，此后可勾选）；1 行 → **改前端**（审批按归属渲染，后端一行不改）；2 行 → **订正判据**
（不该设码）；1 行维持。技术路线：

1. 字典加 7 码 → 两个控制器由角色门改挂码 → `V90` 纯授权迁移按原放行集合补授（**先后端**）。
2. 前端登记、接线、审批按归属渲染（**后前端**）。
3. 086 清单两行判据订正 + 三处「靠数据范围过滤」不实表述订正（原文留痕）。

## 2 技术上下文

- **语言/框架**：Java 21 + Spring Boot（注解式鉴权 `@RequirePermission` + `PermissionAspect` AOP）；
  React + TypeScript + antd + vitest。
- **存储**：Flyway 迁移（MySQL 生产 / H2 测试镜像）；`role_permission(role_id, permission_code)`，
  **无唯一键**。
- **现有设施（一律复用，不新建）**：`@RequirePermission`、`hooks/usePermission.ts#hasPerm`、
  `hooks/usePerms.ts`、`constants/permissions.ts`、`scripts/check-perms.mjs`。
- **不新增**：无 DTO、无端点、无前端 hook/组件、**不产 `contracts/`**。
- **迁移**：新增 **V90**（纯授权 INSERT，无 DDL）。

## 3 章程符合性检查

| 原则 | 符合性 |
|---|---|
| 一 契约优先 | ✅ **零端点结构变更**（只加授权注解、撤类级注解）。权限码不是 OpenAPI 契约的一部分 ⇒ **不产 `contracts/`** |
| 二 分层架构 | ✅ 授权注解挂 **Controller 层**（与仓内既有 282 处同形）；**不**把 `ApprovalEngineService.checkApprover` 搬进 Controller、**不**给它加角色码（它是 Service 层的记录归属判定，与权限码是两层） |
| 三 数据完整性/安全/校验 | ✅ **「授权必须在服务端强制执行；仅隐藏 UI 绝不构成访问控制」** ⇒ 顺序固定为**先后端、后前端**；**不以撤门换扩权**（两族一律判据①，逐角色有 IT 断言看着）；**不给有数据范围判定的端点硬挂号** |
| 四 测试优先与门禁 | ✅ 后端分角色双向断言 + 两层 403 分离；前端三处用例；**定向破坏留痕**证明护栏有牙齿 |
| 五 简洁/可维护/可观测 | ✅ **YAGNI**：只建被端点真引用的 7 个码（**不建** `comment:update`）；不新增抽象；列表端点**已是分页的**，不碰；授权拒绝已由切面抛错，不新增日志 |

**Gate 结论**：无违规、**无需 `Complexity Tracking`**。

## 4 结构决策

**产 `research.md`，不产 `data-model.md` / `contracts/`**：本项无新实体、无新字段、无迁移建表
（V90 是纯授权 INSERT），无 DTO/端点结构变更。为凑齐工件而生成空壳文件正是「为了流程而流程」
（与 092/095 先例一致）。

## 5 实施顺序（7 次提交，各自可回退）

| # | 提交 | 内容 |
|---|---|---|
| 1 | `docs(096): 立项` | 6 件工件 + `specs/README.md` 模块表行 + `specs/roadmap.md` 行（**勾选框留空**） |
| 2 | `feat(096): 权限字典加 comment 与 custom_object_record 两族码` | `RoleConstants` 7 码（先合并码表，**失败快**） |
| 3 | `feat(096): 两族端点由类级角色门改挂权限码（V90 按原放行集合补授）` | 两个 Controller + `V90` + `schema-h2.sql` 段头 + `SchemaParityIT` 加 `"90"` + `README` 迁移表 V90 行 |
| 4 | `test(096): PermissionEnforcementIT 追加两族分角色断言` | 含两层 403 分离、逐角色零行为变化 |
| 5 | `feat(096): 前端登记新码并收口评论与自定义对象记录` | `permissions.ts` + `CommentSection.tsx` + `CustomObjectRecordPage.tsx` + i18n |
| 6 | `fix(096): 审批中心操作链接改按任务归属渲染` | `ApprovalCenterPage.tsx`（后端一行不改） |
| 7 | `test(096): 前端两族用例与审批归属用例` + `docs(096): 清单判据订正与勾选` | 3 个测试文件 + 086 `research.md` 订正 + `check-perms.mjs` 白名单理由 + `tasks.md` 勾选 + roadmap 勾选与交付后记 |

**顺序不是风格偏好**：第 5 步的前置是第 3 步已绿——前端先改而后端没挂码会是**真收窄**。

## 6 关键实现要点

### 6.1 后端

- `CommentController`：**删类级注解**（连同 `@PreAuthorize` 的 import 若不再需要），
  3 个方法各挂 `@RequirePermission`；类 javadoc 的「刻意保留」段改写为「已建码族并明确授予名单」，
  **原文逐字保留 + 带日期 ⚠️ 块**。
- `CustomObjectController`：5 个记录端点的注解**替换**（不是叠加）；
  定义面 6 个端点一行不动；javadoc 订正「且靠数据范围过滤」。
- `RoleConstants`：`comment:*` 新开 `permGroup("评论协作", …)`（评论在菜单树里**没有**菜单，
  故不能像别的组那样挂菜单名）；`custom_object_record:*` 加进既有「自定义对象」组并订正其注释。
- `V90`：照 `V87` 的批量 INSERT 范式（`JOIN (SELECT … UNION SELECT …)`），
  头注释记录**判据①的推导**与「(角色,码) 组合不存在」的核对结论。

### 6.2 前端

- `CommentSection.tsx`：`canDelete = (c) => can[PERMS.commentDelete] && (user?.role === 'ADMIN' || user?.id === c.authorId)`
  ——**保留** `role === 'ADMIN'` 那一处（它是**数据归属规则的例外**，镜像 `CommentService.delete`，
  `hasPerm` 表达不了「是 ADMIN 但不是作者」），故 `check-perms.mjs` 白名单 **count 仍为 1**，
  只**改理由**。发布区按 `comment:create` 渲染。
- `CustomObjectRecordPage.tsx`：`usePerms([...])` 收口新建/编辑/删除三处。
- `ApprovalCenterPage.tsx`：`const user = useAuthStore((s) => s.user)`；
  渲染条件加 `&& row.approverId === user?.id`。**不引入权限码**（这不是权限判断）。

### 6.3 不可触碰

`.specify/feature.json`（共享单槽指针）、任何**已应用**的迁移（含 `V87` 头注释）、
任何已交付规格的历史勾选行。

## 7 验证

```bash
cd backend  && mvn spotless:apply && mvn -B verify
cd frontend && pnpm typecheck && pnpm lint && pnpm i18n:check && pnpm menu:check && pnpm perms:check && pnpm ui:check && pnpm test:coverage
```

- 后端：退出码 0；JaCoCo INSTRUCTION ≥ **0.73**（阈值未下调）。
- 前端：覆盖率四项对阈值 **33.6 / 47.2 / 21.4**；`ui:check` 冻结台账**不增长**
  （**以实跑读数为准**——095 记的 54 是历史值，调研实跑为 56）。
- **`mvn spotless:apply` 先跑**（`spotless:check` 在 verify 相位、早于 failsafe）。
- **定向破坏**：9 条（见 `quickstart.md` §2），逐条观测转红、逐条**逐字节还原**、破坏期间不提交。
- 手工冒烟需 8081 后端，**须用户放行**；未放行则不声称做过。

## 8 风险

| 风险 | 缓解 |
|---|---|
| **补授范围写错 ⇒ 扩权/收窄**（唯一高危错法） | 判据①：范围 = 原闸门放行集合。逐角色双向断言 + **专门做「漏授 SALES_REP」的破坏**证明断言会红 |
| **把「换码」误当「撤门」⇒ 读路径扩权** | 评论**建读码**而非撤门不设码（撤门会让 VIEWER/ANALYST 获得发言权——类注释已警告） |
| **前端先改、后端没挂码 ⇒ 真收窄** | 提交顺序固定「先后端、后前端」 |
| `V90` 重复 INSERT | 每条 INSERT 前核对 (角色,码) 组合不存在；头注释记明补授清单 |
| `SchemaParityIT` 第三道检查（无 DDL 迁移须有段头） | `schema-h2.sql` 落段头并做定向破坏验证会红 |
| 复用陈旧数字（冻结台账/覆盖率/迁移数） | 一律**实跑取值**，读数写进 `tasks.md` 交付块 |

## 9 明确不做

见 `spec.md` §4（7 条，逐条附理由）。
