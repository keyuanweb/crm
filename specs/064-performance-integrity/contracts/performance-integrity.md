# 契约：性能与数据完整性（无新端点）

本模块不新增端点，行为变化如下：

## 1. 流失预警（GET /api/v1/customers/at-risk）

- 分页语义不变（page/pageSize 生效）。
- 内部实现从逐客户聚合（N+1）改为批量聚合；返回结构与判定规则不变（含 healthScore、risk 等级）。

## 2. 客户/线索创建（POST /api/v1/customers、POST /api/v1/leads）

- 未指定 owner 且创建者为非 ADMIN 业务用户时，响应中 `ownerId` = 当前用户 id。
- ADMIN 创建或显式指定 ownerId 时尊重原值。
- 导入（Excel）路径不变。

## 3. 只读事务

- 行为无变化（内部事务属性优化）。
