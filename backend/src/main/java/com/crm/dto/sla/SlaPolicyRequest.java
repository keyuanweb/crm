package com.crm.dto.sla;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

/** SLA 策略创建/编辑请求（FR-C09）。 */
@Data
public class SlaPolicyRequest {

  @NotNull(message = "优先级不能为空")
  @Pattern(regexp = "^(LOW|MEDIUM|HIGH|URGENT)$", message = "优先级不合法")
  private String priority;

  @Min(value = 1, message = "响应时限至少 1 小时")
  private Integer respondHours;

  @Min(value = 1, message = "解决时限至少 1 小时")
  private Integer resolveHours;

  private Integer enabled = 1;

  private Integer version;
}
