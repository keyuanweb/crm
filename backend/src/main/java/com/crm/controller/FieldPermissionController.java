package com.crm.controller;

import com.crm.common.ApiResponse;
import com.crm.common.PageResult;
import com.crm.dto.field.FieldPermissionRequest;
import com.crm.dto.field.FieldPermissionResponse;
import com.crm.service.FieldPermissionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** 字段权限配置接口（056，仅 ADMIN）。 */
@RestController
@RequestMapping("/api/v1/field-permissions")
@Tag(name = "字段权限")
@PreAuthorize("hasRole('ADMIN')")
public class FieldPermissionController {

  private final FieldPermissionService permissionService;

  public FieldPermissionController(FieldPermissionService permissionService) {
    this.permissionService = permissionService;
  }

  @GetMapping
  @Operation(summary = "字段权限配置列表")
  public ApiResponse<PageResult<FieldPermissionResponse>> page(
      @RequestParam(required = false) String roleCode,
      @RequestParam(required = false) String entityType,
      @RequestParam(defaultValue = "1") long page,
      @RequestParam(defaultValue = "20") long pageSize) {
    return ApiResponse.ok(permissionService.page(roleCode, entityType, page, pageSize));
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  @Operation(summary = "创建/更新字段权限（覆盖式 upsert）")
  public ApiResponse<FieldPermissionResponse> upsert(
      @Valid @RequestBody FieldPermissionRequest request) {
    return ApiResponse.ok(permissionService.upsert(request));
  }

  @DeleteMapping("/{id}")
  @Operation(summary = "删除字段权限配置")
  public ApiResponse<Void> delete(@PathVariable Long id) {
    permissionService.delete(id);
    return ApiResponse.ok(null);
  }
}
