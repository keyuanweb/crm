package com.crm.dto.audit;

import java.time.LocalDateTime;
import lombok.Data;

/** 审计日志响应（FR-017：仅管理员可查）。 */
@Data
public class AuditLogResponse {

  private Long id;
  private Long actorId;
  private String actorName;

  /** CREATE / UPDATE / DELETE / IMPORT / EXPORT / CLOSE / RESET_PASSWORD / CHANGE_PASSWORD。 */
  private String action;

  private String entityType;
  private Long entityId;
  private String detail;
  private LocalDateTime createdAt;
}
