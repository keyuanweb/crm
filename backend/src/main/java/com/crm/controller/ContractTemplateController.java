package com.crm.controller;

import com.crm.common.ApiResponse;
import com.crm.common.PageResult;
import com.crm.dto.contract.ContractTemplateRequest;
import com.crm.dto.contract.ContractTemplateResponse;
import com.crm.security.RequirePermission;
import com.crm.service.ContractTemplateService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
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

/**
 * 合同模板接口（008，FR-CT08）。
 *
 * <p><b>1.5 批 3：三条写操作的 {@code hasRole('ADMIN')} 换成新码 {@code contract_template:manage}，
 * 该码不授给任何角色</b>——可访问范围与改造前完全一致（仅 ADMIN），但管理员从此能在角色页上勾选。 与 {@code integration:manage}、{@code
 * audit:view} 的处置同形（批 2 的"无人被授 ⇒ 保持仅 ADMIN， 只是从此勾得出来"）。
 *
 * <p><b>为什么不复用 {@code contract:create/update/delete}</b>：那三个码授给了销售四角色与 FINANCE_*（V46/V75），
 * 复用的后果是"能签合同的人顺便能改合同的法定文本"——模板是配置面，不是合同业务数据。语义上也不同： 一个描述"这份合同"，一个描述"以后所有合同的模板"。
 *
 * <p><b>读接口刻意不设码</b>：{@code GET /contract-templates} 改造前就没有闸门，且它是建合同页面的下拉数据源 （销售必须能选模板）。字典里没有也不该有
 * {contract_template:read}——设了就是一次收窄，会把销售的表单打空。
 */
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
  @RequirePermission("contract_template:manage")
  @Operation(summary = "创建合同模板")
  public ApiResponse<ContractTemplateResponse> create(
      @Valid @RequestBody ContractTemplateRequest request) {
    return ApiResponse.ok(templateService.create(request));
  }

  @PutMapping("/{id}")
  @RequirePermission("contract_template:manage")
  @Operation(summary = "编辑合同模板")
  public ApiResponse<ContractTemplateResponse> update(
      @PathVariable Long id, @Valid @RequestBody ContractTemplateRequest request) {
    return ApiResponse.ok(templateService.update(id, request));
  }

  @DeleteMapping("/{id}")
  @RequirePermission("contract_template:manage")
  @Operation(summary = "停用合同模板")
  public ApiResponse<Void> delete(@PathVariable Long id) {
    templateService.delete(id);
    return ApiResponse.ok();
  }
}
