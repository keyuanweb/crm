package com.crm.controller;

import com.crm.common.ApiResponse;
import com.crm.common.PageResult;
import com.crm.dto.share.CustomerShareRequest;
import com.crm.dto.share.SharedCustomerResponse;
import com.crm.security.RequirePermission;
import com.crm.service.CustomerShareService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 客户共享接口（012，FR-DP07/DP08）。
 *
 * <p><b>1.5：类级 {@code @PreAuthorize("hasAnyRole('ADMIN','SALES','SUPPORT')")} 撤除。</b>真正的控制在两处内联
 * 判定上，本类一个数据范围都没放松：{@code CustomerShareService} 的"归属者或 ADMIN"（共享/取消共享）与按 {@code sharedToUserId}
 * 过滤的"共享给我的客户"。
 *
 * <p>读（{@code GET /shared-to-me}）**不设码**——它按 {@code SecurityUtil.currentUserId()} 过滤，是自限范围；
 * 写**要设码**：撤门之后任何登录用户都能调 {@code POST /customer-shares}，内联检查会放行"自己的"客户，那正是 VIEWER
 * 这类"一个写码都没有"的角色第一次获得写能力的地方。写挂 {@code customer:update}：共享改的是客户的 可见性，属于对客户的一次写。
 *
 * <p><b>为什么不是 {@code customer:transfer}</b>：那个码描述的是**转移归属**（换 owner），而共享只是授予访问权；
 * 更实际的差别在授予名单——{@code customer:transfer} 的持有者是 ADMIN / SALES / SALES_MANAGER，用它会把
 * SALES_REP（一线最需要把客户共享给同事与主管的人）挡在门外，同时要给 SUPPORT 补一条"转移权"才能保住它今天 已有的共享能力。{@code customer:update}
 * 的持有者恰好是"能改这个客户"的那批人：ADMIN / SALES / SUPPORT （改造前放行的三个）加上 SALES_MANAGER / SALES_REP。
 */
@RestController
@RequestMapping("/api/v1/customer-shares")
@Tag(name = "客户共享")
public class CustomerShareController {

  private final CustomerShareService shareService;

  public CustomerShareController(CustomerShareService shareService) {
    this.shareService = shareService;
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  @RequirePermission("customer:update")
  @Operation(summary = "共享客户给用户（归属者或管理员）")
  public ApiResponse<SharedCustomerResponse> share(
      @Valid @RequestBody CustomerShareRequest request) {
    return ApiResponse.ok(shareService.share(request));
  }

  @DeleteMapping("/{id}")
  @RequirePermission("customer:update")
  @Operation(summary = "取消共享（归属者或管理员）")
  public ApiResponse<Void> unshare(@PathVariable Long id) {
    shareService.unshare(id);
    return ApiResponse.ok();
  }

  @GetMapping("/shared-to-me")
  @Operation(summary = "共享给我的客户列表（只读）")
  public ApiResponse<PageResult<SharedCustomerResponse>> sharedToMe(
      @RequestParam(defaultValue = "1") long page,
      @RequestParam(defaultValue = "20") long pageSize) {
    return ApiResponse.ok(shareService.sharedToMe(page, pageSize));
  }
}
