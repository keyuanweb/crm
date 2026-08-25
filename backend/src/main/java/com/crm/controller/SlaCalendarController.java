package com.crm.controller;

import com.crm.common.ApiResponse;
import com.crm.dto.sla.SlaCalendarConfigRequest;
import com.crm.dto.sla.SlaCalendarConfigResponse;
import com.crm.service.SlaCalendarService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** SLA 日历配置接口（054，仅 ADMIN）。 */
@RestController
@RequestMapping("/api/v1/sla-calendar")
@Tag(name = "SLA 日历")
@PreAuthorize("hasRole('ADMIN')")
public class SlaCalendarController {

  private final SlaCalendarService slaCalendarService;

  public SlaCalendarController(SlaCalendarService slaCalendarService) {
    this.slaCalendarService = slaCalendarService;
  }

  @GetMapping
  @Operation(summary = "获取 SLA 日历配置")
  public ApiResponse<SlaCalendarConfigResponse> get() {
    return ApiResponse.ok(slaCalendarService.get());
  }

  @PutMapping
  @Operation(summary = "更新 SLA 日历配置")
  public ApiResponse<SlaCalendarConfigResponse> update(
      @Valid @RequestBody SlaCalendarConfigRequest request) {
    return ApiResponse.ok(slaCalendarService.update(request));
  }
}
