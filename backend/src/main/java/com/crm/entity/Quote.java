package com.crm.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/** 报价单（007-product-cpq）。 */
@Getter
@Setter
@TableName("quote")
public class Quote extends BaseEntity {

  private String quoteNo;
  private Long customerId;
  private Long opportunityId;
  private LocalDate validUntil;

  /** DRAFT / PENDING_APPROVAL / APPROVED / REJECTED。 */
  private String status;

  /** 总额（分）。 */
  private Long totalAmount;

  private String remark;
  private Long approverId;
  private LocalDateTime approvedAt;
  private String rejectReason;
  private Long createdBy;
}
