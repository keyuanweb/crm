# 快速开始：AI 智能助手（规则型智能建议）

## 后端

1. `SuggestionService`：聚合四类规则（流失/停滞/待跟进/高分线索），去重 + 排序 + 忽略过滤 + 上限 20。
2. `SuggestionController`：GET /suggestions、POST /suggestions/{type}/{entityId}/ignore、GET /suggestions/summary。
3. 忽略记录存 Redis（key `ai:ignore:<userId>:<type>:<entityId>`，TTL 90 天）。
4. 测试：`SuggestionServiceTest` + `SmartSuggestionIT`。

## 前端

1. `types/suggestion.ts` + `services/suggestionService.ts`：fetchSuggestions/ignoreSuggestion/fetchSummary。
2. `SuggestionCenterPage`：建议列表（类型 Tag + 优先级 + 原因 + 跳转 + 忽略）。
3. `DashboardPage`：AI 智能建议摘要卡（US3）。
4. `App.tsx`：注册建议路由（数据分析分组）。

## 验证

- 后端：`mvn test`（新增测试，不影响既有 224）。
- 前端：`pnpm run typecheck` + `lint` + `test`。
- 手动：首页见建议摘要；打开建议列表按优先级排序；忽略后消失。
