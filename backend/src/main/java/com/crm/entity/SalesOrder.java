package com.crm.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

/** 订单（009-order-payment）。 */
@Getter
@Setter
@TableName("sales_order")
public class SalesOrder extends BaseEntity {

  private String orderNo;
  private String title;
  private Long customerId;
  private Long contractId;

  /** 订单金额（分）。 */
  private Long amount;

  /** PENDING / PARTIAL / PAID。 */
  private String status;

  private String description;

  @TableField(fill = FieldFill.INSERT)
  private Long createdBy;
}
