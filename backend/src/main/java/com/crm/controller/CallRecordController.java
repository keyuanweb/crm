package com.crm.controller;

import com.crm.common.ApiResponse;
import com.crm.common.PageResult;
import com.crm.dto.call.CallRecordRequest;
import com.crm.dto.call.CallRecordResponse;
import com.crm.dto.call.CallStatsResponse;
import com.crm.service.CallRecordService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
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

/** 通话记录接口（061）。 */
@RestController
@RequestMapping("/api/v1/call-records")
@Tag(name = "通话记录")
@PreAuthorize("hasAnyRole('ADMIN','SALES','SUPPORT')")
public class CallRecordController {

  private final CallRecordService recordService;

  public CallRecordController(CallRecordService recordService) {
    this.recordService = recordService;
  }

  @GetMapping
  @Operation(summary = "通话记录列表")
  public ApiResponse<PageResult<CallRecordResponse>> page(
      @RequestParam(required = false) String keyword,
      @RequestParam(required = false) String direction,
      @RequestParam(required = false) Long customerId,
      @RequestParam(required = false)
          @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
          LocalDate from,
      @RequestParam(required = false)
          @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
          LocalDate to,
      @RequestParam(defaultValue = "1") long page,
      @RequestParam(defaultValue = "20") long pageSize) {
    LocalDateTime fromTime = from == null ? null : from.atStartOfDay();
    LocalDateTime toTime = to == null ? null : to.plusDays(1).atStartOfDay();
    return ApiResponse.ok(
        recordService.page(keyword, direction, customerId, fromTime, toTime, page, pageSize));
  }

  @GetMapping("/{id}")
  @Operation(summary = "通话记录详情")
  public ApiResponse<CallRecordResponse> detail(@PathVariable Long id) {
    return ApiResponse.ok(recordService.detail(id));
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  @Operation(summary = "录入通话记录（CTI 自动创建也走此端点）")
  public ApiResponse<CallRecordResponse> create(@Valid @RequestBody CallRecordRequest request) {
    return ApiResponse.ok(recordService.create(request));
  }

  @PutMapping("/{id}")
  @Operation(summary = "编辑通话记录")
  public ApiResponse<CallRecordResponse> update(
      @PathVariable Long id, @Valid @RequestBody CallRecordRequest request) {
    return ApiResponse.ok(recordService.update(id, request));
  }

  @DeleteMapping("/{id}")
  @Operation(summary = "删除通话记录")
  public ApiResponse<Void> delete(@PathVariable Long id) {
    recordService.delete(id);
    return ApiResponse.ok(null);
  }

  @GetMapping("/stats")
  @Operation(summary = "通话统计")
  public ApiResponse<CallStatsResponse> stats(
      @RequestParam(required = false)
          @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
          LocalDate from,
      @RequestParam(required = false)
          @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
          LocalDate to,
      @RequestParam(required = false) String direction) {
    LocalDateTime fromTime = from == null ? null : from.atStartOfDay();
    LocalDateTime toTime = to == null ? null : to.plusDays(1).atStartOfDay();
    return ApiResponse.ok(recordService.stats(fromTime, toTime, direction));
  }
}
