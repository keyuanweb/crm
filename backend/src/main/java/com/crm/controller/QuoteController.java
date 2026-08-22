package com.crm.controller;

import com.crm.common.ApiResponse;
import com.crm.common.PageResult;
import com.crm.dto.quote.QuoteRequest;
import com.crm.dto.quote.QuoteResponse;
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
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** 报价单接口（007-product-cpq，FR-P05~P11）。 */
@RestController
@RequestMapping("/api/v1/quotes")
@Tag(name = "报价单")
@PreAuthorize("hasAnyRole('ADMIN','SALES')")
public class QuoteController {

  private final QuoteService quoteService;
  private final QuotePdfService quotePdfService;

  public QuoteController(QuoteService quoteService, QuotePdfService quotePdfService) {
    this.quoteService = quoteService;
    this.quotePdfService = quotePdfService;
  }

  @GetMapping
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
  @Operation(summary = "报价单详情（含行明细）")
  public ApiResponse<QuoteResponse> detail(@PathVariable Long id) {
    return ApiResponse.ok(quoteService.detail(id));
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  @Operation(summary = "创建报价单（草稿）")
  public ApiResponse<QuoteResponse> create(@Valid @RequestBody QuoteRequest request) {
    return ApiResponse.ok(quoteService.create(request));
  }

  @PutMapping("/{id}")
  @Operation(summary = "编辑草稿/被拒报价单")
  public ApiResponse<QuoteResponse> update(
      @PathVariable Long id, @Valid @RequestBody QuoteRequest request) {
    return ApiResponse.ok(quoteService.update(id, request));
  }

  @PostMapping("/{id}/submit")
  @Operation(summary = "提交审批")
  public ApiResponse<QuoteResponse> submit(@PathVariable Long id) {
    return ApiResponse.ok(quoteService.submit(id));
  }

  @PostMapping("/{id}/approve")
  @PreAuthorize("hasRole('ADMIN')")
  @Operation(summary = "审批通过（仅管理员）")
  public ApiResponse<QuoteResponse> approve(@PathVariable Long id) {
    return ApiResponse.ok(quoteService.approve(id));
  }

  @PostMapping("/{id}/reject")
  @PreAuthorize("hasRole('ADMIN')")
  @Operation(summary = "审批拒绝（仅管理员，需填写意见）")
  public ApiResponse<QuoteResponse> reject(
      @PathVariable Long id, @RequestBody(required = false) Map<String, String> body) {
    String reason = body == null ? null : body.get("reason");
    return ApiResponse.ok(quoteService.reject(id, reason));
  }

  @GetMapping("/{id}/pdf")
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
