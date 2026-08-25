# 契约：体验优化批次（无新端点）

本批次不新增 API 端点，行为变化如下：

## 1. 首页统计（GET /api/v1/stats/dashboard 或既有统计端点）

- `forecast.breakdown`：按阶段唯一（同阶段 amount/weighted 合计），不再逐商机多条。
- `weightedAmount`：语义不变。

## 2. 客户列表（GET /api/v1/customers）

- 电话/邮箱**完整显示**（不再列表脱敏）；详情/编辑响应含 `ownerId`。
- 导出（POST /api/v1/exports）仍对非管理员脱敏（063 保留）。

## 3. 产品（GET/POST/PUT /api/v1/products + /products/{id}/prices）

- 前端编辑 Modal 集成多币种价（复用 057 接口，契约不变）。

## 4. 前端行为

- 未知路径 → 404 页（登录后带菜单）。
- 路由切换滚动复位 + 淡入过渡。
- 首页问候插值正确显示用户名。
