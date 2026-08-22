package com.crm.controller;

import com.crm.common.ApiResponse;
import com.crm.common.PageResult;
import com.crm.dto.opportunity.CloseRequest;
import com.crm.dto.opportunity.SalesOpportunityRequest;
import com.crm.dto.opportunity.SalesOpportunityResponse;
import com.crm.service.SalesOpportunityService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** 销售机会接口（contracts/sales-opportunities.md，FR-012~014）。 */
@RestController
@RequestMapping("/api/v1/sales-opportunities")
@Tag(name = "销售机会")
@PreAuthorize("hasAnyRole('ADMIN','SALES')")
public class SalesOpportunityController {

  private final SalesOpportunityService salesOpportunityService;

  public SalesOpportunityController(SalesOpportunityService salesOpportunityService) {
    this.salesOpportunityService = salesOpportunityService;
  }

  @GetMapping
  @Operation(summary = "销售机会列表（按阶段筛选/分页）")
  public ApiResponse<PageResult<SalesOpportunityResponse>> page(
      @RequestParam(required = false) String stage,
      @RequestParam(required = false) Long opportunityId,
      @RequestParam(required = false) Long customerId,
      @RequestParam(defaultValue = "1") long page,
      @RequestParam(defaultValue = "20") long pageSize) {
    return ApiResponse.ok(
        salesOpportunityService.page(stage, opportunityId, customerId, page, pageSize));
  }

  @GetMapping("/{id}")
  @Operation(summary = "销售机会详情")
  public ApiResponse<SalesOpportunityResponse> detail(@PathVariable Long id) {
    return ApiResponse.ok(salesOpportunityService.detail(id));
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  @Operation(summary = "创建销售机会")
  public ApiResponse<SalesOpportunityResponse> create(
      @Valid @RequestBody SalesOpportunityRequest request) {
    return ApiResponse.ok(salesOpportunityService.create(request));
  }

  @PutMapping("/{id}")
  @Operation(summary = "编辑销售机会（阶段流转）")
  public ApiResponse<SalesOpportunityResponse> update(
      @PathVariable Long id, @Valid @RequestBody SalesOpportunityRequest request) {
    return ApiResponse.ok(salesOpportunityService.update(id, request));
  }

  @PostMapping("/{id}/close")
  @Operation(summary = "关闭销售机会（赢单/输单）")
  public ApiResponse<SalesOpportunityResponse> close(
      @PathVariable Long id, @Valid @RequestBody CloseRequest request) {
    return ApiResponse.ok(salesOpportunityService.close(id, request));
  }
}
