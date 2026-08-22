package com.crm.dto.stats;

import java.time.LocalDateTime;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 商机管道统计响应（contracts/stats.md）。 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PipelineStats {

  private List<StageStat> stages;
  private GrandTotal grandTotal;
  private LocalDateTime generatedAt;

  @Data
  @NoArgsConstructor
  @AllArgsConstructor
  public static class StageStat {
    private String stage;
    private long count;
    private long amountTotal;
  }

  @Data
  @NoArgsConstructor
  @AllArgsConstructor
  public static class GrandTotal {
    private long count;
    private long amountTotal;
  }
}
