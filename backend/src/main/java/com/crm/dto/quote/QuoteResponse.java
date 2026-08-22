package com.crm.dto.quote;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import lombok.Data;

/** 报价单响应（含行明细，contracts/products-quotes.md）。 */
@Data
public class QuoteResponse {

  private Long id;
  private String quoteNo;
  private Long customerId;
  private String customerName;
  private Long opportunityId;
  private LocalDate validUntil;
  private String status;
  private Long totalAmount;
  private String remark;
  private Long approverId;
  private LocalDateTime approvedAt;
  private String rejectReason;
  private List<QuoteItemResponse> items;
  private Integer version;
  private LocalDateTime createdAt;
}
