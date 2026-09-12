package com.crm.controller;

import com.crm.common.ApiResponse;
import com.crm.common.PageResult;
import com.crm.dto.order.OrderRequest;
import com.crm.dto.order.OrderResponse;
import com.crm.dto.order.PaymentRequest;
import com.crm.security.RequirePermission;
import com.crm.service.PaymentService;
import com.crm.service.SalesOrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
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
 * 订单与回款接口（009，FR-OP01~OP08）。
 *
 * <p><b>1.5：类级 {@code @PreAuthorize("hasAnyRole('ADMIN','SALES')")} 与删除方法上的 {@code
 * hasRole('ADMIN')} 已移除。</b>本模块被这两道粗粒度门按角色名整体关掉了：081 的角色模型里，
 * SALES_MANAGER（销售总监）、SALES_REP（销售代表）、FINANCE_MANAGER（财务总监）、FINANCE_ACCOUNTANT （财务专员）在**每一个订单接口**上恒
 * 403，而 V75 恰好给这四个角色授了 order:create/update/delete， 它们的 role_menu 里也都有 'orders'。其中
 * FINANCE_ACCOUNTANT 最刺眼——登记回款（本 Controller 的 {@code POST
 * /{id}/payments}）与开票就是它菜单上的两件事，而它在订单模块连列表都打不开；也就是说 081 的财务角色模型在订单/回款这一块同样是不可用的。
 *
 * <p>写操作改挂动作码：创建→{@code order:create}、编辑→{@code order:update}、删除→{@code order:delete}、 登记回款→{@code
 * order:payment}。四个码都在 {@code RoleConstants} 字典的「订单管理」组里，无需新增。 <b>本项没有任何端点因接码而失去今天已有的能力</b>：老门只放行
 * ADMIN 与 SALES，而 V46 给 SALES 的正是 order:create/update/payment，ADMIN 在 {@code PermissionAspect}
 * 里直通，删除那一条老门只放行 ADMIN、 ADMIN 也持有 order:delete——所以这四个码对「原先能用的角色」都已持有，V80 无需为订单补授。
 * 删除的语义变化是**放宽**而非收紧：挂码后除 ADMIN 外，SALES_MANAGER / SALES_REP / FINANCE_MANAGER /
 * FINANCE_ACCOUNTANT（持有 order:delete）也能删除；SALES 依旧不能删——V46 刻意没给它 delete，本项沿用。
 *
 * <p><b>读接口（三个）挂 {@code order:read}，理由不是「服务层已过滤」，恰恰是它没有过滤。</b> {@code SalesOrderService.page()}
 * 只按关键字/状态/客户号筛，{@code detail()} 按 id 直取，两者都没有任何 数据范围过滤（既无 {@code applyDataScopeFilter} / {@code
 * resolveVisibleOwnerIds}，也无角色字面量判断）； {@code PaymentService.reminderSummary()} 更是 {@code
 * selectList} 全表期次后聚合——订单的读是全量可见的。 撤掉类级门而不设码，等于让 SUPPORT / MARKETING_* / ANALYST 这些既无 orders
 * 菜单、也无任何 order 码的 角色（前端看不到入口，但 API 可直取）读走全部订单与回款期次。字典的「订单管理」组原本只有 create/update/delete/payment，没有
 * {@code order:read}，故该码已补入 {@code PERMISSION_DEFS}，按 {@code ticket:read} 的先例授予持有 'orders'
 * 菜单的角色（V46 的 ADMIN/SALES，V75 的 SALES_MANAGER/
 * SALES_REP/FINANCE_MANAGER/FINANCE_ACCOUNTANT/VIEWER），三个读接口随之挂码——出参含金额与客户信息， 且这条读路径没有范围限制，这层码省不得。
 */
@RestController
@RequestMapping("/api/v1/orders")
@Tag(name = "订单")
public class OrderController {

  private final SalesOrderService orderService;
  private final PaymentService paymentService;

  public OrderController(SalesOrderService orderService, PaymentService paymentService) {
    this.orderService = orderService;
    this.paymentService = paymentService;
  }

  @GetMapping
  @RequirePermission("order:read")
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
  @RequirePermission("order:read")
  @Operation(summary = "订单详情（含回款计划台账与回款记录）")
  public ApiResponse<OrderResponse> detail(@PathVariable Long id) {
    return ApiResponse.ok(orderService.detail(id));
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  @RequirePermission("order:create")
  @Operation(summary = "创建订单（可直接创建或基于已生效合同创建）")
  public ApiResponse<OrderResponse> create(@Valid @RequestBody OrderRequest request) {
    return ApiResponse.ok(orderService.create(request));
  }

  @PutMapping("/{id}")
  @RequirePermission("order:update")
  @Operation(summary = "编辑订单（重建期次，已回款期次保留）")
  public ApiResponse<OrderResponse> update(
      @PathVariable Long id, @Valid @RequestBody OrderRequest request) {
    return ApiResponse.ok(orderService.update(id, request));
  }

  @DeleteMapping("/{id}")
  @RequirePermission("order:delete")
  @Operation(summary = "逻辑删除订单（仅管理员，存在回款记录时拒绝）")
  public ApiResponse<Void> delete(@PathVariable Long id) {
    orderService.delete(id);
    return ApiResponse.ok();
  }

  @PostMapping("/{id}/payments")
  @RequirePermission("order:payment")
  @Operation(summary = "登记回款（驱动期次与订单状态）")
  public ApiResponse<OrderResponse> recordPayment(
      @PathVariable Long id, @Valid @RequestBody PaymentRequest request) {
    paymentService.recordPayment(id, request);
    return ApiResponse.ok(orderService.detail(id));
  }

  @GetMapping("/reminder-summary")
  @RequirePermission("order:read")
  @Operation(summary = "回款提醒汇总（逾期/临期期次数）")
  public ApiResponse<Map<String, Long>> reminderSummary() {
    return ApiResponse.ok(paymentService.reminderSummary());
  }
}
