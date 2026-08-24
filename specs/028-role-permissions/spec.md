# Feature Specification: 角色权限管理

**Feature Branch**: `028-role-permissions`

**Created**: 2026-08-23

**Status**: Draft

**Input**: User description: "角色权限管理：角色 CRUD + 角色菜单可见配置 + 角色数据操作权限配置 + 数据范围；登录后按角色返回菜单与操作权限，前端菜单动态过滤、操作按钮显隐"

## User Scenarios & Testing *(mandatory)*

### User Story 1 - 角色管理 (Priority: P1)

管理员可在"角色管理"页创建/编辑/删除角色，配置角色基本信息（名称/编码/描述/默认数据范围/启用状态）。

**Why this priority**: 当前角色是 User.role 硬编码字符串（ADMIN/SALES/SUPPORT），无法按业务自定义；角色管理提供可扩展的角色基础，是权限体系的地基。

**Independent Test**: 可独立验证——管理员创建"区域经理"角色，列表显示，编辑名称后保存生效，删除后消失。

**Acceptance Scenarios**:

1. **Given** 管理员打开角色管理页，**When** 点击"新增角色"填写名称/编码/数据范围并保存，**Then** 角色出现在列表。
2. **Given** 角色列表有角色，**When** 编辑名称/描述并保存，**Then** 列表更新。
3. **Given** 非内建角色，**When** 删除，**Then** 角色消失且用户无法再关联该角色。

### User Story 2 - 菜单可见配置 (Priority: P1)

每个角色可勾选可见菜单（客户/销售/交易/基础/营销服务/数据分析/系统管理各菜单项）；登录后用户仅看到所属角色勾选的菜单，未勾选菜单不可见不可进入。

**Why this priority**: "不同角色显示不同菜单"是权限可视化的核心诉求——销售看不到审计/回收站，客服只看客服相关菜单。

**Independent Test**: 可独立验证——给"客服"角色仅勾选客户+客户服务菜单，该角色用户登录后侧栏只显示对应菜单，直接访问未授权 URL 被拒。

**Acceptance Scenarios**:

1. **Given** 角色 A 仅勾选"客户"菜单，**When** 该角色用户登录，**Then** 侧栏仅显示客户相关菜单。
2. **Given** 角色 A 未勾选"订单"菜单，**When** 用户直接访问 /orders，**Then** 跳回首页或提示无权限。
3. **Given** 管理员修改角色菜单后，**When** 该角色用户刷新，**Then** 菜单按新配置显示。

### User Story 3 - 数据操作权限配置 (Priority: P1)

每个角色可勾选数据操作权限点（如 客户:创建/删除/导入、订单:回款、用户:管理、审批:通过 等）；前端按权限显隐操作按钮，后端对关键写操作校验权限。

**Why this priority**: "不同角色设置数据操作"——如普通销售不可删除客户、不可导入；操作权限控制按钮级可见性与服务端兜底。

**Independent Test**: 可独立验证——角色"只读销售"仅勾选查看权限，该角色用户登录后客户页无"新增/删除/导入"按钮，直接调删除 API 返回 403。

**Acceptance Scenarios**:

1. **Given** 角色无"客户:创建"权限，**When** 该角色用户打开客户页，**Then** 无"新增客户"按钮。
2. **Given** 角色无"订单:回款"权限，**When** 直接调回款 API，**Then** 后端返回 403。
3. **Given** 角色有全部权限（ADMIN），**When** 登录，**Then** 所有操作按钮可见可用。

### Edge Cases

- 内建角色（ADMIN）不可删除、菜单/权限不可缩减（兜底全量）。
- 角色删除时被用户引用：阻止删除或提示先改用户角色。
- 用户未分配有效角色：默认无菜单（仅首页）或沿用 SALES 默认。
- 权限点后端校验缺失时前端显隐是第一道防线（纵深防御，不替代服务端校验）。
- 菜单与权限配置变化需重新登录/刷新生效（fetchMe 重新拉取）。

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: 系统必须提供角色管理（CRUD），角色含 编码/名称/描述/默认数据范围（ALL/DEPT/SELF）/启用状态；内建 ADMIN 角色不可删除。
- **FR-002**: 系统必须支持按角色配置可见菜单（角色-菜单多对多），登录后 `GET /auth/me` 返回当前用户角色的可见菜单列表。
- **FR-003**: 系统必须支持按角色配置数据操作权限点（角色-权限多对多），`GET /auth/me` 返回操作权限码列表。
- **FR-004**: 前端左侧菜单必须按用户可见菜单动态过滤（未勾选菜单不显示）；直接访问未授权路由跳回首页或提示。
- **FR-005**: 前端操作按钮（新增/删除/导入/回款/审批等）必须按用户操作权限码显隐。
- **FR-006**: 后端必须对关键写操作（删除/导入/回款/审批/用户管理）按权限码校验，未授权返回 403。
- **FR-007**: 用户管理页角色选择必须来自角色列表（替代硬编码字符串），角色禁用/删除时用户角色回退。
- **FR-008**: 数据范围：角色配置默认 data_scope，创建用户/编辑用户时可选继承角色默认数据范围或单独设置。

### Key Entities

- **Role**: id, code（唯一）, name, description, dataScope, enabled, builtIn。
- **RoleMenu**: roleId, menuKey（菜单标识）。
- **RolePermission**: roleId, permissionCode（操作权限码）。
- **User.role**: 关联 Role.code（字符串保持兼容）。

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: 角色 CRUD 正确率 100%——创建/编辑/删除角色各验证 5 次。
- **SC-002**: 菜单过滤正确率 100%——配置 3 种角色菜单组合，登录后侧栏菜单与配置一致（抽查 10 次）。
- **SC-003**: 按钮显隐正确率 100%——按权限码断言新增/删除/导入按钮显隐（抽查 10 次）。
- **SC-004**: 后端权限校验正确率 100%——无权限直接调 API 返回 403（抽查 5 次）。
- **SC-005**: 内建角色保护正确率 100%——ADMIN 不可删除、菜单权限不可缩减（抽查 5 次）。
- **SC-006**: 无回归——既有 ADMIN/SALES/SUPPORT 用户登录菜单行为不变（ADMIN 全量），后端测试全通过。

## Assumptions

- 菜单标识（menuKey）与前端路由路径对齐（如 customers/orders/users/reports 等），在 contracts/role-permissions.md 定义完整清单。
- 操作权限码格式 `实体:动作`（如 customer:create、order:payment、user:manage），清单在契约文档定义。
- 内建角色 seed：ADMIN（全菜单全权限 ALL）、SALES（业务菜单+业务操作 SELF）、SUPPORT（客服菜单+客服操作 DEPT），与现有行为兼容（SC-006）。
- 前端 `fetchMe` 返回 { role, menus, permissions }；JWT 不放权限（token 保持轻量）。
- 后端权限校验采用注解/切面（@RequirePermission）或 service 内检查，本期覆盖关键写操作（删除/导入/回款/审批/用户与角色管理）。
- 未授权路由访问：前端路由守卫跳回首页（后端仍 403 兜底）。
