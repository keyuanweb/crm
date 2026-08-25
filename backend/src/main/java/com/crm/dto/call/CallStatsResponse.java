package com.crm.dto.call;

import java.util.List;
import lombok.Data;

/** 通话统计响应。 */
@Data
public class CallStatsResponse {

  private long totalCount;
  private long totalDurationSeconds;
  private long avgDurationSeconds;
  private List<DirectionCount> byDirection;

  @Data
  public static class DirectionCount {
    private String direction;
    private long count;
  }
}
