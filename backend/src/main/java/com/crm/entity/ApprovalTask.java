package com.crm.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

/** 审批任务（033-approval-flow）。 */
@Getter
@Setter
@TableName("approval_task")
public class ApprovalTask {

  private Long id;
  private Long instanceId;
  private String nodeName;

  /** ROLE / USER / MANAGER。 */
  private String approverType;

  private String approverValue;
  private Long approverId;

  /** PENDING / APPROVED / REJECTED / TRANSFERRED。 */
  private String status;

  private String comment;
  private Integer seq;
  private java.time.LocalDateTime createdAt;
  private java.time.LocalDateTime updatedAt;
}
