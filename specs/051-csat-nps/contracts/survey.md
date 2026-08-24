# 契约：工单满意度调查 /tickets/{id}/survey + /surveys/stats

## 评分提交（ADMIN + SERVICE）

### POST /tickets/{id}/survey

对 CLOSED 工单提交满意度评分。

**Body**

```json
{ "rating": 5, "comment": "处理及时，很满意" }
```

**Response 201**

```json
{
  "id": 1, "ticketId": 20, "rating": 5, "comment": "处理及时，很满意",
  "createdBy": 1, "createdAt": "2026-08-25T10:00:00"
}
```

**规则**: 仅 CLOSED 可评（422 SURVEY_STATE_INVALID）；一张工单一次（409 SURVEY_ALREADY_SUBMITTED）；rating 1-5（422 SURVEY_RATING_INVALID）。

### GET /tickets/{id}/survey

查询工单评分。**Response 200**: 评分记录或 null。

## 统计 /surveys/stats（ADMIN + SERVICE）

### GET /surveys/stats?from=2026-01-01&to=2026-08-25

满意度统计（时间范围可选）。

**Response 200**

```json
{
  "sampleCount": 10, "csatAverage": 4.3,
  "npsScore": 40,
  "promoter": { "count": 6, "percent": 60 },
  "passive": { "count": 2, "percent": 20 },
  "detractor": { "count": 2, "percent": 20 }
}
```

- csatAverage：1-5 平均分。
- npsScore：推荐% - 贬损%（-100 ~ 100）。
- 分档：rating=5 推荐；rating=4 中立；rating=1-3 贬损。

## 错误码

| code | status | 含义 |
|---|---|---|
| SURVEY_STATE_INVALID | 422 | 工单未关闭，不可评分 |
| SURVEY_ALREADY_SUBMITTED | 409 | 该工单已评分 |
| SURVEY_RATING_INVALID | 422 | 评分不合法（须 1-5） |
| TICKET_NOT_FOUND | 404 | 工单不存在（沿用） |
