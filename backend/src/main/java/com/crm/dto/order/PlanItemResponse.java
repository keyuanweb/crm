package com.crm.dto.order;

import java.time.LocalDate;
import lombok.Data;

/** 回款计划期次响应（含台账字段与提醒标识）。 */
@Data
public class PlanItemResponse {

  private Long id;
  private Integer seqNo;

  /** 应收金额（分）。 */
  private Long amount;

  private LocalDate dueDate;
  private String description;

  /** PENDING / PARTIAL / PAID。 */
  private String status;

  /** 已收金额（分）。 */
  private Long receivedAmount;

  /** 未收金额（分）。 */
  private Long unpaidAmount;

  /** PAID / NORMAL / DUE_SOON / OVERDUE。 */
  private String reminderStatus;

  /** 逾期天数（仅逾期时）。 */
  private Long overdueDays;
}
