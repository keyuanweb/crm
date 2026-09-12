package com.crm.controller;

import com.crm.common.ApiResponse;
import com.crm.common.PageResult;
import com.crm.dto.sla.SlaOverviewResponse;
import com.crm.dto.sla.SlaPolicyRequest;
import com.crm.dto.sla.SlaPolicyResponse;
import com.crm.security.RequirePermission;
import com.crm.service.SlaEscalationService;
import com.crm.service.SlaPolicyService;
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
 * SLA 策略接口（015，FR-C09/C12）。
 *
 * <p><b>1.5：类级 {@code @PreAuthorize("hasRole('ADMIN')")} 撤除。</b>那个门与四个写方法上的
 * {@code @RequirePermission("sla:manage")} 互相矛盾：V75 把 {@code sla:manage} 授给了 SUPPORT_MANAGER，
 * 而它的菜单里也确实有「SLA 策略」——角色页显示"已授权"，请求却先被类级门拦成 403。细粒度注解在类级门 之下**是一段死代码**（ADMIN
 * 本来就恒放行）。撤门后语义变成矩阵说了算：持有 {@code sla:manage} 的角色 可用本模块全部端点（当前 = SUPPORT_MANAGER，ADMIN 走切面直通）。
 *
 * <p>读操作同样用 {@code sla:manage}，不另设读码：SLA 策略是全局配置（{@code SlaPolicyService} 无任何数据
 * 范围过滤），而持有该菜单的角色与持有该码的角色**恰好是同一个集合**（ADMIN + SUPPORT_MANAGER）， 另设读码只会多出一个永远同进同出的码。
 */
@RestController
@RequestMapping("/api/v1/sla-policies")
@Tag(name = "客户服务")
public class SlaPolicyController {

  private final SlaPolicyService slaPolicyService;
  private final SlaEscalationService slaEscalationService;

  public SlaPolicyController(
      SlaPolicyService slaPolicyService, SlaEscalationService slaEscalationService) {
    this.slaPolicyService = slaPolicyService;
    this.slaEscalationService = slaEscalationService;
  }

  @GetMapping
  @RequirePermission("sla:manage")
  @Operation(summary = "SLA 策略列表")
  public ApiResponse<PageResult<SlaPolicyResponse>> page(
      @RequestParam(defaultValue = "1") long page,
      @RequestParam(defaultValue = "20") long pageSize) {
    return ApiResponse.ok(slaPolicyService.page(page, pageSize));
  }

  @GetMapping("/overview")
  @RequirePermission("sla:manage")
  @Operation(summary = "SLA 超时统计（未关闭工单）")
  public ApiResponse<SlaOverviewResponse> overview() {
    return ApiResponse.ok(slaPolicyService.overview());
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  @RequirePermission("sla:manage")
  @Operation(summary = "创建 SLA 策略（每优先级唯一）")
  public ApiResponse<SlaPolicyResponse> create(@Valid @RequestBody SlaPolicyRequest request) {
    return ApiResponse.ok(slaPolicyService.create(request));
  }

  @PutMapping("/{id}")
  @RequirePermission("sla:manage")
  @Operation(summary = "编辑 SLA 策略")
  public ApiResponse<SlaPolicyResponse> update(
      @PathVariable Long id, @Valid @RequestBody SlaPolicyRequest request) {
    return ApiResponse.ok(slaPolicyService.update(id, request));
  }

  @DeleteMapping("/{id}")
  @RequirePermission("sla:manage")
  @Operation(summary = "删除 SLA 策略")
  public ApiResponse<Void> delete(@PathVariable Long id) {
    slaPolicyService.delete(id);
    return ApiResponse.ok();
  }

  /**
   * 手动触发 SLA 升级扫描（1.3）。
   *
   * <p>与定时作业共用同一实现——本项目既定惯例：另两个作业（定时导出、数据保留）都有手动入口， 否则「作业是否真的会升级」只能等 cron，测试与运维都无法确定性地验证。
   */
  @PostMapping("/escalate-now")
  @RequirePermission("sla:manage")
  @Operation(summary = "手动触发 SLA 升级扫描（返回本轮升级的工单数）")
  public ApiResponse<Map<String, Integer>> escalateNow() {
    return ApiResponse.ok(Map.of("escalated", slaEscalationService.scanAndEscalate()));
  }
}
