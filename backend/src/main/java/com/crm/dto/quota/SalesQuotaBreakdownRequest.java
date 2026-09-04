package com.crm.dto.quota;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import lombok.Getter;
import lombok.Setter;

/** 配额分解请求 DTO（078-sales-quota）。 */
@Getter
@Setter
public class SalesQuotaBreakdownRequest {

  /** 季度（团队分解时可为 NULL）。 */
  private Integer quarter;

  /** 团队 ID（个人分解时可为 NULL）。 */
  private Long teamId;

  /** 用户 ID（团队分解时为 NULL）。 */
  private Long userId;

  /** 分解金额（万元）。 */
  @NotNull(message = "分解金额不能为空")
  @DecimalMin(value = "0.01", message = "分解金额必须大于 0")
  private BigDecimal amount;
}
