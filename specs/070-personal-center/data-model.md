# 数据模型：个人中心页面

**功能分支**: `070-personal-center`

**创建日期**: 2026-08-29

## 实体关系图

```
┌─────────────┐
│    User     │
├─────────────┤
│ id          │──→ 主键
│ username    │──→ 用户名（唯一，不可改）
│ passwordHash│──→ 密码哈希
│ displayName │──→ 显示名（可改）
│ role        │──→ 角色（ADMIN/SALES/SUPPORT）
│ departmentId│──→ 所属部门 ID
│ dataScope   │──→ 数据权限范围
│ enabled     │──→ 启用状态
│ lastLoginAt │──→ 最后登录时间
│ tokenVersion│──→ 令牌版本（密码修改后递增）
│ createdAt   │──→ 创建时间
└─────────────┘
```

## 数据模型定义

### PersonalInfoResponse（个人信息响应）

**用途**: 向后端前端返回当前登录用户的完整信息

**字段定义**:

| 字段 | 类型 | 描述 | 示例 |
|------|------|------|------|
| id | Long | 用户 ID | 1 |
| username | String | 用户名（只读） | "admin" |
| displayName | String | 显示名（可编辑） | "管理员" |
| role | String | 角色 | "ADMIN" |
| departmentId | Long? | 所属部门 ID | 1 |
| departmentName | String? | 所属部门名称 | "技术部" |
| dataScope | String | 数据权限范围 | "ALL" |
| enabled | Boolean | 启用状态 | true |
| lastLoginAt | LocalDateTime? | 最后登录时间 | "2026-08-29T10:00:00" |
| createdAt | LocalDateTime | 创建时间 | "2026-01-01T00:00:00" |
| passwordUpdatedAt | LocalDateTime? | 密码最后修改时间 | "2026-08-01T00:00:00" |

**业务规则**:

- `username` 只读，用户不可修改
- `displayName` 可编辑，长度限制 3~50 位
- `role` 和 `departmentId` 由管理员管理
- `passwordUpdatedAt` 从 `tokenVersion` 推算（无直接字段，可使用 `createdAt` 作为默认值）

### UpdateDisplayNameRequest（更新显示名请求）

**用途**: 用户提交显示名修改请求

**字段定义**:

| 字段 | 类型 | 必填 | 描述 | 校验规则 |
|------|------|------|------|----------|
| displayName | String | 是 | 新显示名 | 长度 3~50 位，不能为空 |

**校验规则**:

- 显示名不能为空
- 显示名长度必须在 3~50 位之间
- 显示名只能包含中文、英文、数字、下划线、空格

## 数据库变更

**无需数据库表结构变更**。

个人中心功能完全复用现有的 `user` 表结构：

- `user` 表已包含所有必需字段（`displayName`, `role`, `departmentId`, `dataScope`, `lastLoginAt`, `createdAt`, `tokenVersion` 等）
- 密码修改时间可通过 `tokenVersion` 变更日志推算，或使用 `updated_at` 字段作为近似值

## API 契约

### GET /api/v1/users/me

**描述**: 获取当前登录用户的个人信息

**请求头**:
```
Authorization: Bearer {accessToken}
```

**响应体**:
```json
{
  "code": 200,
  "message": "success",
  "data": {
    "id": 1,
    "username": "admin",
    "displayName": "管理员",
    "role": "ADMIN",
    "departmentId": 1,
    "departmentName": "技术部",
    "dataScope": "ALL",
    "enabled": true,
    "lastLoginAt": "2026-08-29T10:00:00",
    "createdAt": "2026-01-01T00:00:00",
    "passwordUpdatedAt": "2026-08-01T00:00:00"
  }
}
```

### PUT /api/v1/users/me/display-name

**描述**: 修改当前用户的显示名

**请求头**:
```
Authorization: Bearer {accessToken}
```

**请求体**:
```json
{
  "displayName": "新显示名"
}
```

**响应体**:
```json
{
  "code": 200,
  "message": "success",
  "data": null
}
```

**错误响应**:
```json
{
  "code": 400,
  "message": "显示名长度必须在 3~50 位之间",
  "data": null
}
```

### PUT /api/v1/users/me/password

**描述**: 修改当前用户的密码（已有接口，无需新增）

**请求头**:
```
Authorization: Bearer {accessToken}
```

**请求体**:
```json
{
  "oldPassword": "旧密码",
  "newPassword": "新密码"
}
```

**响应体**:
```json
{
  "code": 200,
  "message": "success",
  "data": null
}
```

## 安全考虑

1. **权限校验**: 所有接口必须校验用户登录状态，确保用户只能访问自己的信息
2. **密码安全**: 密码修改必须验证旧密码，新密码必须符合强度要求
3. **令牌失效**: 密码修改后，`tokenVersion` 字段递增，旧访问令牌自动失效
4. **审计日志**: 显示名修改和密码修改必须记录审计日志
