package com.crm.controller;

import com.crm.common.ApiResponse;
import com.crm.common.PageResult;
import com.crm.dto.customer.CustomerResponse;
import com.crm.dto.pool.BatchTransferRequest;
import com.crm.dto.pool.PoolScanResult;
import com.crm.service.CustomerPoolService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 客户公海接口（011，FR-PL01~PL08）。 */
@RestController
@RequestMapping("/api/v1/customers")
@Tag(name = "客户公海")
public class CustomerPoolController {

  private final CustomerPoolService poolService;

  public CustomerPoolController(CustomerPoolService poolService) {
    this.poolService = poolService;
  }

  @GetMapping("/pool")
  @Operation(summary = "公海客户分页（owner 为空）")
  public ApiResponse<PageResult<CustomerResponse>> pool(
      @RequestParam(required = false) String keyword,
      @RequestParam(required = false) String status,
      @RequestParam(defaultValue = "1") long page,
      @RequestParam(defaultValue = "20") long pageSize) {
    return ApiResponse.ok(poolService.pool(keyword, status, page, pageSize));
  }

  @GetMapping("/my")
  @Operation(summary = "我的客户分页（owner=本人）")
  public ApiResponse<PageResult<CustomerResponse>> mine(
      @RequestParam(required = false) String keyword,
      @RequestParam(required = false) String status,
      @RequestParam(defaultValue = "1") long page,
      @RequestParam(defaultValue = "20") long pageSize) {
    return ApiResponse.ok(poolService.mine(keyword, status, page, pageSize));
  }

  @PostMapping("/pool/{id}/claim")
  @Operation(summary = "从公海领取客户")
  public ApiResponse<CustomerResponse> claim(@PathVariable Long id) {
    return ApiResponse.ok(poolService.claim(id));
  }

  @PostMapping("/pool/scan")
  @PreAuthorize("hasRole('ADMIN')")
  @Operation(summary = "公海扫描：超期未跟进客户退回公海（仅管理员）")
  public ApiResponse<PoolScanResult> scan() {
    return ApiResponse.ok(poolService.scan());
  }

  @PostMapping("/batch-transfer")
  @PreAuthorize("hasRole('ADMIN')")
  @Operation(summary = "批量转移/分配客户（仅管理员，单次 ≤100）")
  public ApiResponse<Long> batchTransfer(@Valid @RequestBody BatchTransferRequest request) {
    return ApiResponse.ok(poolService.batchTransfer(request));
  }
}
