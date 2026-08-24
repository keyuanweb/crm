# 数据模型：发票

## invoice

| 字段 | 类型 | 说明 |
|---|---|---|
| id | bigint | 主键 |
| order_id | bigint | 订单 |
| invoice_no | varchar(30) | 编号 INV-{yyyyMM}-{seq}（唯一） |
| title | varchar(200) | 抬头 |
| tax_no | varchar(50)? | 税号 |
| amount | bigint | 金额（分） |
| invoice_type | varchar(20) | GENERAL(普票)/SPECIAL(专票) |
| status | varchar(20) | DRAFT/ISSUED/VOID |
| void_reason | varchar(255)? | 作废原因 |
| issued_at | datetime? | 开票时间 |
| voided_at | datetime? | 作废时间 |
| created_by / deleted / version / created_at / updated_at | | 审计 |

## 约束

- 累计开票（非作废）≤ 订单金额。
- 编号唯一（订单维度可用）。发票仅作废不删除。
