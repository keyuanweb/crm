package com.crm.dto.currency;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/** 产品币种价请求。 */
@Data
public class ProductPriceRequest {

  @NotBlank(message = "币种不能为空")
  private String currencyCode;

  @NotNull(message = "价格不能为空")
  private Long price;
}
