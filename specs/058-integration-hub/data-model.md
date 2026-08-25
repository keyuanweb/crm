# 数据模型：集成中心模块

**Branch**: `058-integration-hub` | **Date**: 2026-08-25

## 1. integration_channel（集成通道，Flyway V66）

| 列 | 类型 | 约束 | 说明 |
|---|---|---|---|
| id | BIGINT | PK, AUTO | |
| channel_type | VARCHAR(20) | NOT NULL | WECHAT_WORK / DINGTALK / CUSTOM |
| name | VARCHAR(100) | NOT NULL | 通道名称 |
| webhook_url | VARCHAR(500) | NOT NULL | 推送 URL |
| enabled | TINYINT | NOT NULL DEFAULT 1 | 启用 |
| created_by | BIGINT | NULL | |
| created_at / updated_at | DATETIME | NOT NULL | |

## 2. 推送记录（复用 webhook_delivery）

- IntegrationChannelService.publish 为每条启用通道构造推送（event_type=channel 事件、payload=消息 JSON），记录入 webhook_delivery。

## 3. 业务规则

- URL 须 http/https。
- 事件子集：TICKET_ASSIGNED / LEAD_CREATED / APPROVAL_PENDING。
- 推送失败复用 055 退避重试（≤3 次）。
- 无启用通道时跳过。
