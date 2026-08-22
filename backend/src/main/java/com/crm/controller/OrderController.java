package com.crm.controller;

import com.crm.common.ApiResponse;
import com.crm.common.PageResult;
import com.crm.dto.order.OrderRequest;
import com.crm.dto.order.OrderResponse;
import com.crm.dto.order.PaymentRequest;
import com.crm.service.PaymentService;
import com.crm.service.SalesOrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.Map;
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

/** 订单与回款接口（009，FR-OP01~OP08）。 */
@RestController
@RequestMapping("/api/v1/orders")
@Tag(name = "订单")
@PreAuthorize("hasAnyRole('ADMIN','SALES')")
public class OrderController {

  private final SalesOrderService orderService;
  private final PaymentService paymentService;

  public OrderController(SalesOrderService orderService, PaymentService paymentService) {
    this.orderService = orderService;
    this.paymentService = paymentService;
  }

  @GetMapping
  @Operation(summary = "分页查询订单列表（关键字/状态/客户筛选）")
  public ApiResponse<PageResult<OrderResponse>> page(
      @RequestParam(required = false) String keyword,
      @RequestParam(required = false) String status,
      @RequestParam(required = false) Long customerId,
      @RequestParam(defaultValue = "1") long page,
      @RequestParam(defaultValue = "20") long pageSize) {
    return ApiResponse.ok(orderService.page(keyword, status, customerId, page, pageSize));
  }

  @GetMapping("/{id}")
  @Operation(summary = "订单详情（含回款计划台账与回款记录）")
  public ApiResponse<OrderResponse> detail(@PathVariable Long id) {
    return ApiResponse.ok(orderService.detail(id));
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  @Operation(summary = "创建订单（可直接创建或基于已生效合同创建）")
  public ApiResponse<OrderResponse> create(@Valid @RequestBody OrderRequest request) {
    return ApiResponse.ok(orderService.create(request));
  }

  @PutMapping("/{id}")
  @Operation(summary = "编辑订单（重建期次，已回款期次保留）")
  public ApiResponse<OrderResponse> update(
      @PathVariable Long id, @Valid @RequestBody OrderRequest request) {
    return ApiResponse.ok(orderService.update(id, request));
  }

  @DeleteMapping("/{id}")
  @PreAuthorize("hasRole('ADMIN')")
  @Operation(summary = "逻辑删除订单（仅管理员，存在回款记录时拒绝）")
  public ApiResponse<Void> delete(@PathVariable Long id) {
    orderService.delete(id);
    return ApiResponse.ok();
  }

  @PostMapping("/{id}/payments")
  @Operation(summary = "登记回款（驱动期次与订单状态）")
  public ApiResponse<OrderResponse> recordPayment(
      @PathVariable Long id, @Valid @RequestBody PaymentRequest request) {
    paymentService.recordPayment(id, request);
    return ApiResponse.ok(orderService.detail(id));
  }

  @GetMapping("/reminder-summary")
  @Operation(summary = "回款提醒汇总（逾期/临期期次数）")
  public ApiResponse<Map<String, Long>> reminderSummary() {
    return ApiResponse.ok(paymentService.reminderSummary());
  }
}
