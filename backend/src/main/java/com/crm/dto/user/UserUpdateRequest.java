package com.crm.dto.user;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** 编辑用户请求（FR-004）。 */
@Data
public class UserUpdateRequest {

  @Size(max = 50, message = "显示名不能超过 50 字")
  private String displayName;

  @Pattern(regexp = "^(ADMIN|SALES|SUPPORT)$", message = "角色须为 ADMIN/SALES/SUPPORT")
  private String role;

  private Boolean enabled;

  private Integer version;
}
