package com.crm.dto.followup;

import java.time.LocalDateTime;
import lombok.Data;

/** 跟进记录响应。 */
@Data
public class FollowUpResponse {

  private Long id;
  private Long customerId;
  private Long opportunityId;
  private String method;
  private String content;
  private LocalDateTime nextFollowUpAt;
  private Long followUpBy;
  private String followUpByName;
  private Integer version;
  private LocalDateTime createdAt;
}
