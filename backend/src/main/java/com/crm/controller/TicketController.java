package com.crm.controller;

import com.crm.common.ApiResponse;
import com.crm.common.PageResult;
import com.crm.dto.ticket.TicketAssignRequest;
import com.crm.dto.ticket.TicketReplyRequest;
import com.crm.dto.ticket.TicketReplyResponse;
import com.crm.dto.ticket.TicketRequest;
import com.crm.dto.ticket.TicketResponse;
import com.crm.dto.ticket.TicketTransitionRequest;
import com.crm.service.TicketService;
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

/** 工单接口（015，FR-C01~C05）。 */
@RestController
@RequestMapping("/api/v1/tickets")
@Tag(name = "客户服务")
@PreAuthorize("hasAnyRole('ADMIN','SALES','SUPPORT')")
public class TicketController {

  private final TicketService ticketService;

  public TicketController(TicketService ticketService) {
    this.ticketService = ticketService;
  }

  @GetMapping
  @Operation(summary = "工单分页列表（关键字/状态/优先级/处理人/客户筛选）")
  public ApiResponse<PageResult<TicketResponse>> page(
      @RequestParam(required = false) String keyword,
      @RequestParam(required = false) String status,
      @RequestParam(required = false) String priority,
      @RequestParam(required = false) Long assigneeId,
      @RequestParam(required = false) Long customerId,
      @RequestParam(defaultValue = "1") long page,
      @RequestParam(defaultValue = "20") long pageSize) {
    return ApiResponse.ok(
        ticketService.page(keyword, status, priority, assigneeId, customerId, page, pageSize));
  }

  @GetMapping("/{id}")
  @Operation(summary = "工单详情（含 SLA 状态刷新）")
  public ApiResponse<TicketResponse> detail(@PathVariable Long id) {
    return ApiResponse.ok(ticketService.detail(id));
  }

  @GetMapping("/{id}/replies")
  @Operation(summary = "工单回复时间线（分页）")
  public ApiResponse<PageResult<TicketReplyResponse>> replies(
      @PathVariable Long id,
      @RequestParam(defaultValue = "1") long page,
      @RequestParam(defaultValue = "20") long pageSize) {
    return ApiResponse.ok(ticketService.replies(id, page, pageSize));
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  @PreAuthorize("hasAnyRole('ADMIN','SUPPORT')")
  @Operation(summary = "创建工单（ADMIN/SUPPORT，自动计算 SLA）")
  public ApiResponse<TicketResponse> create(@Valid @RequestBody TicketRequest request) {
    return ApiResponse.ok(ticketService.create(request));
  }

  @PutMapping("/{id}")
  @PreAuthorize("hasAnyRole('ADMIN','SUPPORT')")
  @Operation(summary = "编辑工单（ADMIN/SUPPORT）")
  public ApiResponse<TicketResponse> update(
      @PathVariable Long id, @Valid @RequestBody TicketRequest request) {
    return ApiResponse.ok(ticketService.update(id, request));
  }

  @PostMapping("/{id}/assign")
  @PreAuthorize("hasAnyRole('ADMIN','SUPPORT')")
  @Operation(summary = "分配处理人（ADMIN/SUPPORT）")
  public ApiResponse<TicketResponse> assign(
      @PathVariable Long id, @Valid @RequestBody TicketAssignRequest request) {
    return ApiResponse.ok(ticketService.assign(id, request.getAssigneeId()));
  }

  @PostMapping("/{id}/reply")
  @PreAuthorize("hasAnyRole('ADMIN','SUPPORT')")
  @Operation(summary = "追加回复（ADMIN/SUPPORT）")
  public ApiResponse<TicketReplyResponse> reply(
      @PathVariable Long id, @Valid @RequestBody TicketReplyRequest request) {
    return ApiResponse.ok(ticketService.reply(id, request));
  }

  @PostMapping("/{id}/transition")
  @PreAuthorize("hasAnyRole('ADMIN','SUPPORT')")
  @Operation(summary = "状态流转（OPEN→IN_PROGRESS→RESOLVED→CLOSED）")
  public ApiResponse<TicketResponse> transition(
      @PathVariable Long id, @Valid @RequestBody TicketTransitionRequest request) {
    return ApiResponse.ok(ticketService.transition(id, request.getTargetStatus()));
  }

  @DeleteMapping("/{id}")
  @PreAuthorize("hasAnyRole('ADMIN','SUPPORT')")
  @Operation(summary = "删除工单（逻辑删除）")
  public ApiResponse<Void> delete(@PathVariable Long id) {
    ticketService.delete(id);
    return ApiResponse.ok();
  }
}
