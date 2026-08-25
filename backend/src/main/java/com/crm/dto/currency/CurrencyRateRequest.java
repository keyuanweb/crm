package com.crm.dto.currency;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import lombok.Data;

/** 币种请求。 */
@Data
public class CurrencyRateRequest {

  @NotBlank(message = "币种代码不能为空")
  @Size(max = 10, message = "代码不能超过 10 字符")
  private String code;

  @NotBlank(message = "币种名称不能为空")
  @Size(max = 50, message = "名称不能超过 50 字")
  private String name;

  @NotNull(message = "汇率不能为空")
  @DecimalMin(value = "0.000001", message = "汇率须 > 0")
  private BigDecimal rate;

  private Boolean enabled;

  private Integer version;
}
