package com.crm.controller;

import com.crm.common.ApiResponse;
import com.crm.common.BusinessException;
import com.crm.common.ErrorCode;
import com.crm.dto.auth.AuthResponse;
import com.crm.dto.auth.CaptchaResponse;
import com.crm.dto.auth.LoginRequest;
import com.crm.dto.auth.MfaDisableRequest;
import com.crm.dto.auth.MfaDisableResponse;
import com.crm.dto.auth.MfaEnableRequest;
import com.crm.dto.auth.MfaEnableResponse;
import com.crm.dto.auth.MfaRecoveryCodesResponse;
import com.crm.dto.auth.MfaRegenerateRequest;
import com.crm.dto.auth.MfaSetupRequest;
import com.crm.dto.auth.MfaSetupResponse;
import com.crm.dto.auth.MfaStatusResponse;
import com.crm.dto.auth.RefreshRequest;
import com.crm.dto.auth.UserInfo;
import com.crm.security.SecurityUtil;
import com.crm.service.AuthService;
import com.crm.service.CaptchaService;
import com.crm.service.MfaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 认证接口（contracts/auth.md）。
 *
 * <p>082 起另挂 {@code /2fa/**} 的五个注册生命周期端点（{@code setup} / {@code enable} / {@code status} / {@code
 * recovery-codes/regenerate} / {@code disable}）。 <b>它们全部要求已登录 JWT</b>——只有 {@code POST
 * /2fa/verify}（第 9 步）是例外， 它凭密码阶段的一次性票据进场，因此在 {@code SecurityConfig} 里被单独放行。
 *
 * <p>⚠️ 那条例外<b>必须写成精确路径</b>，不能用 {@code /api/v1/auth/2fa/**} 通配：通配会把本类的 这五个端点一起放出去，而它们取的是 {@code
 * SecurityUtil.currentUserId()}——放行之后拿到的是 {@code null}，表现为 500 而不是"被拒绝"，且失败方向是"少了认证"而不是"少了授权"。
 * 防线只有一条，别让它被"顺手加个通配"抹掉（{@code SecurityConfigIT} 会盯住它）。
 */
@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "认证")
public class AuthController {

  private final AuthService authService;
  private final CaptchaService captchaService;
  private final MfaService mfaService;

  public AuthController(
      AuthService authService, CaptchaService captchaService, MfaService mfaService) {
    this.authService = authService;
    this.captchaService = captchaService;
    this.mfaService = mfaService;
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

  // ===== 082：2FA 的注册生命周期（FR-M03~FR-M06、FR-M09、FR-M13） =====

  /**
   * 绑定第一步：生成密钥与二维码，账号保持未启用。
   *
   * <p>{@code @RequestBody(required = false)} 是**契约的一部分**：{@code quickstart.md} §b 的冒烟命令 不带
   * body。写成必填的话，那条命令会在参数解析阶段拿到一个与业务无关的 400。
   */
  @PostMapping("/2fa/setup")
  @Operation(summary = "生成 2FA 绑定密钥与二维码（账号保持未启用）")
  public ApiResponse<MfaSetupResponse> setup(
      @RequestBody(required = false) MfaSetupRequest request) {
    return ApiResponse.ok(
        mfaService.setup(currentUserId(), request == null ? null : request.getPassword()));
  }

  @PostMapping("/2fa/enable")
  @Operation(summary = "校验动态码并启用 2FA，一次性返回恢复码")
  public ApiResponse<MfaEnableResponse> enable(@Valid @RequestBody MfaEnableRequest request) {
    return ApiResponse.ok(mfaService.enable(currentUserId(), request.getCode()));
  }

  @GetMapping("/2fa/status")
  @Operation(summary = "2FA 状态与剩余恢复码数量")
  public ApiResponse<MfaStatusResponse> mfaStatus() {
    return ApiResponse.ok(mfaService.status(currentUserId()));
  }

  @PostMapping("/2fa/recovery-codes/regenerate")
  @Operation(summary = "重新生成恢复码（旧码全部作废）")
  public ApiResponse<MfaRecoveryCodesResponse> regenerateRecoveryCodes(
      @Valid @RequestBody MfaRegenerateRequest request) {
    return ApiResponse.ok(
        mfaService.regenerateRecoveryCodes(currentUserId(), request.getPassword()));
  }

  @PostMapping("/2fa/disable")
  @Operation(summary = "关闭 2FA（密码 + 动态码或恢复码）")
  public ApiResponse<MfaDisableResponse> disable(@Valid @RequestBody MfaDisableRequest request) {
    mfaService.disable(currentUserId(), request);
    return ApiResponse.ok(new MfaDisableResponse(true));
  }

  /**
   * 当前主体 id，缺失即 401。
   *
   * <p>这些端点都在 {@code SecurityConfig} 的 {@code authenticated()} 之后，正常路径上取不到 {@code null}。
   * 但把"没有主体"显式映射成 401 而不是让自动拆箱抛 NPE：后者会变成 500， 而一个 500 会把"调用方没带令牌"混进"服务端坏了"里。
   */
  private static long currentUserId() {
    Long userId = SecurityUtil.currentUserId();
    if (userId == null) {
      throw new BusinessException(ErrorCode.UNAUTHORIZED);
    }
    return userId;
  }
}
