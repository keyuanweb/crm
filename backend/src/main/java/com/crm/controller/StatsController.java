package com.crm.controller;

import com.crm.common.ApiResponse;
import com.crm.dto.stats.DashboardStats;
import com.crm.dto.stats.KpiBoardResponse;
import com.crm.dto.stats.LeaderboardItem;
import com.crm.dto.stats.PipelineStats;
import com.crm.dto.stats.SalesTargetRequest;
import com.crm.dto.stats.SalesTargetResponse;
import com.crm.security.RequirePermission;
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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 统计接口（contracts/stats.md，FR-011 + 006 模块）。
 *
 * <p><b>1.5：类级 {@code @PreAuthorize("hasAnyRole('ADMIN','SALES')")} 撤除。</b>本类的读全是**无数据范围**的聚合 （管道 /
 * 仪表盘 / 目标 / 排行），而「首页」菜单（{@code stats}）在 V46/V75 里是<b>全部 13 个角色</b>都持有的 ——那道门拦下的 081
 * 角色，拦的正是他们自己首页要用的数据：销售代表打开首页看到的仪表盘、财务看到的达成率， 此前全是 403。
 *
 * <p><b>为什么不设读码</b>：授予范围会恰好等于"全部角色"，一个发给所有人的码等于没有码，只会多一个可以在角色页 误关的开关。排行榜（{@code stats/leaderboard}
 * 菜单只有 10 个角色持有）同理不单独设码：客服三类与分析师虽没有 这张菜单，但他们持有「自定义报表」——同样的公司级汇总数字在那边本就看得到，再造一个只排掉他们的码，只会产出
 * "首页看得见、排行 403"的形状。
 *
 * <p><b>写只有一处</b>：{@code PUT /sales-targets} 的闸门是**内联的自限检查**（全局目标仅 ADMIN，个人目标仅本人），
 * 与类级门无关，保持原样——它不是"按角色名字拦人"，而是一条真实的数据范围判定。
 *
 * <p>{@code kpi-board} 单独设码 {@code kpi:view}：它是数据大屏（{@code data-vision} 菜单），原先方法级的 {@code
 * hasRole('ADMIN')} 让**持有该菜单的 ANALYST 恒 403**，而这类"菜单点得进、接口 403"正是 1.5 要消灭的形态。
 */
@RestController
@RequestMapping("/api/v1/stats")
@Tag(name = "统计")
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
  @RequirePermission("kpi:view")
  @Operation(summary = "KPI 大屏聚合数据（数据大屏）")
  public ApiResponse<KpiBoardResponse> kpiBoard() {
    return ApiResponse.ok(kpiBoardService.getKpiBoard());
  }
}
