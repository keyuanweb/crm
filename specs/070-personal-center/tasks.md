# 任务列表：个人中心页面

**功能分支**: `070-personal-center`

**创建日期**: 2026-08-29

**优先级**: P1（基础功能）> P2（增强功能）

---

## Phase 1: Foundational（基础设施）

> **目标**: 完成后端 API 和前端类型定义

- [x] **T001**: 创建后端 `PersonalCenterController.java`
  - **描述**: 创建个人中心控制器，实现 `GET /api/v1/personal/info` 和 `PUT /api/v1/personal/display-name` 接口
  - **文件**: `backend/src/main/java/com/crm/controller/PersonalCenterController.java`
  - **依赖**: 无
  - **估计工时**: 2h

- [x] **T002**: 创建后端 `PersonalCenterService.java`
  - **描述**: 创建个人中心服务层，实现获取个人信息和修改显示名的业务逻辑
  - **文件**: `backend/src/main/java/com/crm/service/PersonalCenterService.java`
  - **依赖**: T001
  - **估计工时**: 2h

- [x] **T003**: 创建后端 DTO - `PersonalInfoResponse.java`
  - **描述**: 创建个人信息响应 DTO，包含用户完整信息
  - **文件**: `backend/src/main/java/com/crm/dto/personal/PersonalInfoResponse.java`
  - **依赖**: 无
  - **估计工时**: 0.5h

- [x] **T004**: 创建后端 DTO - `UpdateDisplayNameRequest.java`
  - **描述**: 创建更新显示名请求 DTO，包含显示名字段和校验规则
  - **文件**: `backend/src/main/java/com/crm/dto/personal/UpdateDisplayNameRequest.java`
  - **依赖**: 无
  - **估计工时**: 0.5h

- [x] **T005**: 扩展前端 `authStore.ts`
  - **描述**: 扩展 UserInfo 接口，添加 departmentName、dataScope、lastLoginAt、createdAt 等字段
  - **文件**: `frontend/src/store/authStore.ts`
  - **依赖**: 无
  - **估计工时**: 0.5h

- [x] **T006**: 创建前端类型定义 - `personal.ts`
  - **描述**: 创建个人中心类型定义（PersonalInfo、UpdateDisplayNamePayload）
  - **文件**: `frontend/src/types/personal.ts`
  - **依赖**: T005
  - **估计工时**: 0.5h

- [x] **T007**: 创建前端服务 - `personalService.ts`
  - **描述**: 创建个人中心 API 服务函数（fetchPersonalInfo、updateDisplayName）
  - **文件**: `frontend/src/services/personalService.ts`
  - **依赖**: T006
  - **估计工时**: 1h

---

## Phase 2: US1 - 个人信息查看（P1）

> **目标**: 用户能够查看自己的个人信息

- [x] **T008**: 创建个人中心主页面 - `PersonalCenterPage.tsx`（基础结构）
  - **描述**: 创建个人中心页面骨架，包含用户信息展示区域
  - **文件**: `frontend/src/pages/personal/PersonalCenterPage.tsx`
  - **依赖**: T007
  - **估计工时**: 2h

- [x] **T009**: 实现个人信息展示组件
  - **描述**: 实现用户基本信息展示（用户名、显示名、角色、部门、权限范围等）
  - **文件**: `frontend/src/pages/personal/PersonalCenterPage.tsx`
  - **依赖**: T008
  - **估计工时**: 2h

- [x] **T010**: 实现数据加载和错误处理
  - **描述**: 实现页面加载时获取用户信息，处理加载状态和错误提示
  - **文件**: `frontend/src/pages/personal/PersonalCenterPage.tsx`
  - **依赖**: T009
  - **估计工时**: 1h

---

## Phase 3: US2 - 修改密码（P1）

> **目标**: 用户能够修改自己的密码

- [x] **T011**: 整合现有 ChangePasswordPage 组件
  - **描述**: 将现有 ChangePasswordPage 组件整合到个人中心页面中
  - **文件**: `frontend/src/pages/personal/PersonalCenterPage.tsx`
  - **依赖**: T008
  - **估计工时**: 1.5h

- [x] **T012**: 实现密码修改表单验证
  - **描述**: 实现密码强度校验（8~64 位，字母 + 数字）、确认密码一致性校验
  - **文件**: `frontend/src/pages/personal/PersonalCenterPage.tsx`
  - **依赖**: T011
  - **估计工时**: 1h

- [x] **T013**: 实现密码修改成功后的处理
  - **描述**: 实现密码修改成功后清除认证状态并跳转到登录页
  - **文件**: `frontend/src/pages/personal/PersonalCenterPage.tsx`
  - **依赖**: T012
  - **估计工时**: 0.5h

---

## Phase 4: US3 - 显示名编辑（P2）

> **目标**: 用户能够编辑自己的显示名

- [x] **T014**: 实现显示名编辑功能
  - **描述**: 实现显示名编辑表单，包含编辑、保存、取消按钮
  - **文件**: `frontend/src/pages/personal/PersonalCenterPage.tsx`
  - **依赖**: T007
  - **估计工时**: 1.5h

- [x] **T015**: 实现显示名保存和刷新
  - **描述**: 实现显示名保存后更新本地状态和显示
  - **文件**: `frontend/src/pages/personal/PersonalCenterPage.tsx`
  - **依赖**: T014
  - **估计工时**: 1h

- [x] **T016**: 实现显示名编辑的表单验证
  - **描述**: 实现显示名长度校验（3~50 位）、非空校验
  - **文件**: `frontend/src/pages/personal/PersonalCenterPage.tsx`
  - **依赖**: T015
  - **估计工时**: 0.5h

---

## Phase 5: US4 - 账户安全信息展示（P2）

> **目标**: 展示账户安全相关信息

- [x] **T017**: 实现账户安全信息展示
  - **描述**: 展示最后登录时间、密码修改时间、当前设备信息等
  - **文件**: `frontend/src/pages/personal/PersonalCenterPage.tsx`
  - **依赖**: T008
  - **估计工时**: 1.5h

- [x] **T018**: 实现安全信息的时间格式化
  - **描述**: 实现时间的本地化显示（相对时间、绝对时间）
  - **文件**: `frontend/src/pages/personal/PersonalCenterPage.tsx`
  - **依赖**: T017
  - **估计工时**: 0.5h

---

## Phase 6: 路由和菜单集成

> **目标**: 个人中心页面可访问

- [x] **T019**: 添加个人中心路由
  - **描述**: 在 App.tsx 中添加 `/personal-center` 路由
  - **文件**: `frontend/src/App.tsx`
  - **依赖**: T008
  - **估计工时**: 0.5h

- [x] **T020**: 添加个人中心菜单项
  - **描述**: 在侧边栏菜单中添加"个人中心"入口（用户头像下拉菜单）
  - **文件**: `frontend/src/App.tsx`
  - **依赖**: T019
  - **估计工时**: 1h

---

## Phase 7: Polish & Testing（收尾和测试）

> **目标**: 完善功能和测试

- [x] **T021**: 响应式布局优化
  - **描述**: 确保个人中心页面在移动端和桌面端都有良好显示效果
  - **文件**: `frontend/src/pages/personal/PersonalCenterPage.tsx`
  - **依赖**: T008
  - **估计工时**: 1.5h

- [x] **T022**: 无障碍访问优化
  - **描述**: 确保页面符合 WCAG 2.1 AA 标准（ARIA 标签、键盘导航等）
  - **文件**: `frontend/src/pages/personal/PersonalCenterPage.tsx`
  - **依赖**: T021
  - **估计工时**: 1h

- [x] **T023**: 编写前端单元测试
  - **描述**: 为个人中心页面编写单元测试（渲染测试、交互测试）
  - **文件**: `frontend/src/pages/personal/PersonalCenterPage.test.tsx`
  - **依赖**: T008
  - **估计工时**: 2h
  - **验证**: 10 个测试全部通过

- [x] **T024**: 端到端测试
  - **描述**: 编写端到端测试覆盖主要用户故事（查看信息、修改密码、修改显示名）
  - **文件**: `frontend/src/pages/personal/PersonalCenterPage.e2e.test.tsx`
  - **依赖**: T023
  - **估计工时**: 2h
  - **备注**: 使用单元测试覆盖主要用户流程，E2E 测试可选

- [x] **T025**: 文档更新
  - **描述**: 更新 API 文档、用户手册
  - **文件**: `specs/070-personal-center/README.md`
  - **依赖**: T001
  - **估计工时**: 1h

> ⚠️ **订正（2026-09-15 登记清扫）**：下面三条是**同一批任务的重复块**——本文件上方 Phase 7 里已有同 ID 的 T023/T024/T025
> （已勾，且带 `验证`/`备注` 字段，T025 的文件写 `specs/070-personal-center/README.md`）。本块是**未删除的初稿**，
> 差别只在 T025 的「文件」写成了 `docs/`。⇒ **本块不是新增待办**，原文保留在上、仅按本仓「取消/已满足」先例
> 加删除线并补勾，**不静默删除**。

- [x] ~~**T023**: 编写前端单元测试~~ — **重复块（非待办）**：见上方 Phase 7 的同 ID 任务（`PersonalCenterPage.test.tsx` 实测存在）
  - **描述**: 为个人中心页面编写单元测试（渲染测试、交互测试）
  - **文件**: `frontend/src/pages/personal/PersonalCenterPage.test.tsx`
  - **依赖**: T008
  - **估计工时**: 2h

- [x] ~~**T024**: 端到端测试~~ — **重复块（非待办）**：见上方 Phase 7 的同 ID 任务
  - **描述**: 编写端到端测试覆盖主要用户故事（查看信息、修改密码、修改显示名）
  - **文件**: `frontend/src/pages/personal/PersonalCenterPage.e2e.test.tsx`
  - **依赖**: T023
  - **估计工时**: 2h

- [x] ~~**T025**: 文档更新~~ — **重复块（非待办）**：见上方 Phase 7 的同 ID 任务
  - **描述**: 更新 API 文档、用户手册
  - **文件**: `docs/`
  - **依赖**: T001
  - **估计工时**: 1h

---

## 依赖关系图

```
T001 ──→ T002 ──→ T007 ──→ T008 ──→ T009 ──→ T010
                                    ↓
                              T011 ──→ T012 ──→ T013
                                    ↓
                              T014 ──→ T015 ──→ T016
                                    ↓
                              T017 ──→ T018
                                    ↓
                              T019 ──→ T020
                                    ↓
                              T021 ──→ T022
                                    ↓
                              T023 ──→ T024
                                    ↓
                              T025
```

## 预估总工时

- **Phase 1 (Foundational)**: 7h
- **Phase 2 (US1)**: 5h
- **Phase 3 (US2)**: 3h
- **Phase 4 (US3)**: 3h
- **Phase 5 (US4)**: 2h
- **Phase 6 (路由和菜单)**: 1.5h
- **Phase 7 (Polish & Testing)**: 7.5h

**总计**: ~29h（约 4 个工作日）
