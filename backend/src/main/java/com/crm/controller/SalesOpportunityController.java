package com.crm.controller;

import com.crm.common.ApiResponse;
import com.crm.common.PageResult;
import com.crm.dto.opportunity.CloseRequest;
import com.crm.dto.opportunity.SalesOpportunityRequest;
import com.crm.dto.opportunity.SalesOpportunityResponse;
import com.crm.security.RequirePermission;
import com.crm.service.SalesOpportunityService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 销售机会接口（contracts/sales-opportunities.md，FR-012~014）。
 *
 * <p><b>1.5：类级 {@code @PreAuthorize("hasAnyRole('ADMIN','SALES')")} 换成动作码，零补授。</b>销售机会复用 「商机」家族的
 * {@code opportunity:read/create/update}——它们在 V46/V75 里授给的不只是 ADMIN/SALES，还有 SALES_MANAGER /
 * SALES_REP，而 {@code opportunity:read} 在 V80 里又给了 VIEWER，菜单里则只有 ADMIN / SALES / SALES_MANAGER /
 * SALES_REP 持有「销售机会」。
 *
 * <p>不复用会怎样：字典里有 {@code opportunity:export} / {@code stage:manage}，但这两个码描述的是商机的导出与
 * 阶段配置，与销售机会的增删改不是一件事；为它新造 {@code sales_opportunity:*} 一族，等于让"商机"与"销售机会" 这两张同源表（{@code
 * SalesOpportunityService.page} 就是按 {@code opportunityId} 过滤的）在权限上分家—— 管理员要在两处勾同一件事。
 *
 * <p>唯一副作用：VIEWER 因为持有 {@code opportunity:read}，现在也读得到销售机会列表（此前被类级门挡住）。
 * 这与它已获得的商机只读权同族；若认为该收，正确的做法是给销售机会另设读码，而不是把 VIEWER 从商机里摘出去。
 */
@RestController
@RequestMapping("/api/v1/sales-opportunities")
@Tag(name = "销售机会")
public class SalesOpportunityController {

  private final SalesOpportunityService salesOpportunityService;

  public SalesOpportunityController(SalesOpportunityService salesOpportunityService) {
    this.salesOpportunityService = salesOpportunityService;
  }

  @GetMapping
  @RequirePermission("opportunity:read")
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
  @RequirePermission("opportunity:read")
  @Operation(summary = "销售机会详情")
  public ApiResponse<SalesOpportunityResponse> detail(@PathVariable Long id) {
    return ApiResponse.ok(salesOpportunityService.detail(id));
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  @RequirePermission("opportunity:create")
  @Operation(summary = "创建销售机会")
  public ApiResponse<SalesOpportunityResponse> create(
      @Valid @RequestBody SalesOpportunityRequest request) {
    return ApiResponse.ok(salesOpportunityService.create(request));
  }

  @PutMapping("/{id}")
  @RequirePermission("opportunity:update")
  @Operation(summary = "编辑销售机会（阶段流转）")
  public ApiResponse<SalesOpportunityResponse> update(
      @PathVariable Long id, @Valid @RequestBody SalesOpportunityRequest request) {
    return ApiResponse.ok(salesOpportunityService.update(id, request));
  }

  @PostMapping("/{id}/close")
  @RequirePermission("opportunity:update")
  @Operation(summary = "关闭销售机会（赢单/输单）")
  public ApiResponse<SalesOpportunityResponse> close(
      @PathVariable Long id, @Valid @RequestBody CloseRequest request) {
    return ApiResponse.ok(salesOpportunityService.close(id, request));
  }
}
