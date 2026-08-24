# 快速开始：邮件营销

## 后端

1. Flyway V48：email_template/email_campaign/email_send_log/email_track 四表。
2. MailConfig（SMTP 环境变量；无 SMTP dev 模拟）。
3. 实体/Mapper 4 组。
4. `EmailTemplateService`（模板 CRUD + 变量渲染）+ `EmailCampaignService`（群发异步队列 + 测试发送 + 统计）。
5. `EmailController` + `EmailTrackController`（公开追踪端点）。
6. 测试：EmailTemplateServiceTest + EmailCampaignServiceTest + EmailIT。

## 前端

1. `types/email.ts` + `emailService.ts`。
2. `EmailTemplatePage`（模板管理）+ `EmailCampaignPage`（群发页：模板+收件人细分/客户+测试发送+统计）。
3. 营销分组路由。

## 验证

- 后端：`mvn test`；前端：`pnpm run typecheck/lint/test`。
- 手动：建模板 → 建群发（细分收件人）→ 发送记录 → 打开/点击统计。
