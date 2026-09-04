/** 定时导出执行记录响应 DTO（079-scheduled-export）。 */
package com.crm.dto;

import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ScheduledExportExecutionResponse {

  private Long id;
  private Long scheduledExportId;
  private LocalDateTime executedAt;
  private String status;
  private String filePath;
  private Long fileSize;
  private Integer rowCount;
  private String emailStatus;
  private String errorMessage;
  private LocalDateTime createdAt;
}
