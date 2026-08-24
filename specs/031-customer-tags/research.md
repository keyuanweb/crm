# 研究：客户标签与细分

## R1 标签模型

**决策**: `tag`（id/name/color/entity_type/created_by）+ `customer_tag`（customer_id/tag_id 多对多，预留 lead_tag/contact_tag）。删除标签时级联删关联（物理）。

## R2 细分条件格式

**决策**: `segment` 表存 JSON 条件：
```json
{ "logic": "AND", "filters": [
  { "field": "tag", "op": "IN", "values": ["VIP"] },
  { "field": "amount", "op": "GT", "value": 100000 },
  { "field": "lastFollowUpDays", "op": "GT", "value": 30 }
]}
```
- field: tag / amount / lastFollowUpDays / newCustomersThisMonth?（本期 tag/amount/lastFollowUpDays）
- op: IN / GT / GTE / LT / LTE

## R3 成员计算

**决策**: SegmentService 解析条件 → 组合查询：
- tag → customer_tag join
- amount → 订单金额聚合（customer 关联订单 sum）
- lastFollowUpDays → follow_up 最近时间距今天数
条件组合用内存过滤（中小数据量：先取可见客户集，逐条件过滤），避免复杂 SQL join。

## R4 数据权限

**决策**: 打标/细分成员均先按 012 数据权限取可见客户集（ADMIN 全量/SALES 本人），再过滤。

## R5 前端

**决策**: 客户列表加"标签"列（Tag 展示）+ 标签筛选（Select 多选）；标签管理页（TagListPage）表格 CRUD；细分管理页（SegmentListPage）条件编辑器（行式筛选器：字段/操作符/值 + AND/OR）+ 成员数预览。
