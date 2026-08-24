package com.crm.controller;

import com.crm.common.ApiResponse;
import com.crm.common.PageResult;
import com.crm.dto.visit.CheckInRequest;
import com.crm.dto.visit.VisitRequest;
import com.crm.dto.visit.VisitResponse;
import com.crm.security.RequirePermission;
import com.crm.service.FieldVisitService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 外勤拜访接口（035-field-visit）。 */
@RestController
@RequestMapping("/api/v1/field-visits")
@Tag(name = "外勤拜访")
public class FieldVisitController {

  private final FieldVisitService visitService;

  public FieldVisitController(FieldVisitService visitService) {
    this.visitService = visitService;
  }

  @GetMapping
  @Operation(summary = "拜访列表")
  public ApiResponse<PageResult<VisitResponse>> list(
      @RequestParam(required = false) String status,
      @RequestParam(required = false) String month,
      @RequestParam(defaultValue = "1") long page,
      @RequestParam(defaultValue = "20") long pageSize) {
    return ApiResponse.ok(visitService.list(status, month, page, pageSize));
  }

  @PostMapping
  @RequirePermission("visit:manage")
  @Operation(summary = "创建拜访计划")
  public ApiResponse<VisitResponse> create(@RequestBody VisitRequest request) {
    return ApiResponse.ok(visitService.create(request));
  }

  @PutMapping("/{id}")
  @RequirePermission("visit:manage")
  @Operation(summary = "编辑拜访计划")
  public ApiResponse<VisitResponse> update(
      @PathVariable Long id, @RequestBody VisitRequest request) {
    return ApiResponse.ok(visitService.update(id, request));
  }

  @PostMapping("/{id}/cancel")
  @RequirePermission("visit:manage")
  @Operation(summary = "取消拜访计划")
  public ApiResponse<Void> cancel(@PathVariable Long id) {
    visitService.cancel(id);
    return ApiResponse.ok(null);
  }

  @PostMapping("/{id}/check-in")
  @RequirePermission("visit:manage")
  @Operation(summary = "拜访签到（防重复，小结转跟进）")
  public ApiResponse<VisitResponse> checkIn(
      @PathVariable Long id, @RequestBody(required = false) CheckInRequest request) {
    return ApiResponse.ok(
        visitService.checkIn(id, request == null ? new CheckInRequest() : request));
  }

  @GetMapping("/stats")
  @Operation(summary = "拜访统计（按销售/月份完成率）")
  public ApiResponse<Map<String, Object>> stats(@RequestParam(required = false) String month) {
    return ApiResponse.ok(visitService.stats(month));
  }
}
