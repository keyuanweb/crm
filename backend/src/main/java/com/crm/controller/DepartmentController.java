package com.crm.controller;

import com.crm.common.ApiResponse;
import com.crm.dto.department.DepartmentRequest;
import com.crm.dto.department.DepartmentResponse;
import com.crm.dto.department.DepartmentTreeResponse;
import com.crm.security.RequirePermission;
import com.crm.service.DepartmentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 部门接口（012，FR-DP01/DP02）。
 *
 * <p><b>1.5：类级 {@code @PreAuthorize("hasRole('ADMIN')")} 换成 {@code department:manage}，并把这个码授给持有
 * 「部门管理」菜单的五个角色。</b>这是本项里**唯一一处有实质扩权**的改动，说明如下：
 *
 * <p>「部门管理」菜单由 V46/V75 授给了 ADMIN + 五个管理角色（SALES_MANAGER / SUPPORT_MANAGER / MARKETING_MANAGER /
 * FINANCE_MANAGER 与 ADMIN），而这个集合与持有 {@code user:manage}、{@code role:manage}
 * 的角色**完全相同**——也就是说这五个角色本来就是各自组织单元的管理员（用户管理、角色权限两页都已授给 它们），部门管理是同一件事的第三块拼图；但本类是
 * ADMIN-only，于是它们看到菜单、点进去恒 403。 撤门 + 补授即"矩阵说能就能"。
 *
 * <p>如果评审认为"部门管理不该交给这五个角色"，**正确的收敛方向是收回它们的「部门管理」菜单**，而不是让 菜单继续指向一个 403
 * 页面——两者只能选一个，不能都不做（现状就是两者都没做）。
 */
@RestController
@RequestMapping("/api/v1/departments")
@Tag(name = "部门")
public class DepartmentController {

  private final DepartmentService departmentService;

  public DepartmentController(DepartmentService departmentService) {
    this.departmentService = departmentService;
  }

  @GetMapping("/tree")
  @RequirePermission("department:manage")
  @Operation(summary = "部门树")
  public ApiResponse<DepartmentTreeResponse> tree() {
    return ApiResponse.ok(departmentService.tree());
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  @RequirePermission("department:manage")
  @Operation(summary = "创建部门")
  public ApiResponse<DepartmentResponse> create(@Valid @RequestBody DepartmentRequest request) {
    return ApiResponse.ok(departmentService.create(request));
  }

  @PutMapping("/{id}")
  @RequirePermission("department:manage")
  @Operation(summary = "编辑部门")
  public ApiResponse<DepartmentResponse> update(
      @PathVariable Long id, @Valid @RequestBody DepartmentRequest request) {
    return ApiResponse.ok(departmentService.update(id, request));
  }

  @DeleteMapping("/{id}")
  @RequirePermission("department:manage")
  @Operation(summary = "删除部门（有子部门或成员时拒绝）")
  public ApiResponse<Void> delete(@PathVariable Long id) {
    departmentService.delete(id);
    return ApiResponse.ok();
  }
}
