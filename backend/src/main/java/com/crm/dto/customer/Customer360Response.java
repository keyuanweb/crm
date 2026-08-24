package com.crm.dto.customer;

import java.time.LocalDate;
import java.util.List;
import lombok.Data;

/** 客户 360 聚合响应：订单/回款/合同/工单 + 金额汇总（018-customer-360，FR-001）。 */
@Data
public class Customer360Response {

  /** 客户基本信息。 */
  private Long id;

  private String name;
  private String company;

  /** 订单列表。 */
  private List<OrderBrief> orders;

  /** 回款摘要（按订单）。 */
  private List<PaymentSummary> paymentSummaries;

  /** 合同列表。 */
  private List<ContractBrief> contracts;

  /** 工单列表。 */
  private List<TicketBrief> tickets;

  /** 金额汇总。 */
  private AmountSummary amountSummary;

  /** 健康度评分。 */
  private HealthScoreDTO health;

  @Data
  public static class OrderBrief {
    private Long id;
    private String orderNo;
    private String title;
    private Long amount;
    private String status;
  }

  @Data
  public static class PaymentSummary {
    private Long orderId;
    private String orderNo;
    private Long totalPlan;
    private Long paid;
    private Long overdue;
  }

  @Data
  public static class ContractBrief {
    private Long id;
    private String contractNo;
    private String title;
    private Long amount;
    private String status;
    private LocalDate startDate;
    private LocalDate endDate;
  }

  @Data
  public static class TicketBrief {
    private Long id;
    private String title;
    private String priority;
    private String status;
    private String slaStatus;
  }

  @Data
  public static class AmountSummary {
    /** 订单金额合计（分）。 */
    private Long totalOrder;

    /** 已回款合计（分）。 */
    private Long paid;

    /** 逾期未回款合计（分）。 */
    private Long dueOverdue;
  }
}
