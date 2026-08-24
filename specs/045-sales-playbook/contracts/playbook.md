# 契约：销售 Playbook /stage-actions + /sales-opportunities/{id}/actions

## 模板管理 /api/v1/stage-actions（仅 ADMIN）

### GET /stage-actions

动作模板分页列表（按阶段筛选）。

**Query**: `stage`、`page`、`pageSize`。

**Response 200**

```json
{
  "items": [
    { "id": 1, "stage": "INITIAL_CONTACT", "actionName": "发送产品资料",
      "description": "首次接触后 24h 内发送", "sortOrder": 0, "required": true,
      "enabled": true, "version": 0, "createdAt": "2026-08-24T10:00:00" }
  ],
  "total": 1, "page": 1, "pageSize": 20
}
```

### POST /stage-actions

创建模板。**Body**: `{ "stage": "INITIAL_CONTACT", "actionName": "发送产品资料", "description": "...", "sortOrder": 0, "required": true }`。**Response 201**。

**校验**: stage ∈ 活动阶段枚举；actionName 必填 ≤100；required/sortOrder 可选。

### PUT /stage-actions/{id}

编辑模板（含 version）。**Body**: 同 POST + `version`。

### DELETE /stage-actions/{id}

逻辑删除模板。

## 销售机会动作 /api/v1/sales-opportunities/{id}/actions（ADMIN+SALES）

### GET /sales-opportunities/{id}/actions

销售机会当前阶段动作清单（仅启用模板，按排序；含完成状态）。

**Response 200**

```json
{
  "items": [
    { "templateId": 1, "actionName": "发送产品资料", "description": "...", "required": true,
      "completed": true, "completedBy": 2, "completedAt": "2026-08-24T11:00:00" }
  ],
  "total": 1
}
```

### POST /sales-opportunities/{id}/actions

勾选完成某动作。**Body**: `{ "templateId": 1 }`。**Response 201**: 完成记录结构。

**规则**: 机会处于活动阶段（非 CLOSED）才可勾选；同一动作不可重复完成（409）；终态阶段不可勾选（422）。

## 阶段流转必做校验（在既有流转接口体现）

- PUT /sales-opportunities/{id} 与 POST /sales-opportunities/{id}/close：若当前阶段存在未完成必做动作，响应增加 `warning: { "code": "PLAYBOOK_REQUIRED_PENDING", "message": "有必做动作未完成" }`（前端确认后继续，后端不阻断）。

## 错误码

| code | status | 含义 |
|---|---|---|
| PLAYBOOK_TEMPLATE_NOT_FOUND | 404 | 动作模板不存在 |
| PLAYBOOK_STAGE_INVALID | 422 | 阶段不可配置动作（非活动阶段） |
| PLAYBOOK_ACTION_ALREADY_DONE | 409 | 该动作已完成 |
| PLAYBOOK_OPPORTUNITY_CLOSED | 422 | 机会已关闭，不可操作动作 |
