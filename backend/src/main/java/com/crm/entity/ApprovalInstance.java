package com.crm.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

/** 审批实例（033-approval-flow）。 */
@Getter
@Setter
@TableName("approval_instance")
public class ApprovalInstance extends BaseEntity {

  private Long flowId;
  private String businessType;
  private Long businessId;
  private String title;

  /** PENDING / APPROVED / REJECTED / CANCELED。 */
  private String status;

  private Long currentTaskId;
  private Long initiator;
}
