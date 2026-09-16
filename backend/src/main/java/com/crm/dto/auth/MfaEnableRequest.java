package com.crm.dto.auth;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/** 绑定第二步（确认启用）请求：认证器当前显示的动态码（082，contracts/auth-mfa.md §2）。 */
@Data
public class MfaEnableRequest {

  @NotBlank(message = "动态码不能为空")
  private String code;
}
