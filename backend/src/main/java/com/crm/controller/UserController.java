package com.crm.controller;

import com.crm.common.ApiResponse;
import com.crm.common.PageResult;
import com.crm.dto.user.ChangePasswordRequest;
import com.crm.dto.user.ResetPasswordRequest;
import com.crm.dto.user.UserCreateRequest;
import com.crm.dto.user.UserResponse;
import com.crm.dto.user.UserUpdateRequest;
import com.crm.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** 用户管理接口（002-user-management，FR-001~008）。 */
@RestController
@RequestMapping("/api/v1/users")
@Tag(name = "用户管理")
public class UserController {

  private final UserService userService;

  public UserController(UserService userService) {
    this.userService = userService;
  }

  @GetMapping
  @PreAuthorize("hasRole('ADMIN')")
  @Operation(summary = "分页查询用户列表（关键字/角色筛选）")
  public ApiResponse<PageResult<UserResponse>> page(
      @RequestParam(required = false) String keyword,
      @RequestParam(required = false) String role,
      @RequestParam(defaultValue = "1") long page,
      @RequestParam(defaultValue = "20") long pageSize) {
    return ApiResponse.ok(userService.page(keyword, role, page, pageSize));
  }

  @GetMapping("/{id}")
  @PreAuthorize("hasRole('ADMIN')")
  @Operation(summary = "用户详情")
  public ApiResponse<UserResponse> detail(@PathVariable Long id) {
    return ApiResponse.ok(userService.detail(id));
  }

  @PostMapping
  @PreAuthorize("hasRole('ADMIN')")
  @ResponseStatus(HttpStatus.CREATED)
  @Operation(summary = "创建用户")
  public ApiResponse<UserResponse> create(@Valid @RequestBody UserCreateRequest request) {
    return ApiResponse.ok(userService.create(request));
  }

  @PutMapping("/{id}")
  @PreAuthorize("hasRole('ADMIN')")
  @Operation(summary = "编辑用户（显示名/角色/启停）")
  public ApiResponse<UserResponse> update(
      @PathVariable Long id, @Valid @RequestBody UserUpdateRequest request) {
    return ApiResponse.ok(userService.update(id, request));
  }

  @PutMapping("/{id}/password")
  @PreAuthorize("hasRole('ADMIN')")
  @Operation(summary = "管理员重置密码（旧令牌失效）")
  public ApiResponse<Void> resetPassword(
      @PathVariable Long id, @Valid @RequestBody ResetPasswordRequest request) {
    userService.resetPassword(id, request);
    return ApiResponse.ok();
  }

  @PutMapping("/me/password")
  @Operation(summary = "登录用户修改自己的密码（旧令牌失效）")
  public ApiResponse<Void> changeOwnPassword(@Valid @RequestBody ChangePasswordRequest request) {
    userService.changeOwnPassword(request);
    return ApiResponse.ok();
  }
}
