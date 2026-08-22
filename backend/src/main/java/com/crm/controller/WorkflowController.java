package com.crm.controller;

import com.crm.common.ApiResponse;
import com.crm.common.PageResult;
import com.crm.dto.workflow.ExecutionLogResponse;
import com.crm.dto.workflow.NotificationResponse;
import com.crm.dto.workflow.WorkflowRuleRequest;
import com.crm.dto.workflow.WorkflowRuleResponse;
import com.crm.service.WorkflowExecutionLogQueryService;
import com.crm.service.WorkflowNotificationService;
import com.crm.service.WorkflowRuleService;
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

/** 工作流接口（013，FR-W01~W08）。 */
@RestController
@RequestMapping("/api/v1/workflows")
@Tag(name = "工作流")
public class WorkflowController {

  private final WorkflowRuleService ruleService;
  private final WorkflowExecutionLogQueryService logQueryService;
  private final WorkflowNotificationService notificationService;

  public WorkflowController(
      WorkflowRuleService ruleService,
      WorkflowExecutionLogQueryService logQueryService,
      WorkflowNotificationService notificationService) {
    this.ruleService = ruleService;
    this.logQueryService = logQueryService;
    this.notificationService = notificationService;
  }

  @GetMapping("/rules")
  @PreAuthorize("hasRole('ADMIN')")
  @Operation(summary = "规则分页列表（仅管理员）")
  public ApiResponse<PageResult<WorkflowRuleResponse>> rules(
      @RequestParam(required = false) String keyword,
      @RequestParam(required = false) String eventType,
      @RequestParam(required = false) Boolean enabled,
      @RequestParam(defaultValue = "1") long page,
      @RequestParam(defaultValue = "20") long pageSize) {
    return ApiResponse.ok(ruleService.page(keyword, eventType, enabled, page, pageSize));
  }

  @PostMapping("/rules")
  @ResponseStatus(HttpStatus.CREATED)
  @PreAuthorize("hasRole('ADMIN')")
  @Operation(summary = "创建规则（仅管理员）")
  public ApiResponse<WorkflowRuleResponse> createRule(
      @Valid @RequestBody WorkflowRuleRequest request) {
    return ApiResponse.ok(ruleService.create(request));
  }

  @PutMapping("/rules/{id}")
  @PreAuthorize("hasRole('ADMIN')")
  @Operation(summary = "编辑规则（仅管理员）")
  public ApiResponse<WorkflowRuleResponse> updateRule(
      @PathVariable Long id, @Valid @RequestBody WorkflowRuleRequest request) {
    return ApiResponse.ok(ruleService.update(id, request));
  }

  @PostMapping("/rules/{id}/toggle")
  @PreAuthorize("hasRole('ADMIN')")
  @Operation(summary = "启停规则（仅管理员）")
  public ApiResponse<WorkflowRuleResponse> toggleRule(@PathVariable Long id) {
    return ApiResponse.ok(ruleService.toggle(id));
  }

  @DeleteMapping("/rules/{id}")
  @PreAuthorize("hasRole('ADMIN')")
  @Operation(summary = "删除规则（仅管理员）")
  public ApiResponse<Void> deleteRule(@PathVariable Long id) {
    ruleService.delete(id);
    return ApiResponse.ok();
  }

  @GetMapping("/logs")
  @PreAuthorize("hasRole('ADMIN')")
  @Operation(summary = "执行日志分页（仅管理员）")
  public ApiResponse<PageResult<ExecutionLogResponse>> logs(
      @RequestParam(required = false) Long ruleId,
      @RequestParam(required = false) String eventType,
      @RequestParam(required = false) Boolean success,
      @RequestParam(defaultValue = "1") long page,
      @RequestParam(defaultValue = "20") long pageSize) {
    return ApiResponse.ok(logQueryService.page(ruleId, eventType, success, page, pageSize));
  }

  @GetMapping("/notifications")
  @Operation(summary = "当前用户通知列表")
  public ApiResponse<PageResult<NotificationResponse>> notifications(
      @RequestParam(defaultValue = "1") long page,
      @RequestParam(defaultValue = "20") long pageSize) {
    return ApiResponse.ok(notificationService.list(page, pageSize));
  }

  @PostMapping("/notifications/{id}/read")
  @Operation(summary = "标记通知已读")
  public ApiResponse<Void> markRead(@PathVariable Long id) {
    notificationService.markRead(id);
    return ApiResponse.ok();
  }
}
