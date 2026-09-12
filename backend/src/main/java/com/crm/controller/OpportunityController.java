package com.crm.controller;

import com.crm.common.ApiResponse;
import com.crm.common.PageResult;
import com.crm.dto.opportunity.OpportunityDetailResponse;
import com.crm.dto.opportunity.OpportunityRequest;
import com.crm.dto.opportunity.OpportunityResponse;
import com.crm.security.RequirePermission;
import com.crm.service.CustomFieldFilterSupport;
import com.crm.service.OpportunityService;
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
 * 商机接口（contracts/opportunities.md，FR-007~011）。
 *
 * <p><b>1.5：类级 {@code @PreAuthorize("hasAnyRole('ADMIN','SALES')")} 已移除。</b> 那一层只认 ADMIN、 SALES
 * 两个字面量角色名，于是 081 新增的销售角色在本 Controller 上被整体挡在门外：SALES_MANAGER 与 SALES_REP 的菜单里有「商机」，V75 也早已给它们发了
 * opportunity:create/update/delete，实测却一律 403—— 正是 1.5 要消灭的那类「矩阵上写了、实际拿不到」。VIEWER
 * 同理：它持有「商机」菜单，却连列表都打不开。
 *
 * <p>写操作改挂动作码（create/update/delete 取字典「商机管理」组里的同名码）。{@code PUT /{id}} 兼作 状态字段的修改（{@code
 * OpportunityService.update} 会写入 status），字典里没有单独的阶段流转码，故仍用 {@code
 * opportunity:update}——改状态就是改商机。ADMIN 由 PermissionAspect 直通，不受影响。
 *
 * <p>三个码里 {@code opportunity:delete} 此前**没有任何销售角色持有**：V46 只给 SALES 发了
 * create/update，而它原先靠类级粗粒度门是能删商机的。不补授就等于把「能用的」打成 403，故须在 V80 中把 opportunity:delete 补授给
 * SALES（SALES_MANAGER / SALES_REP 在 V75 里已有该码）。
 *
 * <p><b>读接口（列表/详情）挂 {@code opportunity:read}。</b>{@code OpportunityService.page} 与 {@code detail}
 * 都没有数据范围过滤——前者是纯条件分页（未调用 DataPermissionService / EntityAccessService），后者连范围校验都没有；撤掉类级门却不设码，这两个 GET
 * 就对全体登录用户开放。 字典「商机管理」组原本只有 create/update/delete/export 四个写码，没有读码，故 {@code opportunity:read} 已补入
 * {@code PERMISSION_DEFS}，授予门放行的 ADMIN/SALES 与持有 'opportunities' 菜单的角色，并<b>刻意 不含
 * SUPPORT</b>——{@code OpportunityIT.supportRoleForbidden} 断言客服读不到商机，那条断言此前是靠类级
 * 门顺手满足的，撤门后只能靠「码存在但不授给 SUPPORT」继续满足。读码不复用写码，否则「能看」与「能改」 会被绑成同一个集合。
 */
@RestController
@RequestMapping("/api/v1/opportunities")
@Tag(name = "商机")
public class OpportunityController {

  private final OpportunityService opportunityService;
  private final CustomFieldFilterSupport customFieldFilterSupport;

  public OpportunityController(
      OpportunityService opportunityService, CustomFieldFilterSupport customFieldFilterSupport) {
    this.opportunityService = opportunityService;
    this.customFieldFilterSupport = customFieldFilterSupport;
  }

  @GetMapping
  @RequirePermission("opportunity:read")
  @Operation(summary = "分页查询商机列表（支持 cf_<fieldId> 自定义字段筛选）")
  public ApiResponse<PageResult<OpportunityResponse>> page(
      @RequestParam(required = false) String keyword,
      @RequestParam(required = false) Long customerId,
      @RequestParam(required = false) String status,
      @RequestParam(defaultValue = "1") long page,
      @RequestParam(defaultValue = "20") long pageSize,
      @RequestParam Map<String, String> params) {
    List<Long> cfMatchedIds =
        customFieldFilterSupport.matchEntityIds(
            "OPPORTUNITY", customFieldFilterSupport.parseFilters(params));
    return ApiResponse.ok(
        opportunityService.page(keyword, customerId, status, cfMatchedIds, page, pageSize));
  }

  @GetMapping("/{id}")
  @RequirePermission("opportunity:read")
  @Operation(summary = "商机详情（含下属销售机会）")
  public ApiResponse<OpportunityDetailResponse> detail(@PathVariable Long id) {
    return ApiResponse.ok(opportunityService.detail(id));
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  @RequirePermission("opportunity:create")
  @Operation(summary = "创建商机")
  public ApiResponse<OpportunityResponse> create(@Valid @RequestBody OpportunityRequest request) {
    return ApiResponse.ok(opportunityService.create(request));
  }

  @PutMapping("/{id}")
  @RequirePermission("opportunity:update")
  @Operation(summary = "编辑商机")
  public ApiResponse<OpportunityResponse> update(
      @PathVariable Long id, @Valid @RequestBody OpportunityRequest request) {
    return ApiResponse.ok(opportunityService.update(id, request));
  }

  @DeleteMapping("/{id}")
  @RequirePermission("opportunity:delete")
  @Operation(summary = "逻辑删除商机（级联删除销售机会）")
  public ApiResponse<Void> delete(@PathVariable Long id) {
    opportunityService.delete(id);
    return ApiResponse.ok();
  }
}
