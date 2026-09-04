# Research: 回收站与批量恢复

## R1 绕过逻辑删除查询

**决策**: MyBatis-Plus 通用方法自动加 `deleted=0` 过滤；自定义注解 SQL（@Select/@Update/@Delete）不受影响。各 Mapper 增加：
- `@Select("SELECT * FROM <table> WHERE deleted=1 AND created_by=#{userId} ORDER BY updated_at DESC") selectDeletedByUser(Long userId)`（SALES）
- `@Select("SELECT * FROM <table> WHERE deleted=1 ORDER BY updated_at DESC") selectDeletedAll()`
- `@Update("UPDATE <table> SET deleted=0, version=version+1, updated_at=NOW() WHERE id=#{id}") restoreById(Long id)`
- `@Delete("DELETE FROM <table> WHERE id=#{id}") purgeById(Long id)`

**表名**: customer / `lead` / contact / opportunity。

## R2 数据权限

**决策**: ADMIN 调 selectDeletedAll；SALES 调 selectDeletedByUser(当前用户)。回收站主要面向 ADMIN（系统管理分组），SALES 视为可扩展。

## R3 恢复唯一性冲突

**决策**: 客户按 (name, company) 查重（复用 selectCount 带 deleted=0）；冲突时该条失败并提示，其余继续。线索/联系人/商机无强唯一约束，直接恢复。

## R4 合并分页

**决策**: 4 实体各查 deleted=1 记录（每实体最多 200 条），合并为 RecycleItem 列表（type/name/deletedAt/deletedBy），内存过滤分页。数据量小（回收站通常不多）。

## R5 前端

**决策**: RecycleBinPage 用 Table（类型 Tag + 名称 + 删除时间 + 删除人）+ 类型筛选 Select + 搜索 + 行选择 + "恢复/彻底删除"按钮（Modal 确认）。
