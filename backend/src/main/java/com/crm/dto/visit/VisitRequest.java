package com.crm.dto.visit;

import java.time.LocalDateTime;
import lombok.Data;

/** 拜访计划请求（035）。 */
@Data
public class VisitRequest {

  private Long customerId;
  private String theme;
  private LocalDateTime visitTime;
  private Integer durationMinutes;
}
