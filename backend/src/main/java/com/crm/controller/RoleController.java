package com.crm.controller;

import com.crm.common.ApiResponse;
import com.crm.common.PageResult;
import com.crm.dto.role.RoleOption;
import com.crm.dto.role.RoleRequest;
import com.crm.dto.role.RoleResponse;
import com.crm.security.RequirePermission;
import com.crm.service.RoleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 角色权限接口（028-role-permissions，contracts/role-permissions.md，ADMIN）。 */
@RestController
@RequestMapping("/api/v1/roles")
@Tag(name = "角色权限")
public class RoleController {

  private final RoleService roleService;

  public RoleController(RoleService roleService) {
    this.roleService = roleService;
  }

  @GetMapping
  @RequirePermission("role:manage")
  @Operation(summary = "角色列表（含菜单/权限）")
  public ApiResponse<PageResult<RoleResponse>> list(
      @RequestParam(defaultValue = "1") long page,
      @RequestParam(defaultValue = "20") long pageSize) {
    return ApiResponse.ok(roleService.list(page, pageSize));
  }

  @PostMapping
  @RequirePermission("role:manage")
  @Operation(summary = "创建角色")
  public ApiResponse<RoleResponse> create(@RequestBody RoleRequest request) {
    return ApiResponse.ok(roleService.create(request));
  }

  @PutMapping("/{id}")
  @RequirePermission("role:manage")
  @Operation(summary = "编辑角色（含菜单/权限配置）")
  public ApiResponse<RoleResponse> update(@PathVariable Long id, @RequestBody RoleRequest request) {
    return ApiResponse.ok(roleService.update(id, request));
  }

  @DeleteMapping("/{id}")
  @RequirePermission("role:manage")
  @Operation(summary = "删除角色（内建/被引用拒绝）")
  public ApiResponse<Void> delete(@PathVariable Long id) {
    roleService.delete(id);
    return ApiResponse.ok(null);
  }

  @GetMapping("/options")
  @Operation(summary = "角色下拉选项（启用角色，用户管理用）")
  public ApiResponse<List<RoleOption>> options() {
    return ApiResponse.ok(roleService.options());
  }

  @GetMapping("/menu-tree")
  @RequirePermission("role:manage")
  @Operation(summary = "菜单树字典（配置页勾选）")
  public ApiResponse<List<Map<String, Object>>> menuTree() {
    return ApiResponse.ok(roleService.menuTree());
  }

  @GetMapping("/permission-defs")
  @RequirePermission("role:manage")
  @Operation(summary = "操作权限点字典（配置页勾选）")
  public ApiResponse<List<Map<String, Object>>> permissionDefs() {
    return ApiResponse.ok(roleService.permissionDefs());
  }
}
