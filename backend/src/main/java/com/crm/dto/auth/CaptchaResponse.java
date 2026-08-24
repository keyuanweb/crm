package com.crm.dto.auth;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 图形验证码响应：唯一标识 + 内联 PNG 图片（data URL）。 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CaptchaResponse {

  private String captchaId;
  private String imageBase64;
}
