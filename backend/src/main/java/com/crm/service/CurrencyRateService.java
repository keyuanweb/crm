package com.crm.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.crm.common.BusinessException;
import com.crm.common.ErrorCode;
import com.crm.common.PageResult;
import com.crm.dto.currency.CurrencyRateRequest;
import com.crm.dto.currency.CurrencyRateResponse;
import com.crm.entity.CurrencyRate;
import com.crm.repository.CurrencyRateMapper;
import com.crm.security.SecurityUtil;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 币种汇率服务（057，FR-M01~M07）：CRUD/基准保护/汇率缓存/折算。 */
@Service
public class CurrencyRateService {

  public static final String BASE_CODE = "CNY";

  private final CurrencyRateMapper currencyMapper;

  /** 汇率内存缓存：code → rate。 */
  private volatile Map<String, BigDecimal> rateCache = Map.of();

  public CurrencyRateService(CurrencyRateMapper currencyMapper) {
    this.currencyMapper = currencyMapper;
    reloadCache();
  }

  public List<CurrencyRateResponse> list() {
    return currencyMapper
        .selectList(
            new LambdaQueryWrapper<CurrencyRate>()
                .orderByDesc(CurrencyRate::getIsBase)
                .orderByAsc(CurrencyRate::getId))
        .stream()
        .map(this::toResponse)
        .toList();
  }

  @Transactional
  public CurrencyRateResponse create(CurrencyRateRequest req) {
    Long exists =
        currencyMapper.selectCount(
            new LambdaQueryWrapper<CurrencyRate>().eq(CurrencyRate::getCode, req.getCode().trim().toUpperCase()));
    if (exists != null && exists > 0) {
      throw new BusinessException(ErrorCode.CURRENCY_CODE_DUPLICATE);
    }
    validateRate(req.getRate());
    CurrencyRate c = new CurrencyRate();
    c.setCode(req.getCode().trim().toUpperCase());
    c.setName(req.getName().trim());
    c.setRate(req.getRate());
    // 首个币种或代码 CNY 自动标记基准
    Long total = currencyMapper.selectCount(null);
    boolean isBase = "CNY".equals(c.getCode()) || (total == null || total == 0);
    c.setIsBase(isBase ? 1 : 0);
    c.setEnabled(req.getEnabled() == null || req.getEnabled() ? 1 : 0);
    c.setCreatedBy(SecurityUtil.currentUserId());
    currencyMapper.insert(c);
    reloadCache();
    return toResponse(currencyMapper.selectById(c.getId()));
  }

  @Transactional
  public CurrencyRateResponse update(Long id, CurrencyRateRequest req) {
    CurrencyRate existing = require(id);
    if (existing.getIsBase() != null && existing.getIsBase() == 1) {
      throw new BusinessException(ErrorCode.CURRENCY_BASE_IMMUTABLE);
    }
    validateRate(req.getRate());
    existing.setName(req.getName().trim());
    existing.setRate(req.getRate());
    existing.setEnabled(req.getEnabled() == null || req.getEnabled() ? 1 : 0);
    existing.setVersion(req.getVersion());
    currencyMapper.updateById(existing);
    reloadCache();
    return toResponse(currencyMapper.selectById(id));
  }

  @Transactional
  public void delete(Long id) {
    CurrencyRate existing = require(id);
    if (existing.getIsBase() != null && existing.getIsBase() == 1) {
      throw new BusinessException(ErrorCode.CURRENCY_BASE_IMMUTABLE);
    }
    currencyMapper.deleteById(id);
    reloadCache();
  }

  /** 金额折算（分→分，四舍五入）。rate 语义：1 单位该币种 = rate 单位基准币种。 */
  public long convert(long amount, String fromCurrency, String toCurrency) {
    if (fromCurrency == null || toCurrency == null) {
      throw new BusinessException(ErrorCode.CURRENCY_NOT_FOUND);
    }
    BigDecimal fromRate = rateOf(fromCurrency.toUpperCase());
    BigDecimal toRate = rateOf(toCurrency.toUpperCase());
    // 目标金额 = amount × fromRate / toRate（先转基准再转目标）
    BigDecimal baseAmount = BigDecimal.valueOf(amount).multiply(fromRate);
    return baseAmount.divide(toRate, 0, RoundingMode.HALF_UP).longValue();
  }

  /** 基准金额（CNY 分）→ 目标币种金额（分）。 */
  public long fromBase(long baseAmount, String currencyCode) {
    return convert(baseAmount, BASE_CODE, currencyCode);
  }

  public BigDecimal rateOf(String code) {
    BigDecimal rate = rateCache.get(code);
    if (rate == null) {
      throw new BusinessException(ErrorCode.CURRENCY_NOT_FOUND);
    }
    return rate;
  }

  private void validateRate(BigDecimal rate) {
    if (rate == null || rate.compareTo(BigDecimal.ZERO) <= 0) {
      throw new BusinessException(ErrorCode.CURRENCY_RATE_INVALID);
    }
  }

  private CurrencyRate require(Long id) {
    CurrencyRate c = currencyMapper.selectById(id);
    if (c == null) {
      throw new BusinessException(ErrorCode.CURRENCY_NOT_FOUND);
    }
    return c;
  }

  private void reloadCache() {
    Map<String, BigDecimal> map = new ConcurrentHashMap<>();
    for (CurrencyRate c : currencyMapper.selectList(null)) {
      map.put(c.getCode(), c.getRate());
    }
    this.rateCache = map;
  }

  private CurrencyRateResponse toResponse(CurrencyRate c) {
    CurrencyRateResponse resp = new CurrencyRateResponse();
    resp.setId(c.getId());
    resp.setCode(c.getCode());
    resp.setName(c.getName());
    resp.setRate(c.getRate());
    resp.setIsBase(c.getIsBase() != null && c.getIsBase() == 1);
    resp.setEnabled(c.getEnabled() != null && c.getEnabled() == 1);
    resp.setVersion(c.getVersion());
    resp.setUpdatedAt(c.getUpdatedAt());
    return resp;
  }
}
