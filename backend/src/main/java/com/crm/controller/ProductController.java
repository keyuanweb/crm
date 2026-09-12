package com.crm.controller;

import com.crm.common.ApiResponse;
import com.crm.common.PageResult;
import com.crm.dto.product.ProductRequest;
import com.crm.dto.product.ProductResponse;
import com.crm.security.RequirePermission;
import com.crm.service.ProductService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
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
 * 产品接口（007-product-cpq，FR-P01~P04）。
 *
 * <p><b>1.5 批 3：三条写操作的 {@code hasRole('ADMIN')} 换成 {@code product:*} 动作码，零补授。</b> 字典里
 * create/update/delete 三个码一直存在，此前只有 {@code product:update} 被定价端点（ProductPriceController，
 * V83）用过一次，产品**本身**的增删改则始终是角色字面量。接线后各按各的授予范围放行：
 *
 * <ul>
 *   <li>{@code product:create} / {@code product:delete} → ADMIN（切面直通）∪ MARKETING_MANAGER。后者是 V75
 *       里唯一持有这两个码的角色，接线只是把一条**早已存在的授权**变成真的。
 *   <li>{@code product:update} → 再加 SALES / SALES_MANAGER / SALES_REP / MARKETING_SPECIALIST（V83
 *       为产品定价 授给它们的），于是"产品"菜单的持有者真的点得动"编辑"。
 * </ul>
 *
 * <p><b>刻意不动读接口</b>：{@code GET} 列表与详情改造前就没有闸门（产品是共享目录，不属任何人的数据），
 * 给它设码是一次收窄——报价单/订单的产品选择器都要它，收窄只会把销售的表单打成空的。
 *
 * <p><b>一处已知的不一致（留作裁决，不在本批顺手改）</b>：create/delete 从未授给销售角色，于是持有「产品」菜单的 SALES_MANAGER / SALES_REP /
 * SALES 能编辑却不能新建/删除产品。这是矩阵的答案而非接线失误；若认为该扩， 正确做法是补授那两个码（按菜单扩），不是把这三个端点退回角色字面量。
 */
@RestController
@RequestMapping("/api/v1/products")
@Tag(name = "产品")
public class ProductController {

  private final ProductService productService;

  public ProductController(ProductService productService) {
    this.productService = productService;
  }

  @GetMapping
  @Operation(summary = "分页查询产品列表（关键字/状态筛选）")
  public ApiResponse<PageResult<ProductResponse>> page(
      @RequestParam(required = false) String keyword,
      @RequestParam(required = false) String status,
      @RequestParam(defaultValue = "1") long page,
      @RequestParam(defaultValue = "20") long pageSize) {
    return ApiResponse.ok(productService.page(keyword, status, page, pageSize));
  }

  @GetMapping("/{id}")
  @Operation(summary = "产品详情")
  public ApiResponse<ProductResponse> detail(@PathVariable Long id) {
    return ApiResponse.ok(productService.detail(id));
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  @RequirePermission("product:create")
  @Operation(summary = "创建产品")
  public ApiResponse<ProductResponse> create(@Valid @RequestBody ProductRequest request) {
    return ApiResponse.ok(productService.create(request));
  }

  @PutMapping("/{id}")
  @RequirePermission("product:update")
  @Operation(summary = "编辑产品")
  public ApiResponse<ProductResponse> update(
      @PathVariable Long id, @Valid @RequestBody ProductRequest request) {
    return ApiResponse.ok(productService.update(id, request));
  }

  @DeleteMapping("/{id}")
  @RequirePermission("product:delete")
  @Operation(summary = "逻辑删除产品")
  public ApiResponse<Void> delete(@PathVariable Long id) {
    productService.delete(id);
    return ApiResponse.ok();
  }
}
