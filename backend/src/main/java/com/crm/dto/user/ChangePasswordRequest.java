package com.crm.dto.user;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** 登录用户修改自己密码请求（FR-006）。 */
@Data
public class ChangePasswordRequest {

  @NotBlank(message = "旧密码不能为空")
  private String oldPassword;

  @NotBlank(message = "新密码不能为空")
  @Size(min = 8, max = 64, message = "密码长度须为 8~64 位")
  private String newPassword;
}
