package com.crm.dto.signature;

import java.time.LocalDateTime;
import lombok.Data;

/** 签署记录响应。 */
@Data
public class SignatureRecordResponse {

  private Long id;
  private String businessType;
  private Long businessId;
  private Long signerId;
  private String signerName;
  private LocalDateTime signedAt;
  private String signatureImage;
}
