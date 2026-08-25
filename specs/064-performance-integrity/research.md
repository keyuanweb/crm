# Research: 性能与数据完整性模块

**Branch**: `064-performance-integrity` | **Date**: 2026-08-25

## 1. 流失预警批量聚合

**Decision**: `Customer360Service.healthLevelsBatch(customerIds)` 已存在（返回 level 映射）。扩展新方法 `healthScoresBatch(customerIds)` 返回 `Map<Long, Integer>`（level + score 一并返回），`atRiskCustomers` 改为：分页取候选客户（复用现有可见性/筛选条件）→ 批量取分 → 内存按 score 过滤高风险。SQL 层面仍分页，聚合从 7N 次查询降为固定次数（一次订单/回款/合同/工单/跟进批量查询）。

**Rationale**: 复用现有批量装配逻辑，避免全表载入。

**Alternatives considered**: 纯 SQL 聚合（复杂且跨 5 表）；保持现状（不可接受）。

## 2. 创建默认 owner

**Decision**: `CustomerService.create` / `LeadService.create`：`apply()` 后若 ownerId 为 null 且非 ADMIN 场景，设 owner = SecurityUtil.currentUserId()。ADMIN 显式指定或导入路径（ExcelService 内部插入）不强制。

**Rationale**: 消除无主数据；与行级权限模型一致（创建者拥有）。

**注**: 线索池（claim 流程）仍可用——管理员/导入的线索可不设 owner 进入池子；SALES 新建线索归自己。

## 3. 只读事务

**Decision**: 给主要只读聚合/详情方法（Customer/Lead/Contact/Opportunity/Ticket 的 page/detail/stats）补 `@Transactional(readOnly = true)`。

**Rationale**: MySQL 只读连接路由 + 一致性快照；无行为变化。
