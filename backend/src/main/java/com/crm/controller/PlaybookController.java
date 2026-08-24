package com.crm.controller;

import com.crm.common.ApiResponse;
import com.crm.common.PageResult;
import com.crm.dto.playbook.ActionCompleteRequest;
import com.crm.dto.playbook.ActionTemplateRequest;
import com.crm.dto.playbook.ActionTemplateResponse;
import com.crm.dto.playbook.ActionViewResponse;
import com.crm.service.SalesOpportunityActionService;
import com.crm.service.StageActionTemplateService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
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

/** 销售 Playbook 接口（045，FR-P01~P06）。 */
@RestController
@RequestMapping("/api/v1")
@Tag(name = "销售 Playbook")
public class PlaybookController {

  private final StageActionTemplateService templateService;
  private final SalesOpportunityActionService actionService;

  public PlaybookController(
      StageActionTemplateService templateService, SalesOpportunityActionService actionService) {
    this.templateService = templateService;
    this.actionService = actionService;
  }

  // ===== 模板管理（仅 ADMIN） =====

  @GetMapping("/stage-actions")
  @PreAuthorize("hasRole('ADMIN')")
  @Operation(summary = "动作模板分页列表（按阶段筛选）")
  public ApiResponse<PageResult<ActionTemplateResponse>> page(
      @RequestParam(required = false) String stage,
      @RequestParam(defaultValue = "1") long page,
      @RequestParam(defaultValue = "20") long pageSize) {
    return ApiResponse.ok(templateService.page(stage, page, pageSize));
  }

  @PostMapping("/stage-actions")
  @ResponseStatus(HttpStatus.CREATED)
  @PreAuthorize("hasRole('ADMIN')")
  @Operation(summary = "创建动作模板")
  public ApiResponse<ActionTemplateResponse> create(
      @Valid @RequestBody ActionTemplateRequest request) {
    return ApiResponse.ok(templateService.create(request));
  }

  @PutMapping("/stage-actions/{id}")
  @PreAuthorize("hasRole('ADMIN')")
  @Operation(summary = "编辑动作模板")
  public ApiResponse<ActionTemplateResponse> update(
      @PathVariable Long id, @Valid @RequestBody ActionTemplateRequest request) {
    return ApiResponse.ok(templateService.update(id, request));
  }

  @DeleteMapping("/stage-actions/{id}")
  @PreAuthorize("hasRole('ADMIN')")
  @Operation(summary = "删除动作模板（逻辑删除）")
  public ApiResponse<Void> delete(@PathVariable Long id) {
    templateService.delete(id);
    return ApiResponse.ok();
  }

  // ===== 销售机会动作（ADMIN + SALES） =====

  @GetMapping("/sales-opportunities/{id}/actions")
  @PreAuthorize("hasAnyRole('ADMIN','SALES')")
  @Operation(summary = "销售机会当前阶段动作清单（含完成状态）")
  public ApiResponse<List<ActionViewResponse>> listActions(@PathVariable Long id) {
    return ApiResponse.ok(actionService.listForOpportunity(id));
  }

  @PostMapping("/sales-opportunities/{id}/actions")
  @ResponseStatus(HttpStatus.CREATED)
  @PreAuthorize("hasAnyRole('ADMIN','SALES')")
  @Operation(summary = "勾选完成动作")
  public ApiResponse<ActionViewResponse> completeAction(
      @PathVariable Long id, @Valid @RequestBody ActionCompleteRequest request) {
    return ApiResponse.ok(actionService.complete(id, request.getTemplateId()));
  }
}
