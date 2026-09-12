package com.crm.controller.quota;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.crm.dto.quota.SalesQuotaAchievementResponse;
import com.crm.dto.quota.SalesQuotaBreakdownRequest;
import com.crm.dto.quota.SalesQuotaRequest;
import com.crm.dto.quota.SalesQuotaResponse;
import com.crm.dto.quota.SalesQuotaSummaryResponse;
import com.crm.security.RequirePermission;
import com.crm.service.quota.SalesQuotaService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 销售配额 Controller（078-sales-quota）。
 *
 * <p><b>1.5 批 3：类级 {@code @PreAuthorize("hasAnyRole('ADMIN','SALES_MANAGER')")} 撤除，换成 {@code
 * quota:*} 系列动作码。</b>这个类级门是"方法级带参"之外最容易被忽略的一种形态——它把十个端点 （含六个读）一起锁在两个角色里，而字典里 {@code quota:*}
 * 五个码**没有任何角色持有**，所以本批有两件事必须 一起做，缺一即错：
 *
 * <ul>
 *   <li><b>新增读码 {@code quota:read}</b>：六个读端点（列表/详情/分解/版本/团队排名/年度汇总）在 Service 里
 *       没有任何数据范围过滤，撤门又不设码就是对全体登录用户开放"每个人的配额与达成率"。
 *   <li><b>按判据①补授</b>：{@code quota:read/create/update/breakdown/achievement} 全部补授给 SALES_MANAGER
 *       ——不补的话，改造前能用这套接口的销售经理会被打成 403，正是判据①要防的那件事。
 * </ul>
 *
 * <p>{@code quota:delete} 至今没有任何端点引用（本类没有删除动作），保持"字典里可勾、无人使用"的原状。
 *
 * <p><b>一处登记不改</b>：「销售配额」菜单（084 归入销售管理域）在种子里**没有任何角色持有**，所以这个页面 目前只有
 * ADMIN（走菜单兜底）看得见。本批只管把端点接上码，不动菜单授权——是否把该菜单授给销售角色是 084 菜单 IA 的裁决范围。
 */
@RestController
@RequestMapping("/api/v1/sales-quota")
@RequiredArgsConstructor
public class SalesQuotaController {

  private final SalesQuotaService salesQuotaService;

  /** 创建配额。 */
  @PostMapping
  @RequirePermission("quota:create")
  public ResponseEntity<SalesQuotaResponse> createQuota(
      @Valid @RequestBody SalesQuotaRequest request) {
    return ResponseEntity.status(HttpStatus.CREATED).body(salesQuotaService.createQuota(request));
  }

  /** 获取配额列表。 */
  @GetMapping
  @RequirePermission("quota:read")
  public ResponseEntity<IPage<SalesQuotaResponse>> getQuotas(
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size,
      @RequestParam(required = false) Integer year,
      @RequestParam(required = false) Long teamId,
      @RequestParam(required = false) Long userId,
      @RequestParam(required = false) String status) {
    return ResponseEntity.ok(salesQuotaService.getQuotas(page, size, year, teamId, userId, status));
  }

  /** 获取配额详情。 */
  @GetMapping("/{id}")
  @RequirePermission("quota:read")
  public ResponseEntity<SalesQuotaResponse> getQuota(@PathVariable Long id) {
    return ResponseEntity.ok(salesQuotaService.getQuota(id));
  }

  /** 更新配额。 */
  @PutMapping("/{id}")
  @RequirePermission("quota:update")
  public ResponseEntity<SalesQuotaResponse> updateQuota(
      @PathVariable Long id, @Valid @RequestBody SalesQuotaRequest request) {
    return ResponseEntity.ok(salesQuotaService.updateQuota(id, request));
  }

  /** 分解配额。 */
  @PostMapping("/{id}/breakdown")
  @RequirePermission("quota:breakdown")
  public ResponseEntity<Void> breakdownQuota(
      @PathVariable Long id, @Valid @RequestBody List<SalesQuotaBreakdownRequest> breakdowns) {
    salesQuotaService.breakdownQuota(id, breakdowns);
    return ResponseEntity.status(HttpStatus.CREATED).build();
  }

  /** 获取配额分解。 */
  @GetMapping("/{id}/breakdown")
  @RequirePermission("quota:read")
  public ResponseEntity<List<Map<String, Object>>> getBreakdown(@PathVariable Long id) {
    return ResponseEntity.ok(salesQuotaService.getBreakdown(id));
  }

  /** 获取配额达成率。 */
  @GetMapping("/{id}/achievement")
  @RequirePermission("quota:achievement")
  public ResponseEntity<SalesQuotaAchievementResponse> getAchievement(@PathVariable Long id) {
    return ResponseEntity.ok(salesQuotaService.getAchievement(id));
  }

  /** 获取配额版本历史。 */
  @GetMapping("/{id}/versions")
  @RequirePermission("quota:read")
  public ResponseEntity<List<Map<String, Object>>> getVersions(@PathVariable Long id) {
    return ResponseEntity.ok(salesQuotaService.getVersions(id));
  }

  /** 获取团队排名。 */
  @GetMapping("/ranking")
  @RequirePermission("quota:read")
  public ResponseEntity<List<Map<String, Object>>> getTeamRanking(@RequestParam Integer year) {
    return ResponseEntity.ok(salesQuotaService.getTeamRanking(year));
  }

  /** 获取年度配额汇总（总配额 / 总实际 / 总达成率）。 */
  @GetMapping("/summary")
  @RequirePermission("quota:read")
  public ResponseEntity<SalesQuotaSummaryResponse> getSummary(@RequestParam Integer year) {
    return ResponseEntity.ok(salesQuotaService.getSummary(year));
  }
}
