# 快速开始：客户标签与细分

## 后端

1. Flyway V47：tag/customer_tag/segment 表。
2. 实体/Mapper：Tag/CustomerTag/Segment。
3. `TagService`：标签 CRUD + 打标/移除 + 客户标签查询（数据权限）。
4. `SegmentService`：细分 CRUD + 条件解析 + 成员计算（tag/amount/lastFollowUpDays，内存过滤）。
5. `TagController`/`SegmentController`。
6. 测试：TagServiceTest + SegmentServiceTest + TagIT。

## 前端

1. `types/tag.ts` + `tagService.ts` + `segmentService.ts`。
2. `TagListPage`（标签管理）+ `SegmentListPage`（细分管理，条件编辑器 + 成员预览）。
3. 客户列表：标签列 + 标签筛选。

## 验证

- 后端：`mvn test`；前端：`pnpm run typecheck/lint/test`。
- 手动：建标签 → 打标 → 筛选 → 建细分 → 成员计算 → 客户列表标签列。
