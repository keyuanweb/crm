package com.crm.security;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 请求频率上限注解（100-rate-limit-consolidation）：由 {@link RateLimitAspect} 在方法体之前判定，超限抛 {@code
 * RateLimitExceededException}（HTTP 429 + {@code Retry-After}）。
 *
 * <p><b>{@code scope} 为什么是显式字符串，而不是从 URI / 方法名推导</b>——配额由 {@link RateLimitKeys} 的键决定，而键里有 scope。从
 * URI 推导会让「改路径」变成「换桶」：端点改名之后计数从零开始， 而<b>没有任何用例会红</b>（限流照常工作，只是换了个人数）。「按 URI 前缀给配额」在本仓同样不成立：同一个
 * {@code /api/v1/public/} 之下，匿名只读、匿名建单、匿名凭证校验的风险差一个量级。显式常量还有第二个好处—— {@code RateLimitCoverageTest}
 * 能逐字核对它，于是「两条路径不小心共用一个 scope」这类静默合并也能被发现。
 *
 * <p><b>⚠️ 本注解是 opt-in 的，它自己不保证任何东西</b>：忘了标注 = 该端点没有限流，且<b>没有任何用例会红</b> （端点的正常路径行为一字不变）。把它换成
 * default-deny 的是字节码扫描台账 {@code RateLimitCoverageTest}——每个 Controller 方法要么带本注解、要么在白名单里，而白名单每条都要写理由。
 * ⇒ 新增 Controller 方法时先想清楚它属于哪一类，再决定标注还是登记豁免。
 *
 * <p><b>阈值写在注解上而不是配置里</b>（与 {@code @RequirePermission} 把权限码写在方法上同址、同体例）：
 * 配额是<b>逐端点</b>的数字，与端点定义写在一起才看得出彼此的比例关系（匿名建单 5/60s 与导出 10/60s 差一倍，与邮件追踪 60/60s 差十二倍；分散到 yml
 * 里就看不出来了）。全局只有两个开关，见 {@code crm.rate-limit.*}。
 */
@Target({ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface RateLimit {

  /**
   * 配额桶名（如 {@code export-generate}、{@code public-form-submit}）。
   *
   * <p>⚠️ <b>改它等于换一个计数器</b>：旧键留在 Redis 里直到 TTL 到期，新键从零开始。
   */
  String scope();

  /** 窗口内允许的请求数（<b>含</b>第 {@code limit} 次；第 {@code limit + 1} 次被拒）。 */
  int limit();

  /** 窗口长度（秒），同时也是计数键的 TTL。 */
  long windowSeconds();

  /** 身份维度，默认 {@link RateLimitDimension#AUTO}。 */
  RateLimitDimension by() default RateLimitDimension.AUTO;
}
