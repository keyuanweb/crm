# Quickstart: 自定义报表

## 后端

1. `ReportService`：按维度聚合（SALES=created_by、PRODUCT=quote_item、SOURCE=lead.source、STAGE=stage、TIME=日期粒度），内存分组。
2. `ReportController`：POST /reports/query、GET /reports/export（POI xlsx）。
3. 模板（P3）：Flyway V45 report_template 表 + ReportTemplateService（可选，本期基础）。
4. 数据权限：SALES 仅本人。
5. 测试：`ReportServiceTest` + `CustomReportsIT`。

## 前端

1. `types/report.ts` + `services/reportService.ts`：queryReport/exportReport。
2. `ReportCenterPage`：维度/指标/时间选择 + 结果表格（金额占比 Progress）+ 导出按钮。
3. `App.tsx`：注册报表路由（数据分析分组）。

## 验证

- 后端：`mvn test`（新增测试，不影响既有 220）。
- 前端：`pnpm run typecheck` + `lint` + `test`。
- 手动：选择"销售+赢单金额+本月"→ 表格展示；导出 xlsx 内容一致。
