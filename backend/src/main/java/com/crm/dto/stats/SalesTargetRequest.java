package com.crm.dto.stats;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

/** 销售目标设置请求（contracts/stats.md）。 */
@Data
public class SalesTargetRequest {

  @NotBlank(message = "目标月份不能为空")
  @Pattern(regexp = "^\\d{4}-(0[1-9]|1[0-2])$", message = "月份格式必须为 YYYY-MM")
  private String month;

  @Min(value = 0, message = "目标金额不能为负")
  private Long targetAmount;

  /** 目标归属用户（空=全局目标；非空=个人目标，020）。 */
  private Long userId;
}
