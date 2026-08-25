package com.crm.controller;

import com.crm.common.ApiResponse;
import com.crm.dto.currency.ProductPriceRequest;
import com.crm.service.ProductPriceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** 产品多币种价格接口（057）。 */
@RestController
@RequestMapping("/api/v1/products")
@Tag(name = "多币种")
@PreAuthorize("hasAnyRole('ADMIN','SALES')")
public class ProductPriceController {

  private final ProductPriceService priceService;

  public ProductPriceController(ProductPriceService priceService) {
    this.priceService = priceService;
  }

  @GetMapping("/{id}/prices")
  @Operation(summary = "产品多币种价格视图")
  public ApiResponse<Map<String, Object>> prices(@PathVariable Long id) {
    return ApiResponse.ok(priceService.priceView(id));
  }

  @PostMapping("/{id}/prices")
  @ResponseStatus(HttpStatus.CREATED)
  @Operation(summary = "设置产品币种价（upsert）")
  public ApiResponse<Void> setPrice(
      @PathVariable Long id, @Valid @RequestBody ProductPriceRequest request) {
    priceService.setPrice(id, request);
    return ApiResponse.ok(null);
  }

  @DeleteMapping("/{id}/prices/{currencyCode}")
  @Operation(summary = "删除产品币种价（回退汇率折算）")
  public ApiResponse<Void> deletePrice(@PathVariable Long id, @PathVariable String currencyCode) {
    priceService.deletePrice(id, currencyCode);
    return ApiResponse.ok(null);
  }
}
