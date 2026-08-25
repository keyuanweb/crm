package com.crm.controller;

import com.crm.common.ApiResponse;
import com.crm.common.PageResult;
import com.crm.dto.share.CustomerShareRequest;
import com.crm.dto.share.SharedCustomerResponse;
import com.crm.service.CustomerShareService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RestController;

/** 客户共享接口（012，FR-DP07/DP08）。 */
@RestController
@PreAuthorize("hasAnyRole('ADMIN','SALES','SUPPORT')")
@RequestMapping("/api/v1/customer-shares")
@Tag(name = "客户共享")
public class CustomerShareController {

  private final CustomerShareService shareService;

  public CustomerShareController(CustomerShareService shareService) {
    this.shareService = shareService;
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  @Operation(summary = "共享客户给用户（归属者或管理员）")
  public ApiResponse<SharedCustomerResponse> share(
      @Valid @RequestBody CustomerShareRequest request) {
    return ApiResponse.ok(shareService.share(request));
  }

  @DeleteMapping("/{id}")
  @Operation(summary = "取消共享（归属者或管理员）")
  public ApiResponse<Void> unshare(@PathVariable Long id) {
    shareService.unshare(id);
    return ApiResponse.ok();
  }

  @GetMapping("/shared-to-me")
  @Operation(summary = "共享给我的客户列表（只读）")
  public ApiResponse<PageResult<SharedCustomerResponse>> sharedToMe(
      @RequestParam(defaultValue = "1") long page,
      @RequestParam(defaultValue = "20") long pageSize) {
    return ApiResponse.ok(shareService.sharedToMe(page, pageSize));
  }
}
