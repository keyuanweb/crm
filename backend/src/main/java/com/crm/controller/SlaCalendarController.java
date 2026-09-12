package com.crm.controller;

import com.crm.common.ApiResponse;
import com.crm.dto.sla.SlaCalendarConfigRequest;
import com.crm.dto.sla.SlaCalendarConfigResponse;
import com.crm.security.RequirePermission;
import com.crm.service.SlaCalendarService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * SLA 日历配置接口（054）。
 *
 * <p><b>1.5：类级 {@code @PreAuthorize("hasRole('ADMIN')")} 撤除</b>，改用 {@code sla:manage}。理由与 {@code
 * SlaPolicyController} 同：V75 把 {@code sla:manage} 与「SLA 日历」菜单都给了 SUPPORT_MANAGER， 而类级门让这张菜单指向一个恒
 * 403 的页面。日历是全局配置（无需数据范围过滤），持有该码即可读写。
 */
@RestController
@RequestMapping("/api/v1/sla-calendar")
@Tag(name = "SLA 日历")
public class SlaCalendarController {

  private final SlaCalendarService slaCalendarService;

  public SlaCalendarController(SlaCalendarService slaCalendarService) {
    this.slaCalendarService = slaCalendarService;
  }

  @GetMapping
  @RequirePermission("sla:manage")
  @Operation(summary = "获取 SLA 日历配置")
  public ApiResponse<SlaCalendarConfigResponse> get() {
    return ApiResponse.ok(slaCalendarService.get());
  }

  @PutMapping
  @RequirePermission("sla:manage")
  @Operation(summary = "更新 SLA 日历配置")
  public ApiResponse<SlaCalendarConfigResponse> update(
      @Valid @RequestBody SlaCalendarConfigRequest request) {
    return ApiResponse.ok(slaCalendarService.update(request));
  }
}
