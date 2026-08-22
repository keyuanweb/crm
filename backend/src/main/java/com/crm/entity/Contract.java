package com.crm.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/** 合同（008-contract-management）。 */
@Getter
@Setter
@TableName("contract")
public class Contract extends BaseEntity {

  private String contractNo;
  private String title;
  private Long customerId;
  private Long quoteId;

  /** 合同金额（分）。 */
  private Long amount;

  private LocalDate startDate;
  private LocalDate endDate;
  private String content;

  /** DRAFT / PENDING_APPROVAL / APPROVED / EFFECTIVE / COMPLETED / TERMINATED。 */
  private String status;

  private Long approverId;
  private LocalDateTime approvedAt;
  private String rejectReason;
  private LocalDateTime effectiveAt;
  private String terminatedReason;
  private String remark;
  private Long createdBy;
}
