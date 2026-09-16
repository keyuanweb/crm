package com.crm.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * {@link ClientIpResolver} 的解析口径（100-rate-limit-consolidation，T9）。
 *
 * <p>本类是改造前<b>三份副本</b>（{@code AuthService.resolveClientIp} + {@code EmailTrackController} 与 {@code
 * FormService} 的私有 {@code clientIp}）的收敛落点，故它的第一条职责是<b>把三份在退化输入上的分歧钉死</b>： 另两份只判「整个头非空」，于是 {@code
 * X-Forwarded-For: ,} 会让 {@code split(",")[0].trim()} 返回<b>空串</b>， 使所有这类请求共用一个键为 {@code ""}
 * 的桶——一个客户端就能把别人的配额吃光。本类取的是更严的那份语义。
 *
 * <p>纯函数、脱 Spring：配置位直接构造器注入，不需要上下文。
 */
class ClientIpResolverTest {

  private static final String FALLBACK = "10.0.0.9";

  private static HttpServletRequest requestWithHeader(String forwardedFor) {
    HttpServletRequest req = mock(HttpServletRequest.class);
    when(req.getHeader("X-Forwarded-For")).thenReturn(forwardedFor);
    when(req.getRemoteAddr()).thenReturn(FALLBACK);
    return req;
  }

  private static String resolve(boolean trustForwardedFor, HttpServletRequest request) {
    return new ClientIpResolver(trustForwardedFor).resolve(request, FALLBACK);
  }

  @Test
  @DisplayName("多级代理：取首段并去空白（首段是最初的客户端）")
  void theFirstSegmentWins() {
    assertEquals("203.0.113.7", resolve(true, requestWithHeader("203.0.113.7")));
    assertEquals(
        "203.0.113.7", resolve(true, requestWithHeader("203.0.113.7, 10.0.0.1, 10.0.0.2")));
    assertEquals("203.0.113.7", resolve(true, requestWithHeader("  203.0.113.7  ,10.0.0.1")));
  }

  /**
   * <b>退化输入一律回退 fallback，绝不返回空串</b>（本类存在的核心理由，见类 javadoc）。
   *
   * <p>返回空串的后果不是「这个请求不限流」，而是「所有这类请求共用一个桶」——比不限流更难发现： 症状是「别人的请求把配额用光了」。
   */
  @Test
  @DisplayName("头为 `,` / 首段为空 / 全空白 / 缺失 ⇒ 一律回退 fallback（不返回空串）")
  void degenerateHeadersFallBackToTheFallback() {
    assertEquals(FALLBACK, resolve(true, requestWithHeader(",")), "只判「整头非空」会在这里返回空串");
    assertEquals(FALLBACK, resolve(true, requestWithHeader(" , 1.2.3.4")));
    assertEquals(FALLBACK, resolve(true, requestWithHeader("   ")));
    assertEquals(FALLBACK, resolve(true, requestWithHeader("")));
    assertEquals(FALLBACK, resolve(true, requestWithHeader(null)));
  }

  @Test
  @DisplayName("无请求（非 HTTP 线程）⇒ 返回 fallback，不抛 NPE")
  void aMissingRequestReturnsTheFallback() {
    assertEquals(FALLBACK, resolve(true, null));
  }

  /**
   * {@code crm.rate-limit.trust-forwarded-for=false} 时完全不看转发头。
   *
   * <p>供「直连在公网、前面没有可信代理」的部署使用：那种部署下首段是<b>客户端自己写的</b>，信它等于不限流。
   */
  @Test
  @DisplayName("trust-forwarded-for=false ⇒ 忽略转发头")
  void aDistrustedHeaderIsIgnored() {
    assertEquals(FALLBACK, resolve(false, requestWithHeader("203.0.113.7")));
  }

  @Test
  @DisplayName("fallback 原样返回（调用方据此保留自己的「无请求」字面量）")
  void theFallbackIsReturnedVerbatim() {
    assertEquals("unknown", new ClientIpResolver(true).resolve(null, "unknown"));
  }
}
