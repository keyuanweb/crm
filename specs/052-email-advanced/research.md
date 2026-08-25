# Research: 邮件高级能力模块

**Branch**: `052-email-advanced` | **Date**: 2026-08-25

## 1. 退订模型

**Decision**: 新增 `email_unsubscribe`（email 唯一、unsubscribed_at、campaign_id 来源）。公开端点 `POST /api/v1/public/email/unsubscribe`（Body: email）供邮件退订链接调用；群发与 049 自动化发信前按邮箱查退订名单排除。

**Rationale**: 合规必备；按邮箱匹配简单可靠（同邮箱=同订阅者）；公开端点免登录（客户点击退订链接无需登录）。

## 2. 统计口径

**Decision**: 群发详情统计 = EmailSendLog（status=SENT/FAILED）+ EmailTrack（open/click）。打开率 = openCount/sentCount，点击率 = clickCount/sentCount（分母为成功发送）。

**Rationale**: 与行业口径一致；复用既有记录避免新表。

## 3. A/B 测试

**Decision**: EmailCampaign 加 variant（NONE/A/B）、subjectB、winner（A/B/NONE）；EmailSendLog 加 variant（A/B）标记。创建时 variant=A/B 则按 customerId 奇偶分两组各 50%（按发送顺序交错更优——用 id 奇偶近似）。详情统计按 variant 分组打开率，标记更优者。

**Rationale**: 主题变体是 A/B 最简形态；v1 不自动切流量（仅标记），避免发送策略复杂度。

**Alternatives considered**: 按随机数分组——需存储分组结果；按 id 奇偶确定性、可复算，且无需额外随机字段。
