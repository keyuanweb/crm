# 实施计划：个人中心页面

**功能分支**: `070-personal-center`

**创建日期**: 2026-08-29

**状态**: 草稿

## 技术栈

- **前端**: React 18.2.0 + TypeScript + Ant Design 5.22.0 + Vite + Zustand
- **后端**: Spring Boot 3.2.0 + MyBatis-Plus 3.5.5 + Spring Security
- **数据库**: MySQL 8.0
- **构建工具**: 前端 Vite，后端 Maven

## 宪法检查

### 前端宪法检查

- **FRONTEND-001**: 所有页面必须使用 Ant Design 5.x 组件，不得引入其他 UI 库。
- **FRONTEND-002**: 所有 TypeScript 代码必须通过 `tsc --noEmit` 编译检查。
- **FRONTEND-003**: 所有状态管理必须使用 Zustand store，不得使用 Redux。
- **FRONTEND-004**: 所有 API 调用必须通过 `services/` 目录下的服务函数发起。
- **FRONTEND-005**: 所有页面必须支持响应式布局（移动端和桌面端）。

### 后端宪法检查

- **BACKEND-001**: 所有 Controller 必须使用 `@RestController` 和 `@RequestMapping` 注解。
- **BACKEND-002**: 所有 API 响应必须使用 `ApiResponse<T>` 包装。
- **BACKEND-003**: 所有用户输入必须进行参数校验（使用 `@Valid` 和 Jakarta Validation）。
- **BACKEND-004**: 所有敏感操作（密码修改、重置）必须记录审计日志。
- **BACKEND-005**: 所有服务方法必须进行权限校验（使用 `@PreAuthorize`）。

## 项目结构

### 前端文件

```
frontend/src/
├── pages/personal/
│   └── PersonalCenterPage.tsx          # 个人中心主页面
├── services/
│   └── personalService.ts              # 个人中心 API 服务（新增）
├── types/
│   └── personal.ts                     # 个人中心类型定义（新增）
└── store/
    └── authStore.ts                    # 认证状态管理（已有，需扩展）
```

### 后端文件

```
backend/src/main/java/com/crm/
├── controller/
│   └── PersonalCenterController.java   # 个人中心接口（新增）
├── dto/personal/
│   ├── PersonalInfoResponse.java       # 个人信息响应（新增）
│   └── UpdateDisplayNameRequest.java   # 更新显示名请求（新增）
└── service/
    └── PersonalCenterService.java      # 个人中心服务（新增）
```

## 实施策略

采用**增量实施**策略，按用户故事优先级分阶段交付：

1. **Phase 1 - 基础信息查看** (P1): 实现个人信息查看功能
2. **Phase 2 - 密码修改** (P1): 整合现有 ChangePasswordPage 功能
3. **Phase 3 - 显示名编辑** (P2): 实现显示名编辑功能
4. **Phase 4 - 安全信息展示** (P2): 展示账户安全相关信息

## 数据模型

### 个人信息响应（PersonalInfoResponse）

```json
{
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
```

## API 设计

### 个人中心接口

| 方法 | 路径 | 描述 | 权限 |
|------|------|------|------|
| GET | `/api/v1/users/me` | 获取当前用户信息 | 登录用户 |
| PUT | `/api/v1/users/me/display-name` | 修改显示名 | 登录用户 |
| PUT | `/api/v1/users/me/password` | 修改密码 | 登录用户（已有） |

## 风险与缓解

| 风险 | 影响 | 缓解措施 |
|------|------|----------|
| 现有 ChangePasswordPage 功能需要整合 | 前端工作量增加 | 复用现有组件，仅调整布局 |
| 密码修改后令牌失效可能导致用户困惑 | 用户体验下降 | 明确提示"修改成功后需要重新登录" |
| 显示名修改需要更新所有引用处 | 数据一致性风险 | 显示名仅用于展示，不影响业务逻辑 |
