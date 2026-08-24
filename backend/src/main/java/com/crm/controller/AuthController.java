package com.crm.controller;

import com.crm.common.ApiResponse;
import com.crm.dto.auth.AuthResponse;
import com.crm.dto.auth.CaptchaResponse;
import com.crm.dto.auth.LoginRequest;
import com.crm.dto.auth.RefreshRequest;
import com.crm.dto.auth.UserInfo;
import com.crm.security.SecurityUtil;
import com.crm.service.AuthService;
import com.crm.service.CaptchaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 认证接口（contracts/auth.md）。 */
@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "认证")
public class AuthController {

  private final AuthService authService;
  private final CaptchaService captchaService;

  public AuthController(AuthService authService, CaptchaService captchaService) {
    this.authService = authService;
    this.captchaService = captchaService;
  }

  @GetMapping("/captcha")
  @Operation(summary = "获取图形验证码")
  public ApiResponse<CaptchaResponse> captcha() {
    return ApiResponse.ok(captchaService.generate());
  }

  @PostMapping("/login")
  @Operation(summary = "登录并获取令牌")
  public ApiResponse<AuthResponse> login(
      @Valid @RequestBody LoginRequest request, jakarta.servlet.http.HttpServletRequest httpReq) {
    String clientIp =
        AuthService.resolveClientIp(
            httpReq, httpReq.getRemoteAddr() == null ? "unknown" : httpReq.getRemoteAddr());
    return ApiResponse.ok(authService.login(request, clientIp));
  }

  @PostMapping("/refresh")
  @Operation(summary = "刷新访问令牌")
  public ApiResponse<AuthResponse> refresh(@Valid @RequestBody RefreshRequest request) {
    return ApiResponse.ok(authService.refresh(request));
  }

  @PostMapping("/logout")
  @Operation(summary = "登出并吊销刷新令牌")
  public ApiResponse<Void> logout(@RequestBody RefreshRequest request) {
    authService.logout(request);
    return ApiResponse.ok();
  }

  @GetMapping("/me")
  @Operation(summary = "当前用户信息")
  public ApiResponse<UserInfo> me() {
    return ApiResponse.ok(authService.me(SecurityUtil.currentUserId()));
  }
}
