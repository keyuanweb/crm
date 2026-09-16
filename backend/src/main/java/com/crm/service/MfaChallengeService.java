package com.crm.service;

import com.crm.dto.auth.MfaChallenge;
import com.crm.entity.User;
import java.time.Duration;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * 登录时"这个账号要不要走二次验证"的唯一判定点（082，FR-M07/FR-M14）。
 *
 * <p><b>为什么单独一个类、而不是在 {@code AuthService.login} 里写两行</b>：这里是 US5（按角色强制绑定
 * 2FA）将来唯一的落点。强制策略要做的事恰好是"对某些本来不需要二次验证的账号，也说'需要'"—— 那正是在本方法返回 {@link Optional#empty()}
 * 的那条分支上再加一个条件。把它写成一行内联判断， 将来那次改动就得动登录主路径；写成这个方法，改动被限制在一个"只做判定、不做别的事"的地方。
 *
 * <p>⚠️ <b>本方法返回空 = FR-M14 的零回归</b>。空分支上不碰 Redis、不碰数据库、不产生任何副作用—— 非 2FA 账号的登录与 082 之前逐字节相同，这一点由
 * {@code LoginResponseShapeIT} 在 HTTP 层守着。
 */
@Service
public class MfaChallengeService {

  private final MfaStateStore stateStore;
  private final long tokenTtlSeconds;

  public MfaChallengeService(
      MfaStateStore stateStore,
      @Value("${crm.security.mfa.token-ttl-seconds:300}") long tokenTtlSeconds) {
    if (tokenTtlSeconds <= 0) {
      // 早失败：一个非正的 TTL 会让 createTicket 直接抛（Redis 不接受非正过期时间），
      // 而错误现场是"某个用户登录报 503"，看不出是配置错。配置错就该在装配期响。
      throw new IllegalArgumentException(
          "crm.security.mfa.token-ttl-seconds 必须为正数：" + tokenTtlSeconds);
    }
    this.stateStore = stateStore;
    this.tokenTtlSeconds = tokenTtlSeconds;
  }

  /**
   * 该账号是否需要二次验证；需要则顺带签发一张票据。
   *
   * <p><b>票据只在这里签发</b>，且签发的时刻是"密码已经对了"之后（调用点在 {@code AuthService.login} 的 口令校验与停用检查之后）——
   * 反过来说：任何未通过口令校验的请求都不该拿到票据， 否则一张票据就成了"口令猜错也能拿到的一次验证机会"，而票据本身是持有凭证。
   *
   * <p>Redis 不可用时本方法抛 {@code MFA_STORE_UNAVAILABLE}（503）。这是刻意的 fail closed： 让"已启用 2FA 的账号在 Redis
   * 故障期间登不进来"，好过"静默地放进来一次"——后者在响应、审计、 监控里都看不出异常（见 {@code MfaStateStore} 的类 javadoc）。
   *
   * @param user 已通过口令校验的用户（可能未启用 2FA）
   */
  public Optional<MfaChallenge> challengeFor(User user) {
    if (!Boolean.TRUE.equals(user.getTwoFactorEnabled())) {
      return Optional.empty();
    }
    Duration ttl = Duration.ofSeconds(tokenTtlSeconds);
    String ticket = stateStore.createTicket(user.getId(), ttl);
    // 刻意把 long 收窄成 int：契约里 expiresIn 是整数秒，而任何超过 2^31 秒（68 年）的配置都是误配。
    return Optional.of(new MfaChallenge(ticket, Math.toIntExact(tokenTtlSeconds)));
  }

  /** 票据有效期（秒）。供测试与文档引用，避免同一份配置在两处各算一遍。 */
  public int tokenTtlSeconds() {
    return Math.toIntExact(tokenTtlSeconds);
  }
}
