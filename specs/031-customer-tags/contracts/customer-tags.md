# 契约：客户标签与细分

**Base**: `/api/v1/tags`、`/api/v1/segments`（tag:manage 管理；打标按数据范围）

## GET /tags?entityType=CUSTOMER

标签列表：`{ "items": [ { "id":1, "name":"VIP", "color":"red", "entityType":"CUSTOMER" } ], "total": n }`

## POST /tags

创建标签。**Body**: `{ "name":"VIP", "color":"red", "entityType":"CUSTOMER" }`（name 重复 409）。

## PUT /tags/{id} / DELETE /tags/{id}

编辑/删除（删除级联清关联）。

## PUT /customers/{id}/tags

客户打标（覆盖式）。**Body**: `{ "tagIds": [1, 2] }`

## GET /customers/{id}/tags

客户标签列表。

## GET /segments

细分列表（含成员数）：`{ "items": [ { "id":1, "name":"高价值未跟进", "conditions":{...}, "memberCount": 5 } ], "total": n }`

## POST /segments

创建细分。**Body**: `{ "name":"...", "description":"", "conditions": { "logic":"AND", "filters":[ { "field":"tag", "op":"IN", "values":["VIP"] }, { "field":"amount", "op":"GT", "value":100000 }, { "field":"lastFollowUpDays", "op":"GT", "value":30 } ] } }`

## GET /segments/{id}/members?page=&pageSize=

细分成员（客户分页，按数据范围）。

## GET /segments/{id}/count

成员数。

## 条件字段（field）

- tag：标签名集合（op: IN）
- amount：订单金额合计（op: GT/GTE/LT/LTE）
- lastFollowUpDays：距最近跟进天数（op: GT/GTE/LT/LTE；无跟进 = 999 天）

## 备注

- 打标/成员均按 012 数据权限过滤。
- 条件操作符白名单：IN/GT/GTE/LT/LTE；logic: AND/OR。
