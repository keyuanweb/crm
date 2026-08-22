package com.crm.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import java.math.BigDecimal;
import lombok.Getter;
import lombok.Setter;

/** 报价单行明细（007-product-cpq，产品快照）。 */
@Getter
@Setter
@TableName("quote_item")
public class QuoteItem extends BaseEntity {

  private Long quoteId;
  private Long productId;
  private String productName;

  /** 单价快照（分）。 */
  private Long unitPrice;

  private Integer quantity;

  /** 折扣 0~1。 */
  private BigDecimal discount;

  /** 行小计（分）= 单价×数量×(1-折扣)。 */
  private Long lineTotal;
}
