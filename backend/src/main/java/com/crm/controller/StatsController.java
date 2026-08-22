package com.crm.controller;

import com.crm.common.ApiResponse;
import com.crm.dto.stats.PipelineStats;
import com.crm.service.OpportunityStatsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 统计接口（contracts/stats.md，FR-011）。 */
@RestController
@RequestMapping("/api/v1/stats")
@Tag(name = "统计")
@PreAuthorize("hasAnyRole('ADMIN','SALES')")
public class StatsController {

  private final OpportunityStatsService statsService;

  public StatsController(OpportunityStatsService statsService) {
    this.statsService = statsService;
  }

  @GetMapping("/opportunity-pipeline")
  @Operation(summary = "商机管道统计（按阶段汇总数量与金额）")
  public ApiResponse<PipelineStats> pipeline() {
    return ApiResponse.ok(statsService.getPipeline());
  }
}
