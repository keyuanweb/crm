package com.crm.controller;

import com.crm.common.ApiResponse;
import com.crm.dto.department.DepartmentRequest;
import com.crm.dto.department.DepartmentResponse;
import com.crm.dto.department.DepartmentTreeResponse;
import com.crm.service.DepartmentService;
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
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** 部门接口（012，FR-DP01/DP02，仅 ADMIN）。 */
@RestController
@RequestMapping("/api/v1/departments")
@Tag(name = "部门")
@PreAuthorize("hasRole('ADMIN')")
public class DepartmentController {

  private final DepartmentService departmentService;

  public DepartmentController(DepartmentService departmentService) {
    this.departmentService = departmentService;
  }

  @GetMapping("/tree")
  @Operation(summary = "部门树")
  public ApiResponse<DepartmentTreeResponse> tree() {
    return ApiResponse.ok(departmentService.tree());
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  @Operation(summary = "创建部门")
  public ApiResponse<DepartmentResponse> create(@Valid @RequestBody DepartmentRequest request) {
    return ApiResponse.ok(departmentService.create(request));
  }

  @PutMapping("/{id}")
  @Operation(summary = "编辑部门")
  public ApiResponse<DepartmentResponse> update(
      @PathVariable Long id, @Valid @RequestBody DepartmentRequest request) {
    return ApiResponse.ok(departmentService.update(id, request));
  }

  @DeleteMapping("/{id}")
  @Operation(summary = "删除部门（有子部门或成员时拒绝）")
  public ApiResponse<Void> delete(@PathVariable Long id) {
    departmentService.delete(id);
    return ApiResponse.ok();
  }
}
