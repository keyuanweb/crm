# 研究：邮件营销

## R1 SMTP 配置

**决策**: `MailConfig` 用 application.yml 环境变量（MAIL_HOST/MAIL_PORT/MAIL_USERNAME/MAIL_PASSWORD）；未配置时注入 JavaMailSender 为 null，发送走"模拟"路径（日志 + 标记 SENT）。

## R2 模板渲染

**决策**: 变量 `{name}`/`{company}`/`{phone}` 用 String.replace 替换（防模板注入）；未匹配字段保留原样。模板正文存 HTML。

## R3 群发队列

**决策**: `EmailCampaignService.send()` 创建 campaign（PENDING）→ @Async 线程池逐封发送 → 每封 email_send_log（SENT/FAILED）+ 更新 campaign 状态。频控：单批上限 500（配置），发送间隔 sleep 50ms。

## R4 追踪

**决策**:
- 打开：正文嵌入 `<img src="/api/v1/public/track/open/{sendLogId}">` → 控制器返回 1x1 透明 GIF + 记录 email_track（OPEN，防重复：同 log 仅记首次）。
- 点击：正文链接改写为 `/api/v1/public/track/click/{sendLogId}?url=<encoded>` → 302 跳转 + 记录（CLICK）。

## R5 收件人来源

**决策**: CampaignRequest 支持 source 类型：SEGMENT（细分 id，复用 SegmentService.members）/ CUSTOMER_IDS（客户 id 列表，查 email）。收件人邮箱为空跳过。

## R6 前端

**决策**: 营销分组加"邮件模板"页（CRUD + 变量提示 + 预览）与"邮件群发"页（选模板 + 收件人来源 + 测试发送 + 发送记录 + 打开/点击统计）。
