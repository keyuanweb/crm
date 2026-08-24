# Research: 销售 Playbook 模块

**Branch**: `045-sales-playbook` | **Date**: 2026-08-24

## 1. 动作引导 vs 阶段自定义

**Decision**: v1 实现"每阶段动作引导"（模板 + 勾选完成），**不做**阶段枚举自定义。阶段枚举仍为 INITIAL_CONTACT/NEGOTIATING/CLOSED_WON/CLOSED_LOST，硬编码于 SalesOpportunityService.STAGES、StageConversionService.DEFAULT_PROBABILITY、前端 STAGE_LABELS、漏斗/预测多处。

**Rationale**: 阶段自定义牵动状态机/预测/漏斗/前端多处，风险高且收益分散；动作引导独立交付、直接提升跟进质量（对标 Salesforce Opportunity Guidance）。阶段自定义留待后续专项模块。

**Alternatives considered**: v1 即做阶段自定义——改动面大、回归风险高，否。

## 2. 数据模型

**Decision**: 双表：`stage_action_template`（阶段、名称、描述、排序、必做、启用、逻辑删除、乐观锁）+ `sales_opportunity_action`（销售机会 id、模板 id、完成人、完成时间，唯一约束 机会+模板）。模板删除为逻辑删除；动作完成记录保留（机会关闭后仍可查）。

**Rationale**: 模板与完成记录分离，模板可复用、完成历史不受模板变更影响。

## 3. 必做校验语义

**Decision**: 阶段流转时，若当前阶段存在未完成必做动作，前端在勾选状态中提示"有必做动作未完成"（可确认继续）。后端不强制阻断（与成熟 CRM 引导语义一致，避免死锁销售流程）。

**Rationale**: 引导而非阻断；若强制，销售可能为绕过校验而虚假勾选。

## 4. 契约与权限

**Decision**: 契约写入 `contracts/playbook.md`。模板配置（/stage-actions）仅 ADMIN；动作清单与勾选（/sales-opportunities/{id}/actions）ADMIN+SALES。详情接口聚合动作清单（含完成状态），避免前端多次请求。

**Rationale**: 与既有权限模式一致；服务端强制授权（章程原则三）。
