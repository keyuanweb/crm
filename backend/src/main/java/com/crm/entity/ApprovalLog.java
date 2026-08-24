package com.crm.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

/** 审批日志（033-approval-flow）。 */
@Getter
@Setter
@TableName("approval_log")
public class ApprovalLog {

  private Long id;
  private Long instanceId;
  private Long taskId;

  /** SUBMIT / APPROVE / REJECT / TRANSFER / RENEW。 */
  private String action;

  private Long operator;
  private String comment;
  private java.time.LocalDateTime createdAt;
}
