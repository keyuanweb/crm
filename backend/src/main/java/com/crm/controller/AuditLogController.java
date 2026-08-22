package com.crm.controller;

import com.crm.common.ApiResponse;
import com.crm.common.PageResult;
import com.crm.dto.audit.AuditLogResponse;
import com.crm.service.AuditLogService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 审计日志查询接口（FR-017：仅管理员可查）。 */
@RestController
@RequestMapping("/api/v1/audit-logs")
@Tag(name = "审计日志")
@PreAuthorize("hasRole('ADMIN')")
public class AuditLogController {

  private final AuditLogService auditLogService;

  public AuditLogController(AuditLogService auditLogService) {
    this.auditLogService = auditLogService;
  }

  @GetMapping
  @Operation(summary = "分页查询审计日志（按动作/对象类型/操作人筛选）")
  public ApiResponse<PageResult<AuditLogResponse>> page(
      @RequestParam(required = false) String action,
      @RequestParam(required = false) String entityType,
      @RequestParam(required = false) String actorName,
      @RequestParam(defaultValue = "1") long page,
      @RequestParam(defaultValue = "20") long pageSize) {
    return ApiResponse.ok(auditLogService.page(action, entityType, actorName, page, pageSize));
  }
}
