package com.crm.dto.sla;

import java.util.List;
import lombok.Data;

/** SLA 超时统计（FR-C12）。 */
@Data
public class SlaOverviewResponse {

  private long totalOpen;
  private long overdue;
  private Double overdueRate;

  /** 响应达成率 = 1 - 响应违约数 / 未关闭工单数；无未关闭工单时为 null（1.3）。 */
  private Double respondComplianceRate;

  /** 解决达成率 = 1 - 解决违约数 / 未关闭工单数；无未关闭工单时为 null（1.3）。 */
  private Double resolveComplianceRate;

  private List<PrioritySla> byPriority;

  /** 按处理人维度（1.3）；未分配处理人的记录 assigneeId 为 null、name 为「未分配」。 */
  private List<AssigneeSla> byAssignee;

  @Data
  public static class PrioritySla {
    private String priority;
    private long totalOpen;
    private long overdue;
    private Double overdueRate;
  }

  /** 按处理人维度的超时统计（1.3）。 */
  @Data
  public static class AssigneeSla {
    private Long assigneeId;
    private String assigneeName;
    private long totalOpen;
    private long overdue;
    private Double overdueRate;
  }
}
