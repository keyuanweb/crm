package com.crm.controller;

import com.crm.common.ApiResponse;
import com.crm.common.PageResult;
import com.crm.dto.contract.ContractResponse;
import com.crm.service.ContractRenewalService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 合同续约接口（046，FR-R01~R06）。 */
@RestController
@RequestMapping("/api/v1/contracts")
@Tag(name = "合同")
@PreAuthorize("hasAnyRole('ADMIN','SALES')")
public class ContractRenewalController {

  private final ContractRenewalService renewalService;

  public ContractRenewalController(ContractRenewalService renewalService) {
    this.renewalService = renewalService;
  }

  @GetMapping("/renewal-overview")
  @Operation(summary = "续约漏斗视图（即将到期/已到期未续/已续约）")
  public ApiResponse<PageResult<ContractResponse>> overview(
      @RequestParam String group,
      @RequestParam(required = false) String keyword,
      @RequestParam(defaultValue = "1") long page,
      @RequestParam(defaultValue = "20") long pageSize) {
    return ApiResponse.ok(renewalService.overview(group, keyword, page, pageSize));
  }
}
