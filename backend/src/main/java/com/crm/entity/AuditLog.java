package com.crm.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/** 审计日志（FR-017）：仅记录操作元数据，不落敏感字段明文。 */
@Getter
@Setter
@TableName("audit_log")
public class AuditLog {

  @TableId(type = IdType.AUTO)
  private Long id;

  private Long actorId;
  private String actorName;

  /** CREATE / UPDATE / DELETE / IMPORT / EXPORT / CLOSE。 */
  private String action;

  /** CUSTOMER / OPPORTUNITY / SALES_OPPORTUNITY。 */
  private String entityType;

  private Long entityId;
  private String detail;
  private LocalDateTime createdAt;
}
