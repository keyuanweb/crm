# 快速开始：客户查重合并

## 后端

1. `CustomerMergeService`：查重扫描（名称归一化分组 + 电话/邮箱精确）+ 合并（关联数据转移 + 冲突 + 回收站 + 审计）。
2. `CustomerMergeController`：/customers/duplicates + /customers/merge。
3. 测试：CustomerMergeServiceTest + MergeIT。

## 前端

1. `types/merge.ts` + `customerMergeService.ts`。
2. `DuplicateMergePage`（查重合并页：扫描 + 重复对 + 合并确认）。
3. 路由注册。

## 验证

- 后端：`mvn test`；前端：`pnpm run typecheck/lint/test`。
- 手动：建两个相似客户 → 扫描检出 → 合并 → 关联数据转移 + 回收站可见。
