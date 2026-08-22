# Research: 市场营销模块

**Branch**: `014-marketing` | **Date**: 2026-08-22

## 1. 归因模型

**Decision**: `lead.campaign_id` 与 `customer.campaign_id`（V33 迁移，可空，索引）。创建线索/客户请求新增 `campaignId`（可选，校验活动存在）。线索转化客户时 campaign_id 随转化带入（转化逻辑已存在，补充字段）。活动详情归因计数 = 按 campaign_id 统计 lead/customer 数量。

**Rationale**: 归因挂在实体上（查询/统计简单）；转化带入保持链路一致。

## 2. ROI 口径

**Decision**: 按渠道聚合：活动数、总成本（Σ campaign.cost）、归因线索数（lead where campaign 属于该渠道）、归因客户数、线索转化率（归因客户数/归因线索数）、预估收益（归因客户关联商机 expected_amount_max 合计）、ROI = 收益/成本（成本为 0 时 ROI=null 显示 "-"）。

**Rationale**: spec 假设收益=商机 max 金额合计；ROI 除零保护。

## 3. 状态流转

**Decision**: PLANNING→RUNNING→ENDED 单向；ENDED 不可回退（409 CAMPAIGN_INVALID_STATE）；创建默认 PLANNING。

**Rationale**: 生命周期单向保证统计口径稳定。

## 4. 删除防护

**Decision**: 删除前统计 lead/customer 的 campaign_id 引用，>0 → 409 CAMPAIGN_HAS_ATTRIBUTION。

**Rationale**: 防止归因悬空。

## 5. 契约与权限

**Decision**: 契约写入 `contracts/marketing.md`。活动管理（写）：ADMIN+SALES；查看：全员。ROI 报表：全员可看（管理决策辅助）。前端活动管理页 + 渠道 ROI 页；线索/客户创建弹窗加活动选择。

**Rationale**: 与既有权限模式一致；服务端强制授权（章程原则三）。
