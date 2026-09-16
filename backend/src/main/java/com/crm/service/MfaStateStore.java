package com.crm.service;

import com.crm.common.BusinessException;
import com.crm.common.ErrorCode;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

/**
 * 2FA 的一次性状态：票据、失败计数、已用过的时间步（082-two-factor-auth）。
 *
 * <p><b>全仓唯一一处「Redis 故障 ⇒ fail closed」的边界</b>。其余所有地方（含 {@code AuthService} 的登录失败 计数）都是 <b>fail
 * open</b>：catch 住 Redis 异常、打一条 warn、当作"没有记录"继续走。那对限流是合理的 ——它的代价是"限流暂时失效"，而不放行会让整个系统在 Redis
 * 抖动时无法登录。但 2FA 不能这么做： 这里任何一处 {@code catch { return null; }} 或 {@code catch { return true; }} 的语义都是
 * <b>"跳过二次验证"</b>，即 Redis 一抖，<b>全体已启用 2FA 的账号静默降级为单因素</b>—— 而响应里<b>看不出来</b>（用户正常登录、审计没有异常、监控没有报错）。
 *
 * <p>⇒ 本类的每一个公开方法都整体包在 try/catch 里并转抛 {@link ErrorCode#MFA_STORE_UNAVAILABLE}。 一个"顺手"的 catch
 * 就是一次静默的单因素降级。<b>新增任何方法都必须照此办理。</b>
 *
 * <p><b>两个原子原语，各有一个非它不可的理由</b>：
 *
 * <ul>
 *   <li>{@link #consumeTicket} 用 {@code getAndDelete}（Redis 6.2 的 {@code GETDEL}），<b>不是</b> {@code
 *       get} + {@code delete}。后者是 TOCTOU：并发的两次验证可以都读到同一张票据、都判为有效， 于是"一张票据 ⇒
 *       一个会话"失效。注意<b>并发下这不限于同一个码</b>——两张不同的有效恢复码 同样能配对成功，而防重放（下面那个时间步标记）抓不到这种组合。
 *   <li>{@link #markTimeStepUsed} 用 {@code setIfAbsent}（{@code SET NX}），<b>不是</b>{@code get} +
 *       {@code set}。后者让防重放变成竞态：两个并发请求都读到"这个步还没用过"，于是同一个动态码 被用两次——正是 FR-M08 要防的。
 * </ul>
 *
 * <p>两者的可观测行为在<b>单线程下相同</b>，所以 <b>MockMvc 集成测试在结构上区分不出正解与劣解</b> （见 {@code MfaStateStoreTest} 的类
 * javadoc）。守住它们的是<b>调用形状断言</b>（surefire）， 这是本批唯一能钉住原子性的地方。
 *
 * <p><b>票面内容不落日志</b>：票据本身就是持有凭证（bearer），任何一条把它打进日志的 warn 都等于把「能换一个会话的字符串」递给了能读日志的人。故本类的日志只出 userId
 * 与计数。
 *
 * <p>键族沿用仓内惯例（{@code auth:refresh:} / {@code auth:fail:} / {@code auth:captcha:}）： {@code
 * auth:2fa-ticket:} / {@code auth:2fa-fail:} / {@code auth:2fa-used:}。
 */
@Service
public class MfaStateStore {

  private static final Logger log = LoggerFactory.getLogger(MfaStateStore.class);

  private static final String TICKET_PREFIX = "auth:2fa-ticket:";
  private static final String FAIL_PREFIX = "auth:2fa-fail:";
  private static final String USED_PREFIX = "auth:2fa-used:";

  /**
   * 票据的服务端权威是 Redis —— 不用 JWT：那会引入第二真源（自身 {@code exp} 与 TTL 可各说各话）， 而"JWT 解得开但票据没了"仍要折回 {@code
   * MFA_TICKET_INVALID}。
   */
  private static final int TICKET_BYTES = 32;

  private final RedisTemplate<String, Object> redisTemplate;
  private final int maxAttempts;
  private final Duration lockWindow;
  private final SecureRandom random = new SecureRandom();

  public MfaStateStore(
      RedisTemplate<String, Object> redisTemplate,
      @Value("${crm.security.mfa.max-attempts:5}") int maxAttempts,
      @Value("${crm.security.mfa.lock-duration-seconds:900}") long lockDurationSeconds) {
    if (maxAttempts <= 0) {
      throw new IllegalArgumentException("crm.security.mfa.max-attempts 必须为正数：" + maxAttempts);
    }
    if (lockDurationSeconds <= 0) {
      throw new IllegalArgumentException(
          "crm.security.mfa.lock-duration-seconds 必须为正数：" + lockDurationSeconds);
    }
    this.redisTemplate = redisTemplate;
    this.maxAttempts = maxAttempts;
    this.lockWindow = Duration.ofSeconds(lockDurationSeconds);
  }

  // ===== 票据 =====

  /**
   * 签发一张一次性票据并登记，返回票面字符串。
   *
   * <p><b>票面的形态由本类定义</b>（32 字节 {@link SecureRandom} → 无填充 Base64URL）：它只在
   * "签出"与"验回"这一对之间有意义，把形态与存储放在同一个类里，将来改形态就不会漏改另一边。
   *
   * @param userId 票据指向的用户
   * @param ttl 票据有效期（{@code crm.security.mfa.token-ttl-seconds}）
   */
  public String createTicket(long userId, Duration ttl) {
    byte[] raw = new byte[TICKET_BYTES];
    random.nextBytes(raw);
    String ticket = Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
    try {
      redisTemplate.opsForValue().set(ticketKey(ticket), userId, ttl);
    } catch (Exception ex) {
      throw unavailable("登记二次验证票据", ex);
    }
    return ticket;
  }

  /**
   * 读票据指向的用户，<b>不消费</b>。
   *
   * <p>验证流程必须先"只看一眼"：失败计数要不要拒、码对不对，都要在<b>消耗掉票据之前</b>判。 若这一步直接消费，那么"剩余尝试次数"就没有意义——第一次输错码就把票据烧了，
   * 用户拿不到第二次机会（SC-M04 的"第 6 次"永远发生不了）。
   */
  public Optional<Long> findTicketUserId(String ticket) {
    if (ticket == null || ticket.isBlank()) {
      return Optional.empty();
    }
    Object raw;
    try {
      raw = redisTemplate.opsForValue().get(ticketKey(ticket));
    } catch (Exception ex) {
      throw unavailable("读取二次验证票据", ex);
    }
    return raw instanceof Number number ? Optional.of(number.longValue()) : Optional.empty();
  }

  /**
   * 消费票据：<b>原子的</b>读并删，返回它是否确实属于 {@code userId}。
   *
   * <p>{@code false} 有两种来源（票据已过期/已被消费、或票据不属于该用户），调用方对两者 都应当作 {@code
   * MFA_TICKET_INVALID}——它们对用户是同一件事："这次验证不能完成，请重新登录"。
   *
   * <p>返回 {@code false} 而不是抛异常：票据过期是**正常**的路径（用户在两分钟后才输完码）， 不是故障。而 Redis 不可用是故障，抛。
   */
  public boolean consumeTicket(String ticket, long userId) {
    if (ticket == null || ticket.isBlank()) {
      return false;
    }
    Object raw;
    try {
      raw = redisTemplate.opsForValue().getAndDelete(ticketKey(ticket));
    } catch (Exception ex) {
      throw unavailable("消费二次验证票据", ex);
    }
    return raw instanceof Number number && number.longValue() == userId;
  }

  // ===== 失败计数与锁定 =====

  /** 当前失败次数；无记录为 0。 */
  public int failureCount(long userId) {
    Object raw;
    try {
      raw = redisTemplate.opsForValue().get(failKey(userId));
    } catch (Exception ex) {
      throw unavailable("读取二次验证失败计数", ex);
    }
    return raw instanceof Number number ? number.intValue() : 0;
  }

  /**
   * 记录一次失败，返回累计次数。
   *
   * <p><b>只在计数达到阈值那一次设 TTL</b>——不是第一次失败就设。这两种写法的可观测差别是 锁定窗口的<b>起点</b>：本法的窗口从"达到阈值的那次失败"起算（连续试了 5
   * 次才开始计时）， 而"第一次就设"会让窗口从第 1 次失败起算，等于把 5 次尝试压缩进一个已经在倒计时的窗口里。
   *
   * <p>仓内既有的计数惯例（{@code AuthService} 的 {@code auth:fail:}）是 {@code increment} 后 <b>无条件</b> {@code
   * expire}：每一次失败都续一整窗，于是"一直失败"就"一直锁着"。 对登录限流那是有意的；对 MFA 则不然——这里锁的是<b>一个人</b>，而窗口内他连试都不能试。 故本法的
   * {@code expire} 条件是 {@code count == maxAttempts}（<b>恰好</b>达到，不是 {@code >=}）：
   * 阈值之后即便因并发多记了一次，也不会把锁再续一窗。
   */
  public int recordFailure(long userId) {
    String key = failKey(userId);
    Long count;
    try {
      count = redisTemplate.opsForValue().increment(key);
      if (count != null && count == maxAttempts) {
        redisTemplate.expire(key, lockWindow);
      }
    } catch (Exception ex) {
      throw unavailable("记录二次验证失败", ex);
    }
    int total = count == null ? 0 : count.intValue();
    log.warn("2FA 验证失败：userId={}，累计 {}/{} 次", userId, total, maxAttempts);
    return total;
  }

  /**
   * 剩余锁定秒数；{@code 0} 表示未锁定。
   *
   * <p><b>计数已满但 TTL 不在了 ⇒ 补回整窗并报已锁</b>。这是个真实存在的洞，不是防御性编程： 仓内既有的 {@code increment} + {@code expire}
   * 是<b>两次</b>调用，中间断掉（或键被别的路径 覆盖）就会留下一个"计数是满的、但没有过期时间"的键——而 {@code INCR} 会一直让它满着。 若这里把"TTL ≤
   * 0"读成"没锁"，那个人的 2FA 将<b>永久</b>不可用（重启进程也没用，键在 Redis 里）， 且症状是"这个人怎么试都不行"，排查方向会整个跑偏。补窗的代价是一个
   * TTL，收益是从"永久"变回"15 分钟"。
   *
   * <p><b>为什么取毫秒而不是 {@code getExpire(key)}（秒）</b>——这条是实测推出来的，两个方向都会出错：
   *
   * <ul>
   *   <li>秒精度下 Redis 把"还剩 400ms"按四舍五入报成 {@code 0}，而 {@code ttl <= 0} 正是上面那条"窗口缺失" 的判据 ⇒
   *       一次恰好落在窗口最后半秒的验证会<b>把锁再续一整窗（900 秒）</b>。补窗的补救措施自己变成了延长锁的手段。
   *   <li>返回值仍要向上取整到秒：直接 {@code ttlMillis / 1000} 会把"还剩 400ms"报成 {@code 0}，而调用方 （{@code
   *       MfaVerificationService}）判的是 {@code > 0} ⇒ <b>还锁着的人会被放行一次尝试</b>。
   * </ul>
   *
   * <p>即"补窗判据"要的是"键上到底有没有 TTL"，"返回值"要的是"还锁不锁着"，两者精度需求相反，故一次读、两种处理。
   */
  public long lockRemainingSeconds(long userId) {
    if (failureCount(userId) < maxAttempts) {
      return 0;
    }
    String key = failKey(userId);
    Long ttlMillis;
    try {
      ttlMillis = redisTemplate.getExpire(key, TimeUnit.MILLISECONDS);
      if (ttlMillis == null || ttlMillis <= 0) {
        redisTemplate.expire(key, lockWindow);
        log.warn("2FA 失败计数已满但锁定窗口缺失，已补回整窗：userId={}", userId);
        return lockWindow.toSeconds();
      }
    } catch (Exception ex) {
      throw unavailable("读取二次验证锁定窗口", ex);
    }
    return (ttlMillis + 999) / 1000;
  }

  /** 验证成功后清零（连同锁定窗口）。 */
  public void clearFailures(long userId) {
    try {
      redisTemplate.delete(failKey(userId));
    } catch (Exception ex) {
      throw unavailable("清除二次验证失败计数", ex);
    }
  }

  // ===== 防重放 =====

  /**
   * 标记某个时间步已被用过，返回"这次标记是不是由我做的"。
   *
   * <p>{@code false} = 这一步<b>已经被用过</b> ⇒ 同一个码的第二次提交，调用方应报 401。
   *
   * <p>{@code retention} 由 {@code TotpService.timeStepRetention()} 给出：它必须 ≥ 该步还会被
   * 接受的全部时长。取短了，被标记的步会在仍可被接受时解禁，同一个码就能在两条票据上各用一次。
   */
  public boolean markTimeStepUsed(long userId, long timeStep, Duration retention) {
    Boolean first;
    try {
      first = redisTemplate.opsForValue().setIfAbsent(usedKey(userId, timeStep), "1", retention);
    } catch (Exception ex) {
      throw unavailable("标记已使用的动态码时间步", ex);
    }
    // setIfAbsent 在管道/事务下可能返回 null；拿不到明确答复时按"已被用过"处理（fail closed）。
    return Boolean.TRUE.equals(first);
  }

  // ===== 键构造（包内可见，供调用形状断言逐字核对键名） =====

  static String ticketKey(String ticket) {
    return TICKET_PREFIX + ticket;
  }

  static String failKey(long userId) {
    return FAIL_PREFIX + userId;
  }

  static String usedKey(long userId, long timeStep) {
    return USED_PREFIX + userId + ":" + timeStep;
  }

  private BusinessException unavailable(String action, Exception cause) {
    // 不回显 Redis 的原始消息到响应里（它可能含连接串/主机名），只留在日志。
    log.error("2FA 状态存储不可用（{}）—— 拒绝本次操作，不降级为单因素", action, cause);
    return new BusinessException(ErrorCode.MFA_STORE_UNAVAILABLE);
  }
}
