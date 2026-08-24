package com.crm.dto.user;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** 创建用户请求（FR-003）。 */
@Data
public class UserCreateRequest {

  @NotBlank(message = "用户名不能为空")
  @Pattern(regexp = "^[a-zA-Z0-9_]{3,50}$", message = "用户名须为 3~50 位字母/数字/下划线")
  private String username;

  @NotBlank(message = "显示名不能为空")
  @Size(max = 50, message = "显示名不能超过 50 字")
  private String displayName;

  @NotBlank(message = "角色不能为空")
  @Pattern(regexp = "^[A-Z][A-Z0-9_]{0,49}$", message = "角色编码不合法")
  private String role;

  @NotBlank(message = "初始密码不能为空")
  @Size(min = 8, max = 64, message = "密码长度须为 8~64 位")
  private String password;
}
