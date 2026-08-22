package com.crm.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDate;
import lombok.Getter;
import lombok.Setter;

/** 回款计划期次（009-order-payment）。 */
@Getter
@Setter
@TableName("payment_plan")
public class PaymentPlan extends BaseEntity {

  private Long orderId;
  private Integer seqNo;

  /** 应收金额（分）。 */
  private Long amount;

  private LocalDate dueDate;
  private String description;

  /** PENDING / PARTIAL / PAID。 */
  private String status;
}
