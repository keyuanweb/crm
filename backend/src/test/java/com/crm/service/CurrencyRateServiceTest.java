package com.crm.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.crm.common.BusinessException;
import com.crm.common.ErrorCode;
import com.crm.dto.currency.CurrencyRateRequest;
import com.crm.entity.CurrencyRate;
import com.crm.repository.CurrencyRateMapper;
import com.crm.security.JwtAuthFilter.CrmPrincipal;
import com.crm.security.SecurityUtil;
import java.math.BigDecimal;
import java.util.List;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

/** CurrencyRateService 单元测试（057 T008）：CRUD/基准保护/折算。 */
class CurrencyRateServiceTest {

  private CurrencyRateMapper currencyMapper;
  private CurrencyRateService service;
  private MockedStatic<SecurityUtil> securityUtilMock;

  @BeforeAll
  static void initTableInfo() {
    MybatisConfiguration configuration = new MybatisConfiguration();
    MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, "");
    TableInfoHelper.initTableInfo(assistant, CurrencyRate.class);
  }

  @BeforeEach
  void setUp() {
    currencyMapper = mock(CurrencyRateMapper.class);
    // 构造时 reloadCache：stub selectList 返回 CNY
    when(currencyMapper.selectList(any())).thenReturn(List.of(baseCny()));
    service = new CurrencyRateService(currencyMapper);
    securityUtilMock = Mockito.mockStatic(SecurityUtil.class);
    securityUtilMock
        .when(SecurityUtil::currentPrincipal)
        .thenReturn(new CrmPrincipal(1L, "admin", "ADMIN"));
    securityUtilMock.when(SecurityUtil::currentUserId).thenReturn(1L);
  }

  @AfterEach
  void tearDown() {
    securityUtilMock.close();
  }

  private CurrencyRate baseCny() {
    CurrencyRate c = new CurrencyRate();
    c.setId(1L);
    c.setCode("CNY");
    c.setName("人民币");
    c.setRate(BigDecimal.ONE);
    c.setIsBase(1);
    c.setEnabled(1);
    c.setVersion(0);
    return c;
  }

  private CurrencyRate usd() {
    CurrencyRate c = new CurrencyRate();
    c.setId(2L);
    c.setCode("USD");
    c.setName("美元");
    c.setRate(new BigDecimal("7.2"));
    c.setIsBase(0);
    c.setEnabled(1);
    c.setVersion(0);
    return c;
  }

  @Test
  @DisplayName("折算：100000 分 CNY → USD（7.2）≈ 13889")
  void convertCnyToUsd() {
    when(currencyMapper.selectList(any())).thenReturn(List.of(baseCny(), usd()));
    service = new CurrencyRateService(currencyMapper); // 重新加载缓存

    long result = service.convert(100000, "CNY", "USD");

    assertThat(result).isEqualTo(13889L); // 100000 / 7.2 = 13888.88 → 13889
  }

  @Test
  @DisplayName("折算：USD → CNY 反向")
  void convertUsdToCny() {
    when(currencyMapper.selectList(any())).thenReturn(List.of(baseCny(), usd()));
    service = new CurrencyRateService(currencyMapper);

    long result = service.convert(7200, "USD", "CNY");

    assertThat(result).isEqualTo(51840L); // 7200 × 7.2 / 1 = 51840
  }

  @Test
  @DisplayName("创建：重复代码 → 409")
  void createDuplicateThrows() {
    when(currencyMapper.selectCount(any())).thenReturn(1L);
    CurrencyRateRequest req = new CurrencyRateRequest();
    req.setCode("USD");
    req.setName("美元");
    req.setRate(new BigDecimal("7.2"));

    assertThatThrownBy(() -> service.create(req))
        .isInstanceOf(BusinessException.class)
        .extracting(e -> ((BusinessException) e).getErrorCode())
        .isEqualTo(ErrorCode.CURRENCY_CODE_DUPLICATE);
  }

  @Test
  @DisplayName("更新基准币种 → 422")
  void updateBaseThrows() {
    when(currencyMapper.selectById(1L)).thenReturn(baseCny());
    CurrencyRateRequest req = new CurrencyRateRequest();
    req.setCode("CNY");
    req.setName("人民币");
    req.setRate(BigDecimal.ONE);

    assertThatThrownBy(() -> service.update(1L, req))
        .isInstanceOf(BusinessException.class)
        .extracting(e -> ((BusinessException) e).getErrorCode())
        .isEqualTo(ErrorCode.CURRENCY_BASE_IMMUTABLE);
  }

  @Test
  @DisplayName("汇率非法（≤0）→ 422")
  void invalidRateThrows() {
    CurrencyRateRequest req = new CurrencyRateRequest();
    req.setCode("USD");
    req.setName("美元");
    req.setRate(BigDecimal.ZERO);

    assertThatThrownBy(() -> service.create(req))
        .isInstanceOf(BusinessException.class)
        .extracting(e -> ((BusinessException) e).getErrorCode())
        .isEqualTo(ErrorCode.CURRENCY_RATE_INVALID);
  }
}
