# Research: 数据权限增强模块

**Branch**: `012-data-permission` | **Date**: 2026-08-22

## 1. 部门模型

**Decision**: `department` 表：id、name、parent_id（空=顶级）、逻辑删除/乐观锁/时间戳。部门树 = 内存构建（量级小，全量加载一次）；"部门及下级" = 递归收集子孙。删除校验：有成员（user.department_id 引用）或子部门 → 409。

**Rationale**: 树形单上级（spec 假设）；全量加载构建树避免递归查询；删除防护保证组织完整性。

## 2. 行级数据权限解析

**Decision**: `User.data_scope ∈ SELF/DEPT/DEPT_AND_CHILD/ALL`（默认 SELF，ADMIN 强制 ALL）。`DataPermissionService.resolveVisibleOwnerIds(userId)`：查询当前用户（含 department_id/data_scope）→ 按 scope 解析可见 owner 集合：
- SELF → [userId]
- DEPT → 本部门所有成员 id
- DEPT_AND_CHILD → 本部门及全部子孙部门成员 id
- ALL → 空集合（表示不过滤，含公海）
返回集合供 CustomerService 过滤（`owner_id IN (...)`；ALL 不过滤）。

**Rationale**: 集中解析避免各 Service 重复实现；ALL 用"空=不过滤"约定（避免大集合）。

## 3. 过滤应用点

**Decision**: `CustomerService.page`（原全量列表）与 011 的 `pool`（公海不受限）之外，新增按权限过滤的客户列表：`CustomerService.page` 改为默认应用权限（ALL 之外过滤 owner），011 的"我的客户"（owner=本人）与公海保持不变；详情/编辑/删除校验：owner 在可见集合或已共享（共享用户只读——编辑/删除仍拒绝）。新端点 `GET /customers`（权限过滤版）直接替换既有 page 行为。

**Rationale**: 单点修改既有 page 行为最简；共享只读在写操作校验（FR-DP06/DP07）。

## 4. 客户共享

**Decision**: `customer_share` 表：customer_id、shared_to_user_id、shared_by、created_at、逻辑删除；唯一约束（customer_id+shared_to_user_id，非删除时 active_key 生成列模式）。共享管理：归属者（owner=本人）或 ADMIN 可共享/取消；共享用户可见详情（只读）且出现在"共享给我的客户"列表。写操作（编辑/删除）校验 owner 归属（共享用户 403）。

**Rationale**: 归属者+管理员管理共享；只读共享防越权修改；unique 防重复共享。

## 5. 契约与权限

**Decision**: 契约写入 `contracts/data-permission.md`。部门 CRUD/用户部门与 scope 设置：仅 ADMIN；客户共享：归属者+ADMIN；"共享给我"列表：所有登录用户。前端用户管理页扩展部门选择与数据权限下拉；客户列表页扩展共享弹窗。

**Rationale**: 与既有权限模式一致；服务端强制授权（章程原则三）。
