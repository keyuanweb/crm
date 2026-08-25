package com.crm.dto.landing;

import java.util.List;
import lombok.Data;

/** 落地页 UTM 归因统计响应。 */
@Data
public class UtmStatsResponse {

  private Long landingPageId;
  private long total;
  private String from;
  private String to;
  private List<DimensionCount> bySource;
  private List<DimensionCount> byCampaign;

  @Data
  public static class DimensionCount {
    private String dimension;
    private long count;
  }
}
