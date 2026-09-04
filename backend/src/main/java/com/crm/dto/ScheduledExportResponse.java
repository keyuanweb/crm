/** 定时导出任务响应 DTO（079-scheduled-export）。 */
package com.crm.dto;

import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ScheduledExportResponse {

  private Long id;
  private Long userId;
  private String entityType;
  private String filterConditions;
  private String exportFormat;
  private String cronExpression;
  private String status;
  private LocalDateTime nextExecutionTime;
  private LocalDateTime createdAt;
  private LocalDateTime updatedAt;
}
