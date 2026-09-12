package com.crm.controller;

import com.crm.common.ApiResponse;
import com.crm.common.PageResult;
import com.crm.dto.ticket.TicketAssignRequest;
import com.crm.dto.ticket.TicketReplyRequest;
import com.crm.dto.ticket.TicketReplyResponse;
import com.crm.dto.ticket.TicketRequest;
import com.crm.dto.ticket.TicketResponse;
import com.crm.dto.ticket.TicketTransitionRequest;
import com.crm.security.RequirePermission;
import com.crm.service.CustomFieldFilterSupport;
import com.crm.service.TicketService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
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
 * 工单接口（015，FR-C01~C05）。
 *
 * <p><b>1.5：类级 {@code @PreAuthorize("hasAnyRole('ADMIN','SALES','SUPPORT')")} 与各写方法上的 {@code
 * hasAnyRole('ADMIN','SUPPORT')} 已移除。</b>本 Controller 是「粗粒度角色门」最严重的受害
 * 者：它挡住的正是**客服本身**——SUPPORT_MANAGER / SUPPORT_AGENT 在全部工单接口上恒 403，而这两个 角色的菜单里有「工单管理」、权限列表里也已经有
 * ticket:create/update/delete/assign/reply。 也就是说 081 的客服角色模型在本模块是完全不可用的。
 *
 * <p>写操作改挂动作码，读操作挂 {@code ticket:read}（理由见 {@code RoleConstants} 工单组注释：工单
 * 没有数据范围过滤，读留空等于对全体登录用户公开）。{@code POST /{id}/transition} 挂的是 {@code ticket:update}——状态流转就是改工单，字典里的
 * {@code ticket:approve} 在本模块没有对应动作。
 */
@RestController
@RequestMapping("/api/v1/tickets")
@Tag(name = "客户服务")
public class TicketController {

  private final TicketService ticketService;
  private final CustomFieldFilterSupport customFieldFilterSupport;

  public TicketController(
      TicketService ticketService, CustomFieldFilterSupport customFieldFilterSupport) {
    this.ticketService = ticketService;
    this.customFieldFilterSupport = customFieldFilterSupport;
  }

  @GetMapping
  @RequirePermission("ticket:read")
  @Operation(summary = "工单分页列表（关键字/状态/优先级/处理人/客户/自定义字段筛选）")
  public ApiResponse<PageResult<TicketResponse>> page(
      @RequestParam(required = false) String keyword,
      @RequestParam(required = false) String status,
      @RequestParam(required = false) String priority,
      @RequestParam(required = false) Long assigneeId,
      @RequestParam(required = false) Long customerId,
      @RequestParam(defaultValue = "1") long page,
      @RequestParam(defaultValue = "20") long pageSize,
      @RequestParam Map<String, String> params) {
    List<Long> cfMatchedIds =
        customFieldFilterSupport.matchEntityIds(
            "TICKET", customFieldFilterSupport.parseFilters(params));
    return ApiResponse.ok(
        ticketService.page(
            keyword, status, priority, assigneeId, customerId, cfMatchedIds, page, pageSize));
  }

  @GetMapping("/{id}")
  @RequirePermission("ticket:read")
  @Operation(summary = "工单详情（含 SLA 状态刷新）")
  public ApiResponse<TicketResponse> detail(@PathVariable Long id) {
    return ApiResponse.ok(ticketService.detail(id));
  }

  @GetMapping("/{id}/replies")
  @RequirePermission("ticket:read")
  @Operation(summary = "工单回复时间线（分页）")
  public ApiResponse<PageResult<TicketReplyResponse>> replies(
      @PathVariable Long id,
      @RequestParam(defaultValue = "1") long page,
      @RequestParam(defaultValue = "20") long pageSize) {
    return ApiResponse.ok(ticketService.replies(id, page, pageSize));
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  @RequirePermission("ticket:create")
  @Operation(summary = "创建工单（ADMIN/SUPPORT，自动计算 SLA）")
  public ApiResponse<TicketResponse> create(@Valid @RequestBody TicketRequest request) {
    return ApiResponse.ok(ticketService.create(request));
  }

  @PutMapping("/{id}")
  @RequirePermission("ticket:update")
  @Operation(summary = "编辑工单（ADMIN/SUPPORT）")
  public ApiResponse<TicketResponse> update(
      @PathVariable Long id, @Valid @RequestBody TicketRequest request) {
    return ApiResponse.ok(ticketService.update(id, request));
  }

  @PostMapping("/{id}/assign")
  @RequirePermission("ticket:assign")
  @Operation(summary = "分配处理人（ADMIN/SUPPORT）")
  public ApiResponse<TicketResponse> assign(
      @PathVariable Long id, @Valid @RequestBody TicketAssignRequest request) {
    return ApiResponse.ok(ticketService.assign(id, request.getAssigneeId()));
  }

  @PostMapping("/{id}/reply")
  @RequirePermission("ticket:reply")
  @Operation(summary = "追加回复（ADMIN/SUPPORT）")
  public ApiResponse<TicketReplyResponse> reply(
      @PathVariable Long id, @Valid @RequestBody TicketReplyRequest request) {
    return ApiResponse.ok(ticketService.reply(id, request));
  }

  @PostMapping("/{id}/transition")
  @RequirePermission("ticket:update")
  @Operation(summary = "状态流转（OPEN→IN_PROGRESS→RESOLVED→CLOSED）")
  public ApiResponse<TicketResponse> transition(
      @PathVariable Long id, @Valid @RequestBody TicketTransitionRequest request) {
    return ApiResponse.ok(ticketService.transition(id, request.getTargetStatus()));
  }

  @DeleteMapping("/{id}")
  @RequirePermission("ticket:delete")
  @Operation(summary = "删除工单（逻辑删除）")
  public ApiResponse<Void> delete(@PathVariable Long id) {
    ticketService.delete(id);
    return ApiResponse.ok();
  }
}
