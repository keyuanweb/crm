package com.crm.dto.export;

import java.time.LocalDateTime;
import lombok.Data;

/** 导出任务响应。 */
@Data
public class ExportJobResponse {

  private Long id;
  private String exportType;
  private String status;
  private Long rowCount;
  private String errorMessage;
  private String fileName;
  private LocalDateTime createdAt;
  private LocalDateTime completedAt;
}
