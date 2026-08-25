# Quickstart: 邮件账户与同步记录模块验证指南

**Branch**: `062-email-sync`

## 前置条件

- MySQL（`crm_db`）、Redis 运行；后端服务运行于 `SERVER_PORT=8081`。
- Flyway 启动时自动应用 V69（mail_account + mail_sync_record）。
- 前端开发服务运行（`VITE_PROXY_TARGET=http://localhost:8081`）。

## 验证场景

### 1. 后端验证（自动化）

```bash
cd backend
mvn verify              # 全部单元/契约测试 + spotless + JaCoCo
mvn test "-Dtest=EmailSyncIT" # 邮件同步集成测试
```

**预期**: BUILD SUCCESS；全绿。

### 2. 线上端点验证（手动）

```bash
# 配置账户
curl -X POST http://localhost:8081/api/v1/mail-accounts \
  -H "Authorization: Bearer <token>" -H "Content-Type: application/json" \
  -d '{"email":"sales@corp.com","displayName":"销售部","imapHost":"imap.corp.com","imapPort":993,"smtpHost":"smtp.corp.com","smtpPort":465,"enabled":true,"isDefaultSender":true}'

# 模拟同步
curl -X POST http://localhost:8081/api/v1/mail-accounts/1/sync -H "Authorization: Bearer <token>"

# 同步记录
curl http://localhost:8081/api/v1/mail-accounts/1/records -H "Authorization: Bearer <token>"
```

**预期**: 账户 CRUD/默认唯一/邮箱校验正常；模拟同步生成记录。

### 3. 前端验证

- 工作台组"邮件同步"菜单 → 账户配置页 + 同步记录页（模拟同步按钮）。

### 4. 契约核对

- 响应结构对照 `contracts/email-sync.md`。
- Swagger: `http://localhost:8081/swagger-ui.html` → 邮件同步分组。
