package com.crm.dto.portal;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** 门户在线提单请求（公开，手机/邮箱识别客户）。 */
@Data
public class PortalTicketRequest {

  private String phone;

  private String email;

  @NotBlank(message = "工单标题不能为空")
  @Size(max = 200, message = "标题不能超过 200 字")
  private String title;

  @Size(max = 2000, message = "描述不能超过 2000 字")
  private String description;

  @Pattern(regexp = "^(LOW|MEDIUM|HIGH|URGENT)$", message = "优先级不合法")
  private String priority;
}
