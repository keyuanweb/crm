package com.crm.controller;

import com.crm.common.ApiResponse;
import com.crm.common.PageResult;
import com.crm.dto.audit.AuditLogResponse;
import com.crm.security.RequirePermission;
import com.crm.service.AuditLogService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 审计日志查询接口（FR-017）。
 *
 * <p><b>1.5：类级 {@code @PreAuthorize("hasRole('ADMIN')")} 换成 {@code audit:view}</b>。可访问范围不变——
 * 「审计日志」菜单只有 ADMIN 持有，而 {@code audit:view} 至今无人被授（V46/V75/V80 都没有）——区别是这个码
 * 从此在角色页上勾得出来、勾了就真生效，而不是一个只能靠改代码才能打开的端点。审计日志是全局数据， 没有任何数据范围过滤，所以"谁能看"完全由这个码决定（{@code
 * AuditLogIT.nonAdminForbidden} 断言 SUPPORT 仍为 403，撤门不影响该断言：SUPPORT 不持有此码）。
 */
@RestController
@RequestMapping("/api/v1/audit-logs")
@Tag(name = "审计日志")
public class AuditLogController {

  private final AuditLogService auditLogService;

  public AuditLogController(AuditLogService auditLogService) {
    this.auditLogService = auditLogService;
  }

  @GetMapping
  @RequirePermission("audit:view")
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
