package com.crm.controller;

import com.crm.common.ApiResponse;
import com.crm.dto.survey.SurveyRequest;
import com.crm.dto.survey.SurveyResponse;
import com.crm.dto.survey.SurveyStatsResponse;
import com.crm.security.RequirePermission;
import com.crm.service.TicketSurveyService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 工单满意度接口（051，FR-S01~S08）。
 *
 * <p><b>1.5：类级 {@code @PreAuthorize("hasAnyRole('ADMIN','SERVICE','SALES')")} 已移除。</b>那个门里写的 {@code
 * 'SERVICE'} <b>根本不是本项目的角色名</b>（角色是 SUPPORT、SUPPORT_MANAGER、SUPPORT_AGENT）， 于是这道门实际只放行 ADMIN 与
 * SALES——满意度是客服职能，真正的客服角色反而被自己的功能挡在门外。 「角色名拼错」这类错配不会被任何"注解 ⊆ 字典"的测试抓到，因为没有注解——只有把门换成权限码才会。
 *
 * <p>改用 {@code ticket:*}：满意度是工单的子资源，字典里没有 survey 码，也不该为它新造一族 （同 {@code ContactController} 把导入复用
 * contact:create 的口径）。读挂 {@code ticket:read}、 提交评分挂 {@code ticket:update}。
 */
@RestController
@RequestMapping("/api/v1")
@Tag(name = "工单满意度")
public class TicketSurveyController {

  private final TicketSurveyService surveyService;

  public TicketSurveyController(TicketSurveyService surveyService) {
    this.surveyService = surveyService;
  }

  @PostMapping("/tickets/{id}/survey")
  @ResponseStatus(HttpStatus.CREATED)
  @RequirePermission("ticket:update")
  @Operation(summary = "提交工单满意度评分（仅 CLOSED 工单，一张一次）")
  public ApiResponse<SurveyResponse> submit(
      @PathVariable Long id, @Valid @RequestBody SurveyRequest request) {
    return ApiResponse.ok(surveyService.submit(id, request));
  }

  @GetMapping("/tickets/{id}/survey")
  @RequirePermission("ticket:read")
  @Operation(summary = "查询工单满意度评分")
  public ApiResponse<SurveyResponse> byTicket(@PathVariable Long id) {
    return ApiResponse.ok(surveyService.findByTicket(id));
  }

  @GetMapping("/surveys/stats")
  @RequirePermission("ticket:read")
  @Operation(summary = "满意度统计（CSAT 均值 + NPS 分布）")
  public ApiResponse<SurveyStatsResponse> stats(
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
    LocalDateTime fromTime = from == null ? null : from.atStartOfDay();
    LocalDateTime toTime = to == null ? null : to.plusDays(1).atStartOfDay();
    return ApiResponse.ok(surveyService.stats(fromTime, toTime));
  }
}
