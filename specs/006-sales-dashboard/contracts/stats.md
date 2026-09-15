# 契约：统计 /stats（006 扩展）

**Base**: `/api/v1/stats`（权限：ADMIN + SALES，见 README 矩阵）

> 本文档为 `specs/001-crm-core/contracts/stats.md` 的 006 模块扩展。既有 `GET /stats/opportunity-pipeline` 保持不动。

## GET /stats/dashboard

销售仪表盘聚合数据（FR-D01~D09）。

**Query**: 无（固定当月口径；`month` 可选，格式 `YYYY-MM`，默认当月）。

**Response 200**

```json
{
  "summary": {
    "opportunityCount": 10,
    "amountTotal": 2000000,
    "winRate": 0.667,
    "customerCount": 8,
    "activeCustomerCount": 7,
    "newCustomersThisMonth": 2
  },
  "funnel": {
    "stages": [
      { "stage": "INITIAL_CONTACT", "count": 4, "amountTotal": 400000, "conversionRate": null },
      { "stage": "NEGOTIATING",     "count": 3, "amountTotal": 900000, "conversionRate": 0.75 },
      { "stage": "CLOSED_WON",      "count": 2, "amountTotal": 500000, "conversionRate": 0.667 },
      { "stage": "CLOSED_LOST",     "count": 1, "amountTotal": 200000, "conversionRate": null }
    ],
    "grandTotal": { "count": 10, "amountTotal": 2000000 }
  },
  "forecast": {
    "weightedAmount": 830000,
    "breakdown": [
      { "stage": "INITIAL_CONTACT", "amount": 400000, "probability": 0.2, "weighted": 80000,  "probabilitySource": "DEFAULT" },
      { "stage": "NEGOTIATING",     "amount": 900000, "probability": 0.5, "weighted": 450000, "probabilitySource": "HISTORICAL" },
      { "stage": "CLOSED_WON",      "amount": 500000, "probability": 1.0, "weighted": 500000, "probabilitySource": "FIXED" }
    ]
  },
  "performance": {
    "month": "2026-08",
    "targetAmount": 1000000,
    "wonAmount": 500000,
    "achievementRate": 0.5,
    "configured": true
  },
  "followUps": {
    "total": 12,
    "byMethod": [
      { "method": "PHONE", "count": 6 },
      { "method": "EMAIL", "count": 4 },
      { "method": "MEETING", "count": 2 }
    ],
    "recent": [
      { "id": 101, "method": "PHONE", "content": "沟通续约意向", "customerName": "Acme 科技", "followUpBy": "销售员", "createdAt": "2026-08-22T10:00:00" }
    ]
  },
  "stalledOpportunities": [
    { "id": 7, "opportunityName": "CRM 采购", "customerName": "Acme 科技", "amount": 300000, "stage": "NEGOTIATING", "stalledDays": 12, "lastUpdatedAt": "2026-08-10T09:00:00" }
  ],
  "generatedAt": "2026-08-22T12:00:00"
}
```

**规则**:
- 汇总口径 = `deleted = 0`；漏斗/预测基于全部销售机会（含终态）；
- `conversionRate` = 后一阶段数量 / 前一阶段数量（首阶段与终态后为 null）；
- `winRate` = CLOSED_WON / (CLOSED_WON + CLOSED_LOST)，无已关闭机会时为 0；
- 预测概率**不再是固定配置**——见下方「订正块（2026-09-15，由 `specs/093-dashboard-truthfulness/` 追加）」；
- `performance.configured=false` 时 targetAmount/achievementRate 为 null（未设目标）；
- 停滞预警阈值默认 7 天（`crm.stats.stalled-days`），仅活跃阶段（INITIAL_CONTACT/NEGOTIATING）；
- 结果缓存 Redis 5 分钟；写操作（销售机会/客户/跟进/目标）后失效；
- 金额单位为分，前端展示换算。

**订正块（2026-09-15，由 `specs/093-dashboard-truthfulness/` 追加）**

上文两处（示例 JSON 的 `probability`、规则首条的「固定配置」）写于 006 实施期，**当时的实现确为固定概率**。此后该口径已由 **`specs/019-lead-scoring`** 批次的**阶段转化率历史校准**取代，本块订正之；**上文原文一律保留不改写**，理由见本条第三点。

**一、概率现由 `StageConversionService.probabilityFor(stage)` 决定**（`backend/src/main/java/com/crm/service/StageConversionService.java:33`）：

- **终态**（`isTerminal`，即 `CLOSED_WON` / `CLOSED_LOST`）**不参与历史校准**，直接取阶段字典 `opportunity_stage.probability` 列的值（语义确定：1 / 0）。源码注释写明理由：「让『赢单』的概率随历史漂移毫无意义」。
- **非终态**：取该阶段的历史转化率；**样本不足时回退**到阶段字典的 `probability` 列。样本量 = 该阶段活跃数 + 已赢单数，阈值 `MIN_SAMPLE = 10`（`StageConversionService.java:16`），不足即回退并打日志 `Stage {} sample insufficient ({}<{}), fallback default`。
- ⚠️ 因此**默认概率的真源是阶段字典表的 `probability` 列，不是代码里的常量表**——改字典里的赢率等于直接改预测数字。

**二、契约此前缺失的 `probabilitySource` 字段已补**（示例 JSON 同批补上）。取值三选一，由 `DashboardStatsService.probabilitySourceOf`（`:218`）判定：

| 值 | 含义 | 判定条件 |
|---|---|---|
| `HISTORICAL` | 概率来自历史校准（样本充足） | 非终态 且 `stageConversionService.isHistorical(stage)` 为真 |
| `DEFAULT` | 概率回退到阶段字典默认值 | 非终态 且样本不足 |
| `FIXED` | 终态固定概率，不参与校准 | 阶段为 `CLOSED_WON` / `CLOSED_LOST` |

消费端类型见 `frontend/src/types/stats.ts:41-42` 的 `ForecastItem.probabilitySource`（`string`，注释已标注三值）。

**三、为何不回写上文**：概率的**来源**变了，不等于示例里的**数字**错了——`0.2 / 0.5 / 1.0` 在样本不足时仍是回退值。改的是「这些数字从哪来」，不是「它们是多少」。把历史行改写成新口径，会让「006 实施期到底实现了什么」这段记录消失。

**错误**: 401（未认证）/ 403（SUPPORT 角色不可见）。

## GET /stats/sales-targets

查询某月销售目标（FR-D04，查询部分）。

**Query**: `month`（必填，`YYYY-MM`）。

**Response 200**

```json
{
  "month": "2026-08",
  "targetAmount": 1000000,
  "createdBy": 1,
  "updatedAt": "2026-08-22T09:00:00"
}
```

**规则**: 未设置目标时返回 `targetAmount: null`（200，非 404）。

## PUT /stats/sales-targets

设置/更新某月销售目标（FR-D04 设置部分，upsert 语义；仅 ADMIN）。

**Body**:

```json
{ "month": "2026-08", "targetAmount": 1000000 }
```

**Response 200**（同 GET 响应结构）。

**校验**: `month` 格式 `YYYY-MM`；`targetAmount ≥ 0`。

**错误**: 400（格式错误）/ 401 / 403（非 ADMIN）。

## 权限矩阵（更新）

| 端点 | ADMIN | SALES | SUPPORT |
|---|---|---|---|
| GET /stats/opportunity-pipeline | ✅ | ✅ | ❌ |
| GET /stats/dashboard | ✅ | ✅ | ❌ |
| GET /stats/sales-targets | ✅ | ✅ | ❌ |
| PUT /stats/sales-targets | ✅ | ❌ | ❌ |
