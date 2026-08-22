package com.crm.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/** 通知（016-system-enhancement，统一表，替代 workflow_notification）。 */
@Getter
@Setter
@TableName("notification")
public class Notification {

  private Long id;
  private Long userId;

  /** WORKFLOW / TICKET_ASSIGN / TICKET_REPLY。 */
  private String type;

  private String message;

  /** read 为 MySQL 保留字，需反引号转义。 */
  @TableField("`read`")
  private Integer read;

  private String entityType;
  private Long entityId;
  private LocalDateTime createdAt;
}
