package com.crm.dto.quota;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/** 配额达成响应 DTO（078-sales-quota）。 */
@Getter
@Setter
public class SalesQuotaAchievementResponse {

  private Long quotaId;
  private BigDecimal quotaAmount;
  private BigDecimal actualAmount;
  private BigDecimal achievementRate;
  private LocalDateTime calculatedAt;

  /** 状态：ON_TRACK（≥80%）/ AT_RISK（60-80%）/ BELOW_TARGET（<60%）。 */
  private String status;
}
