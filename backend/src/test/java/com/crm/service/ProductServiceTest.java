package com.crm.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.crm.common.BusinessException;
import com.crm.common.ErrorCode;
import com.crm.dto.product.ProductRequest;
import com.crm.entity.Product;
import com.crm.repository.ProductMapper;
import com.crm.security.SecurityUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

/** ProductService 单元测试（007 T013）：编码唯一/404/逻辑删除。 */
@ExtendWith(MockitoExtension.class)
class ProductServiceTest {

  private ProductMapper productMapper;
  private AuditService auditService;
  private ProductService service;
  private MockedStatic<SecurityUtil> securityUtilMock;

  @BeforeEach
  void setUp() {
    productMapper = mock(ProductMapper.class);
    auditService = mock(AuditService.class);
    service = new ProductService(productMapper, auditService);
    securityUtilMock = Mockito.mockStatic(SecurityUtil.class);
    securityUtilMock.when(SecurityUtil::currentUserId).thenReturn(1L);
  }

  @AfterEach
  void tearDown() {
    securityUtilMock.close();
  }

  private ProductRequest request(String code, String name) {
    ProductRequest req = new ProductRequest();
    req.setCode(code);
    req.setName(name);
    req.setStandardPrice(100000L);
    return req;
  }

  private Product product(Long id) {
    Product p = new Product();
    p.setId(id);
    p.setCode("CRM-STD");
    p.setName("CRM 标准版");
    p.setStandardPrice(100000L);
    p.setStatus("ACTIVE");
    p.setVersion(0);
    return p;
  }

  @Test
  @DisplayName("创建产品成功：默认状态 ACTIVE，记录审计")
  void createSucceeds() {
    when(productMapper.selectCount(any())).thenReturn(0L);
    when(productMapper.insert(any(Product.class)))
        .thenAnswer(
            invocation -> {
              Product p = invocation.getArgument(0);
              p.setId(1L);
              return 1;
            });

    var resp = service.create(request("CRM-STD", "CRM 标准版"));

    assertThat(resp.getId()).isEqualTo(1L);
    assertThat(resp.getStatus()).isEqualTo("ACTIVE");
    verify(productMapper).insert(any(Product.class));
    verify(auditService).record("CREATE", "PRODUCT", 1L, "创建产品：CRM 标准版");
  }

  @Test
  @DisplayName("创建产品：编码重复抛出 PRODUCT_DUPLICATE")
  void createDuplicateThrows() {
    when(productMapper.selectCount(any())).thenReturn(1L);

    assertThatThrownBy(() -> service.create(request("CRM-STD", "CRM 标准版")))
        .isInstanceOf(BusinessException.class)
        .extracting(e -> ((BusinessException) e).getErrorCode())
        .isEqualTo(ErrorCode.PRODUCT_DUPLICATE);
    verify(productMapper, never()).insert(any());
  }

  @Test
  @DisplayName("编辑产品：不存在抛出 PRODUCT_NOT_FOUND")
  void updateMissingThrows() {
    when(productMapper.selectById(99L)).thenReturn(null);

    assertThatThrownBy(() -> service.update(99L, request("CRM-STD", "CRM 标准版")))
        .isInstanceOf(BusinessException.class)
        .extracting(e -> ((BusinessException) e).getErrorCode())
        .isEqualTo(ErrorCode.PRODUCT_NOT_FOUND);
  }

  @Test
  @DisplayName("编辑产品成功：状态可更新，审计记录")
  void updateSucceeds() {
    when(productMapper.selectById(1L)).thenReturn(product(1L));
    when(productMapper.selectCount(any())).thenReturn(0L);
    when(productMapper.updateById(any(Product.class))).thenReturn(1);
    Product updated = product(1L);
    updated.setName("CRM 旗舰版");
    when(productMapper.selectById(1L)).thenReturn(updated);

    ProductRequest req = request("CRM-STD", "CRM 旗舰版");
    req.setStatus("INACTIVE");
    req.setVersion(0);
    var resp = service.update(1L, req);

    assertThat(resp.getName()).isEqualTo("CRM 旗舰版");
    assertThat(resp.getStatus()).isEqualTo("INACTIVE");
    verify(productMapper).updateById(any(Product.class));
    verify(auditService).record("UPDATE", "PRODUCT", 1L, "编辑产品：CRM 旗舰版");
  }

  @Test
  @DisplayName("删除产品成功：逻辑删除 + 审计")
  void deleteSucceeds() {
    when(productMapper.selectById(1L)).thenReturn(product(1L));

    service.delete(1L);

    verify(productMapper).deleteById(org.mockito.ArgumentMatchers.<Long>any());
    verify(auditService).record("DELETE", "PRODUCT", 1L, "逻辑删除产品：CRM 标准版");
  }
}
