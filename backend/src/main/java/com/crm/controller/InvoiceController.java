package com.crm.controller;

import com.crm.common.ApiResponse;
import com.crm.common.PageResult;
import com.crm.dto.invoice.InvoiceRequest;
import com.crm.dto.invoice.InvoiceResponse;
import com.crm.dto.invoice.InvoiceStatsResponse;
import com.crm.security.RequirePermission;
import com.crm.service.InvoiceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 发票接口（038-invoice）。 */
@RestController
@RequestMapping("/api/v1/invoices")
@Tag(name = "发票管理")
public class InvoiceController {

  private final InvoiceService invoiceService;

  public InvoiceController(InvoiceService invoiceService) {
    this.invoiceService = invoiceService;
  }

  @GetMapping
  @RequirePermission("invoice:manage")
  @Operation(summary = "发票列表")
  public ApiResponse<PageResult<InvoiceResponse>> list(
      @RequestParam(required = false) Long orderId,
      @RequestParam(required = false) String status,
      @RequestParam(required = false) String invoiceType,
      @RequestParam(defaultValue = "1") long page,
      @RequestParam(defaultValue = "20") long pageSize) {
    return ApiResponse.ok(invoiceService.list(orderId, status, invoiceType, page, pageSize));
  }

  @PostMapping
  @RequirePermission("invoice:manage")
  @Operation(summary = "开票")
  public ApiResponse<InvoiceResponse> create(@RequestBody InvoiceRequest request) {
    return ApiResponse.ok(invoiceService.create(request));
  }

  @PostMapping("/{id}/void")
  @RequirePermission("invoice:manage")
  @Operation(summary = "作废（需原因）")
  public ApiResponse<InvoiceResponse> voidInvoice(
      @PathVariable Long id, @RequestBody(required = false) Map<String, String> body) {
    return ApiResponse.ok(invoiceService.voidInvoice(id, body == null ? null : body.get("reason")));
  }

  @GetMapping("/stats")
  @RequirePermission("invoice:manage")
  @Operation(summary = "开票统计")
  public ApiResponse<InvoiceStatsResponse> stats() {
    return ApiResponse.ok(invoiceService.stats());
  }
}
