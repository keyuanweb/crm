# 契约：外勤拜访

**Base**: `/api/v1/field-visits`（visit:manage 或复用客户权限；数据范围隔离）

## GET /field-visits?status=&month=

拜访列表（按创建人数据范围，可按状态/月份筛选）。

**Response**: `{ "items": [ { "id":1, "customerId":1, "customerName":"Acme", "theme":"谈续约", "visitTime":"2026-08-25T10:00", "status":"PLANNED", "latitude":null, "longitude":null, "checkInTime":null, "summary":null, "lateFlag":false } ], "total": n }`

## POST /field-visits

创建计划。**Body**: `{ "customerId":1, "theme":"谈续约", "visitTime":"2026-08-25T10:00", "durationMinutes":60 }`

## PUT /field-visits/{id}

编辑计划（仅 PLANNED）。

## POST /field-visits/{id}/cancel

取消计划（PLANNED → CANCELED）。

## POST /field-visits/{id}/check-in

签到。**Body**:
```json
{ "latitude": 31.2304, "longitude": 121.4737, "locationText": "上海市浦东新区…", "summary": "客户确认续约意向" }
```
签到成功 → status DONE + 记录坐标/时间 + 小结自动写跟进。**防重复**：非 PLANNED 拒绝 400。

## GET /field-visits/stats?month=2026-08

拜访统计：`{ "items": [ { "userId": 1, "userName": "张三", "planned": 8, "done": 6, "canceled": 1, "completionRate": 0.75 } ], "totalPlanned": 8, "totalDone": 6 }`

## 备注

- 签到写 follow_up（type=VISIT）→ 客户时间线可见。
- 权限 visit:manage 入 028 字典。
