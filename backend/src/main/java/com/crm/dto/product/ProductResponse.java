package com.crm.dto.product;

import java.time.LocalDateTime;
import lombok.Data;

/** 产品响应。 */
@Data
public class ProductResponse {

  private Long id;
  private String code;
  private String name;
  private String spec;
  private String unit;
  private Long standardPrice;
  private String status;
  private Integer version;
  private LocalDateTime createdAt;
}
