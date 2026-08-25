package com.crm.controller;

import com.crm.common.ApiResponse;
import com.crm.common.PageResult;
import com.crm.dto.portal.PortalArticleResponse;
import com.crm.dto.portal.PortalTicketQueryRequest;
import com.crm.dto.portal.PortalTicketRequest;
import com.crm.dto.portal.PortalTicketResponse;
import com.crm.dto.portal.PortalTicketStatusResponse;
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

/** 客户自助门户接口（050，公开访问，FR-P01~P09）。 */
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
  @Operation(summary = "在线提交工单（公开，手机/邮箱识别客户）")
  public ApiResponse<PortalTicketResponse> submitTicket(
      @Valid @RequestBody PortalTicketRequest request) {
    return ApiResponse.ok(portalService.submitTicket(request));
  }

  @PostMapping("/tickets/status")
  @Operation(summary = "工单进度查询（公开，工单号+手机/邮箱双验证）")
  public ApiResponse<PortalTicketStatusResponse> ticketStatus(
      @Valid @RequestBody PortalTicketQueryRequest request) {
    return ApiResponse.ok(portalService.ticketStatus(request));
  }
}
