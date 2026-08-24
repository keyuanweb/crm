# Research: 营销自动化模块

**Branch**: `049-marketing-automation` | **Date**: 2026-08-25

## 1. 复用 vs 新建

**Decision**: 复用 013 工作流引擎（WorkflowRule 表 + WorkflowEngine 触发/匹配/执行/日志链），仅扩展：
- 事件类型：LEAD_SCORE_THRESHOLD、TAG_CHANGED
- 动作类型：SEND_EMAIL（发模板邮件）、ADD_TAG（加标签）
- 发布点：LeadScoringService 评分重算后、标签服务打标后

**Rationale**: 013 已有完整机制（事件→规则匹配→动作→隔离日志），营销自动化本质是更多事件/动作类型，无需重复造引擎；最小侵入、可维护。

**Alternatives considered**: 新建独立营销旅程表/引擎——与 013 功能重叠、双引擎复杂度高，否。

## 2. 条件语义

**Decision**: 沿用 WorkflowRule.conditionJson（field/value）：
- LEAD_SCORE_THRESHOLD: `{"field":"score","value":"80"}` → context 提供 score，匹配 `score >= 阈值`（评分阈值用数值比较，扩展 matches 支持数值字段）
- TAG_CHANGED: `{"field":"tag","value":"高意向"}` → context 提供 tag，等值匹配

**Rationale**: 阈值比较需数值语义，matches 增加数值分支；标签等值沿用既有。

## 3. 动作实现

**Decision**:
- SEND_EMAIL: actionJson `{"templateId":1}` → 取模板（subject/content），发给 context.email（线索邮箱），插 EmailSendLog（campaignId=null 标记自动化）并异步发送。
- ADD_TAG: actionJson `{"tag":"高意向"}` → 为线索实体添加标签（调标签服务）。
- CREATE_TASK: 沿用 013 既有实现。

**Rationale**: 单封模板邮件（非批量营销）；标签复用 031 服务；任务复用 013。

## 4. 事件发布点

**Decision**: LeadScoringService 评分重算后（score 变化时）发布 LEAD_SCORE_THRESHOLD；标签服务打标后发布 TAG_CHANGED。发布走 WorkflowEventPublisher 门面（解耦）。

**Rationale**: 事件源于既有业务动作，自动发布零额外操作成本。
