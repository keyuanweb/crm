package com.crm.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

/** 产品（007-product-cpq，产品目录）。 */
@Getter
@Setter
@TableName("product")
public class Product extends BaseEntity {

  private String code;
  private String name;
  private String spec;
  private String unit;

  /** 标准售价（分）。 */
  private Long standardPrice;

  /** ACTIVE / INACTIVE。 */
  private String status;

  private Long createdBy;
}
