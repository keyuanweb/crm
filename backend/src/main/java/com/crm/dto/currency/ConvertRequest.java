package com.crm.dto.currency;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/** 金额折算请求。 */
@Data
public class ConvertRequest {

  @NotNull(message = "金额不能为空")
  private Long amount;

  @NotBlank(message = "源币种不能为空")
  private String fromCurrency;

  @NotBlank(message = "目标币种不能为空")
  private String toCurrency;
}
