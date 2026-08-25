package com.crm.dto.survey;

import lombok.Data;

/** 满意度统计（CSAT/NPS）。 */
@Data
public class SurveyStatsResponse {

  private long sampleCount;
  private double csatAverage;
  private int npsScore;

  /** NPS 分档（推荐者 5 / 中立者 4 / 贬损者 1-3）。 */
  private Bucket promoter;
  private Bucket passive;
  private Bucket detractor;

  @Data
  public static class Bucket {
    private long count;
    private double percent;
  }
}
