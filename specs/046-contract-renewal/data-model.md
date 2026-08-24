# 数据模型：合同续约管理模块

**Branch**: `046-contract-renewal` | **Date**: 2026-08-25

## 1. contract（扩展，Flyway V57）

| 列 | 类型 | 约束 | 说明 |
|---|---|---|---|
| renewed_from_id | BIGINT | NULL, FK→contract | 续约来源合同（自引用） |

- 现有列：contract_no/title/customer_id/quote_id/amount/start_date/end_date/content/status/approver_id/approved_at/reject_reason/effective_at/terminated_reason/remark（沿用 008）
- 索引：`idx_contract_renewed_from`（renewed_from_id）

## 2. 续约链语义

```
合同A(EFFECTIVE, endDate 临期)
  └─renewedFromId→ 合同B(续约自A, 新周期)
      └─renewedFromId→ 合同C(再次续约)
```

- 方向：`renewed_from_id` 指向旧合同（来源）；新合同为去向。
- 一个旧合同可被多次续约（多新合同引用同一旧合同）。

## 3. 到期归类（聚合视图，无独立表）

| 分组 | 条件 |
|---|---|
| 即将到期 | status=EFFECTIVE AND endDate ≤ today+90d AND endDate ≥ today |
| 已到期未续 | status=EFFECTIVE AND endDate < today（且无续约去向） |
| 已续约 | 存在其他合同 renewed_from_id = 本合同 id |

## 4. 业务规则

- 仅 EFFECTIVE 合同参与到期提醒；COMPLETED/TERMINATED/DRAFT/PENDING 不参与。
- renewed_from_id 引用合同必须存在（Service 校验，404 CONTRACT_NOT_FOUND）。
- 续约阈值 90 天（常量）。
