package com.crm.dto.field;

import lombok.Data;

/**
 * 字段权限配置面的一个**可配字段**（102-builtin-field-permission，{@code GET /field-permissions/available-fields}）。
 *
 * <p>{@code fieldId} 与 {@code fieldKey} **恰好一个非空**，与 {@link FieldPermissionRequest} 的入参形状一一对应：内置字段给
 * {@code fieldKey}（属性名，如 {@code phone}），自定义字段给 {@code fieldId}。客户端把这个对象原样拆成请求体即可，不必自己
 * 记住「哪种字段该发哪个键」。
 *
 * <p>{@code fieldName} 一律由后端给中文（内置字段取注册表的 {@code label}，自定义字段取既有字段名）——照 {@code
 * ExportExecutor.CUSTOMER_HEADERS} 的既有做法，中文不进前端字典，否则同一批字段名会长在两处。
 */
@Data
public class AvailableFieldResponse {

  /** 自定义字段 id；内置字段为 null。 */
  private Long fieldId;

  /** 内置字段名（属性名）；自定义字段为 null。 */
  private String fieldKey;

  private String fieldName;

  /** true = 内置字段（注册表），false = 自定义字段。 */
  private boolean builtin;

  public static AvailableFieldResponse builtin(String fieldKey, String label) {
    AvailableFieldResponse resp = new AvailableFieldResponse();
    resp.setFieldKey(fieldKey);
    resp.setFieldName(label);
    resp.setBuiltin(true);
    return resp;
  }

  public static AvailableFieldResponse custom(Long fieldId, String name) {
    AvailableFieldResponse resp = new AvailableFieldResponse();
    resp.setFieldId(fieldId);
    resp.setFieldName(name);
    resp.setBuiltin(false);
    return resp;
  }
}
