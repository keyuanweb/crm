package com.crm.controller;

import com.crm.common.ApiResponse;
import com.crm.common.PageResult;
import com.crm.dto.field.FieldPermissionRequest;
import com.crm.dto.field.FieldPermissionResponse;
import com.crm.security.RequirePermission;
import com.crm.service.FieldPermissionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 字段权限配置接口（056）。
 *
 * <p><b>1.5：类级 {@code @PreAuthorize("hasRole('ADMIN')")} 换成 {@code
 * field_permission:manage}</b>（字典里本就有 这个码，只是从未被任何端点引用）。可访问范围不变——「字段权限」菜单无人持有，该码也无人被授——但这个码
 * 从此勾得出来、勾了就生效。读也用它：字段权限配置全局唯一、无数据范围，且"能看"与"能配"分家只会造出 一个没有对应页面的读码。
 */
@RestController
@RequestMapping("/api/v1/field-permissions")
@Tag(name = "字段权限")
public class FieldPermissionController {

  private final FieldPermissionService permissionService;

  public FieldPermissionController(FieldPermissionService permissionService) {
    this.permissionService = permissionService;
  }

  @GetMapping
  @RequirePermission("field_permission:manage")
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
  @RequirePermission("field_permission:manage")
  @Operation(summary = "创建/更新字段权限（覆盖式 upsert）")
  public ApiResponse<FieldPermissionResponse> upsert(
      @Valid @RequestBody FieldPermissionRequest request) {
    return ApiResponse.ok(permissionService.upsert(request));
  }

  @DeleteMapping("/{id}")
  @RequirePermission("field_permission:manage")
  @Operation(summary = "删除字段权限配置")
  public ApiResponse<Void> delete(@PathVariable Long id) {
    permissionService.delete(id);
    return ApiResponse.ok(null);
  }
}
