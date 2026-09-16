package com.crm.security;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 请求主体的标识（100-rate-limit-consolidation）：把「这个请求算谁的」收敛成一个纯静态函数，故可脱 Spring 单测 （手工往 {@code
 * SecurityContextHolder} 塞主体即可）。
 *
 * <p>判定顺序见 {@link RateLimitDimension#AUTO}。两处依赖都<b>复用</b>既有实现、不写第二份判断： 机器主体用 {@code
 * SecurityUtil.isMachineSubject()}（046 为行级数据权限而加），密钥 id 用 {@code
 * SecurityUtil.currentApiKeyId()}。后者是新加的——但它是<b>纯新增成员</b>，既有的 {@code currentUserId()} / {@code
 * currentPrincipal()} 一字未动。
 *
 * <p><b>回退的取舍</b>：显式声明了 {@code USER} / {@code API_KEY} 而请求实际不具备该主体时（例如端点被匿名 打通、或密钥 id 缺失），本类<b>退化成按
 * IP 分桶并记一条 warn</b>，不退化成「不限流」。理由是两种失效的可见性 差一个量级：退化成 IP 桶仍然在限流（只是粒度粗了，而 warn 会把它暴露出来），而「不限流」在客户端行为上
 * <b>完全看不出来</b>——它只在被滥用时才显形。
 */
final class RateLimitIdentity {

  private static final Logger log = LoggerFactory.getLogger(RateLimitIdentity.class);

  private RateLimitIdentity() {}

  /**
   * 解析本次请求在给定 scope 下的计数键。
   *
   * @param scope 配额桶名（{@code @RateLimit#scope()}）
   * @param by 维度；{@code AUTO} 时按类 javadoc 的顺序判定
   * @param request 当前请求（取 IP 用）
   * @param ipResolver 客户端 IP 解析器（信任口径由配置决定）
   */
  static String keyOf(
      String scope,
      RateLimitDimension by,
      HttpServletRequest request,
      ClientIpResolver ipResolver) {
    return switch (by) {
      case IP -> ipKey(scope, request, ipResolver);
      case USER -> {
        Long userId = SecurityUtil.currentUserId();
        if (userId == null) {
          log.warn("限流维度声明为 USER 但请求无登录主体，退化为按 IP 分桶：scope={}", scope);
          yield ipKey(scope, request, ipResolver);
        }
        yield RateLimitKeys.user(scope, userId);
      }
      case API_KEY -> {
        Long keyId = SecurityUtil.currentApiKeyId();
        if (keyId == null) {
          log.warn("限流维度声明为 API_KEY 但请求非机器主体，退化为按 IP 分桶：scope={}", scope);
          yield ipKey(scope, request, ipResolver);
        }
        yield RateLimitKeys.apiKey(scope, keyId);
      }
      case AUTO -> autoKey(scope, request, ipResolver);
    };
  }

  private static String autoKey(
      String scope, HttpServletRequest request, ClientIpResolver ipResolver) {
    if (SecurityUtil.isMachineSubject()) {
      Long keyId = SecurityUtil.currentApiKeyId();
      if (keyId == null) {
        // 机器主体却没有密钥 id：ApiKeyAuthFilter 一定会塞 details，走到这里说明鉴权链变了。
        log.warn("机器主体缺少密钥 id，退化为按 IP 分桶：scope={}", scope);
        return ipKey(scope, request, ipResolver);
      }
      return RateLimitKeys.apiKey(scope, keyId);
    }
    Long userId = SecurityUtil.currentUserId();
    if (userId != null) {
      return RateLimitKeys.user(scope, userId);
    }
    return ipKey(scope, request, ipResolver);
  }

  private static String ipKey(
      String scope, HttpServletRequest request, ClientIpResolver ipResolver) {
    // fallback 取 remoteAddr：与改造前 FormService/EmailTrackController 的 clientIp 行为一致。
    String fallback = request == null ? null : request.getRemoteAddr();
    String ip = ipResolver.resolve(request, fallback == null ? "unknown" : fallback);
    return RateLimitKeys.ip(scope, ip);
  }
}
