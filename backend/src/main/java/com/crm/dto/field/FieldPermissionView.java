package com.crm.dto.field;

import lombok.Data;

/** 字段权限视图（字段列表响应内嵌）。 */
@Data
public class FieldPermissionView {

  private boolean hidden;
  private boolean readOnly;

  public static FieldPermissionView editable() {
    FieldPermissionView v = new FieldPermissionView();
    v.hidden = false;
    v.readOnly = false;
    return v;
  }

  public static FieldPermissionView of(String permission) {
    FieldPermissionView v = new FieldPermissionView();
    v.hidden = "HIDDEN".equals(permission);
    v.readOnly = "READ_ONLY".equals(permission);
    return v;
  }
}
