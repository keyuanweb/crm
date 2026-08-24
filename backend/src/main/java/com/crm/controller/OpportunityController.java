package com.crm.controller;

import com.crm.common.ApiResponse;
import com.crm.common.PageResult;
import com.crm.dto.opportunity.OpportunityDetailResponse;
import com.crm.dto.opportunity.OpportunityRequest;
import com.crm.dto.opportunity.OpportunityResponse;
import com.crm.service.CustomFieldFilterSupport;
import com.crm.service.OpportunityService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** 商机接口（contracts/opportunities.md，FR-007~011）。 */
@RestController
@RequestMapping("/api/v1/opportunities")
@Tag(name = "商机")
@PreAuthorize("hasAnyRole('ADMIN','SALES')")
public class OpportunityController {

  private final OpportunityService opportunityService;
  private final CustomFieldFilterSupport customFieldFilterSupport;

  public OpportunityController(
      OpportunityService opportunityService, CustomFieldFilterSupport customFieldFilterSupport) {
    this.opportunityService = opportunityService;
    this.customFieldFilterSupport = customFieldFilterSupport;
  }

  @GetMapping
  @Operation(summary = "分页查询商机列表（支持 cf_<fieldId> 自定义字段筛选）")
  public ApiResponse<PageResult<OpportunityResponse>> page(
      @RequestParam(required = false) String keyword,
      @RequestParam(required = false) Long customerId,
      @RequestParam(required = false) String status,
      @RequestParam(defaultValue = "1") long page,
      @RequestParam(defaultValue = "20") long pageSize,
      @RequestParam Map<String, String> params) {
    List<Long> cfMatchedIds =
        customFieldFilterSupport.matchEntityIds(
            "OPPORTUNITY", customFieldFilterSupport.parseFilters(params));
    return ApiResponse.ok(
        opportunityService.page(keyword, customerId, status, cfMatchedIds, page, pageSize));
  }

  @GetMapping("/{id}")
  @Operation(summary = "商机详情（含下属销售机会）")
  public ApiResponse<OpportunityDetailResponse> detail(@PathVariable Long id) {
    return ApiResponse.ok(opportunityService.detail(id));
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  @Operation(summary = "创建商机")
  public ApiResponse<OpportunityResponse> create(@Valid @RequestBody OpportunityRequest request) {
    return ApiResponse.ok(opportunityService.create(request));
  }

  @PutMapping("/{id}")
  @Operation(summary = "编辑商机")
  public ApiResponse<OpportunityResponse> update(
      @PathVariable Long id, @Valid @RequestBody OpportunityRequest request) {
    return ApiResponse.ok(opportunityService.update(id, request));
  }

  @DeleteMapping("/{id}")
  @Operation(summary = "逻辑删除商机（级联删除销售机会）")
  public ApiResponse<Void> delete(@PathVariable Long id) {
    opportunityService.delete(id);
    return ApiResponse.ok();
  }
}
