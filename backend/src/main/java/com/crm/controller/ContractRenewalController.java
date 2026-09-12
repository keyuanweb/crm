package com.crm.controller;

import com.crm.common.ApiResponse;
import com.crm.common.PageResult;
import com.crm.dto.contract.ContractResponse;
import com.crm.security.RequirePermission;
import com.crm.service.ContractRenewalService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 合同续约接口（046，FR-R01~R06）。
 *
 * <p><b>1.5：类级 {@code @PreAuthorize("hasAnyRole('ADMIN','SALES')")} 换成 {@code
 * contract:renewal}——这个码 此前无人持有、也无人引用，是本轮里"矩阵上有、实际没有"的典型。</b>
 *
 * <p>授予范围（V84）= 改造前那道门放行的 ADMIN / SALES ∪ 销售侧持有「合同」菜单的 SALES_MANAGER / SALES_REP； 财务与 VIEWER
 * 不授——他们能读合同（{@code contract:read}），但续约漏斗是销售的作战视图，不是一个更宽的合同读。
 *
 * <p>同一批还给这四个角色补了 {@code contract-renewal} **菜单**：前端路由（{@code ContractRenewalPage}）与接口
 * 都齐全，MENU_TREE 里也有这一项，却没有任何角色持有它——于是"合同续约"这个功能对所有人不可见（只有 ADMIN 靠 菜单兜底看得见）。这一条与 V80
 * 给客服补「满意度调查」菜单同一形态：把已经做好的功能还给它的角色，在 V84 里 单列以便复核。
 */
@RestController
@RequestMapping("/api/v1/contracts")
@Tag(name = "合同")
public class ContractRenewalController {

  private final ContractRenewalService renewalService;

  public ContractRenewalController(ContractRenewalService renewalService) {
    this.renewalService = renewalService;
  }

  @GetMapping("/renewal-overview")
  @RequirePermission("contract:renewal")
  @Operation(summary = "续约漏斗视图（即将到期/已到期未续/已续约）")
  public ApiResponse<PageResult<ContractResponse>> overview(
      @RequestParam String group,
      @RequestParam(required = false) String keyword,
      @RequestParam(defaultValue = "1") long page,
      @RequestParam(defaultValue = "20") long pageSize) {
    return ApiResponse.ok(renewalService.overview(group, keyword, page, pageSize));
  }
}
