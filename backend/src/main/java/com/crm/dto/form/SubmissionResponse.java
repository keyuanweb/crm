package com.crm.dto.form;

import java.time.LocalDateTime;
import lombok.Data;

/** 提交记录响应（036）。 */
@Data
public class SubmissionResponse {

  private Long id;
  private String payload;
  private String clientIp;
  private Long leadId;
  private LocalDateTime createdAt;
}
