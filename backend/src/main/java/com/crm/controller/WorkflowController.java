package com.crm.controller;

import com.crm.common.ApiResponse;
import com.crm.common.PageResult;
import com.crm.dto.workflow.ExecutionLogResponse;
import com.crm.dto.workflow.NotificationResponse;
import com.crm.dto.workflow.WorkflowRuleRequest;
import com.crm.dto.workflow.WorkflowRuleResponse;
import com.crm.security.RequirePermission;
import com.crm.service.WorkflowExecutionLogQueryService;
import com.crm.service.WorkflowNotificationService;
import com.crm.service.WorkflowRuleService;
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
 * 工作流接口（013，FR-W01~W08）。
 *
 * <p><b>1.5 批 3：六处 {@code hasRole('ADMIN')} 换成字典里现成的 {@code workflow:create/update/delete} （写）与新增的
 * {@code workflow:read}（读），零补授——可访问范围与改造前完全一致（仅 ADMIN）。</b>
 * 原因是字典里那三个写码**没有任何角色持有**，接线不改变任何人能做什么，只是把"仅 ADMIN"从角色字面量 换成了可勾选的码。
 *
 * <p><b>为什么读要新增一个码，而不是复用 {@code workflow:manage}</b>：{@code workflow:manage} 被授给了 SALES_MANAGER /
 * MARKETING_MANAGER / FINANCE_MANAGER（V75 给管理者的"系统管理"礼包）。挂它上去等于让三个
 * 非管理员角色读到全部自动化规则与执行日志，而「工作流」菜单（V46 起）只授给了 ADMIN。规则能发通知、写字段、 触发外部通道，它的配置面与执行流水都不是"管理者礼包"该顺手带出来的东西。
 *
 * <p><b>那三个角色持有的 {@code workflow:manage} 因此仍是死授权</b>（勾得上、没有任何端点认）——这是本批
 * 登记的一处不一致，没有顺手改：把规则管理交给营销/财务经理需要一次明确裁决，不是接线该顺带完成的事。
 *
 * <p><b>通知两个端点不动</b>：{@code /notifications} 是"当前用户的通知"，{@code NotificationService.list}
 * 按登录用户过滤、{@code markRead} 校验归属，是真实的自限范围——给自限接口设码是造一个可以在角色页误关的开关。
 */
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
  @RequirePermission("workflow:read")
  @Operation(summary = "规则分页列表")
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
  @RequirePermission("workflow:create")
  @Operation(summary = "创建规则")
  public ApiResponse<WorkflowRuleResponse> createRule(
      @Valid @RequestBody WorkflowRuleRequest request) {
    return ApiResponse.ok(ruleService.create(request));
  }

  @PutMapping("/rules/{id}")
  @RequirePermission("workflow:update")
  @Operation(summary = "编辑规则")
  public ApiResponse<WorkflowRuleResponse> updateRule(
      @PathVariable Long id, @Valid @RequestBody WorkflowRuleRequest request) {
    return ApiResponse.ok(ruleService.update(id, request));
  }

  @PostMapping("/rules/{id}/toggle")
  @RequirePermission("workflow:update")
  @Operation(summary = "启停规则（启停属编辑，与其它模块同形）")
  public ApiResponse<WorkflowRuleResponse> toggleRule(@PathVariable Long id) {
    return ApiResponse.ok(ruleService.toggle(id));
  }

  @DeleteMapping("/rules/{id}")
  @RequirePermission("workflow:delete")
  @Operation(summary = "删除规则")
  public ApiResponse<Void> deleteRule(@PathVariable Long id) {
    ruleService.delete(id);
    return ApiResponse.ok();
  }

  @GetMapping("/logs")
  @RequirePermission("workflow:read")
  @Operation(summary = "执行日志分页")
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
