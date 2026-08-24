# 快速开始：发票管理

## 后端

1. Flyway V53：invoice 表。
2. 实体/Mapper。
3. `InvoiceService`：开票（编号 + 可开额校验）+ 作废（原因 + 审计）+ 列表 + 统计。
4. `InvoiceController`。
5. 测试：InvoiceServiceTest + InvoiceIT。

## 前端

1. `types/invoice.ts` + `invoiceService.ts`。
2. `InvoiceListPage`（发票列表 + 统计卡 + 开票/作废弹窗）。
3. 路由注册（交易分组）。

## 验证

- 后端：`mvn test`；前端：`pnpm run typecheck/lint/test`。
- 手动：开票 → 列表可见 → 超可开额拒绝 → 作废（原因）→ 统计更新。
