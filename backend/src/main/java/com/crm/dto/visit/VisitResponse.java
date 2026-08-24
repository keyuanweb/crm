package com.crm.dto.visit;

import java.time.LocalDateTime;
import lombok.Data;

/** 拜访响应（035）。 */
@Data
public class VisitResponse {

  private Long id;
  private Long customerId;
  private String customerName;
  private String theme;
  private LocalDateTime visitTime;
  private Integer durationMinutes;
  private String status;
  private Double latitude;
  private Double longitude;
  private String locationText;
  private LocalDateTime checkInTime;
  private String summary;
  private Boolean lateFlag;
}
