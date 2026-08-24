# 契约：智能线索评分与销售预测校准 lead-scoring

**Base**: 复用 `/api/v1/leads` 与 `/api/v1/stats`（无新端点；评分随线索返回，校准随预测返回）

## 线索评分（随现有接口返回）

`GET /leads`、`GET /leads/{id}`、`POST /leads`、`PUT /leads/{id}` 的线索对象 `score` 字段为自动评分（0-100，规则引擎计算），非手工输入。

```json
{ "id": 1, "name": "张三", "score": 75, "status": "NEW", "source": "REFERRAL" }
```

**排序**: `GET /leads` 支持 `sortBy=score&order=desc`（默认按评分降序；线索池视图同样按评分排序）。

## 销售预测校准（`GET /stats/dashboard`）

`data.forecast.breakdown[]` 每项新增 `probabilitySource` 字段：

```json
{
  "stage": "INITIAL_CONTACT",
  "amount": 1000000,
  "probability": 0.35,
  "weighted": 350000,
  "probabilitySource": "HISTORICAL"
}
```

- `probabilitySource`: `HISTORICAL`（历史样本 ≥10 计算）或 `DEFAULT`（样本不足回退）。
- CLOSED_WON / CLOSED_LOST 固定 1.0 / 0.0，`probabilitySource` 为 `FIXED`。

## 评分配置（可选扩展，管理员）

`GET/PUT /lead-score-config`：查询/调整评分规则（来源分值、颜色阈值）。若本期不做配置 UI，可延后，默认用种子配置。

## 备注

- 评分写回 lead.score 字段，事务内完成（创建/更新/跟进后触发）。
- 转化率统计缓存 5 分钟。
