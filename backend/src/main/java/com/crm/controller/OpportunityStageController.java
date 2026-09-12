package com.crm.controller;

import com.crm.common.ApiResponse;
import com.crm.dto.opportunity.OpportunityStageRequest;
import com.crm.dto.opportunity.OpportunityStageResponse;
import com.crm.security.RequirePermission;
import com.crm.service.OpportunityStageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
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
 * 商机阶段字典接口（1.2-stage-configurable）。
 *
 * <p><b>为什么读接口不加权限注解</b>：阶段名是看板列头、商机表单下拉、漏斗图例的共同数据源，销售每天都在用。 给它加权限只会让「看得见商机、看不见商机在哪一列」——写成全员可读、写操作收
 * {@code stage:manage}。
 */
@RestController
@RequestMapping("/api/v1/opportunity-stages")
@Tag(name = "商机阶段")
public class OpportunityStageController {

  private final OpportunityStageService stageService;

  public OpportunityStageController(OpportunityStageService stageService) {
    this.stageService = stageService;
  }

  @GetMapping
  @Operation(summary = "商机阶段列表（含停用，按 sort_order 升序）")
  public ApiResponse<List<OpportunityStageResponse>> list() {
    return ApiResponse.ok(stageService.list());
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  @RequirePermission("stage:manage")
  @Operation(summary = "新建商机阶段（仅进行中阶段）")
  public ApiResponse<OpportunityStageResponse> create(
      @Valid @RequestBody OpportunityStageRequest request) {
    return ApiResponse.ok(
        stageService.create(
            request.getCode(),
            request.getName(),
            request.getSortOrder(),
            request.getProbability(),
            request.getStageType()));
  }

  @PutMapping("/{id}")
  @RequirePermission("stage:manage")
  @Operation(summary = "编辑商机阶段（编码不可改）")
  public ApiResponse<OpportunityStageResponse> update(
      @PathVariable Long id, @Valid @RequestBody OpportunityStageRequest request) {
    return ApiResponse.ok(
        stageService.update(
            id, request.getName(), request.getSortOrder(), request.getProbability()));
  }

  @PostMapping("/{id}/enabled")
  @RequirePermission("stage:manage")
  @Operation(summary = "启用/停用商机阶段（停用只影响能否再选到，历史商机照常显示）")
  public ApiResponse<Void> setEnabled(@PathVariable Long id, @RequestParam boolean enabled) {
    stageService.setEnabled(id, enabled);
    return ApiResponse.ok();
  }

  @DeleteMapping("/{id}")
  @RequirePermission("stage:manage")
  @Operation(summary = "删除商机阶段（须先停用；内建终态不可删）")
  public ApiResponse<Void> delete(@PathVariable Long id) {
    stageService.delete(id);
    return ApiResponse.ok();
  }
}
