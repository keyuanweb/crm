package com.crm.dto.customer;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 健康度评分：分数 + 颜色等级 + 失分原因（018-customer-360，FR-002/003/004）。 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class HealthScoreDTO {

  /** 0-100。 */
  private int score;

  /** RED / YELLOW / GREEN。 */
  private String level;

  /** 失分原因列表。 */
  private List<ScoreDeduction> deductions;

  @Data
  @NoArgsConstructor
  @AllArgsConstructor
  public static class ScoreDeduction {
    private String dimension;
    private int deduct;
  }
}
