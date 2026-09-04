/** 数据保留策略响应 DTO（080-data-retention）。 */
package com.crm.dto;

import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class DataRetentionPolicyResponse {

  private Long id;
  private String entityType;
  private Integer retentionDays;
  private String actionType;
  private String status;
  private LocalDateTime createdAt;
  private LocalDateTime updatedAt;
}
