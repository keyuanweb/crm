# Quickstart: 回收站与批量恢复

## 后端

1. 4 个 Mapper（Customer/Lead/Contact/Opportunity）加 `selectDeletedByUser/selectDeletedAll/restoreById/purgeById`（@Select/@Update/@Delete 注解 SQL）。
2. `RecycleBinService`：跨实体列表（合并分页 + 数据权限过滤）+ 批量恢复（冲突跳过 + 审计）+ 彻底删除（审计）。
3. `RecycleBinController`：GET /recycle-bin、POST /recycle-bin/restore、POST /recycle-bin/purge。
4. 测试：`RecycleBinServiceTest` + `RecycleBinIT`。

## 前端

1. `types/recycle.ts` + `services/recycleService.ts`。
2. `RecycleBinPage`：Table + 类型筛选 + 搜索 + 勾选恢复/彻底删除。
3. `App.tsx`：注册回收站路由（系统管理分组）。

## 验证

- 后端：`mvn test`（新增测试，不影响既有 233）。
- 前端：`pnpm run typecheck` + `lint` + `test`。
- 手动：删除客户 → 回收站可见 → 恢复 → 客户列表重现。
