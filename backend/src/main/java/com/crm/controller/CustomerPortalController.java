package com.crm.controller;

import com.crm.common.ApiResponse;
import com.crm.common.PageResult;
import com.crm.dto.portal.PortalArticleResponse;
import com.crm.dto.portal.PortalTicketQueryRequest;
import com.crm.dto.portal.PortalTicketRequest;
import com.crm.dto.portal.PortalTicketResponse;
import com.crm.dto.portal.PortalTicketStatusResponse;
import com.crm.security.RateLimit;
import com.crm.security.RateLimitDimension;
import com.crm.service.CustomerPortalService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 客户自助门户接口（050，公开访问，FR-P01~P09）。
 *
 * <p>⚠️ <b>2026-09-17（100-rate-limit-consolidation）</b>：两个 <b>POST</b>（匿名建单 {@code #submitTicket}
 * 5/60s、匿名凭证校验 {@code #ticketStatus} 10/60s）各挂 {@link RateLimit}，维度取
 * <b>IP</b>（这两个端点匿名可达，没有更细的主体可用）。配额差异是有意的： {@code ticketStatus} 是<b>全仓唯一的匿名凭证校验端点</b>（工单号 +
 * 手机/邮箱双验证 ⇒ 枚举面），而 {@code submitTicket} 有<b>写副作用</b> （建单 + 写客户），故后者更紧。两个 <b>GET</b>（文章列表/详情）属
 * <b>P1</b> 的 {@code public-read} 组（同批的后续提交）。
 */
@RestController
@RequestMapping("/api/v1/public/portal")
@Tag(name = "客户门户")
public class CustomerPortalController {

  private final CustomerPortalService portalService;

  public CustomerPortalController(CustomerPortalService portalService) {
    this.portalService = portalService;
  }

  @GetMapping("/articles")
  @Operation(summary = "知识库文章列表（公开，仅已发布）")
  public ApiResponse<PageResult<PortalArticleResponse>> articles(
      @RequestParam(required = false) String keyword,
      @RequestParam(defaultValue = "1") long page,
      @RequestParam(defaultValue = "20") long pageSize) {
    return ApiResponse.ok(portalService.articles(keyword, page, pageSize));
  }

  @GetMapping("/articles/{id}")
  @Operation(summary = "知识库文章详情（公开，仅已发布）")
  public ApiResponse<PortalArticleResponse> article(@PathVariable Long id) {
    return ApiResponse.ok(portalService.article(id));
  }

  @PostMapping("/tickets")
  @ResponseStatus(HttpStatus.CREATED)
  @RateLimit(
      scope = "public-ticket-submit",
      limit = 5,
      windowSeconds = 60,
      by = RateLimitDimension.IP)
  @Operation(summary = "在线提交工单（公开，手机/邮箱识别客户）")
  public ApiResponse<PortalTicketResponse> submitTicket(
      @Valid @RequestBody PortalTicketRequest request) {
    return ApiResponse.ok(portalService.submitTicket(request));
  }

  @PostMapping("/tickets/status")
  @RateLimit(
      scope = "public-ticket-status",
      limit = 10,
      windowSeconds = 60,
      by = RateLimitDimension.IP)
  @Operation(summary = "工单进度查询（公开，工单号+手机/邮箱双验证）")
  public ApiResponse<PortalTicketStatusResponse> ticketStatus(
      @Valid @RequestBody PortalTicketQueryRequest request) {
    return ApiResponse.ok(portalService.ticketStatus(request));
  }
}
