package com.crm.dto.invoice;

import java.time.LocalDateTime;
import lombok.Data;

/** 发票响应（038）。 */
@Data
public class InvoiceResponse {

  private Long id;
  private Long orderId;
  private String orderNo;
  private String customerName;
  private String invoiceNo;
  private String title;
  private String taxNo;
  private Long amount;
  private String invoiceType;
  private String status;
  private String voidReason;
  private LocalDateTime issuedAt;
  private LocalDateTime voidedAt;
}
