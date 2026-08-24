package com.crm.dto.stats;

import lombok.Data;

/** 客户健康度分布（023-kpi-dashboard）。 */
@Data
public class HealthDistribution {

  private int red;
  private int yellow;
  private int green;
}
