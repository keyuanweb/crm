package com.crm.controller;

import com.crm.common.ApiResponse;
import com.crm.common.PageResult;
import com.crm.dto.contract.ContractRequest;
import com.crm.dto.contract.ContractResponse;
import com.crm.service.ContractService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.Map;
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

/** 合同接口（008，FR-CT01~CT06）。 */
@RestController
@RequestMapping("/api/v1/contracts")
@Tag(name = "合同")
@PreAuthorize("hasAnyRole('ADMIN','SALES')")
public class ContractController {

  private final ContractService contractService;

  public ContractController(ContractService contractService) {
    this.contractService = contractService;
  }

  @GetMapping
  @Operation(summary = "分页查询合同列表（关键字/状态/客户筛选）")
  public ApiResponse<PageResult<ContractResponse>> page(
      @RequestParam(required = false) String keyword,
      @RequestParam(required = false) String status,
      @RequestParam(required = false) Long customerId,
      @RequestParam(defaultValue = "1") long page,
      @RequestParam(defaultValue = "20") long pageSize) {
    return ApiResponse.ok(contractService.page(keyword, status, customerId, page, pageSize));
  }

  @GetMapping("/{id}")
  @Operation(summary = "合同详情（含正文、审批信息、附件列表）")
  public ApiResponse<ContractResponse> detail(@PathVariable Long id) {
    return ApiResponse.ok(contractService.detail(id));
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  @Operation(summary = "创建合同（可直接创建或基于已通过报价单创建）")
  public ApiResponse<ContractResponse> create(@Valid @RequestBody ContractRequest request) {
    return ApiResponse.ok(contractService.create(request));
  }

  @PutMapping("/{id}")
  @Operation(summary = "编辑草稿/被拒合同")
  public ApiResponse<ContractResponse> update(
      @PathVariable Long id, @Valid @RequestBody ContractRequest request) {
    return ApiResponse.ok(contractService.update(id, request));
  }

  @PostMapping("/{id}/submit")
  @Operation(summary = "提交审批")
  public ApiResponse<ContractResponse> submit(@PathVariable Long id) {
    return ApiResponse.ok(contractService.submit(id));
  }

  @PostMapping("/{id}/approve")
  @PreAuthorize("hasRole('ADMIN')")
  @Operation(summary = "审批通过（仅管理员）")
  public ApiResponse<ContractResponse> approve(@PathVariable Long id) {
    return ApiResponse.ok(contractService.approve(id));
  }

  @PostMapping("/{id}/reject")
  @PreAuthorize("hasRole('ADMIN')")
  @Operation(summary = "审批拒绝（仅管理员，需填写意见）")
  public ApiResponse<ContractResponse> reject(
      @PathVariable Long id, @RequestBody(required = false) Map<String, String> body) {
    String reason = body == null ? null : body.get("reason");
    return ApiResponse.ok(contractService.reject(id, reason));
  }

  @PostMapping("/{id}/effective")
  @Operation(summary = "标记合同生效")
  public ApiResponse<ContractResponse> effective(@PathVariable Long id) {
    return ApiResponse.ok(contractService.effective(id));
  }

  @PostMapping("/{id}/complete")
  @Operation(summary = "标记合同完成")
  public ApiResponse<ContractResponse> complete(@PathVariable Long id) {
    return ApiResponse.ok(contractService.complete(id));
  }

  @PostMapping("/{id}/terminate")
  @Operation(summary = "标记合同终止（需填写原因）")
  public ApiResponse<ContractResponse> terminate(
      @PathVariable Long id, @RequestBody(required = false) Map<String, String> body) {
    String reason = body == null ? null : body.get("reason");
    return ApiResponse.ok(contractService.terminate(id, reason));
  }
}
