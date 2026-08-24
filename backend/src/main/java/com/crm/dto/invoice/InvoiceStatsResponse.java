package com.crm.dto.invoice;

import java.util.List;
import lombok.Data;

/** 开票统计响应（038）。 */
@Data
public class InvoiceStatsResponse {

  private long totalInvoiceAmount;
  private long totalOrderAmount;
  private double invoiceRate;

  private List<ByOrder> byOrder;

  @Data
  public static class ByOrder {
    private Long orderId;
    private String orderNo;
    private long invoiced;
    private long orderAmount;
    private double rate;
  }
}
