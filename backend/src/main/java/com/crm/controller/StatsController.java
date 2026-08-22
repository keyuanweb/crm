package com.crm.controller;

import com.crm.common.ApiResponse;
import com.crm.dto.stats.DashboardStats;
import com.crm.dto.stats.PipelineStats;
import com.crm.dto.stats.SalesTargetRequest;
import com.crm.dto.stats.SalesTargetResponse;
import com.crm.service.DashboardStatsService;
import com.crm.service.OpportunityStatsService;
import com.crm.service.SalesTargetService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
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

  public StatsController(
      OpportunityStatsService statsService,
      DashboardStatsService dashboardStatsService,
      SalesTargetService salesTargetService) {
    this.statsService = statsService;
    this.dashboardStatsService = dashboardStatsService;
    this.salesTargetService = salesTargetService;
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
  @Operation(summary = "查询某月销售目标")
  public ApiResponse<SalesTargetResponse> getSalesTarget(@RequestParam String month) {
    return ApiResponse.ok(salesTargetService.get(month));
  }

  @PutMapping("/sales-targets")
  @PreAuthorize("hasRole('ADMIN')")
  @Operation(summary = "设置/更新某月销售目标（仅管理员）")
  public ApiResponse<SalesTargetResponse> setSalesTarget(
      @Valid @RequestBody SalesTargetRequest request) {
    return ApiResponse.ok(salesTargetService.set(request));
  }
}
