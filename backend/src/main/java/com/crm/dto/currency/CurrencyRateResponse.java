package com.crm.dto.currency;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Data;

/** 币种响应。 */
@Data
public class CurrencyRateResponse {

  private Long id;
  private String code;
  private String name;
  private BigDecimal rate;
  private Boolean isBase;
  private Boolean enabled;
  private Integer version;
  private LocalDateTime updatedAt;
}
