package com.crm.dto.auth;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/** 登录请求。 */
@Data
public class LoginRequest {

  @NotBlank(message = "用户名不能为空")
  private String username;

  @NotBlank(message = "密码不能为空")
  private String password;

  /** 验证码标识（由 GET /auth/captcha 返回）。 */
  private String captchaId;

  /** 用户输入的验证码（不区分大小写）。 */
  private String captchaCode;
}
