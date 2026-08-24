# Research: 合同续约管理模块

**Branch**: `046-contract-renewal` | **Date**: 2026-08-25

## 1. 续约模型

**Decision**: `contract` 表新增 `renewed_from_id`（自引用，可空）。创建合同时可选"续约自"旧合同，建立单向续约链（旧合同 → 新合同）；一个旧合同可被多次续约（历史链）。

**Rationale**: 最小侵入——不新建续约实体，复用合同生命周期；自引用可追溯客户续约历史（对标 Salesforce Account Renewal）。

**Alternatives considered**: 独立 renewal 表——需双写维护，复杂度高；不记录续约关系——无法形成漏斗，否。

## 2. 到期归类

**Decision**: 续约视图按生效合同（EFFECTIVE）endDate 分组：到期 ≤90 天 → 即将到期；endDate < today → 已到期未续；存在续约去向（其他合同 renewedFromId=本合同）→ 已续约。已完成/终止/草稿不参与。

**Rationale**: 90 天为行业常用续约提前量；分组覆盖续约漏斗三态。

## 3. 接口设计

**Decision**: 独立 `ContractRenewalService` + `GET /contracts/renewal-overview`（分组 + 搜索 + 分页）。合同创建/详情在既有 ContractService/DTO 中补 renewedFromId 字段与来源/去向装配。

**Rationale**: 视图为聚合查询，独立服务避免污染生命周期逻辑；合同 CRUD 复用既有链。
