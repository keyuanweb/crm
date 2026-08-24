package com.crm.dto.signature;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/** 签署请求（FR-S03）。 */
@Data
public class SignRequest {

  @NotBlank(message = "签名图不能为空")
  private String signatureImage;
}
