package com.crm.dto.marketing;

import lombok.Data;

/** 渠道 ROI 统计响应（FR-M06/M07）。 */
@Data
public class ChannelRoiResponse {

  private String channel;
  private long campaignCount;
  private long totalCost;

  /** 归因线索数。 */
  private long leadCount;

  /** 归因客户数。 */
  private long customerCount;

  /** 线索转化率 = 客户数/线索数。 */
  private Double conversionRate;

  /** 预估收益（归因客户商机 max 金额合计）。 */
  private long estimatedRevenue;

  /** ROI = 收益/成本（成本 0 时为 null）。 */
  private Double roi;
}
