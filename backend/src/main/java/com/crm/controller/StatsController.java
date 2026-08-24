package com.crm.controller;

import com.crm.common.ApiResponse;
import com.crm.dto.stats.DashboardStats;
import com.crm.dto.stats.KpiBoardResponse;
import com.crm.dto.stats.LeaderboardItem;
import com.crm.dto.stats.PipelineStats;
import com.crm.dto.stats.SalesTargetRequest;
import com.crm.dto.stats.SalesTargetResponse;
import com.crm.security.SecurityUtil;
import com.crm.service.DashboardStatsService;
import com.crm.service.KpiBoardService;
import com.crm.service.OpportunityStatsService;
import com.crm.service.SalesTargetService;
import com.crm.service.TeamLeaderboardService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 统计接口（contracts/stats.md，FR-011 + 006 模块）。 */
@RestController
@RequestMapping("/api/v1/stats")
@Tag(name = "统计")
@PreAuthorize("hasAnyRole('ADMIN','SALES')")
public class StatsController {

  private final OpportunityStatsService statsService;
  private final DashboardStatsService dashboardStatsService;
  private final SalesTargetService salesTargetService;
  private final TeamLeaderboardService leaderboardService;
  private final KpiBoardService kpiBoardService;

  public StatsController(
      OpportunityStatsService statsService,
      DashboardStatsService dashboardStatsService,
      SalesTargetService salesTargetService,
      TeamLeaderboardService leaderboardService,
      KpiBoardService kpiBoardService) {
    this.statsService = statsService;
    this.dashboardStatsService = dashboardStatsService;
    this.salesTargetService = salesTargetService;
    this.leaderboardService = leaderboardService;
    this.kpiBoardService = kpiBoardService;
  }

  @GetMapping("/opportunity-pipeline")
  @Operation(summary = "商机管道统计（按阶段汇总数量与金额）")
  public ApiResponse<PipelineStats> pipeline() {
    return ApiResponse.ok(statsService.getPipeline());
  }

  @GetMapping("/dashboard")
  @Operation(summary = "销售仪表盘聚合（核心指标/漏斗/预测/达成/客户/跟进/停滞预警）")
  public ApiResponse<DashboardStats> dashboard() {
    return ApiResponse.ok(dashboardStatsService.getDashboard());
  }

  @GetMapping("/sales-targets")
  @Operation(summary = "查询某月销售目标（userId 空=全局目标）")
  public ApiResponse<SalesTargetResponse> getSalesTarget(
      @RequestParam String month, @RequestParam(required = false) Long userId) {
    return ApiResponse.ok(salesTargetService.get(month, userId));
  }

  @PutMapping("/sales-targets")
  @Operation(summary = "设置/更新某月销售目标（全局仅管理员；个人目标本人或管理员）")
  public ApiResponse<SalesTargetResponse> setSalesTarget(
      @Valid @RequestBody SalesTargetRequest request) {
    // 全局目标仅管理员；个人目标本人或管理员可设置
    boolean isAdmin = "ADMIN".equals(SecurityUtil.currentPrincipal().role());
    if (request.getUserId() == null) {
      if (!isAdmin) {
        throw new com.crm.common.BusinessException(
            com.crm.common.ErrorCode.FORBIDDEN, "仅管理员可设置全局目标");
      }
    } else if (!isAdmin && !request.getUserId().equals(SecurityUtil.currentUserId())) {
      throw new com.crm.common.BusinessException(com.crm.common.ErrorCode.FORBIDDEN, "只能设置自己的目标");
    }
    return ApiResponse.ok(salesTargetService.set(request));
  }

  @GetMapping("/leaderboard")
  @Operation(summary = "团队销售排行（按月；按达成率或赢单金额排序）")
  public ApiResponse<Map<String, Object>> leaderboard(
      @RequestParam(required = false) String month,
      @RequestParam(defaultValue = "rate") String sortBy) {
    String m = month == null || month.isBlank() ? java.time.YearMonth.now().toString() : month;
    List<LeaderboardItem> items = leaderboardService.leaderboard(m, sortBy);
    return ApiResponse.ok(Map.of("items", items, "month", m));
  }

  @GetMapping("/kpi-board")
  @PreAuthorize("hasRole('ADMIN')")
  @Operation(summary = "KPI 大屏聚合数据（仅管理员）")
  public ApiResponse<KpiBoardResponse> kpiBoard() {
    return ApiResponse.ok(kpiBoardService.getKpiBoard());
  }
}
