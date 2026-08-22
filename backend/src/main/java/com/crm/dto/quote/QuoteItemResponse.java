package com.crm.dto.quote;

import java.math.BigDecimal;
import lombok.Data;

/** 报价行响应。 */
@Data
public class QuoteItemResponse {

  private Long id;
  private Long productId;
  private String productName;
  private Long unitPrice;
  private Integer quantity;
  private BigDecimal discount;
  private Long lineTotal;
}
