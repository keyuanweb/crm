package com.crm.controller;

import com.crm.common.ApiResponse;
import com.crm.common.PageResult;
import com.crm.dto.contract.ContractTemplateRequest;
import com.crm.dto.contract.ContractTemplateResponse;
import com.crm.service.ContractTemplateService;
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

/** 合同模板接口（008，FR-CT08）。 */
@RestController
@RequestMapping("/api/v1/contract-templates")
@Tag(name = "合同模板")
public class ContractTemplateController {

  private final ContractTemplateService templateService;

  public ContractTemplateController(ContractTemplateService templateService) {
    this.templateService = templateService;
  }

  @GetMapping
  @Operation(summary = "分页查询合同模板（关键字/状态筛选）")
  public ApiResponse<PageResult<ContractTemplateResponse>> page(
      @RequestParam(required = false) String keyword,
      @RequestParam(required = false) String status,
      @RequestParam(defaultValue = "1") long page,
      @RequestParam(defaultValue = "20") long pageSize) {
    return ApiResponse.ok(templateService.page(keyword, status, page, pageSize));
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  @PreAuthorize("hasRole('ADMIN')")
  @Operation(summary = "创建合同模板（仅管理员）")
  public ApiResponse<ContractTemplateResponse> create(
      @Valid @RequestBody ContractTemplateRequest request) {
    return ApiResponse.ok(templateService.create(request));
  }

  @PutMapping("/{id}")
  @PreAuthorize("hasRole('ADMIN')")
  @Operation(summary = "编辑合同模板（仅管理员）")
  public ApiResponse<ContractTemplateResponse> update(
      @PathVariable Long id, @Valid @RequestBody ContractTemplateRequest request) {
    return ApiResponse.ok(templateService.update(id, request));
  }

  @DeleteMapping("/{id}")
  @PreAuthorize("hasRole('ADMIN')")
  @Operation(summary = "停用合同模板（仅管理员）")
  public ApiResponse<Void> delete(@PathVariable Long id) {
    templateService.delete(id);
    return ApiResponse.ok();
  }
}
