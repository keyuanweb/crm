package com.crm.dto.approval;

import lombok.Data;

/** 审批操作请求（033）：approve/reject/transfer/renew。 */
@Data
public class TaskActionRequest {

  private String comment;
  private Long toUserId;
}
