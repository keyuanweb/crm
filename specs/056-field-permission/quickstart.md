# Quickstart: 字段级读写权限模块验证指南

**Branch**: `056-field-permission`

## 前置条件

- MySQL（`crm_db`）、Redis 运行；后端服务运行于 `SERVER_PORT=8081`。
- Flyway 启动时自动应用 V64（field_permission）。
- 前端开发服务运行（`VITE_PROXY_TARGET=http://localhost:8081`）。

## 验证场景

### 1. 后端验证（自动化）

```bash
cd backend
mvn verify                     # 全部单元/契约测试 + spotless + JaCoCo
mvn test "-Dtest=FieldPermissionIT" # 字段权限集成测试
```

**预期**: BUILD SUCCESS；全绿。

### 2. 线上端点验证（手动）

```bash
# 配置 SALES 角色对客户"手机号"字段只读
curl -X POST http://localhost:8081/api/v1/field-permissions \
  -H "Authorization: Bearer <token>" -H "Content-Type: application/json" \
  -d '{"roleId":2,"entityType":"CUSTOMER","fieldId":5,"permission":"READ_ONLY"}'

# 字段列表带权限标记
curl http://localhost:8081/api/v1/custom-fields?entityType=CUSTOMER -H "Authorization: Bearer <sales-token>"

# SALES 提交修改手机号 → 422 FIELD_READ_ONLY
```

**预期**: 字段列表返回 permission 标记；只读/隐藏字段保存 422；ADMIN 不受限。

### 3. 前端验证

- 流程与配置组"字段权限"菜单 → 配置矩阵（角色×实体×字段）。
- 自定义字段表单按权限隐藏/禁用。

### 4. 契约核对

- 响应结构对照 `contracts/field-permission.md`。
- Swagger: `http://localhost:8081/swagger-ui.html` → 字段权限分组。
