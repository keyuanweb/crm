# Research: 自定义对象模块

**Branch**: `059-custom-object` | **Date**: 2026-08-25

## 1. 对象模型

**Decision**: `custom_object` 表（name、code 唯一、fields JSON、enabled、deleted 逻辑删除）。字段集内嵌 JSON：`[{"field":"name","label":"项目名称","type":"TEXT","required":true},{"field":"status","label":"状态","type":"SELECT","options":"进行中,已完成"}]`。

**Rationale**: 字段集内嵌简化（不建独立字段表）；编码唯一防冲突。

## 2. 记录模型

**Decision**: `custom_object_record` 表（object_id、values JSON 键值对、created_by/created_at）。搜索按 JSON 值 LIKE（v1 简单搜索）。

**Rationale**: JSON 键值对存储适配动态字段；搜索用 values LIKE 满足基本检索。

## 3. 权限与生命周期

**Decision**: 对象定义仅 ADMIN；记录管理 ADMIN+SALES。停用对象不可新建记录（422），既有可查；删除对象逻辑删除（记录保留）。

**Rationale**: 低代码建模需管理员管控；记录读写面向业务用户。
