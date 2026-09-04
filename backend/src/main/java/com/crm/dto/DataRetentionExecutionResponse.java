/** 数据保留执行记录响应 DTO（080-data-retention）。 */
package com.crm.dto;

import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class DataRetentionExecutionResponse {

  private Long id;
  private Long policyId;
  private LocalDateTime executedAt;
  private String status;
  private Integer processedCount;
  private String errorMessage;
  private LocalDateTime createdAt;
}
