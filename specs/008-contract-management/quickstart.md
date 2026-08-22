# Quickstart: 合同管理模块验证指南

**Branch**: `008-contract-management`

## 前置条件

- MySQL（`crm_db`）、Redis 运行；后端服务运行于 `SERVER_PORT=8081`。
- Flyway 启动时自动应用 V16~V18（contract / contract_attachment / contract_template）。
- 附件目录 `./contract-files`（后端工作目录下，可配置 `crm.contract.storage-dir`）。
- 前端开发服务运行（`VITE_PROXY_TARGET=http://localhost:8081`）。

## 验证场景

### 1. 后端验证（自动化）

```bash
cd backend
mvn verify                            # 全部单元/契约测试 + spotless + JaCoCo
mvn test "-Dtest=ContractIT"          # 合同集成测试（含附件上传下载、状态机、权限）
```

**预期**: BUILD SUCCESS；`*Test` 全绿；`*IT` 单独运行通过。

### 2. 线上端点验证（手动）

```bash
# 登录（admin）
curl -X POST http://localhost:8081/api/v1/auth/login \
  -H "Content-Type: application/json" -d '{"username":"admin","password":"admin123"}'

# 创建模板（ADMIN）
curl -X POST http://localhost:8081/api/v1/contract-templates \
  -H "Authorization: Bearer <token>" -H "Content-Type: application/json" \
  -d '{"name":"标准合同","content":"甲方：{customerName}，合同号 {contractNo}，金额 {amount} 元"}'

# 创建合同（基于已通过报价 5）
curl -X POST http://localhost:8081/api/v1/contracts \
  -H "Authorization: Bearer <token>" -H "Content-Type: application/json" \
  -d '{"title":"CRM 采购合同","customerId":2,"quoteId":5,"templateId":1,"amount":1470000}'

# 提交流程
curl -X POST http://localhost:8081/api/v1/contracts/<id>/submit -H "Authorization: Bearer <token>"
curl -X POST http://localhost:8081/api/v1/contracts/<id>/approve -H "Authorization: Bearer <token>"
curl -X POST http://localhost:8081/api/v1/contracts/<id>/effective -H "Authorization: Bearer <token>"
curl -X POST http://localhost:8081/api/v1/contracts/<id>/complete -H "Authorization: Bearer <token>"

# 附件上传/下载
curl -X POST http://localhost:8081/api/v1/contracts/<id>/attachments \
  -H "Authorization: Bearer <token>" -F "file=@扫描件.pdf"
curl -o out.pdf http://localhost:8081/api/v1/contracts/<id>/attachments/<aid>/download \
  -H "Authorization: Bearer <token>"
```

**预期**: 合同编号 HT-YYYYMMDD-XXXX 自动生成；基于报价金额自动带入；正文占位符替换为客户名；状态机 DRAFT→PENDING_APPROVAL→APPROVED→EFFECTIVE→COMPLETED 流转正确；附件上传下载内容一致。

### 3. 前端验证

- 合同列表页 `/contracts`：新建（可选报价/模板）、列表/搜索/状态筛选。
- 合同详情页 `/contracts/:id`：正文、审批信息、附件上传/下载/删除、状态操作按钮（提交/审批/生效/完成/终止，按状态与角色显示）。
- 模板管理页 `/contract-templates`：新增/编辑/停用模板（仅管理员）。

### 4. 契约核对

- 响应结构对照 `contracts/contracts.md`。
- Swagger: `http://localhost:8081/swagger-ui.html` → 合同/合同模板分组可见新端点。
