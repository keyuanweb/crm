package com.crm.dto.portal;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/** 门户工单进度查询请求（工单号 + 手机/邮箱双验证）。 */
@Data
public class PortalTicketQueryRequest {

  @NotNull(message = "工单号（id）不能为空")
  private Long ticketId;

  private String phone;

  private String email;
}
