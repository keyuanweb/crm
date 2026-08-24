package com.crm.dto.stats;

import lombok.Data;

/** 团队排行项（020-sales-targets，FR-004）。 */
@Data
public class LeaderboardItem {

  private Long userId;
  private String displayName;

  /** 个人目标金额（未设置 null）。 */
  private Long targetAmount;

  /** 当月赢单金额（分）。 */
  private Long wonAmount;

  /** 达成率 0-1（未设目标或目标为 0 时 null）。 */
  private Double achievementRate;
}
