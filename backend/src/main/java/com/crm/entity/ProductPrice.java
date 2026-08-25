package com.crm.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/** 产品多币种价格（057-multi-currency）。 */
@Getter
@Setter
@TableName("product_price")
public class ProductPrice {

  private Long id;
  private Long productId;
  private String currencyCode;

  /** 价格（分）。 */
  private Long price;

  private LocalDateTime createdAt;
  private LocalDateTime updatedAt;
}
