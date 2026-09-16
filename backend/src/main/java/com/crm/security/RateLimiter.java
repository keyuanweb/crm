package com.crm.security;

import com.crm.common.RateLimitExceededException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;

/**
 * 限流件的组合层（100-rate-limit-consolidation）：**身份 → 键 → 存储 → 超限即抛**。
 *
 * <p><b>为什么与 {@link RateLimitStore} 分成两个类</b>：两层的失效方式完全不同，混成一个类会让两侧都测不干净。 store
 * 的失效是「计数怎么算、失败了怎么办」（协议层，用替身测行为）；本层的失效是「这算谁的、键拼得对不对」 （判据层，纯函数即可测）。合起来的话，任何一条用例都得同时装 Redis
 * 替身与安全上下文，而两类断言会互相遮蔽 ——例如「键名拼错」在装了替身的用例里表现为「计数没落到预期键上」，很容易被读成「没接线」。
 *
 * <p><b>本类不碰 {@code SecurityContextHolder}</b>：那是 {@link RateLimitIdentity} 的职责。这里只做编排， 因此可以拿着裸
 * mock 的 store 断言「键算对了、超限抛了、未超限不抛」。
 */
@Component
public class RateLimiter {

  private final RateLimitStore store;
  private final ClientIpResolver ipResolver;

  public RateLimiter(RateLimitStore store, ClientIpResolver ipResolver) {
    this.store = store;
    this.ipResolver = ipResolver;
  }

  /**
   * 按注解声明的配额记一次请求；超限则抛 {@link RateLimitExceededException}。
   *
   * @param rateLimit 端点上的配额声明
   * @param request 当前请求；{@code null}（非 HTTP 调用）时身份退化为 {@code ip:unknown}
   */
  public void check(RateLimit rateLimit, HttpServletRequest request) {
    String key = RateLimitIdentity.keyOf(rateLimit.scope(), rateLimit.by(), request, ipResolver);
    long retryAfterSeconds = store.record(key, rateLimit.limit(), rateLimit.windowSeconds());
    if (retryAfterSeconds > 0) {
      throw new RateLimitExceededException(retryAfterSeconds);
    }
  }

  /**
   * 显式 IP 的重载：调用方<b>已经</b>自行解析好身份（服务内部拿着 IP 字符串，而不是 {@link HttpServletRequest}）。
   *
   * <p><b>只给「被限流单元是服务方法」的场景用</b>（{@code FormService#submit} 在字段校验之前限流， 且它必须保留 {@code request ==
   * null → "unknown"} 这个退化字面量）。<b>端点自己就是被限流单元时用 {@link RateLimit} 注解</b>， 别在这里手写配额 ——
   * 写在注解上的配额才进得了覆盖台账（{@code RateLimitCoverageTest}）。
   *
   * <p>⚠️ 它<b>只接受 IP 身份</b>（键族固定为 {@code rl:<scope>:ip:<ip>}），不提供「任意身份串」的通用重载： 那会绕开 {@link
   * RateLimitIdentity} 的判定顺序，而「机器主体按 {@code keyId} 分桶、不按创建者 id」正是靠那条顺序保证的。
   */
  public void checkIp(String scope, int limit, long windowSeconds, String ip) {
    long retryAfterSeconds = store.record(RateLimitKeys.ip(scope, ip), limit, windowSeconds);
    if (retryAfterSeconds > 0) {
      throw new RateLimitExceededException(retryAfterSeconds);
    }
  }
}
