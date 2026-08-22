package com.crm.controller;

import com.crm.common.ApiResponse;
import com.crm.common.PageResult;
import com.crm.dto.sla.SlaOverviewResponse;
import com.crm.dto.sla.SlaPolicyRequest;
import com.crm.dto.sla.SlaPolicyResponse;
import com.crm.service.SlaPolicyService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
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

/** SLA 策略接口（015，FR-C09/C12，仅 ADMIN）。 */
@RestController
@RequestMapping("/api/v1/sla-policies")
@Tag(name = "客户服务")
@PreAuthorize("hasRole('ADMIN')")
public class SlaPolicyController {

  private final SlaPolicyService slaPolicyService;

  public SlaPolicyController(SlaPolicyService slaPolicyService) {
    this.slaPolicyService = slaPolicyService;
  }

  @GetMapping
  @Operation(summary = "SLA 策略列表")
  public ApiResponse<PageResult<SlaPolicyResponse>> page(
      @RequestParam(defaultValue = "1") long page,
      @RequestParam(defaultValue = "20") long pageSize) {
    return ApiResponse.ok(slaPolicyService.page(page, pageSize));
  }

  @GetMapping("/overview")
  @Operation(summary = "SLA 超时统计（未关闭工单）")
  public ApiResponse<SlaOverviewResponse> overview() {
    return ApiResponse.ok(slaPolicyService.overview());
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  @Operation(summary = "创建 SLA 策略（每优先级唯一）")
  public ApiResponse<SlaPolicyResponse> create(@Valid @RequestBody SlaPolicyRequest request) {
    return ApiResponse.ok(slaPolicyService.create(request));
  }

  @PutMapping("/{id}")
  @Operation(summary = "编辑 SLA 策略")
  public ApiResponse<SlaPolicyResponse> update(
      @PathVariable Long id, @Valid @RequestBody SlaPolicyRequest request) {
    return ApiResponse.ok(slaPolicyService.update(id, request));
  }

  @DeleteMapping("/{id}")
  @Operation(summary = "删除 SLA 策略")
  public ApiResponse<Void> delete(@PathVariable Long id) {
    slaPolicyService.delete(id);
    return ApiResponse.ok();
  }
}
