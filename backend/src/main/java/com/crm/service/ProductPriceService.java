package com.crm.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.crm.common.BusinessException;
import com.crm.common.ErrorCode;
import com.crm.dto.currency.ProductPriceRequest;
import com.crm.entity.Product;
import com.crm.entity.ProductPrice;
import com.crm.repository.ProductMapper;
import com.crm.repository.ProductPriceMapper;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 产品多币种价格服务（057，FR-M04~M07）：CRUD/折算视图。 */
@Service
public class ProductPriceService {

  private final ProductPriceMapper priceMapper;
  private final ProductMapper productMapper;
  private final CurrencyRateService currencyRateService;

  public ProductPriceService(
      ProductPriceMapper priceMapper,
      ProductMapper productMapper,
      CurrencyRateService currencyRateService) {
    this.priceMapper = priceMapper;
    this.productMapper = productMapper;
    this.currencyRateService = currencyRateService;
  }

  /** 产品价格视图：基准价 + 各启用币种价（配置价优先，未配置按汇率折算）。 */
  public Map<String, Object> priceView(Long productId) {
    Product product = productMapper.selectById(productId);
    if (product == null) {
      throw new BusinessException(ErrorCode.BAD_REQUEST, "产品不存在");
    }
    Long basePrice = product.getStandardPrice() == null ? 0L : product.getStandardPrice();
    List<ProductPrice> configured =
        priceMapper.selectList(
            new LambdaQueryWrapper<ProductPrice>().eq(ProductPrice::getProductId, productId));
    Map<String, Long> configuredMap =
        configured.stream()
            .collect(
                Collectors.toMap(
                    ProductPrice::getCurrencyCode, ProductPrice::getPrice, (a, b) -> a));

    List<Map<String, Object>> prices =
        currencyRateService.list().stream()
            .filter(c -> !c.getIsBase())
            .filter(c -> Boolean.TRUE.equals(c.getEnabled()))
            .map(
                c -> {
                  Long price = configuredMap.get(c.getCode());
                  boolean isConfigured = price != null;
                  Long finalPrice =
                      isConfigured ? price : currencyRateService.fromBase(basePrice, c.getCode());
                  return Map.<String, Object>of(
                      "currencyCode",
                      c.getCode(),
                      "price",
                      finalPrice == null ? 0L : finalPrice,
                      "converted",
                      currencyRateService.fromBase(basePrice, c.getCode()),
                      "configured",
                      isConfigured);
                })
            .toList();

    return Map.of("productId", productId, "basePrice", basePrice, "prices", prices);
  }

  /** 设置产品币种价（upsert）。 */
  @Transactional
  public void setPrice(Long productId, ProductPriceRequest req) {
    if (productMapper.selectById(productId) == null) {
      throw new BusinessException(ErrorCode.BAD_REQUEST, "产品不存在");
    }
    String code = req.getCurrencyCode().trim().toUpperCase();
    // 校验币种存在
    currencyRateService.rateOf(code);
    ProductPrice existing =
        priceMapper.selectOne(
            new LambdaQueryWrapper<ProductPrice>()
                .eq(ProductPrice::getProductId, productId)
                .eq(ProductPrice::getCurrencyCode, code)
                .last("LIMIT 1"));
    if (existing == null) {
      ProductPrice pp = new ProductPrice();
      pp.setProductId(productId);
      pp.setCurrencyCode(code);
      pp.setPrice(req.getPrice());
      priceMapper.insert(pp);
    } else {
      existing.setPrice(req.getPrice());
      priceMapper.updateById(existing);
    }
  }

  /** 删除产品币种价（回退汇率折算）。 */
  @Transactional
  public void deletePrice(Long productId, String currencyCode) {
    priceMapper.delete(
        new LambdaQueryWrapper<ProductPrice>()
            .eq(ProductPrice::getProductId, productId)
            .eq(ProductPrice::getCurrencyCode, currencyCode.trim().toUpperCase()));
  }
}
