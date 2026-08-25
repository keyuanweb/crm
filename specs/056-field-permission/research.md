# Research: 字段级读写权限模块

**Branch**: `056-field-permission` | **Date**: 2026-08-25

## 1. 权限模型

**Decision**: `field_permission` 表（role_id、entity_type、field_id、permission HIDDEN/READ_ONLY/EDITABLE，唯一约束 角色+实体+字段）。ADMIN 角色恒豁免（不查配置直接 EDITABLE）。

**Rationale**: 三态覆盖隐藏/只读/可编辑；角色粒度与 028 一致；唯一约束防重复。

## 2. 应用点

**Decision**:
- 字段列表：CustomFieldService.list（或字段视图 DTO）按当前角色加 `permission` 标记（hidden/readOnly/editable）。
- 保存校验：CustomFieldService.saveValues 遍历提交值，HIDDEN 字段写入 → 422 FIELD_HIDDEN；READ_ONLY 字段（已存在值被修改）→ 422 FIELD_READ_ONLY。ADMIN 跳过。

**Rationale**: 服务端强制是安全底线（前端隐藏仅 UX）；saveValues 是自定义字段统一写入点，拦截集中。

**Alternatives considered**: 前端仅隐藏——可绕过；逐 Controller 校验——分散。集中 saveValues 最简。
