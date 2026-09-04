package com.crm.dto.quota;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.Getter;
import lombok.Setter;

/** 销售配额请求 DTO（078-sales-quota）。 */
@Getter
@Setter
public class SalesQuotaRequest {

  /** 上级配额 ID（年度配额为 NULL）。 */
  private Long parentId;

  /** 季度（1-4，年度配额为 NULL）。 */
  private Integer quarter;

  /** 年份。 */
  @NotNull(message = "年份不能为空")
  private Integer year;

  /** 团队 ID（个人配额为 NULL）。 */
  private Long teamId;

  /** 用户 ID（团队配额为 NULL）。 */
  private Long userId;

  /** 配额金额（万元）。 */
  @NotNull(message = "配额金额不能为空")
  @DecimalMin(value = "0.01", message = "配额金额必须大于 0")
  private BigDecimal amount;

  /** 期间开始日期。 */
  @NotNull(message = "期间开始日期不能为空")
  private LocalDate periodStart;

  /** 期间结束日期。 */
  @NotNull(message = "期间结束日期不能为空")
  private LocalDate periodEnd;

  /** 调整原因（更新时必填）。 */
  private String changeReason;
}
