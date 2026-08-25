# 契约：SLA 日历 /api/v1/sla-calendar

**Base**: `/api/v1/sla-calendar`（仅 ADMIN）

## GET /sla-calendar

当前日历配置。**Response 200**

```json
{
  "id": 1, "workSlots": [ { "start": "09:00", "end": "18:00" } ],
  "workDays": [1, 2, 3, 4, 5],
  "holidays": ["2026-10-01"],
  "enabled": true, "updatedAt": "2026-08-25T10:00:00"
}
```

## PUT /sla-calendar

更新配置。**Body**: 同响应结构（enabled 必填）。**Response 200**: 更新后配置。

**校验**: workSlots 每项 start<end 且格式 HH:mm；workDays ∈ 1-7；holidays 格式 YYYY-MM-DD。

## 生效说明

- 无配置/未启用：工单 SLA = 创建时刻 + respondHours/resolveHours（旧行为）。
- 启用：SLA 按工作日历计算（跳过非工作时间与节假日），仅新工单生效。

## 错误码

| code | status | 含义 |
|---|---|---|
| SLA_CALENDAR_SLOT_INVALID | 422 | 工作时间段不合法 |
| SLA_CALENDAR_DAY_INVALID | 422 | 工作周配置不合法 |
| SLA_CALENDAR_HOLIDAY_INVALID | 422 | 节假日格式不合法 |
