package com.crm.dto.user;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** 管理员重置密码请求（FR-005）。 */
@Data
public class ResetPasswordRequest {

  @NotBlank(message = "新密码不能为空")
  @Size(min = 8, max = 64, message = "密码长度须为 8~64 位")
  private String newPassword;
}
