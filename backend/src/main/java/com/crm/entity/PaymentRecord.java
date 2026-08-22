package com.crm.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDate;
import lombok.Getter;
import lombok.Setter;

/** 回款记录（009-order-payment）。 */
@Getter
@Setter
@TableName("payment_record")
public class PaymentRecord extends BaseEntity {

  private Long planId;
  private Long orderId;

  /** 回款金额（分，>0）。 */
  private Long amount;

  private LocalDate paidAt;

  /** TRANSFER / CASH / CHECK / OTHER。 */
  private String method;

  private Long recordedBy;
}
