package com.crm.dto.quota;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/** 销售配额响应 DTO（078-sales-quota）。 */
@Getter
@Setter
public class SalesQuotaResponse {

  private Long id;
  private Long parentId;
  private Integer quarter;
  private Integer year;
  private Long teamId;
  private Long userId;
  private BigDecimal amount;
  private String status;
  private LocalDate periodStart;
  private LocalDate periodEnd;
  private BigDecimal actualAmount;
  private BigDecimal achievementRate;
  private LocalDateTime createdAt;

  /** 团队名称（非数据库字段）。 */
  private String teamName;

  /** 用户名称（非数据库字段）。 */
  private String userName;
}
