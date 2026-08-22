package com.crm.dto.order;

import java.time.LocalDateTime;
import java.util.List;
import lombok.Data;

/** 订单响应（含回款计划台账与回款记录，contracts/orders.md）。 */
@Data
public class OrderResponse {

  private Long id;
  private String orderNo;
  private String title;
  private Long customerId;
  private String customerName;
  private Long contractId;
  private Long amount;
  private String status;
  private Long paidAmount;
  private String description;
  private List<PlanItemResponse> plans;
  private List<PaymentRecordResponse> payments;
  private Integer version;
  private LocalDateTime createdAt;
}
