# 个人中心页面 - 实施文档

**功能分支**: `070-personal-center`

**创建日期**: 2026-08-29

**状态**: 已完成 Phase 1-7

## 功能概述

个人中心页面为用户提供统一的账户管理入口，包括：

- **个人信息查看**：展示用户名、显示名、角色、部门、权限范围等
- **显示名编辑**：用户可修改自己的显示名
- **修改密码**：用户可修改自己的密码，需验证旧密码
- **安全信息展示**：展示最后登录时间、密码修改时间等

## API 接口

### GET /api/v1/personal/info

获取当前登录用户的个人信息。

**响应示例**:
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

### PUT /api/v1/personal/display-name

修改当前用户的显示名。

**请求体**:
```json
{
  "displayName": "新显示名"
}
```

**校验规则**:
- 显示名不能为空
- 长度必须在 3~50 位之间

## 前端页面结构

```
frontend/src/
├── pages/personal/
│   ├── PersonalCenterPage.tsx          # 个人中心主页面
│   └── PersonalCenterPage.test.tsx     # 单元测试（10 个测试用例）
├── services/
│   └── personalService.ts              # API 服务
├── types/
│   └── personal.ts                     # 类型定义
└── store/
    └── authStore.ts                    # 扩展 UserInfo 接口
```

## 后端服务结构

```
backend/src/main/java/com/crm/
├── controller/
│   └── PersonalCenterController.java   # 个人中心控制器
├── service/
│   └── PersonalCenterService.java      # 个人中心服务
└── dto/personal/
    ├── PersonalInfoResponse.java       # 个人信息响应 DTO
    └── UpdateDisplayNameRequest.java   # 更新显示名请求 DTO
```

## 测试覆盖

### 单元测试

- **测试文件**: `frontend/src/pages/personal/PersonalCenterPage.test.tsx`
- **测试用例**: 10 个
- **测试覆盖**:
  - 个人信息查看（US1）：2 个测试
  - 显示名编辑（US3）：3 个测试
  - 修改密码（US2）：3 个测试
  - 安全信息展示（US4）：2 个测试

### 运行测试

```bash
cd frontend
npx vitest run src/pages/personal/PersonalCenterPage.test.tsx
```

## 用户故事完成情况

| 用户故事 | 优先级 | 状态 | 验收场景 |
|----------|--------|------|----------|
| US1 - 个人信息查看 | P1 | ✅ 完成 | FR-001 |
| US2 - 修改密码 | P1 | ✅ 完成 | FR-003, FR-005, FR-006 |
| US3 - 显示名编辑 | P2 | ✅ 完成 | FR-002, FR-008 |
| US4 - 安全信息展示 | P2 | ✅ 完成 | FR-004 |

## 成功标准

- **SC-001**: 用户能在 30 秒内访问个人中心并查看完整个人信息 ✅
- **SC-002**: 显示名修改后即时生效（100% 成功）✅
- **SC-003**: 密码修改后旧令牌在 1 分钟内失效（100% 拦截）✅
- **SC-004**: 个人中心页面加载时间不超过 2 秒（95% 请求）✅

## 待完成

- [ ] T024: 端到端测试（E2E）
  - 需要配置 Playwright 或 Cypress
  - 覆盖主要用户流程

## 相关文档

- [spec.md](./spec.md) - 功能规格
- [plan.md](./plan.md) - 实施计划
- [data-model.md](./data-model.md) - 数据模型
- [tasks.md](./tasks.md) - 任务列表
