package com.crm.controller;

import com.crm.common.ApiResponse;
import com.crm.common.PageResult;
import com.crm.dto.customer.CustomerResponse;
import com.crm.dto.pool.BatchTransferRequest;
import com.crm.dto.pool.PoolScanResult;
import com.crm.security.RequirePermission;
import com.crm.service.CustomerPoolService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 客户公海接口（011，FR-PL01~PL08）。
 *
 * <p><b>1.5 批 3：本类是全批唯一"无闸门的写"。</b>改造前只有 scan / batchTransfer 挂着 {@code hasRole('ADMIN')}，而 {@code
 * claim}（领取公海客户）**一条校验都没有**——任何登录用户，包括 VIEWER / ANALYST /
 * FINANCE_*，都能把公海客户领成自己的。所以这一处不是"撤掉粗粒度门"，而是判据②第一次用在写接口上： 照矩阵补一个码。新码 {@code customer:claim}
 * 授给销售三角色（SALES / SALES_MANAGER / SALES_REP）， 理由与边界写在 {@code RoleConstants} 该码的注释里。
 *
 * <p><b>两个批量端点刻意不扩权</b>：{@code scan} 与 {@code batch-transfer} 合成一个新码 {@code
 * customer:pool_manage}，且该码**不授给任何角色**——见 {@code RoleConstants} 里的理由 （batchTransfer 直接改 owner
 * 且不校验调用者是否拥有这些客户，是数据范围的一处绕行）。表面上它们与 {@code customer:transfer} 同义，但复用那个码会让销售代表拿到"把全队客户改成自己的"的能力。
 *
 * <p><b>两个读接口不动</b>：{@code /pool} 列的是 owner 为空的客户（公海本身就该人人可见，否则没人能领）， {@code /my}
 * 按当前用户过滤。两者都不是"泄漏别人的数据"，因此不设码——设了反而会让除销售外的角色 打开客户页时看到一片 403。
 */
@RestController
@RequestMapping("/api/v1/customers")
@Tag(name = "客户公海")
public class CustomerPoolController {

  private final CustomerPoolService poolService;

  public CustomerPoolController(CustomerPoolService poolService) {
    this.poolService = poolService;
  }

  @GetMapping("/pool")
  @Operation(summary = "公海客户分页（owner 为空）")
  public ApiResponse<PageResult<CustomerResponse>> pool(
      @RequestParam(required = false) String keyword,
      @RequestParam(required = false) String status,
      @RequestParam(defaultValue = "1") long page,
      @RequestParam(defaultValue = "20") long pageSize) {
    return ApiResponse.ok(poolService.pool(keyword, status, page, pageSize));
  }

  @GetMapping("/my")
  @Operation(summary = "我的客户分页（owner=本人）")
  public ApiResponse<PageResult<CustomerResponse>> mine(
      @RequestParam(required = false) String keyword,
      @RequestParam(required = false) String status,
      @RequestParam(defaultValue = "1") long page,
      @RequestParam(defaultValue = "20") long pageSize) {
    return ApiResponse.ok(poolService.mine(keyword, status, page, pageSize));
  }

  @PostMapping("/pool/{id}/claim")
  @RequirePermission("customer:claim")
  @Operation(summary = "从公海领取客户")
  public ApiResponse<CustomerResponse> claim(@PathVariable Long id) {
    return ApiResponse.ok(poolService.claim(id));
  }

  @PostMapping("/pool/scan")
  @RequirePermission("customer:pool_manage")
  @Operation(summary = "公海扫描：超期未跟进客户退回公海")
  public ApiResponse<PoolScanResult> scan() {
    return ApiResponse.ok(poolService.scan());
  }

  @PostMapping("/batch-transfer")
  @RequirePermission("customer:pool_manage")
  @Operation(summary = "批量转移/分配客户（单次 ≤100）")
  public ApiResponse<Long> batchTransfer(@Valid @RequestBody BatchTransferRequest request) {
    return ApiResponse.ok(poolService.batchTransfer(request));
  }
}
