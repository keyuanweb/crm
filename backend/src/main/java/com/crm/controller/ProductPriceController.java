package com.crm.controller;

import com.crm.common.ApiResponse;
import com.crm.dto.currency.ProductPriceRequest;
import com.crm.security.RequirePermission;
import com.crm.service.ProductPriceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 产品多币种价格接口（057）。
 *
 * <p><b>1.5：类级 {@code @PreAuthorize("hasAnyRole('ADMIN','SALES')")} 撤除。读不设码，写挂 {@code
 * product:update}。</b>
 *
 * <p>读（{@code GET /products/{id}/prices}）与 ProductController 的口径一致：产品目录是全局数据，没有归属维度，
 * 那边连类级门都没有，价格视图跟着不设码。
 *
 * <p>写挂 {@code product:update}——价格是产品目录的一部分，复用产品自己的编辑码比新造一个 {@code product_price:manage}
 * 更符合"矩阵已经承诺过的事"：V75 明确给 MARKETING_MANAGER 授了 {@code product:create/update/delete} 与「产品」菜单，而
 * ProductController 的三个写至今是方法级 {@code hasRole('ADMIN')}——**本类不去动它**（ProductController 不在批 2 的 16
 * 个控制器内，见 1.5 报告）， 所以"编辑产品本身"仍是 ADMIN 专属，而"定价"这一支先按矩阵接线。
 *
 * <p>授予范围（V83）= 改造前那道门放行的 ADMIN / SALES ∪ 持有「产品」菜单的角色（SALES_MANAGER / SALES_REP /
 * MARKETING_SPECIALIST；MARKETING_MANAGER 本就持有）。
 */
@RestController
@RequestMapping("/api/v1/products")
@Tag(name = "多币种")
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
  @RequirePermission("product:update")
  @Operation(summary = "设置产品币种价（upsert）")
  public ApiResponse<Void> setPrice(
      @PathVariable Long id, @Valid @RequestBody ProductPriceRequest request) {
    priceService.setPrice(id, request);
    return ApiResponse.ok(null);
  }

  @DeleteMapping("/{id}/prices/{currencyCode}")
  @RequirePermission("product:update")
  @Operation(summary = "删除产品币种价（回退汇率折算）")
  public ApiResponse<Void> deletePrice(@PathVariable Long id, @PathVariable String currencyCode) {
    priceService.deletePrice(id, currencyCode);
    return ApiResponse.ok(null);
  }
}
