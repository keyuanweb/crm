package com.crm.dto.field;

/** 字段值通用接口（权限校验用，兼容 CustomFieldValueDTO）。 */
public interface FieldValueLike {
  Long getFieldId();

  String getValue();
}
