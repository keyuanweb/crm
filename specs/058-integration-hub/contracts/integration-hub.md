# 契约：集成中心 /api/v1/integration-channels

**Base**: `/api/v1/integration-channels`（仅 ADMIN 配置）

## GET /integration-channels

通道列表。**Response 200**

```json
{
  "items": [
    { "id": 1, "channelType": "WECHAT_WORK", "name": "销售群", "webhookUrl": "https://qyapi.weixin.qq.com/...", "enabled": true, "createdAt": "..." }
  ],
  "total": 1
}
```

## POST /integration-channels

创建通道。**Body**: `{ "channelType": "WECHAT_WORK", "name": "销售群", "webhookUrl": "https://..." }`。**Response 201**。

## PUT /integration-channels/{id} / DELETE /integration-channels/{id} / POST /{id}/toggle

编辑 / 删除 / 启停。

## 推送记录（ADMIN）

### GET /integration-channels/{id}/deliveries?page&pageSize

该通道推送记录（复用 webhook_delivery）。

## 事件映射

| 业务事件 | 触发点 | 推送标题示例 |
|---|---|---|
| TICKET_ASSIGNED | 工单分配 | 【工单】#{id} 已分配给您 |
| LEAD_CREATED | 线索创建 | 【线索】#{name} 新线索 |
| APPROVAL_PENDING | 审批发起 | 【审批】#{title} 待处理 |

推送负载为 JSON 文本（`{ "msgtype": "text", "text": { "content": "<title>" } }` 兼容企业微信/钉钉）。

## 错误码

| code | status | 含义 |
|---|---|---|
| INTEGRATION_URL_INVALID | 422 | 通道 URL 不合法（须 http/https） |
| INTEGRATION_TYPE_INVALID | 422 | 通道类型不合法 |
| INTEGRATION_CHANNEL_NOT_FOUND | 404 | 通道不存在 |
