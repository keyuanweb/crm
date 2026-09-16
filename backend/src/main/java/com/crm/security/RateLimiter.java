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
}
