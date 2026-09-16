package com.crm.controller;

import com.crm.common.ApiResponse;
import com.crm.common.PageResult;
import com.crm.dto.quote.QuoteRequest;
import com.crm.dto.quote.QuoteResponse;
import com.crm.security.RateLimit;
import com.crm.security.RateLimitDimension;
import com.crm.security.RequirePermission;
import com.crm.service.QuotePdfService;
import com.crm.service.QuoteService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
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
 * 报价单接口（007-product-cpq，FR-P05~P11）。
 *
 * <p><b>1.5：类级 {@code @PreAuthorize("hasAnyRole('ADMIN','SALES')")} 与 approve/reject 上的
 * {@code @PreAuthorize("hasRole('ADMIN')")} 已移除。</b>粗粒度那一层只认字面量角色名，于是 081 的销售角色
 * 被整体挡在门外：SALES_MANAGER 与 SALES_REP 的菜单里都有 {@code quotes}（V75），V75 也已把 {@code
 * quote:create/update/delete} 授给二者（SALES_MANAGER 另有 {@code quote:approve}），可这两个角色 在**全部**报价单接口上恒
 * 403——正是「矩阵上写了、实际拿不到」。写操作改挂动作码后：创建 = 创建者自己的 动作，挂 {@code
 * quote:create}；编辑与提交审批都是对草稿的改写（SALES_MANAGER / SALES_REP 的矩阵里 没有第 4 个码可用，而"提交"本就不是审批），挂 {@code
 * quote:update}；审批通过与拒绝是同一个审批决定的 两面，同挂 {@code quote:approve}。
 *
 * <p><b>本次没有任何角色丢失能力</b>：旧的类级门只放行 ADMIN 与 SALES，而 V46 恰好给了这两个角色上面用到的 全部码（ADMIN 持
 * create/update/approve，SALES 持 create/update）；approve/reject 原先只放行 ADMIN， ADMIN 同样持有 {@code
 * quote:approve}。因此 V80 不需要为本 Controller 的写操作补授任何一条授权——这是本次 扫掠里少见的「零 preserve 授权」的模块（读接口另补了一个
 * {@code quote:read}，见下段）。刻意**不**补的 一条：SALES_REP 持有同族写码（create/update/delete） 但不持 {@code
 * quote:approve}，按"同族"规则看似该补，实则不该——审批在本模块从来不是销售的既有能力 （旧注解是 {@code hasRole('ADMIN')}），矩阵也只把 approve
 * 承诺给了 SALES_MANAGER。
 *
 * <p><b>读接口（列表/详情/PDF）挂 {@code quote:read}。</b>{@code QuoteService.page} 与 {@code detail}
 * 都没有数据范围过滤：page 只有关键字/状态/客户三个查询条件，detail 按 id 直取，既不调 {@code DataPermissionService} 也不调 {@code
 * EntityAccessService}（报价单有 {@code created_by}，服务里 却从未用它过滤）。类级门一撤，任何登录用户都能拉走全部报价单（含客户名、单价、折扣、金额）；
 * {@code GET /{id}/pdf} 同理——它是 GET，实现就是 {@code detail()} 加一次 PDF 渲染。字典的「报价单管理」 组里原本只有
 * create/update/delete/approve，没有 {@code quote:read}，而这类无范围过滤的读缺了码就是整表 可读，故该码已补入 {@code
 * PERMISSION_DEFS} 并授给持有 'quotes' 菜单的角色（ADMIN / SALES / SALES_MANAGER /
 * SALES_REP），三个读接口随之挂码。读码不复用写码，否则「能看」与「能改」会被绑成同一 个集合。
 *
 * <p>另注：{@code quote:delete} 在字典里存在、V46/V75 也授给了 ADMIN / SALES_MANAGER / SALES_REP， 但本
 * Controller（以及仓库其它位置）没有任何删除报价单的端点，该码至今无人引用。
 *
 * <p><b>097：该码的台账</b>——全仓 22 个未接线码的逐条性质与理由记在 {@code com.crm.security.UnwiredPermissionCodeTest} 的
 * {@code LEDGER}（本码记在 B 类「全仓没有对应操作」）。 集合增或减该测试都红：多一个 = 又造了一个没人用的码，少一个 = 接线了却没更新台账。
 */
@RestController
@RequestMapping("/api/v1/quotes")
@Tag(name = "报价单")
public class QuoteController {

  private final QuoteService quoteService;
  private final QuotePdfService quotePdfService;

  public QuoteController(QuoteService quoteService, QuotePdfService quotePdfService) {
    this.quoteService = quoteService;
    this.quotePdfService = quotePdfService;
  }

  @GetMapping
  @RequirePermission("quote:read")
  @Operation(summary = "分页查询报价单列表（关键字/状态/客户筛选）")
  public ApiResponse<PageResult<QuoteResponse>> page(
      @RequestParam(required = false) String keyword,
      @RequestParam(required = false) String status,
      @RequestParam(required = false) Long customerId,
      @RequestParam(defaultValue = "1") long page,
      @RequestParam(defaultValue = "20") long pageSize) {
    return ApiResponse.ok(quoteService.page(keyword, status, customerId, page, pageSize));
  }

  @GetMapping("/{id}")
  @RequirePermission("quote:read")
  @Operation(summary = "报价单详情（含行明细）")
  public ApiResponse<QuoteResponse> detail(@PathVariable Long id) {
    return ApiResponse.ok(quoteService.detail(id));
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  @RequirePermission("quote:create")
  @Operation(summary = "创建报价单（草稿）")
  public ApiResponse<QuoteResponse> create(@Valid @RequestBody QuoteRequest request) {
    return ApiResponse.ok(quoteService.create(request));
  }

  @PutMapping("/{id}")
  @RequirePermission("quote:update")
  @Operation(summary = "编辑草稿/被拒报价单")
  public ApiResponse<QuoteResponse> update(
      @PathVariable Long id, @Valid @RequestBody QuoteRequest request) {
    return ApiResponse.ok(quoteService.update(id, request));
  }

  @PostMapping("/{id}/submit")
  @RequirePermission("quote:update")
  @Operation(summary = "提交审批")
  public ApiResponse<QuoteResponse> submit(@PathVariable Long id) {
    return ApiResponse.ok(quoteService.submit(id));
  }

  @PostMapping("/{id}/approve")
  @RequirePermission("quote:approve")
  @Operation(summary = "审批通过（仅管理员）")
  public ApiResponse<QuoteResponse> approve(@PathVariable Long id) {
    return ApiResponse.ok(quoteService.approve(id));
  }

  @PostMapping("/{id}/reject")
  @RequirePermission("quote:approve")
  @Operation(summary = "审批拒绝（仅管理员，需填写意见）")
  public ApiResponse<QuoteResponse> reject(
      @PathVariable Long id, @RequestBody(required = false) Map<String, String> body) {
    String reason = body == null ? null : body.get("reason");
    return ApiResponse.ok(quoteService.reject(id, reason));
  }

  /**
   * 导出报价单 PDF。
   *
   * <p>⚠️ <b>2026-09-17（100-rate-limit-consolidation）</b>：走 PDF 引擎<b>现算字节</b>，属 {@code
   * export-generate} 组 （10/60s、{@code USER} 维度——导出是「谁在导」不是「从哪导」）。注意它的权限码是 {@code
   * quote:read}：<b>读码不等于低成本</b>， 这一条正是「判定依据是响应类型 + 是否走 Excel/PDF 引擎，而不是 URI 前缀或方法名」的例子。
   */
  @GetMapping("/{id}/pdf")
  @RequirePermission("quote:read")
  @RateLimit(
      scope = "export-generate",
      limit = 10,
      windowSeconds = 60,
      by = RateLimitDimension.USER)
  @Operation(summary = "导出报价单 PDF")
  public ResponseEntity<byte[]> pdf(@PathVariable Long id) {
    QuoteResponse quote = quoteService.detail(id);
    byte[] bytes = quotePdfService.generate(quote);
    String filename = "quote-" + quote.getQuoteNo() + ".pdf";
    return ResponseEntity.ok()
        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
        .contentType(MediaType.APPLICATION_PDF)
        .body(bytes);
  }
}
