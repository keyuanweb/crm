# Research: 自定义报表

## R1 维度与指标矩阵

**决策**: 限定有意义的维度×指标组合（不做任意笛卡尔），各维度有对应数据源：

| 维度 | 数据源 | 可用指标 |
|---|---|---|
| 销售（created_by） | sales_opportunity | 数量、金额（阶段过滤可选） |
| 产品 | quote_item（product_id/product_name） | 数量（行数）、金额（lineTotal） |
| 线索来源 | lead（source） | 数量（按状态可选） |
| 商机阶段 | sales_opportunity（stage） | 数量、金额 |
| 时间（日/月） | 各实体 created_at | 数量、金额 |

**聚合方式**: Mapper.selectList 按维度字段+时间范围取全量（万级），内存 LinkedHashMap 分组累加 count/amount。

## R2 时间维度

**决策**: 时间维度可选"按日/按月"粒度，与其它维度（销售/产品/来源/阶段）正交：选时间维度时结果按日/月分组；选其它维度时按该维度分组（时间仅作过滤）。支持"时间维度 + 第二维度"组合（如 月×销售）。

## R3 导出

**决策**: `GET /reports/export?query...` 用 POI 生成 xlsx（表头=维度列+数量+金额+占比），响应流下载。复用 016 的 POI 依赖（poi-ooxml 已有）。

## R4 模板（P3）

**决策**: report_template 表（id/name/dimension/metric/granularity/start_date/end_date/created_by），ADMIN 保存/加载。查询接口接收模板 id 或完整参数。

## R5 数据权限

**决策**: 报表查询按 012 过滤——SALES 用户仅聚合其可见数据（own/share），ADMIN 全量。实现：查询时对实体 id 应用 dataScopeFilter（复用 CustomerService/LeadService 的模式，或按 created_by=当前用户简化）。
