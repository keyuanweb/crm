package com.crm.dto.order;

import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.Data;

/** 回款记录响应。 */
@Data
public class PaymentRecordResponse {

  private Long id;
  private Long planId;
  private Long amount;
  private LocalDate paidAt;
  private String method;
  private Long recordedBy;
  private LocalDateTime createdAt;
}
