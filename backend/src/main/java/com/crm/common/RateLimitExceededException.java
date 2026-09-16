package com.crm.common;

import lombok.Getter;

/**
 * 限流拒绝（100-rate-limit-consolidation）。由共享限流件在超限时抛出，`GlobalExceptionHandler` 映射为 429 + `Retry-After`。
 *
 * <p><b>为什么继承 {@link BusinessException} 而不是直接继承 {@code RuntimeException}</b>：多重保障。
 * 一旦某条路径没走到本类的专用处理器（例如将来有人在别的 {@code @RestControllerAdvice} 里按父类 拦截），父类的 {@code handleBusiness} 仍会按
 * {@link ErrorCode#RATE_LIMITED} 给出**正确的 429**， 而不是掉进 {@code Exception} 的 catch-all 变成 500 ——
 * 限流拒绝被报成服务端错误会让排查方向整个跑偏。
 */
@Getter
public class RateLimitExceededException extends BusinessException {

  /**
   * 建议的重试等待秒数（窗口剩余时间，向上取整）。
   *
   * <p>放在异常里而不是让处理器回头去查 Redis：拒绝路径上 Redis 可能正好不可用（那时根本没有窗口 可查），而处理器不该依赖一个可能故障的外部系统来填一个响应头。
   */
  private final long retryAfterSeconds;

  public RateLimitExceededException(long retryAfterSeconds) {
    super(ErrorCode.RATE_LIMITED);
    this.retryAfterSeconds = retryAfterSeconds;
  }
}
