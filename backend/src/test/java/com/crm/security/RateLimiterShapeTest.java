package com.crm.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.crm.common.ErrorCode;
import com.crm.common.RateLimitExceededException;
import jakarta.servlet.http.HttpServletRequest;
import java.lang.annotation.Annotation;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * {@link RateLimiter} 的<b>调用形状</b>（100-rate-limit-consolidation，T6 的组合层另一半）。
 *
 * <p>钉的是「身份算出来的键<b>逐字</b>交给 store，且只交一次」。键名写错是一种<b>没有任何行为用例能发现</b>的缺陷：
 * 它不报错、不改变响应，只是让本该共用一个桶的两次请求各数一份，或者让两个 scope 悄悄合并成一个。 故这里用裸 mock 的 store 逐字核对键名与配额三元组（照 {@code
 * MfaStateStoreTest} 的论述：形状与行为 不可互相替代——行为在 {@code RateLimitIT} 与 {@code RateLimitStoreTest} 里）。
 *
 * <p>{@link RateLimitStore} 在这里是 mock：本类不问「计数怎么算」。反过来，{@code RateLimitStoreTest} 不问
 * 「键长什么样」。两个问题的失效方式不同，混一类测会让彼此的失败信息互相遮蔽。
 */
class RateLimiterShapeTest {

  private static final String SCOPE = "export-generate";

  private RateLimitStore store;

  private RateLimiter limiter;

  private HttpServletRequest request;

  /** 注解实例：Java 注解没有可直接 new 的实现类，故就地实现（Mockito 也能 mock 注解，但这样更直白）。 */
  private static RateLimit limitOf(
      String scope, int limit, long windowSeconds, RateLimitDimension by) {
    return new RateLimit() {
      @Override
      public Class<? extends Annotation> annotationType() {
        return RateLimit.class;
      }

      @Override
      public String scope() {
        return scope;
      }

      @Override
      public int limit() {
        return limit;
      }

      @Override
      public long windowSeconds() {
        return windowSeconds;
      }

      @Override
      public RateLimitDimension by() {
        return by;
      }
    };
  }

  private void setUp(RateLimitStore stub, HttpServletRequest req) {
    this.store = stub;
    this.request = req;
    this.limiter = new RateLimiter(stub, new ClientIpResolver(true));
  }

  private static HttpServletRequest requestFrom(String remoteAddr, String forwardedFor) {
    HttpServletRequest req = mock(HttpServletRequest.class);
    when(req.getRemoteAddr()).thenReturn(remoteAddr);
    when(req.getHeader("X-Forwarded-For")).thenReturn(forwardedFor);
    return req;
  }

  private static void authenticateHuman(long userId) {
    SecurityContextHolder.getContext()
        .setAuthentication(
            new UsernamePasswordAuthenticationToken(
                new JwtAuthFilter.CrmPrincipal(userId, "probe-user", "SALES"), null, List.of()));
  }

  private static void authenticateMachine(long creatorId, long keyId) {
    var auth =
        new UsernamePasswordAuthenticationToken(
            new JwtAuthFilter.CrmPrincipal(creatorId, "open-api", "OPEN_API", true),
            null,
            List.of());
    auth.setDetails(new ApiKeyAuthFilter.ApiKeyPrincipal(keyId, "probe-key"));
    SecurityContextHolder.getContext().setAuthentication(auth);
  }

  @AfterEach
  void tearDown() {
    SecurityContextHolder.clearContext();
  }

  private RateLimitStore startingStore() {
    RateLimitStore stub = mock(RateLimitStore.class);
    when(stub.record(anyString(), anyInt(), anyLong())).thenReturn(0L);
    return stub;
  }

  @Test
  @DisplayName("已认证用户 ⇒ 键为 rl:<scope>:user:<id>，配额三元组逐字交给 store，且只交一次")
  void theKeyAndQuotaAreHandedToTheStoreVerbatim() {
    RateLimitStore stub = startingStore();
    setUp(stub, requestFrom("10.0.0.9", "203.0.113.7"));
    authenticateHuman(42L);

    limiter.check(limitOf(SCOPE, 10, 60, RateLimitDimension.AUTO), request);

    verify(stub).record("rl:export-generate:user:42", 10, 60L);
    verifyNoMoreInteractions(stub);
  }

  /**
   * 机器主体按 <b>keyId</b> 分桶，不按创建者 id。
   *
   * <p>按创建者分桶会让同一个管理员创建的多个密钥<b>共用一个桶</b>：一个密钥打满、其余全部被拒， 而每个密钥单独看都「没超限」——只有合起来看才看得出。
   */
  @Test
  @DisplayName("机器主体 ⇒ 键用 keyId（不是密钥创建者的 userId）")
  void machineSubjectsAreBucketedByKeyId() {
    RateLimitStore stub = startingStore();
    setUp(stub, requestFrom("10.0.0.9", null));
    authenticateMachine(1L, 7L);

    limiter.check(limitOf(SCOPE, 60, 60, RateLimitDimension.API_KEY), request);

    verify(stub).record("rl:export-generate:key:7", 60, 60L);
  }

  @Test
  @DisplayName("超限 ⇒ 抛 RateLimitExceededException，携带剩余秒数（供 Retry-After）")
  void overTheLimitThrowsWithTheRemainingSeconds() {
    RateLimitStore stub = mock(RateLimitStore.class);
    when(stub.record(anyString(), anyInt(), anyLong())).thenReturn(7L);
    setUp(stub, requestFrom("10.0.0.9", null));
    authenticateHuman(42L);

    RateLimitExceededException ex =
        assertThrows(
            RateLimitExceededException.class,
            () -> limiter.check(limitOf(SCOPE, 3, 60, RateLimitDimension.USER), request));

    assertEquals(7L, ex.getRetryAfterSeconds());
    assertEquals(
        ErrorCode.RATE_LIMITED, ex.getErrorCode(), "必须是通用限流码——MFA_LOCKED 的语义（直到解锁为止）与限流相反");
  }

  @Test
  @DisplayName("匿名 + 无转发头 ⇒ 键用 remoteAddr；有 XFF ⇒ 用其首段")
  void anonymousRequestsAreBucketedByTheResolvedClientIp() {
    RateLimitStore stub = startingStore();
    setUp(stub, requestFrom("10.0.0.9", "203.0.113.7"));
    SecurityContextHolder.clearContext();

    limiter.check(limitOf(SCOPE, 5, 60, RateLimitDimension.AUTO), request);
    verify(stub).record("rl:export-generate:ip:203.0.113.7", 5, 60L);

    setUp(startingStore(), requestFrom("10.0.0.9", null));
    limiter.check(limitOf(SCOPE, 5, 60, RateLimitDimension.AUTO), request);
    verify(store).record("rl:export-generate:ip:10.0.0.9", 5, 60L);
  }

  /**
   * 非 HTTP 调用（{@code request == null}）<b>仍然在限流</b>，退化成 {@code ip:unknown} 这一个共享桶。
   *
   * <p>退化成「不限流」是更坏的选择：它在客户端行为上完全看不出来（响应一字不变，只是护栏不见了）， 只在被滥用时才显形。共用一个桶至少会拦住无节制的调用，且这个键名本身就是一条线索。
   */
  @Test
  @DisplayName("无请求上下文（@Scheduled 等）⇒ ip:unknown，仍计数")
  void nonHttpInvocationsStillCountUnderASharedKey() {
    RateLimitStore stub = startingStore();
    setUp(stub, null);
    SecurityContextHolder.clearContext();

    limiter.check(limitOf(SCOPE, 10, 60, RateLimitDimension.AUTO), null);

    verify(stub).record("rl:export-generate:ip:unknown", 10, 60L);
  }
}
