package com.crm.dto.stats;

import com.crm.dto.suggestion.SuggestionSummary;
import java.util.List;
import lombok.Data;

/** KPI 大屏聚合响应（023-kpi-dashboard，contracts/kpi-board.md）。 */
@Data
public class KpiBoardResponse {

  private DashboardStats.Summary kpi;
  private DashboardStats.Funnel funnel;
  private List<LeaderboardItem> leaderboard;
  private HealthDistribution healthDistribution;
  private SuggestionSummary suggestions;
  private List<TrendPoint> trend;
}
