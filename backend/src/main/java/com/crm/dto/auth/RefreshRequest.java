package com.crm.dto.auth;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/** 刷新令牌请求。 */
@Data
public class RefreshRequest {

  @NotBlank(message = "刷新令牌不能为空")
  private String refreshToken;
}
