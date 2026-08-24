package com.crm.controller;

import com.crm.common.ApiResponse;
import com.crm.dto.approval.FlowRequest;
import com.crm.dto.approval.FlowResponse;
import com.crm.dto.approval.TaskActionRequest;
import com.crm.entity.ApprovalInstance;
import com.crm.entity.ApprovalTask;
import com.crm.security.RequirePermission;
import com.crm.security.SecurityUtil;
import com.crm.service.ApprovalEngineService;
import com.crm.service.ApprovalFlowService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 审批流接口（033-approval-flow）。 */
@RestController
@RequestMapping("/api/v1")
@Tag(name = "审批流")
public class ApprovalController {

  private final ApprovalFlowService flowService;
  private final ApprovalEngineService engineService;

  public ApprovalController(ApprovalFlowService flowService, ApprovalEngineService engineService) {
    this.flowService = flowService;
    this.engineService = engineService;
  }

  // ---------- 流程定义 ----------

  @GetMapping("/approval-flows")
  @RequirePermission("workflow:manage")
  @Operation(summary = "审批流列表")
  public ApiResponse<List<FlowResponse>> flows(
      @RequestParam(required = false) String businessType) {
    return ApiResponse.ok(flowService.list(businessType));
  }

  @PostMapping("/approval-flows")
  @RequirePermission("workflow:manage")
  @Operation(summary = "创建审批流")
  public ApiResponse<FlowResponse> createFlow(@RequestBody FlowRequest request) {
    return ApiResponse.ok(flowService.create(request));
  }

  @PutMapping("/approval-flows/{id}")
  @RequirePermission("workflow:manage")
  @Operation(summary = "编辑审批流")
  public ApiResponse<FlowResponse> updateFlow(
      @PathVariable Long id, @RequestBody FlowRequest request) {
    return ApiResponse.ok(flowService.update(id, request));
  }

  @DeleteMapping("/approval-flows/{id}")
  @RequirePermission("workflow:manage")
  @Operation(summary = "删除审批流")
  public ApiResponse<Void> deleteFlow(@PathVariable Long id) {
    flowService.delete(id);
    return ApiResponse.ok(null);
  }

  // ---------- 审批执行 ----------

  @GetMapping("/approvals/todos")
  @Operation(summary = "我的待办")
  public ApiResponse<List<ApprovalTask>> todos() {
    return ApiResponse.ok(engineService.todos(SecurityUtil.currentUserId()));
  }

  @GetMapping("/approvals/done")
  @Operation(summary = "我的已办")
  public ApiResponse<List<ApprovalTask>> done() {
    return ApiResponse.ok(engineService.done(SecurityUtil.currentUserId()));
  }

  @GetMapping("/approvals/{id}")
  @Operation(summary = "审批实例详情（任务 + 日志）")
  public ApiResponse<Map<String, Object>> detail(@PathVariable Long id) {
    return ApiResponse.ok(
        Map.of(
            "instance", engineService.detail(id),
            "tasks", engineService.tasksOf(id),
            "logs", engineService.logsOf(id)));
  }

  @PostMapping("/approvals/{id}/tasks/{taskId}/approve")
  @Operation(summary = "审批通过")
  public ApiResponse<Void> approve(
      @PathVariable Long id,
      @PathVariable Long taskId,
      @RequestBody(required = false) TaskActionRequest req) {
    engineService.approve(taskId, req == null ? new TaskActionRequest() : req);
    return ApiResponse.ok(null);
  }

  @PostMapping("/approvals/{id}/tasks/{taskId}/reject")
  @Operation(summary = "审批驳回")
  public ApiResponse<Void> reject(
      @PathVariable Long id,
      @PathVariable Long taskId,
      @RequestBody(required = false) TaskActionRequest req) {
    engineService.reject(taskId, req == null ? new TaskActionRequest() : req);
    return ApiResponse.ok(null);
  }

  @PostMapping("/approvals/{id}/tasks/{taskId}/transfer")
  @Operation(summary = "转交")
  public ApiResponse<Void> transfer(
      @PathVariable Long id, @PathVariable Long taskId, @RequestBody TaskActionRequest req) {
    engineService.transfer(taskId, req);
    return ApiResponse.ok(null);
  }

  @PostMapping("/approvals/{id}/renew")
  @Operation(summary = "重提（驳回后）")
  public ApiResponse<ApprovalInstance> renew(@PathVariable Long id) {
    return ApiResponse.ok(engineService.renew(id));
  }

  @GetMapping("/approvals/business/{businessType}/{businessId}")
  @Operation(summary = "按业务查审批实例")
  public ApiResponse<List<ApprovalInstance>> byBusiness(
      @PathVariable String businessType, @PathVariable Long businessId) {
    return ApiResponse.ok(engineService.instancesOf(businessType, businessId));
  }
}
