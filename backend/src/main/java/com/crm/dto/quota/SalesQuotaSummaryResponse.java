package com.crm.dto.quota;

import java.math.BigDecimal;
import lombok.Getter;
import lombok.Setter;

/** 销售配额年度汇总响应 DTO（078-sales-quota）。 */
@Getter
@Setter
public class SalesQuotaSummaryResponse {

  /** 年度总配额（万元）。 */
  private BigDecimal totalQuota;

  /** 年度总实际销售额（万元）。 */
  private BigDecimal totalActual;

  /** 年度总达成率（%）。 */
  private BigDecimal achievementRate;
}
