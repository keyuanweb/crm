# Research: 权限体系加固模块

**Branch**: `063-security-hardening` | **Date**: 2026-08-25

## 1. 行级过滤模型

**Decision**: 复用 DataPermissionService（012）。`resolveVisibleOwnerIds(userId)` 返回当前用户可见的 owner 集合（本人 + ALL 角色可见集 + 共享），列表查询用 `owner_id IN (...)` SQL 过滤（非 ADMIN）；detail/update/delete 用 `checkWritePermission` 模式校验。

**Rationale**: SQL IN 过滤避免全量载入内存；与 CustomerService 现有实现对齐，语义一致。

**Alternatives considered**: 服务端注解切面（PermissionAspect 仅角色级）——行级需业务字段上下文，服务层校验更可控。

## 2. 实体过滤策略

| 实体 | 过滤维度 |
|---|---|
| Lead | owner_id ∈ 可见集（无 owner 或 NULL owner：仅 ADMIN 可见，防无主数据泄露） |
| Contact | 所属 customer.owner_id ∈ 可见集 或 customer 被共享给当前用户 |
| FollowUp | 关联实体（lead/customer）可见则可见 |
| Comment | 关联实体可见则可见 |

## 3. 导出安全

**Decision**: ExportExecutor 各导出类型接 `resolveVisibleOwnerIds` 过滤（非 ADMIN）；导出映射时手机/邮箱用 MaskingUtil 脱敏（ADMIN 豁免）。

**Rationale**: 与列表脱敏一致（MaskingUtil 已用于列表）。

## 4. 异常处理

**Decision**: GlobalExceptionHandler 补：HttpMessageNotReadableException/MethodArgumentTypeMismatchException/MissingServletRequestParameterException → 400；NoResourceFoundException → 404；DuplicateKeyException/DataIntegrityViolation → 409。IllegalArgumentException 不再直接回传 message（仅 500 统一文案）。

**Rationale**: 状态码语义正确；不泄露内部细节。
