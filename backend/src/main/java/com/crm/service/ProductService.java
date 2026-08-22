package com.crm.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.crm.common.BusinessException;
import com.crm.common.ErrorCode;
import com.crm.common.PageResult;
import com.crm.dto.product.ProductRequest;
import com.crm.dto.product.ProductResponse;
import com.crm.entity.Product;
import com.crm.repository.ProductMapper;
import com.crm.security.SecurityUtil;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/** 产品服务（007-product-cpq，FR-P01~P04）：CRUD/编码唯一/逻辑删除。 */
@Service
public class ProductService {

  private final ProductMapper productMapper;
  private final AuditService auditService;

  public ProductService(ProductMapper productMapper, AuditService auditService) {
    this.productMapper = productMapper;
    this.auditService = auditService;
  }

  public PageResult<ProductResponse> page(String keyword, String status, long page, long pageSize) {
    LambdaQueryWrapper<Product> qw = new LambdaQueryWrapper<>();
    if (StringUtils.hasText(keyword)) {
      String kw = keyword.trim();
      qw.and(w -> w.like(Product::getName, kw).or().like(Product::getCode, kw));
    }
    if (StringUtils.hasText(status)) {
      qw.eq(Product::getStatus, status.trim());
    }
    qw.orderByDesc(Product::getId);
    Page<Product> p = productMapper.selectPage(new Page<>(page, pageSize), qw);
    return PageResult.of(
        p.getRecords().stream().map(this::toResponse).toList(), p.getTotal(), page, pageSize);
  }

  public ProductResponse detail(Long id) {
    return toResponse(require(id));
  }

  @Transactional
  public ProductResponse create(ProductRequest req) {
    ensureUniqueCode(null, req.getCode());
    Product product = new Product();
    apply(req, product);
    product.setStatus(StringUtils.hasText(req.getStatus()) ? req.getStatus().trim() : "ACTIVE");
    product.setCreatedBy(SecurityUtil.currentUserId());
    productMapper.insert(product);
    auditService.record("CREATE", "PRODUCT", product.getId(), "创建产品：" + product.getName());
    return toResponse(product);
  }

  @Transactional
  public ProductResponse update(Long id, ProductRequest req) {
    Product existing = require(id);
    ensureUniqueCode(id, req.getCode());
    apply(req, existing);
    if (StringUtils.hasText(req.getStatus())) {
      existing.setStatus(req.getStatus().trim());
    }
    existing.setVersion(req.getVersion());
    int rows = productMapper.updateById(existing);
    if (rows == 0) {
      throw new BusinessException(ErrorCode.VERSION_CONFLICT);
    }
    auditService.record("UPDATE", "PRODUCT", id, "编辑产品：" + existing.getName());
    return toResponse(productMapper.selectById(id));
  }

  @Transactional
  public void delete(Long id) {
    Product product = require(id);
    productMapper.deleteById(id);
    auditService.record("DELETE", "PRODUCT", id, "逻辑删除产品：" + product.getName());
  }

  public Product require(Long id) {
    Product product = productMapper.selectById(id);
    if (product == null) {
      throw new BusinessException(ErrorCode.PRODUCT_NOT_FOUND);
    }
    return product;
  }

  private void ensureUniqueCode(Long excludeId, String code) {
    LambdaQueryWrapper<Product> qw =
        new LambdaQueryWrapper<Product>().eq(Product::getCode, code.trim());
    if (excludeId != null) {
      qw.ne(Product::getId, excludeId);
    }
    Long count = productMapper.selectCount(qw);
    if (count != null && count > 0) {
      throw new BusinessException(ErrorCode.PRODUCT_DUPLICATE);
    }
  }

  private void apply(ProductRequest req, Product product) {
    product.setCode(req.getCode().trim());
    product.setName(req.getName().trim());
    product.setSpec(trimToNull(req.getSpec()));
    product.setUnit(trimToNull(req.getUnit()));
    product.setStandardPrice(req.getStandardPrice() == null ? 0L : req.getStandardPrice());
  }

  private ProductResponse toResponse(Product product) {
    ProductResponse resp = new ProductResponse();
    resp.setId(product.getId());
    resp.setCode(product.getCode());
    resp.setName(product.getName());
    resp.setSpec(product.getSpec());
    resp.setUnit(product.getUnit());
    resp.setStandardPrice(product.getStandardPrice());
    resp.setStatus(product.getStatus());
    resp.setVersion(product.getVersion());
    resp.setCreatedAt(product.getCreatedAt());
    return resp;
  }

  private String trimToNull(String value) {
    return StringUtils.hasText(value) ? value.trim() : null;
  }
}
