package com.crm.dto.ticket;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** 工单创建/编辑请求（FR-C01）。 */
@Data
public class TicketRequest {

  @NotNull(message = "客户不能为空")
  private Long customerId;

  private Long contactId;

  @NotBlank(message = "标题不能为空")
  @Size(max = 200, message = "标题不能超过 200 字")
  private String title;

  private String description;

  @NotBlank(message = "优先级不能为空")
  @Pattern(regexp = "^(LOW|MEDIUM|HIGH|URGENT)$", message = "优先级不合法")
  private String priority;

  private Long assigneeId;

  @Size(max = 500, message = "备注不能超过 500 字")
  private String remark;

  private Integer version;
}
