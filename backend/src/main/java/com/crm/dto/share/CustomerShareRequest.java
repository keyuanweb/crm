package com.crm.dto.share;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/** 客户共享请求（FR-DP07）。 */
@Data
public class CustomerShareRequest {

  @NotNull(message = "客户不能为空")
  private Long customerId;

  @NotNull(message = "共享目标用户不能为空")
  private Long sharedToUserId;
}
