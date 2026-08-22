package com.crm.controller;

import com.crm.common.ApiResponse;
import com.crm.common.PageResult;
import com.crm.dto.customfield.CustomFieldRequest;
import com.crm.dto.customfield.CustomFieldResponse;
import com.crm.service.CustomFieldService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
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

/** 自定义字段接口（016，FR-S01，仅 ADMIN 配置）。 */
@RestController
@RequestMapping("/api/v1/custom-fields")
@Tag(name = "系统增强")
@PreAuthorize("hasRole('ADMIN')")
public class CustomFieldController {

  private final CustomFieldService customFieldService;

  public CustomFieldController(CustomFieldService customFieldService) {
    this.customFieldService = customFieldService;
  }

  @GetMapping
  @Operation(summary = "字段定义分页列表（按实体筛选）")
  public ApiResponse<PageResult<CustomFieldResponse>> page(
      @RequestParam(required = false) String entityType,
      @RequestParam(defaultValue = "1") long page,
      @RequestParam(defaultValue = "20") long pageSize) {
    return ApiResponse.ok(customFieldService.page(entityType, page, pageSize));
  }

  @GetMapping("/definitions")
  @Operation(summary = "某实体启用的字段定义（业务用户读取，供表单渲染）")
  @PreAuthorize("hasAnyRole('ADMIN','SALES','SUPPORT')")
  public ApiResponse<List<CustomFieldResponse>> definitions(@RequestParam String entityType) {
    return ApiResponse.ok(customFieldService.listByEntity(entityType));
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  @Operation(summary = "创建字段定义")
  public ApiResponse<CustomFieldResponse> create(@Valid @RequestBody CustomFieldRequest request) {
    return ApiResponse.ok(customFieldService.create(request));
  }

  @PutMapping("/{id}")
  @Operation(summary = "编辑字段定义")
  public ApiResponse<CustomFieldResponse> update(
      @PathVariable Long id, @Valid @RequestBody CustomFieldRequest request) {
    return ApiResponse.ok(customFieldService.update(id, request));
  }

  @DeleteMapping("/{id}")
  @Operation(summary = "删除字段定义（物理清理关联值）")
  public ApiResponse<Void> delete(@PathVariable Long id) {
    customFieldService.delete(id);
    return ApiResponse.ok();
  }
}
