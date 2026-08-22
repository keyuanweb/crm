package com.crm.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

/** 站内通知（013-workflow-automation，NOTIFY 动作产出）。 */
@Getter
@Setter
@TableName("workflow_notification")
public class WorkflowNotification {

  private Long id;
  private Long userId;
  private String message;
  private Boolean read;
  private java.time.LocalDateTime createdAt;
}
