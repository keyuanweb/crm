package com.crm.controller.quota;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.crm.dto.quota.SalesQuotaAchievementResponse;
import com.crm.dto.quota.SalesQuotaBreakdownRequest;
import com.crm.dto.quota.SalesQuotaRequest;
import com.crm.dto.quota.SalesQuotaResponse;
import com.crm.dto.quota.SalesQuotaSummaryResponse;
import com.crm.service.quota.SalesQuotaService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 销售配额 Controller（078-sales-quota）。 */
@RestController
@RequestMapping("/api/v1/sales-quota")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN', 'SALES_MANAGER')")
public class SalesQuotaController {

  private final SalesQuotaService salesQuotaService;

  /** 创建配额。 */
  @PostMapping
  public ResponseEntity<SalesQuotaResponse> createQuota(
      @Valid @RequestBody SalesQuotaRequest request) {
    return ResponseEntity.status(HttpStatus.CREATED).body(salesQuotaService.createQuota(request));
  }

  /** 获取配额列表。 */
  @GetMapping
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
  public ResponseEntity<SalesQuotaResponse> getQuota(@PathVariable Long id) {
    return ResponseEntity.ok(salesQuotaService.getQuota(id));
  }

  /** 更新配额。 */
  @PutMapping("/{id}")
  public ResponseEntity<SalesQuotaResponse> updateQuota(
      @PathVariable Long id, @Valid @RequestBody SalesQuotaRequest request) {
    return ResponseEntity.ok(salesQuotaService.updateQuota(id, request));
  }

  /** 分解配额。 */
  @PostMapping("/{id}/breakdown")
  public ResponseEntity<Void> breakdownQuota(
      @PathVariable Long id, @Valid @RequestBody List<SalesQuotaBreakdownRequest> breakdowns) {
    salesQuotaService.breakdownQuota(id, breakdowns);
    return ResponseEntity.status(HttpStatus.CREATED).build();
  }

  /** 获取配额分解。 */
  @GetMapping("/{id}/breakdown")
  public ResponseEntity<List<Map<String, Object>>> getBreakdown(@PathVariable Long id) {
    return ResponseEntity.ok(salesQuotaService.getBreakdown(id));
  }

  /** 获取配额达成率。 */
  @GetMapping("/{id}/achievement")
  public ResponseEntity<SalesQuotaAchievementResponse> getAchievement(@PathVariable Long id) {
    return ResponseEntity.ok(salesQuotaService.getAchievement(id));
  }

  /** 获取配额版本历史。 */
  @GetMapping("/{id}/versions")
  public ResponseEntity<List<Map<String, Object>>> getVersions(@PathVariable Long id) {
    return ResponseEntity.ok(salesQuotaService.getVersions(id));
  }

  /** 获取团队排名。 */
  @GetMapping("/ranking")
  public ResponseEntity<List<Map<String, Object>>> getTeamRanking(@RequestParam Integer year) {
    return ResponseEntity.ok(salesQuotaService.getTeamRanking(year));
  }

  /** 获取年度配额汇总（总配额 / 总实际 / 总达成率）。 */
  @GetMapping("/summary")
  public ResponseEntity<SalesQuotaSummaryResponse> getSummary(@RequestParam Integer year) {
    return ResponseEntity.ok(salesQuotaService.getSummary(year));
  }
}
