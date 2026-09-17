package com.crm.support;

/**
 * 一个可配权限的**内置字段**（102-builtin-field-permission）。
 *
 * <p>{@code fieldKey} 就是实体 / 请求 / 响应三侧共用的**属性名**（如 {@code phone}）——注册表在启动时
 * 用反射在每一个载体类上解析它，因此「属性名打错」在本设计下不可能静默生效，只会让 ApplicationContext 启动失败（见 {@link
 * BuiltinFieldRegistry#validate()}）。
 *
 * <p>{@code label} 是给配置面看的中文名（{@code GET /field-permissions/available-fields}）。
 *
 * <p><b>为什么是 record 而查找表用 {@code Map} 而不是 {@code Set}</b>：本类只被当成值读， 从不用 {@code equals}/{@code
 * hashCode} 做成员判定——那会把生成的这两个方法拉进覆盖率义务。
 */
public record BuiltinField(String entityType, String fieldKey, String label) {}
