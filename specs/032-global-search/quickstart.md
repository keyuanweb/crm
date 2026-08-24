# 快速开始：全局搜索

## 后端

1. `SearchService`：6 实体 LIKE 聚合 + 数据权限 + 多关键字。
2. `SearchController`：/api/v1/search（下拉）+ /api/v1/search/full（结果页）。
3. 测试：SearchServiceTest + SearchIT。

## 前端

1. `searchService.ts`。
2. `GlobalSearch`（Header 搜索框：防抖下拉 + 回车结果页）。
3. `SearchResultPage`（结果页：Tab 分组 + 高亮 + 分页）。
4. App.tsx Header 集成 + 路由 /search。

## 验证

- 后端：`mvn test`；前端：`pnpm run typecheck/lint/test`。
- 手动：Header 输关键字 → 下拉分组结果 → 点击跳转 → 回车结果页。
