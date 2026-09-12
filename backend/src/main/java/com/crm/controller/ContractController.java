package com.crm.controller;

import com.crm.common.ApiResponse;
import com.crm.common.PageResult;
import com.crm.dto.contract.ContractRequest;
import com.crm.dto.contract.ContractResponse;
import com.crm.security.RequirePermission;
import com.crm.service.ContractService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.Map;
import org.springframework.http.HttpStatus;
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
 * 合同接口（008，FR-CT01~CT06）。
 *
 * <p><b>1.5：类级 {@code @PreAuthorize("hasAnyRole('ADMIN','SALES')")} 与 {@code approve} / {@code
 * reject} 上的 {@code hasRole('ADMIN')} 已移除。</b>粗粒度那层只认字面角色名，于是 081 的五个
 * 角色在本模块被整体挡在门外：SALES_MANAGER、SALES_REP、FINANCE_MANAGER、FINANCE_ACCOUNTANT、 VIEWER
 * 的菜单里都有「合同」（V46/V75 的 {@code role_menu}），前四者的权限列表里还有 contract:create/update/delete（FINANCE_MANAGER
 * 另有 contract:approve）——正是「矩阵上写了、实际 拿不到」。写操作改挂动作码：
 *
 * <ul>
 *   <li>{@code POST /}、{@code PUT /{id}} → {@code contract:create} / {@code contract:update}；
 *   <li>{@code POST /{id}/approve}、{@code POST /{id}/reject} → {@code contract:approve}：拒绝是审批权的
 *       另一半，字典里没有 {@code contract:reject}。原先只有 ADMIN 能审批，换成码后行为不变（切面对 ADMIN 恒放行），而矩阵里本就有该码的
 *       SALES_MANAGER / FINANCE_MANAGER 获得审批权；
 *   <li>状态推进 {@code /{id}/submit}、{@code /{id}/effective}、{@code /{id}/complete}、 {@code
 *       /{id}/terminate} → {@code contract:update}：字典里没有 {@code
 *       contract:submit/effective/complete/terminate}，而状态推进在语义上就是改这份合同，与 {@code TicketController} 把
 *       {@code /transition} 挂 {@code ticket:update} 是同一口径。这四条 原先只由类级那层放行 ADMIN 与 SALES，而 SALES 持有
 *       {@code contract:update}，故无人被收窄。
 * </ul>
 *
 * <p>写操作本次不需要补授：用到的三个码 SALES 与 ADMIN 在 V46 里都已持有（SALES 有 create/update；ADMIN 另有 approve，且切面本就对
 * ADMIN 恒放行），不存在 {@code ContactController} 那种「码在字典里、却没有角色 拿得到」的断档。
 *
 * <p><b>读接口（列表/详情）挂 {@code contract:read}。</b>摘掉类级门之后，这两个接口一度对全体登录用户可读： {@code
 * ContractService.page()} 只按关键字/状态/客户拼条件，{@code detail()} 直接 {@code selectById}，
 * 服务层<b>没有任何数据范围过滤</b>，合同（含金额、正文、审批意见）只要登录就能整表拉走。字典的合同组原先 只有
 * create/update/delete/approve/renewal，没有 {@code contract:read}，故该码已补入 {@code PERMISSION_DEFS}，按「持有
 * contracts 菜单」的口径授予 ADMIN / SALES / SALES_MANAGER / SALES_REP / FINANCE_MANAGER /
 * FINANCE_ACCOUNTANT / VIEWER 七个角色，两个读接口随之挂码。
 */
@RestController
@RequestMapping("/api/v1/contracts")
@Tag(name = "合同")
public class ContractController {

  private final ContractService contractService;

  public ContractController(ContractService contractService) {
    this.contractService = contractService;
  }

  @GetMapping
  @RequirePermission("contract:read")
  @Operation(summary = "分页查询合同列表（关键字/状态/客户筛选）")
  public ApiResponse<PageResult<ContractResponse>> page(
      @RequestParam(required = false) String keyword,
      @RequestParam(required = false) String status,
      @RequestParam(required = false) Long customerId,
      @RequestParam(defaultValue = "1") long page,
      @RequestParam(defaultValue = "20") long pageSize) {
    return ApiResponse.ok(contractService.page(keyword, status, customerId, page, pageSize));
  }

  @GetMapping("/{id}")
  @RequirePermission("contract:read")
  @Operation(summary = "合同详情（含正文、审批信息、附件列表）")
  public ApiResponse<ContractResponse> detail(@PathVariable Long id) {
    return ApiResponse.ok(contractService.detail(id));
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  @RequirePermission("contract:create")
  @Operation(summary = "创建合同（可直接创建或基于已通过报价单创建）")
  public ApiResponse<ContractResponse> create(@Valid @RequestBody ContractRequest request) {
    return ApiResponse.ok(contractService.create(request));
  }

  @PutMapping("/{id}")
  @RequirePermission("contract:update")
  @Operation(summary = "编辑草稿/被拒合同")
  public ApiResponse<ContractResponse> update(
      @PathVariable Long id, @Valid @RequestBody ContractRequest request) {
    return ApiResponse.ok(contractService.update(id, request));
  }

  @PostMapping("/{id}/submit")
  @RequirePermission("contract:update")
  @Operation(summary = "提交审批")
  public ApiResponse<ContractResponse> submit(@PathVariable Long id) {
    return ApiResponse.ok(contractService.submit(id));
  }

  @PostMapping("/{id}/approve")
  @RequirePermission("contract:approve")
  @Operation(summary = "审批通过（仅管理员）")
  public ApiResponse<ContractResponse> approve(@PathVariable Long id) {
    return ApiResponse.ok(contractService.approve(id));
  }

  @PostMapping("/{id}/reject")
  @RequirePermission("contract:approve")
  @Operation(summary = "审批拒绝（仅管理员，需填写意见）")
  public ApiResponse<ContractResponse> reject(
      @PathVariable Long id, @RequestBody(required = false) Map<String, String> body) {
    String reason = body == null ? null : body.get("reason");
    return ApiResponse.ok(contractService.reject(id, reason));
  }

  @PostMapping("/{id}/effective")
  @RequirePermission("contract:update")
  @Operation(summary = "标记合同生效")
  public ApiResponse<ContractResponse> effective(@PathVariable Long id) {
    return ApiResponse.ok(contractService.effective(id));
  }

  @PostMapping("/{id}/complete")
  @RequirePermission("contract:update")
  @Operation(summary = "标记合同完成")
  public ApiResponse<ContractResponse> complete(@PathVariable Long id) {
    return ApiResponse.ok(contractService.complete(id));
  }

  @PostMapping("/{id}/terminate")
  @RequirePermission("contract:update")
  @Operation(summary = "标记合同终止（需填写原因）")
  public ApiResponse<ContractResponse> terminate(
      @PathVariable Long id, @RequestBody(required = false) Map<String, String> body) {
    String reason = body == null ? null : body.get("reason");
    return ApiResponse.ok(contractService.terminate(id, reason));
  }
}
