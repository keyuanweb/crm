package com.crm.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * {@link RateLimitIdentity} 的判定顺序（100-rate-limit-consolidation，T7/T8）。
 *
 * <p><b>纯函数，脱 Spring</b>：身份只从 {@code SecurityContextHolder} 读，故这里手工塞主体即可——不需要
 * {@code @SpringBootTest}，也不需要 mock {@code SecurityUtil} 的静态方法（那样反而绕过了被测的顺序判定）。
 *
 * <p>本类里每一条断言都问同一个问题的不同侧面：<b>这个请求算谁的</b>。顺序写错不会有任何报错——它只是让
 * 配额落进另一个桶，而绝大多数情况下「没超限」的观察结果是一样的（错桶也要累积到阈值才显形）。
 */
class RateLimitIdentityTest {

  private static final String SCOPE = "open-api-read";

  private final ClientIpResolver ipResolver = new ClientIpResolver(true);

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

  private static void authenticateMachine(long creatorId, Long keyId) {
    var auth =
        new UsernamePasswordAuthenticationToken(
            new JwtAuthFilter.CrmPrincipal(creatorId, "open-api", "OPEN_API", true),
            null,
            List.of());
    if (keyId != null) {
      auth.setDetails(new ApiKeyAuthFilter.ApiKeyPrincipal(keyId, "probe-key"));
    }
    SecurityContextHolder.getContext().setAuthentication(auth);
  }

  @AfterEach
  void tearDown() {
    SecurityContextHolder.clearContext();
  }

  private String keyOf(RateLimitDimension by, HttpServletRequest request) {
    return RateLimitIdentity.keyOf(SCOPE, by, request, ipResolver);
  }

  // ===== T7：机器主体按 keyId 分桶 =====

  /**
   * 机器主体的身份是<b>密钥</b>，不是密钥的创建者。
   *
   * <p>按创建者分桶会让同一个管理员创建的多个密钥<b>共用一个桶</b>：一个密钥打满、其余全部被拒， 而每个密钥单独看都「没超限」——只有把两个密钥的用量合起来才看得出。造出这个缺陷的是
   * {@code ApiKeyAuthFilter} 的一个既有事实：主体里的 {@code userId} 是 {@code key.getCreatedBy()}， 密钥 id 另放在
   * {@code authentication.getDetails()} 里。
   */
  @Test
  @DisplayName("T7：机器主体按 keyId 分桶，同一创建者的两个密钥互不共用配额")
  void machineSubjectsAreBucketedByKeyId() {
    authenticateMachine(1L, 7L);
    String first = keyOf(RateLimitDimension.AUTO, requestFrom("10.0.0.9", null));
    assertEquals("rl:open-api-read:key:7", first);

    authenticateMachine(1L, 8L);
    String second = keyOf(RateLimitDimension.AUTO, requestFrom("10.0.0.9", null));
    assertEquals("rl:open-api-read:key:8", second);

    assertNotEquals(first, second, "同一个创建者的两个密钥共用一个桶 = 一个密钥能把其余的额度吃光");
    assertNotEquals("rl:open-api-read:user:1", first, "不得退化成按创建者分桶");
  }

  /** 机器主体却拿不到密钥 id（鉴权链被改动）⇒ 退化成 IP 桶，<b>不退化成不限流</b>。 */
  @Test
  @DisplayName("机器主体无密钥 id ⇒ 退化成 IP 分桶（不是不限流）")
  void aMachineSubjectWithoutAKeyIdFallsBackToTheIpBucket() {
    authenticateMachine(1L, null);

    assertEquals(
        "rl:open-api-read:ip:203.0.113.7",
        keyOf(RateLimitDimension.AUTO, requestFrom("10.0.0.9", "203.0.113.7")));
  }

  // ===== T8：AUTO 的判定顺序 =====

  /**
   * AUTO 的顺序：<b>机器主体 → 已认证用户 → 匿名 IP</b>，前面的分支优先于后面的。
   *
   * <p>「已认证用户」那一步尤其要钉：一个已登录用户带了 XFF 时，配额必须落在<b>他的账号</b>上而不是他的来源地址 ——移动网络与多分支办公会让同一个人的出口 IP 反复变化，按
   * IP 分桶会让同一个人被反复当成新主体。
   */
  @Test
  @DisplayName("T8：AUTO 顺序 —— 已认证主体优先于转发头里的 IP")
  void autoPrefersTheAuthenticatedSubjectOverTheClientIp() {
    authenticateHuman(42L);

    assertEquals(
        "rl:open-api-read:user:42",
        keyOf(RateLimitDimension.AUTO, requestFrom("10.0.0.9", "203.0.113.7")));
  }

  @Test
  @DisplayName("T8：AUTO —— 未认证才落到 IP（用解析后的客户端 IP，不是 remoteAddr）")
  void autoFallsBackToTheResolvedIpForAnonymousRequests() {
    assertEquals(
        "rl:open-api-read:ip:203.0.113.7",
        keyOf(RateLimitDimension.AUTO, requestFrom("10.0.0.9", "203.0.113.7")));

    assertEquals(
        "rl:open-api-read:ip:10.0.0.9",
        keyOf(RateLimitDimension.AUTO, requestFrom("10.0.0.9", null)));
  }

  @Test
  @DisplayName("T8：AUTO —— 机器主体优先于已认证用户（两者同时具备时）")
  void autoPrefersTheMachineSubjectOverTheUser() {
    authenticateMachine(42L, 7L);

    assertEquals(
        "rl:open-api-read:key:7",
        keyOf(RateLimitDimension.AUTO, requestFrom("10.0.0.9", "203.0.113.7")));
  }

  // ===== 显式维度：主体缺失时退化成 IP，不退化成不限流 =====

  /**
   * 显式声明了某个维度而请求偏不具备该主体时，退化成 IP 桶并记一条 warn。
   *
   * <p>两种失效的可见性差一个量级：退化成 IP 桶仍然在限流（只是粒度粗了，且 warn 会把它暴露出来）， 而「不限流」在客户端行为上<b>完全看不出来</b>——它只在被滥用时才显形。
   */
  @Test
  @DisplayName("显式 USER 但未登录 / 显式 API_KEY 但非机器主体 ⇒ 都退化成 IP 桶")
  void declaredDimensionsFallBackToTheIpBucketWhenTheSubjectIsMissing() {
    assertEquals(
        "rl:open-api-read:ip:203.0.113.7",
        keyOf(RateLimitDimension.USER, requestFrom("10.0.0.9", "203.0.113.7")));
    assertEquals(
        "rl:open-api-read:ip:203.0.113.7",
        keyOf(RateLimitDimension.API_KEY, requestFrom("10.0.0.9", "203.0.113.7")));
    // 反面：已认证用户在 API_KEY 维度下同样落到 IP —— 不能反过来把用户当成密钥。
    authenticateHuman(42L);
    assertEquals(
        "rl:open-api-read:ip:203.0.113.7",
        keyOf(RateLimitDimension.API_KEY, requestFrom("10.0.0.9", "203.0.113.7")));
  }

  @Test
  @DisplayName("显式 IP ⇒ 即使已认证也按 IP 分桶")
  void theIpDimensionIgnoresTheSubject() {
    authenticateHuman(42L);

    assertEquals(
        "rl:open-api-read:ip:203.0.113.7",
        keyOf(RateLimitDimension.IP, requestFrom("10.0.0.9", "203.0.113.7")));
  }

  /**
   * 无请求上下文（{@code @Scheduled}、直接调用）⇒ 匿名落到 {@code ip:unknown} 这一个共享桶，<b>仍在限流</b>。
   *
   * <p>不抛 NPE 也是判据的一部分：注解挂在被异步或定时调用的方法上时，切面拿不到请求是正常情形， 抛异常会把一个「限流粒度粗」的问题升级成「定时任务整体失败」。
   */
  @Test
  @DisplayName("无请求上下文 ⇒ ip:unknown（不抛 NPE）")
  void aMissingRequestYieldsTheUnknownIpBucket() {
    assertEquals("rl:open-api-read:ip:unknown", keyOf(RateLimitDimension.AUTO, null));
  }
}
