# Quickstart: 部门管理优化验证指南

## 前置条件

1. 后端服务运行在 http://localhost:8081
2. 前端服务运行在 http://localhost:5173
3. 数据库已初始化（Flyway 迁移已完成）
4. 管理员账号已创建（username: admin）

## 验证场景

### 场景 1: 部门树加载

**步骤**:
1. 使用管理员账号登录系统
2. 导航到"部门管理"页面
3. 观察部门树形列表加载

**预期结果**:
- [ ] 部门树正确显示（层级关系正确）
- [ ] 加载时间 < 1 秒
- [ ] 树节点可展开/收起

**命令**:
```bash
# 后端 API 验证
curl -H "Authorization: Bearer <JWT_TOKEN>" http://localhost:8081/api/v1/departments/tree

# 预期响应
{
  "code": 200,
  "message": "success",
  "data": {
    "departments": [...]
  }
}
```

---

### 场景 2: 部门搜索

**步骤**:
1. 在部门管理页面找到搜索框
2. 输入关键字"技术"
3. 观察搜索结果

**预期结果**:
- [ ] 仅显示名称包含"技术"的部门
- [ ] 搜索响应时间 < 200ms
- [ ] 搜索高亮显示匹配关键字

**命令**:
```bash
# 如果实现了搜索 API
curl -H "Authorization: Bearer <JWT_TOKEN>" "http://localhost:8081/api/v1/departments/search?keyword=技术"
```

---

### 场景 3: 创建部门

**步骤**:
1. 点击"新增部门"按钮
2. 填写部门信息（名称、上级部门、描述、排序号）
3. 点击"保存"

**预期结果**:
- [ ] 创建成功，显示成功提示
- [ ] 新部门显示在树形列表中
- [ ] 上级部门选择器正确显示所有部门（排除自身）

**命令**:
```bash
# 直接调用 API 创建
curl -X POST http://localhost:8081/api/v1/departments \
  -H "Authorization: Bearer <JWT_TOKEN>" \
  -H "Content-Type: application/json" \
  -d '{
    "name": "测试部门",
    "parentId": 1,
    "description": "测试用",
    "sortOrder": 10
  }'
```

---

### 场景 4: 编辑部门

**步骤**:
1. 点击部门右侧"编辑"按钮
2. 修改部门信息
3. 点击"保存"

**预期结果**:
- [ ] 编辑成功，显示成功提示
- [ ] 树形列表更新显示新信息
- [ ] 版本号正确递增

**命令**:
```bash
# 直接调用 API 更新
curl -X PUT http://localhost:8081/api/v1/departments/3 \
  -H "Authorization: Bearer <JWT_TOKEN>" \
  -H "Content-Type: application/json" \
  -d '{
    "name": "更新后的名称",
    "parentId": 1,
    "version": 0
  }'
```

---

### 场景 5: 删除部门（无子部门、无成员）

**步骤**:
1. 选择一个空部门（无子部门、无成员）
2. 点击"删除"按钮
3. 确认删除

**预期结果**:
- [ ] 删除成功，显示成功提示
- [ ] 部门从树形列表中移除

**命令**:
```bash
# 直接调用 API 删除
curl -X DELETE http://localhost:8081/api/v1/departments/3 \
  -H "Authorization: Bearer <JWT_TOKEN>"
```

---

### 场景 6: 删除防护（有子部门）

**步骤**:
1. 选择一个有子部门的部门
2. 点击"删除"按钮
3. 确认删除

**预期结果**:
- [ ] 删除被拒绝
- [ ] 显示错误提示："该部门存在子部门，无法删除"

**命令**:
```bash
# 直接调用 API 删除（有子部门）
curl -X DELETE http://localhost:8081/api/v1/departments/1 \
  -H "Authorization: Bearer <JWT_TOKEN>"

# 预期响应
{
  "code": 400,
  "message": "该部门存在子部门，无法删除",
  "detail": null
}
```

---

### 场景 7: 删除防护（有成员）

**步骤**:
1. 选择一个有成员的部门
2. 点击"删除"按钮
3. 确认删除

**预期结果**:
- [ ] 删除被拒绝
- [ ] 显示错误提示："该部门存在成员，无法删除"

---

### 场景 8: 部门详情查看

**步骤**:
1. 点击部门右侧"查看详情"按钮
2. 观察详情面板

**预期结果**:
- [ ] 详情面板正确显示
- [ ] 显示部门完整信息（名称、上级部门、描述、创建时间、创建人、成员数量、子部门数量）
- [ ] 可关闭详情面板

## 性能验证

### 部门树加载性能

**步骤**:
1. 使用浏览器开发者工具 Network 面板
2. 刷新部门管理页面
3. 观察 `/api/v1/departments/tree` 请求

**预期结果**:
- [ ] 响应时间 < 200ms（1000 个部门数据量下）
- [ ] 响应体大小合理（< 1MB）

### 搜索性能

**步骤**:
1. 在搜索框输入关键字
2. 观察搜索响应时间

**预期结果**:
- [ ] 搜索响应时间 < 200ms

## 兼容性验证

### 浏览器兼容性

- [ ] Chrome 90+
- [ ] Edge 90+
- [ ] Firefox 88+

### 响应式布局

- [ ] 桌面端（1920x1080）
- [ ] 笔记本（1366x768）
- [ ] 平板（768x1024）
- [ ] 手机（375x667）

## 回归测试

### 现有功能验证

- [ ] 用户管理页面正常（部门下拉选择器）
- [ ] 角色管理页面正常
- [ ] 数据权限功能正常

## 测试数据准备

### 创建测试部门树

```sql
-- 顶级部门
INSERT INTO department (name, parent_id, description, sort_order, created_by, version)
VALUES ('技术部', NULL, '技术研发部门', 1, 1, 0);

-- 二级部门
INSERT INTO department (name, parent_id, description, sort_order, created_by, version)
VALUES ('前端组', 1, '前端开发组', 1, 1, 0);
INSERT INTO department (name, parent_id, description, sort_order, created_by, version)
VALUES ('后端组', 1, '后端开发组', 2, 1, 0);

-- 三级部门
INSERT INTO department (name, parent_id, description, sort_order, created_by, version)
VALUES ('React 组', 2, 'React 开发组', 1, 1, 0);
INSERT INTO department (name, parent_id, description, sort_order, created_by, version)
VALUES ('Vue 组', 2, 'Vue 开发组', 2, 1, 0);
```

## 完成标准

所有验证场景通过，且：
- [ ] 无控制台错误
- [ ] 无网络请求失败
- [ ] 无性能问题
- [ ] 用户体验流畅
- [ ] 符合所有功能需求（FR-001 ~ FR-010）
- [ ] 符合所有成功标准（SC-001 ~ SC-004）
