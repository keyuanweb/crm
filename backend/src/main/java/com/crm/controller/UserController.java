package com.crm.controller;

import com.crm.common.ApiResponse;
import com.crm.common.PageResult;
import com.crm.dto.department.DataPermissionRequest;
import com.crm.dto.user.ChangePasswordRequest;
import com.crm.dto.user.ResetPasswordRequest;
import com.crm.dto.user.UserCreateRequest;
import com.crm.dto.user.UserResponse;
import com.crm.dto.user.UserUpdateRequest;
import com.crm.security.RequirePermission;
import com.crm.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
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
 * 用户管理接口（002-user-management）。
 *
 * <p><b>1.5：方法级 {@code @PreAuthorize("hasRole('ADMIN')")} 已换成
 * {@code @RequirePermission("user:manage")}。</b> 两者对 ADMIN 等价（切面对 ADMIN 直通），差别在 081
 * 的四个管理角色：SALES_MANAGER、SUPPORT_MANAGER、 MARKETING_MANAGER、FINANCE_MANAGER 的权限列表里**都有
 * user:manage**，菜单里**也都有「用户管理」**， 但 {@code hasRole('ADMIN')} 只认字面量 ADMIN，于是这四个角色点进用户管理一片 403。换成码之后，
 * 矩阵说能就能，且 ADMIN 的行为一字不变。
 *
 * <p>{@code PUT /me/password} 保持无注解：它是任何登录用户改**自己**密码的自助入口（服务层用当前 principal，不接 id），不该被 user:manage
 * 挡住。
 */
@RestController
@RequestMapping("/api/v1/users")
@Tag(name = "用户管理")
public class UserController {

  private final UserService userService;

  public UserController(UserService userService) {
    this.userService = userService;
  }

  @GetMapping
  @RequirePermission("user:manage")
  @Operation(summary = "分页查询用户列表（关键字/角色筛选）")
  public ApiResponse<PageResult<UserResponse>> page(
      @RequestParam(required = false) String keyword,
      @RequestParam(required = false) String role,
      @RequestParam(defaultValue = "1") long page,
      @RequestParam(defaultValue = "20") long pageSize) {
    return ApiResponse.ok(userService.page(keyword, role, page, pageSize));
  }

  @GetMapping("/{id}")
  @RequirePermission("user:manage")
  @Operation(summary = "用户详情")
  public ApiResponse<UserResponse> detail(@PathVariable Long id) {
    return ApiResponse.ok(userService.detail(id));
  }

  @PostMapping
  @RequirePermission("user:manage")
  @ResponseStatus(HttpStatus.CREATED)
  @Operation(summary = "创建用户")
  public ApiResponse<UserResponse> create(@Valid @RequestBody UserCreateRequest request) {
    return ApiResponse.ok(userService.create(request));
  }

  @PutMapping("/{id}")
  @RequirePermission("user:manage")
  @Operation(summary = "编辑用户（显示名/角色/启停）")
  public ApiResponse<UserResponse> update(
      @PathVariable Long id, @Valid @RequestBody UserUpdateRequest request) {
    return ApiResponse.ok(userService.update(id, request));
  }

  @PutMapping("/{id}/password")
  @RequirePermission("user:manage")
  @Operation(summary = "管理员重置密码（旧令牌失效）")
  public ApiResponse<Void> resetPassword(
      @PathVariable Long id, @Valid @RequestBody ResetPasswordRequest request) {
    userService.resetPassword(id, request);
    return ApiResponse.ok();
  }

  @PutMapping("/{id}/data-permission")
  @RequirePermission("user:manage")
  @Operation(summary = "设置用户部门与数据权限范围（仅管理员）")
  public ApiResponse<UserResponse> setDataPermission(
      @PathVariable Long id, @Valid @RequestBody DataPermissionRequest request) {
    return ApiResponse.ok(userService.setDataPermission(id, request));
  }

  @PutMapping("/me/password")
  @Operation(summary = "登录用户修改自己的密码（旧令牌失效）")
  public ApiResponse<Void> changeOwnPassword(@Valid @RequestBody ChangePasswordRequest request) {
    userService.changeOwnPassword(request);
    return ApiResponse.ok();
  }
}
