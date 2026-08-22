package com.crm.dto.sla;

import java.util.List;
import lombok.Data;

/** SLA 超时统计（FR-C12）。 */
@Data
public class SlaOverviewResponse {

  private long totalOpen;
  private long overdue;
  private Double overdueRate;
  private List<PrioritySla> byPriority;

  @Data
  public static class PrioritySla {
    private String priority;
    private long totalOpen;
    private long overdue;
    private Double overdueRate;
  }
}
