# Research: 客户服务模块

**Branch**: `015-customer-service` | **Date**: 2026-08-22

## 1. 工单模型

**Decision**: 新增 `ticket` 表（客户 id 必填、联系人 id 可选、标题、描述、优先级、状态、处理人 id、SLA 响应/解决到期时间、SLA 状态、逻辑删除、乐观锁）+ `ticket_reply` 表（工单 id、回复人 id、内容、时间戳，一对多时间线）。状态流转 OPEN→IN_PROGRESS→RESOLVED→CLOSED 单向，CLOSED 不可回退。

**Rationale**: 工单是客户服务的核心实体；回复独立成表便于时间线查询与分页；状态单向保证口径稳定。

**Alternatives considered**: 工单内嵌 JSON 回复数组 —— 不利于查询/审计，拒绝。

## 2. SLA 计算与超时标记

**Decision**: `sla_policy` 表按优先级（LOW/MEDIUM/HIGH/URGENT）配置响应时限/解决时限（小时，可空表示不约束）。创建工单时按优先级查策略，计算 `sla_respond_deadline`/`sla_resolve_deadline`（创建时刻 + 小时数）落库；未配置对应优先级策略 → 两个时限均 null（无 SLA）。SLA 状态按需计算：已过 resolve 时限 → OVERDUE（已超时）；任一剩余时限 ≤ 2h → WARNING（即将超时）；否则 NORMAL。超时统计：未关闭工单中 OVERDUE 数/比例。

**Rationale**: 到期时间落库避免频繁计算；WARNING 阈值取固定 2h（简单且覆盖长/短时限）；假设不引入节假日日历（spec 假设已记录）。

**Alternatives considered**: 运行时常算 SLA 状态 —— 列表查询需逐行计算，拒绝（落库 + 按需刷新）。动态 25% 阈值 —— 需存储总时限，复杂度高且收益低，拒绝（固定 2h）。

## 3. 权限模型

**Decision**: 工单写操作（创建/回复/流转/分配/删除）：ADMIN+SUPPORT；查看：ADMIN/SUPPORT 全部 + SALES 仅看 customer.owner_id = 当前用户 的工单（Service 层行级过滤）。知识库：ADMIN/SUPPORT 写，全员读（搜索仅已发布）。SLA 策略：仅 ADMIN 可配（含 CRUD 与启用/停用）。

**Rationale**: 与既有角色体系一致（SUPPORT=客服）；SALES 只读自身客户工单符合 012 数据权限精神；服务端强制授权（章程原则三）。

## 4. 知识库搜索

**Decision**: 关键字 LIKE 匹配标题/内容/关键词，仅返回 PUBLISHED 文章，分页返回；创建默认 DRAFT，显式发布/下线切换。

**Rationale**: 简单可用的内部检索；不引入全文检索引擎（YAGNI，章程原则五）。

## 5. 契约与审计

**Decision**: 契约写入 `contracts/`（tickets.md/knowledge.md/sla.md）。创建/流转/回复/分配/删除记录 AuditService 审计。前端：工单列表/详情页、知识库列表页、SLA 策略配置页 + 客户服务菜单。

**Rationale**: 沿用既有契约优先与审计惯例（章程原则一/五）。
