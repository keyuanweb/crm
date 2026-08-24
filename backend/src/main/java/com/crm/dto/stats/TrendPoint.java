package com.crm.dto.stats;

import lombok.Data;

/** 大屏趋势点（023-kpi-dashboard）。 */
@Data
public class TrendPoint {

  private String date;
  private long count;
  private long amount;
}
