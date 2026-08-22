package com.crm.dto.order;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.time.LocalDate;
import lombok.Data;

/** 回款登记请求（FR-OP05）。 */
@Data
public class PaymentRequest {

  @NotNull(message = "必须选择回款期次")
  private Long planId;

  @Min(value = 1, message = "回款金额必须 >0")
  private Long amount;

  @NotNull(message = "回款日期不能为空")
  private LocalDate paidAt;

  @Pattern(regexp = "^(TRANSFER|CASH|CHECK|OTHER)$", message = "回款方式不合法")
  private String method;
}
