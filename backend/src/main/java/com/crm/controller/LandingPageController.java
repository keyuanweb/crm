package com.crm.controller;

import com.crm.common.ApiResponse;
import com.crm.common.PageResult;
import com.crm.dto.landing.LandingPagePublicResponse;
import com.crm.dto.landing.LandingPageRequest;
import com.crm.dto.landing.LandingPageResponse;
import com.crm.dto.landing.UtmStatsResponse;
import com.crm.security.RequirePermission;
import com.crm.service.LandingPageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
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

/** 托管落地页接口（053）。 */
@RestController
@RequestMapping("/api/v1")
@Tag(name = "落地页")
public class LandingPageController {

  private final LandingPageService landingPageService;

  public LandingPageController(LandingPageService landingPageService) {
    this.landingPageService = landingPageService;
  }

  // ===== 管理（仅 ADMIN） =====

  @GetMapping("/landing-pages")
  @RequirePermission("marketing:manage")
  @Operation(summary = "落地页列表")
  public ApiResponse<PageResult<LandingPageResponse>> page(
      @RequestParam(required = false) String keyword,
      @RequestParam(defaultValue = "1") long page,
      @RequestParam(defaultValue = "20") long pageSize) {
    return ApiResponse.ok(landingPageService.page(keyword, page, pageSize));
  }

  @PostMapping("/landing-pages")
  @ResponseStatus(HttpStatus.CREATED)
  @RequirePermission("marketing:manage")
  @Operation(summary = "创建落地页")
  public ApiResponse<LandingPageResponse> create(
      @Valid @RequestBody LandingPageRequest request) {
    return ApiResponse.ok(landingPageService.create(request));
  }

  @PutMapping("/landing-pages/{id}")
  @RequirePermission("marketing:manage")
  @Operation(summary = "编辑落地页")
  public ApiResponse<LandingPageResponse> update(
      @PathVariable Long id, @Valid @RequestBody LandingPageRequest request) {
    return ApiResponse.ok(landingPageService.update(id, request));
  }

  @DeleteMapping("/landing-pages/{id}")
  @RequirePermission("marketing:manage")
  @Operation(summary = "删除落地页（逻辑删除）")
  public ApiResponse<Void> delete(@PathVariable Long id) {
    landingPageService.delete(id);
    return ApiResponse.ok(null);
  }

  @GetMapping("/landing-pages/{id}/stats")
  @RequirePermission("marketing:manage")
  @Operation(summary = "落地页 UTM 归因统计")
  public ApiResponse<UtmStatsResponse> stats(
      @PathVariable Long id,
      @RequestParam(required = false)
          @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
          LocalDate from,
      @RequestParam(required = false)
          @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
          LocalDate to) {
    LocalDateTime fromTime = from == null ? null : from.atStartOfDay();
    LocalDateTime toTime = to == null ? null : to.plusDays(1).atStartOfDay();
    return ApiResponse.ok(landingPageService.stats(id, fromTime, toTime));
  }

  // ===== 公开渲染（无需登录） =====

  @GetMapping("/public/lp/{id}")
  @Operation(summary = "落地页公开渲染（落地页 + 表单）")
  public ApiResponse<LandingPagePublicResponse> publicView(@PathVariable Long id) {
    return ApiResponse.ok(landingPageService.publicView(id));
  }
}
