package com.crm.dto.contract;

import java.time.LocalDateTime;
import lombok.Data;

/** 合同附件响应。 */
@Data
public class AttachmentResponse {

  private Long id;
  private String fileName;
  private Long fileSize;
  private String contentType;
  private Long uploadedBy;
  private LocalDateTime createdAt;
}
