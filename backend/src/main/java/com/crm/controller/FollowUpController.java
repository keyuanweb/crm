package com.crm.controller;

import com.crm.common.ApiResponse;
import com.crm.common.PageResult;
import com.crm.dto.followup.FollowUpRequest;
import com.crm.dto.followup.FollowUpResponse;
import com.crm.service.FollowUpService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** 跟进记录接口（contracts/follow-ups.md，FR-015）。 */
@RestController
@PreAuthorize("hasAnyRole('ADMIN','SALES','SUPPORT')")
@RequestMapping("/api/v1/follow-ups")
@Tag(name = "跟进记录")
public class FollowUpController {

  private final FollowUpService followUpService;

  public FollowUpController(FollowUpService followUpService) {
    this.followUpService = followUpService;
  }

  @GetMapping
  @Operation(summary = "按客户/线索/商机查询跟进记录（时间线）")
  public ApiResponse<PageResult<FollowUpResponse>> page(
      @RequestParam(required = false) Long customerId,
      @RequestParam(required = false) Long leadId,
      @RequestParam(required = false) Long opportunityId,
      @RequestParam(defaultValue = "1") long page,
      @RequestParam(defaultValue = "20") long pageSize) {
    return ApiResponse.ok(followUpService.page(customerId, leadId, opportunityId, page, pageSize));
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  @Operation(summary = "添加跟进记录")
  public ApiResponse<FollowUpResponse> create(@Valid @RequestBody FollowUpRequest request) {
    return ApiResponse.ok(followUpService.create(request));
  }

  @PutMapping("/{id}")
  @Operation(summary = "编辑跟进记录（仅本人或管理员）")
  public ApiResponse<FollowUpResponse> update(
      @PathVariable Long id, @Valid @RequestBody FollowUpRequest request) {
    return ApiResponse.ok(followUpService.update(id, request));
  }
}
