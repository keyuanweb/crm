package com.crm.controller;

import com.crm.common.ApiResponse;
import com.crm.dto.survey.SurveyRequest;
import com.crm.dto.survey.SurveyResponse;
import com.crm.dto.survey.SurveyStatsResponse;
import com.crm.service.TicketSurveyService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** 工单满意度接口（051，FR-S01~S08）。 */
@RestController
@RequestMapping("/api/v1")
@Tag(name = "工单满意度")
@PreAuthorize("hasAnyRole('ADMIN','SERVICE','SALES')")
public class TicketSurveyController {

  private final TicketSurveyService surveyService;

  public TicketSurveyController(TicketSurveyService surveyService) {
    this.surveyService = surveyService;
  }

  @PostMapping("/tickets/{id}/survey")
  @ResponseStatus(HttpStatus.CREATED)
  @Operation(summary = "提交工单满意度评分（仅 CLOSED 工单，一张一次）")
  public ApiResponse<SurveyResponse> submit(
      @PathVariable Long id, @Valid @RequestBody SurveyRequest request) {
    return ApiResponse.ok(surveyService.submit(id, request));
  }

  @GetMapping("/tickets/{id}/survey")
  @Operation(summary = "查询工单满意度评分")
  public ApiResponse<SurveyResponse> byTicket(@PathVariable Long id) {
    return ApiResponse.ok(surveyService.findByTicket(id));
  }

  @GetMapping("/surveys/stats")
  @Operation(summary = "满意度统计（CSAT 均值 + NPS 分布）")
  public ApiResponse<SurveyStatsResponse> stats(
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
    LocalDateTime fromTime = from == null ? null : from.atStartOfDay();
    LocalDateTime toTime = to == null ? null : to.plusDays(1).atStartOfDay();
    return ApiResponse.ok(surveyService.stats(fromTime, toTime));
  }
}
