# 契约：邮件账户与同步记录 /api/v1/mail-accounts

**Base**: `/api/v1/mail-accounts`

## 账户配置（仅 ADMIN）

### GET /mail-accounts

账户列表。**Response 200**

```json
{
  "items": [
    { "id": 1, "email": "sales@corp.com", "displayName": "销售部",
      "imapHost": "imap.corp.com", "imapPort": 993, "smtpHost": "smtp.corp.com", "smtpPort": 465,
      "enabled": true, "isDefaultSender": true, "createdAt": "..." }
  ],
  "total": 1
}
```

### POST /mail-accounts

创建账户。**Body**: `{ "email": "sales@corp.com", "displayName": "销售部", "imapHost": "...", "imapPort": 993, "smtpHost": "...", "smtpPort": 465, "enabled": true, "isDefaultSender": false }`。**Response 201**。

### PUT /mail-accounts/{id} / DELETE /mail-accounts/{id}

编辑 / 删除。

## 同步记录（ADMIN + SALES）

### POST /mail-accounts/{id}/sync

模拟同步（生成一条 INBOUND 记录，验证链路）。**Response 200**

```json
{ "id": 10, "accountId": 1, "direction": "INBOUND", "subject": "模拟同步邮件",
  "fromAddress": "customer@ext.com", "toAddress": "sales@corp.com",
  "syncStatus": "SYNCED", "syncTime": "..." }
```

### GET /mail-accounts/{id}/records?page&pageSize

账户同步记录列表。**Response 200**

```json
{
  "items": [ { "id": 10, "accountId": 1, "direction": "INBOUND", "subject": "模拟同步邮件",
    "fromAddress": "customer@ext.com", "toAddress": "sales@corp.com",
    "syncStatus": "SYNCED", "syncTime": "..." } ],
  "total": 1
}
```

### DELETE /mail-accounts/{id}/records/{recordId}

删除同步记录。

## 错误码

| code | status | 含义 |
|---|---|---|
| MAIL_EMAIL_INVALID | 422 | 邮箱格式不合法 |
| MAIL_EMAIL_DUPLICATE | 409 | 邮箱已存在 |
| MAIL_ACCOUNT_NOT_FOUND | 404 | 账户不存在 |
| MAIL_RECORD_NOT_FOUND | 404 | 同步记录不存在 |
