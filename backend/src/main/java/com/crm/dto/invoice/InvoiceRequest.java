package com.crm.dto.invoice;

import lombok.Data;

/** 开票请求（038）。 */
@Data
public class InvoiceRequest {

  private Long orderId;
  private String title;
  private String taxNo;

  /** 金额（分）。 */
  private Long amount;

  private String invoiceType;
}
