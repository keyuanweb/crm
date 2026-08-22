package com.crm.dto.followup;

import java.time.LocalDateTime;
import lombok.Data;

/** 客户详情中的跟进记录摘要。 */
@Data
public class FollowUpBrief {

  private Long id;
  private String method;
  private String content;
  private LocalDateTime createdAt;
}
