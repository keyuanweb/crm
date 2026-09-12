package com.crm.controller;

import com.crm.common.ApiResponse;
import com.crm.common.PageResult;
import com.crm.dto.customobject.CustomObjectRequest;
import com.crm.dto.customobject.CustomObjectResponse;
import com.crm.dto.customobject.RecordRequest;
import com.crm.dto.customobject.RecordResponse;
import com.crm.security.RequirePermission;
import com.crm.service.CustomObjectRecordService;
import com.crm.service.CustomObjectService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
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

/**
 * 自定义对象接口（059）。
 *
 * <p><b>084：五个对象**定义**端点的 {@code hasRole('ADMIN')} 换成权限码</b>——{@code read / create / update /
 * delete}（{@code {id}/toggle} 是启停，属编辑，用 {@code update}）。原因：V75 把「自定义对象」菜单授给了 ANALYST（V80
 * 又把三个写码补进字典并授出），而本控制器整体是 ADMIN-only，于是 ANALYST 角色看得见菜单、 角色页也勾得上，点进去恒 403——「菜单已授却打不开」，正是 084 的 US1
 * 要根除的那类断链。批准人 龙星， 2026-09-12（FR-N24）。
 *
 * <p><b>{@code /{id}/records*} 五个端点不动</b>：那是对象的**记录**（业务数据）面，改造前就是 {@code
 * hasAnyRole('ADMIN','SALES')}，且靠数据范围过滤，不在 084 范围内。定义面是纯配置面，与 {@code CustomFieldController#page}
 * 同形，故用独立的读码而不是复用写码。
 */
@RestController
@RequestMapping("/api/v1/custom-objects")
@Tag(name = "自定义对象")
public class CustomObjectController {

  private final CustomObjectService objectService;
  private final CustomObjectRecordService recordService;

  public CustomObjectController(
      CustomObjectService objectService, CustomObjectRecordService recordService) {
    this.objectService = objectService;
    this.recordService = recordService;
  }

  // ===== 对象定义（按权限码；084 前是仅 ADMIN） =====

  @GetMapping
  @RequirePermission("custom_object:read")
  @Operation(summary = "对象列表")
  public ApiResponse<PageResult<CustomObjectResponse>> page(
      @RequestParam(required = false) String keyword,
      @RequestParam(defaultValue = "1") long page,
      @RequestParam(defaultValue = "20") long pageSize) {
    return ApiResponse.ok(objectService.page(keyword, page, pageSize));
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  @RequirePermission("custom_object:create")
  @Operation(summary = "创建对象")
  public ApiResponse<CustomObjectResponse> create(@Valid @RequestBody CustomObjectRequest request) {
    return ApiResponse.ok(objectService.create(request));
  }

  @PutMapping("/{id}")
  @RequirePermission("custom_object:update")
  @Operation(summary = "编辑对象")
  public ApiResponse<CustomObjectResponse> update(
      @PathVariable Long id, @Valid @RequestBody CustomObjectRequest request) {
    return ApiResponse.ok(objectService.update(id, request));
  }

  @PostMapping("/{id}/toggle")
  @RequirePermission("custom_object:update")
  @Operation(summary = "启停对象")
  public ApiResponse<CustomObjectResponse> toggle(@PathVariable Long id) {
    return ApiResponse.ok(objectService.toggle(id));
  }

  @DeleteMapping("/{id}")
  @RequirePermission("custom_object:delete")
  @Operation(summary = "删除对象（逻辑删除）")
  public ApiResponse<Void> delete(@PathVariable Long id) {
    objectService.delete(id);
    return ApiResponse.ok(null);
  }

  // ===== 记录管理（ADMIN + SALES） =====

  @GetMapping("/{id}/records")
  @PreAuthorize("hasAnyRole('ADMIN','SALES')")
  @Operation(summary = "对象记录列表（搜索按字段值）")
  public ApiResponse<PageResult<RecordResponse>> records(
      @PathVariable Long id,
      @RequestParam(required = false) String keyword,
      @RequestParam(defaultValue = "1") long page,
      @RequestParam(defaultValue = "20") long pageSize) {
    return ApiResponse.ok(recordService.page(id, keyword, page, pageSize));
  }

  @GetMapping("/{id}/records/{recordId}")
  @PreAuthorize("hasAnyRole('ADMIN','SALES')")
  @Operation(summary = "记录详情")
  public ApiResponse<RecordResponse> recordDetail(
      @PathVariable Long id, @PathVariable Long recordId) {
    return ApiResponse.ok(recordService.detail(id, recordId));
  }

  @PostMapping("/{id}/records")
  @ResponseStatus(HttpStatus.CREATED)
  @PreAuthorize("hasAnyRole('ADMIN','SALES')")
  @Operation(summary = "创建记录")
  public ApiResponse<RecordResponse> createRecord(
      @PathVariable Long id, @RequestBody RecordRequest request) {
    return ApiResponse.ok(recordService.create(id, request));
  }

  @PutMapping("/{id}/records/{recordId}")
  @PreAuthorize("hasAnyRole('ADMIN','SALES')")
  @Operation(summary = "编辑记录")
  public ApiResponse<RecordResponse> updateRecord(
      @PathVariable Long id, @PathVariable Long recordId, @RequestBody RecordRequest request) {
    return ApiResponse.ok(recordService.update(id, recordId, request));
  }

  @DeleteMapping("/{id}/records/{recordId}")
  @PreAuthorize("hasAnyRole('ADMIN','SALES')")
  @Operation(summary = "删除记录")
  public ApiResponse<Void> deleteRecord(@PathVariable Long id, @PathVariable Long recordId) {
    recordService.delete(id, recordId);
    return ApiResponse.ok(null);
  }
}
