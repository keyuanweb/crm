# 研究：客户 360 聚合与健康度评分设计

## R1 客户 360 聚合数据来源

**决策**: 在现有 `CustomerService.detail()`（已聚合商机/销售机会计数/跟进/联系人）基础上扩展，新增订单、回款、合同、工单的聚合与金额汇总。复用现有 Mapper（SalesOrderMapper、PaymentPlanMapper、PaymentRecordMapper、ContractMapper、TicketMapper），按 `customerId` 查询。

**数据来源**:
- 订单：`SalesOrder.customerId`（金额 amount 分，状态 PENDING/PARTIAL/PAID）
- 回款：`PaymentPlan` → 通过 `orderId` 关联订单（`PaymentPlan.orderId in (订单ids)`）
- 合同：`Contract.customerId`（状态 DRAFT/EFFECTIVE/EXPIRED 等）
- 工单：`Ticket.customerId`（状态 OPEN/IN_PROGRESS/RESOLVED/CLOSED，SLA 状态）
- 金额汇总：累计成交 = 已付/已结清订单金额；待回款 = 订单金额 - 已回款

**性能**: 批量装配——先查订单列表，再按订单 ids 批量查回款计划与回款记录，避免 N+1（章程原则五）。

## R2 健康度评分规则引擎

**决策**: 纯规则引擎计分函数，维度权重可配置（存 `health_score_config` 表），总分 0-100。非 ML 模型（YAGNI）。

**评分维度（默认）**:

| 维度 | 权重 | 计分逻辑（满分该维度权重分） |
|---|---|---|
| 跟进活跃度 | 30 | 最近 30 天有跟进 → 满分；每超过 30 天扣分，>90 天得 0 |
| 回款及时性 | 25 | 无逾期回款 → 满分；存在逾期期次按逾期比例扣分 |
| 工单/投诉 | 20 | 近期无未解决工单 → 满分；有 OPEN/OVERDUE 工单按数量与严重度扣分 |
| 合作深度 | 15 | 有订单/合同 → 按累计成交金额分档得分；纯线索客户得低分 |
| 近期互动 | 10 | 最近 N 天有任意业务活动（跟进/订单/工单回复）→ 满分，否则递减 |

**颜色映射**: <60 红（风险）、60-79 黄（关注）、≥80 绿（健康），阈值可配置。

**失分原因**: 每个维度扣分时记录 `{维度名, 扣分}` 列表，前端展示"为什么是这个分数"（FR-004）。

## R3 流失预警规则

**决策**: `GET /customers/health/at-risk` 返回"超过 N 天无跟进且无新订单"的客户（N 默认 45 天，可配置），按健康度升序排序，仅返回当前用户可访问客户。

**联动**: 预警列表项含跟进按钮 → 复用现有跟进创建接口，创建成功后客户下次查询不再命中预警（因为有新跟进记录）。

## R4 评分配置存储

**决策**: 新增 `health_score_config` 表（Flyway V42 迁移），字段：维度键、权重、参数（如天数阈值）、启用状态。默认种子 5 条（对应 R2 五个维度）。`HealthScoreConfigMapper` 查询所有启用配置，`HealthScoreService` 读取后按配置计算。

**理由**: 配置表可被管理员通过接口调整（FR-008 配置后立即生效），比硬编码或配置文件更灵活；评分为只读计算不落库，规则变更即时生效（SC-004）。

## R5 数据权限复用

**决策**: 评分与预警计算基于 `CustomerService.checkViewPermission` 已过滤的客户集合；预警接口复用现有数据权限查询（012），确保 SALES 仅见自己/共享客户。

## R6 前端交互

**决策**: 客户详情页新增"客户 360"Tabs 区块（概览/订单回款/合同/工单），概览 Tab 展示健康度评分卡片（分数 + 颜色 + 失分原因）+ 金额汇总 Statistic；新增独立"流失预警"菜单页（AtRiskCustomersPage），表格列出预警客户 + 一键跟进弹窗。
