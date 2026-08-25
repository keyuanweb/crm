package com.crm.dto.survey;

import java.time.LocalDateTime;
import lombok.Data;

/** 工单满意度响应。 */
@Data
public class SurveyResponse {

  private Long id;
  private Long ticketId;
  private Integer rating;
  private String comment;
  private Long createdBy;
  private LocalDateTime createdAt;
}
