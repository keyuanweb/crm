package com.crm.dto.sla;

import java.time.LocalDateTime;
import lombok.Data;

/** SLA 策略响应。 */
@Data
public class SlaPolicyResponse {

  private Long id;
  private String priority;
  private Integer respondHours;
  private Integer resolveHours;
  private Integer enabled;
  private Integer version;
  private LocalDateTime createdAt;
}
