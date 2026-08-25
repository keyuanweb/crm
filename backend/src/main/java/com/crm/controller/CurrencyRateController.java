package com.crm.controller;

import com.crm.common.ApiResponse;
import com.crm.dto.currency.ConvertRequest;
import com.crm.dto.currency.CurrencyRateRequest;
import com.crm.dto.currency.CurrencyRateResponse;
import com.crm.service.CurrencyRateService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** 币种汇率接口（057）。 */
@RestController
@RequestMapping("/api/v1/currencies")
@Tag(name = "多币种")
public class CurrencyRateController {

  private final CurrencyRateService currencyService;

  public CurrencyRateController(CurrencyRateService currencyService) {
    this.currencyService = currencyService;
  }

  @GetMapping
  @PreAuthorize("hasAnyRole('ADMIN','SALES')")
  @Operation(summary = "币种列表")
  public ApiResponse<List<CurrencyRateResponse>> list() {
    return ApiResponse.ok(currencyService.list());
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  @PreAuthorize("hasRole('ADMIN')")
  @Operation(summary = "新增币种")
  public ApiResponse<CurrencyRateResponse> create(@Valid @RequestBody CurrencyRateRequest request) {
    return ApiResponse.ok(currencyService.create(request));
  }

  @PutMapping("/{id}")
  @PreAuthorize("hasRole('ADMIN')")
  @Operation(summary = "更新币种（基准不可改）")
  public ApiResponse<CurrencyRateResponse> update(
      @PathVariable Long id, @Valid @RequestBody CurrencyRateRequest request) {
    return ApiResponse.ok(currencyService.update(id, request));
  }

  @DeleteMapping("/{id}")
  @PreAuthorize("hasRole('ADMIN')")
  @Operation(summary = "删除币种（基准不可删）")
  public ApiResponse<Void> delete(@PathVariable Long id) {
    currencyService.delete(id);
    return ApiResponse.ok(null);
  }

  @PostMapping("/convert")
  @PreAuthorize("hasAnyRole('ADMIN','SALES')")
  @Operation(summary = "金额折算")
  public ApiResponse<Map<String, Object>> convert(@Valid @RequestBody ConvertRequest request) {
    long converted =
        currencyService.convert(
            request.getAmount(), request.getFromCurrency(), request.getToCurrency());
    Map<String, Object> resp = new LinkedHashMap<>();
    resp.put("amount", request.getAmount());
    resp.put("fromCurrency", request.getFromCurrency().toUpperCase());
    resp.put("toCurrency", request.getToCurrency().toUpperCase());
    resp.put("convertedAmount", converted);
    return ApiResponse.ok(resp);
  }
}
