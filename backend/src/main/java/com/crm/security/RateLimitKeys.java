package com.crm.security;

/**
 * 限流键的构造（100-rate-limit-consolidation）：键族 {@code rl:<scope>:<身份>}（如 {@code
 * rl:export-generate:user:42}、{@code rl:open-api-read:key:7}）。
 *
 * <p>与 {@code auth:*}（登录失败计数、验证码、2FA）<b>同级</b>——同为顶层前缀，故运维可以按前缀监控与 注入故障：{@code
 * InMemoryRedisTestSupport.failOnKeyPrefix("rl:")} 正好只覆盖限流、不影响登录计数， 「Redis 挂了 ⇒ 限流 fail open
 * 而登录路径不受影响」因此能在同一次注入下断言。
 *
 * <p>方法<b>包内可见 + 具名</b>（照 {@code MfaStateStore.failKey} 那组的先例）：调用形状断言可以逐字核对键名。
 * 这不是形式主义——「键名写错」是一种<b>没有任何行为用例能发现</b>的缺陷：它不报错、不改变响应， 只是让本该共用一个桶的两次请求各数一份，或者让两个 scope 悄悄合并成一个。
 */
final class RateLimitKeys {

  /**
   * 键族前缀。
   *
   * <p>⚠️ 改它会让<b>全部</b>计数从零开始（在窗口中途部署 = 一次全面放行），且旧键会一直躺在 Redis 里 直到 TTL 到期。
   */
  static final String PREFIX = "rl:";

  private RateLimitKeys() {}

  /** 按登录用户分桶。 */
  static String user(String scope, long userId) {
    return PREFIX + scope + ":user:" + userId;
  }

  /** 按 API Key 分桶（机器主体）。 */
  static String apiKey(String scope, long keyId) {
    return PREFIX + scope + ":key:" + keyId;
  }

  /** 按客户端 IP 分桶。 */
  static String ip(String scope, String ip) {
    return PREFIX + scope + ":ip:" + ip;
  }
}
