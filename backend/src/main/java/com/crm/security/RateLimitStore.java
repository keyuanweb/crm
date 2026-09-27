package com.crm.security;

import java.time.Duration;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

/**
 * 限流计数的存储协议（100-rate-limit-consolidation）：Redis 固定窗口，全仓唯一碰计数原语的地方。
 *
 * <p><b>为什么是 Redis 而不是进程内桶</b>：本项要消灭的<b>正是</b>「单 JVM 内存桶」——多实例部署时 每个实例各数一份，真实阈值 = 配置值 ×
 * 实例数，而每个实例单独看都「没超限」。
 *
 * <p><b>为什么只用 {@code increment} + 条件 {@code expire}，不用 Lua</b>：{@code INCR} 与 {@code EXPIRE}
 * 是两次调用，理论上能被别的客户端插进来。但（a）本批的原子性需求远比 MFA 的 {@code GETDEL}/{@code SETNX} 弱
 * ——最坏后果是「多放行一次」，不是「票据被用两次」；（b）测试替身 {@code InMemoryRedisTestSupport} <b>刻意不实现</b>管道/事务/Lua，而裸 mock
 * 下 {@code execute(script)} 会返回 {@code null} ⇒ 上 Lua 等于再开一条<b>静默 no-op 的假绿通道</b>。
 *
 * <p><b>{@code expire} 只在 {@code count == 1} 那次做</b>（照 {@code MfaStateStore.recordFailure} 的正确范式，
 * <b>不照</b> {@code AuthService.recordFailure} 的无条件续窗）。后者对<b>登录失败计数</b>是有意的
 * （「一直失败就一直锁」），但搬到<b>请求限流</b>上会把「3 次 / 60 秒」事实上变成「每 60 秒只准过 3 次、
 * 之后每来一次就把窗口推后一次」，症状是「这个客户端怎么都不行」，而单看每一行代码都对。
 *
 * <p><b>读时补窗并放行</b>（本类唯一一处与 {@code MfaStateStore} <b>刻意相反</b>的处置）： {@code INCR}
 * 成功后进程挂掉、或键被别的路径覆盖，会留下「计数 ≥ 阈值但<b>没有 TTL</b>」的键， 而 {@code INCR} 会一直让它满着 ⇒ 该主体<b>永久
 * 429</b>（重启进程无效，键在 Redis 里）， 排查方向会整个跑偏。处置是：判超限时发现 TTL 没了，就<b>补回整窗、记一条 warn、放行本次</b>。
 *
 * <p>与 {@code MfaStateStore.lockRemainingSeconds}（补窗后<b>继续锁</b>）相反的理由：那里锁的是「一个人的一个流程」，
 * 多放一次意味着「绕过一次二次验证」；这里锁的是「一个主体一个窗口的请求配额」，多放一次的代价是 <b>一个请求</b>——而误判的代价是一个客户端在窗口长度内谁都救不了。⚠️
 * 补窗后<b>计数不清零</b>： 那个数字是真实发生过的请求数，不能因为窗口丢了就当作没发生；补窗的作用是让键<b>有了过期时间</b>， 使「永久」变回「一个窗口」。
 *
 * <p><b>失败立场是 fail open</b>（依据 {@code MfaStateStore} 类 javadoc 对两种立场的论述：那里说 fail open
 * 「对限流是合理的——代价只是限流暂时失效，而不放行会让整个系统在 Redis 抖动时不可用」）。 ⚠️ 只 catch {@link DataAccessException}，<b>不
 * catch 裸 {@code Exception}</b>：那会把「键构造写错」 「类型不对」这类<b>真 bug</b> 也静默降级成放行，症状变成「限流时有时无」。也不提供 {@code
 * fail-open=false} 开关——那等于给运维一个「Redis 一抖、全站导出 503」的自伤按钮。
 *
 * <p>⚠️ <b>落点就一个类型，不是两个</b>：{@link RedisConnectionFailureException} <b>本身就是</b> {@code
 * DataAccessException} 的子类，故 catch 父类即覆盖「Redis 连不上」与「Redis 报错」两种情形 （写成 {@code catch
 * (RedisConnectionFailureException | DataAccessException)} 是**编译错误**—— 多 catch 不允许一支被另一支涵盖）。测试替身
 * {@code failOnKeyPrefix} 注入的正是 {@code RedisConnectionFailureException}，所以 IT 里那条 fail-open
 * 用例打的是同一分支。
 *
 * <p><b>配额语义（104-ai-content-generation，2026-09-27 起）</b>：{@link #usage} / {@link #charge}
 * 是本类承载的<b>第二种</b>计数语义—— 值是<b>累计量</b>（AI 端点里是 token 数），不是请求次数。判定<b>不由本类做</b>：{@link #record}
 * 自带"次数是否超阈值"的判定，而配额把判定留给调用方 （阈值多少、耗尽之后回什么码，是业务决定，见 {@code
 * AiTokenBudget}）。两者共用同一套存储协议与失败立场，所以同居本类——这也让本类 "全仓唯一碰计数原语的地方"那句继续成立，而不是被第二个计数实现悄悄架空。
 *
 * <p>⚠️ 配额两方法<b>不</b>复用 {@link #record} 那条"读时补窗并放行"的处置：那个处置救的是"计数已满但窗口丢了 ⇒ 该主体<b>永久</b> 429"，
 * 而配额键里已经带了日期（{@code …:{yyyyMMdd}}），TTL 丢了也只影响当天、不会跨天累加，于是补窗在这里没有要救的东西。
 */
@Component
public class RateLimitStore {

  private static final Logger log = LoggerFactory.getLogger(RateLimitStore.class);

  private final RedisTemplate<String, Object> redisTemplate;

  public RateLimitStore(RedisTemplate<String, Object> redisTemplate) {
    this.redisTemplate = redisTemplate;
  }

  /**
   * 记一次请求并判定是否放行。
   *
   * @param key 计数键（由 {@link RateLimitKeys} 构造）
   * @param limit 窗口内允许的请求数
   * @param windowSeconds 窗口长度（秒）
   * @return {@code 0} = 放行；{@code > 0} = 拒绝，值为建议的 {@code Retry-After} 秒数
   */
  public long record(String key, int limit, long windowSeconds) {
    Long count;
    try {
      count = redisTemplate.opsForValue().increment(key);
      if (count == null) {
        // 管道/事务下 increment 可能返回 null：拿不到明确答复时放行（fail open）。
        log.warn("限流计数未返回结果，本次放行（fail open）：key={}", key);
        return 0;
      }
      if (count == 1L) {
        // 只在首次设 TTL。真 Redis 的 INCR 不修改已有 TTL，故这一步不会给窗口续期。
        redisTemplate.expire(key, Duration.ofSeconds(windowSeconds));
      }
      if (count <= limit) {
        return 0;
      }
    } catch (DataAccessException ex) {
      log.warn("限流计数不可用，本次放行（fail open）：key={}，原因={}", key, ex.getMessage());
      return 0;
    }
    return retryAfterSeconds(key, windowSeconds);
  }

  /**
   * 读取一个<b>配额</b>键的已用量（配额语义：值是累计量，不是请求次数）。
   *
   * <p><b>键不存在或 Redis 不可用 ⇒ 返回 0</b>（fail open，与 {@link #record} 同一立场）：拿不到已用量时"当作没花过"
   * 会让预算暂时失效，而"当作花光了"会让全站 AI 端点在 Redis 抖动期间不可用——后者更坏（同 {@link #record} 的立场论述）。
   *
   * <p>⚠️ 值<b>不是数字时也只警告、按 0 计</b>，不抛：序列化器与写入方不匹配（比如写进去的是字符串 {@code "42"}）是一种真 bug，
   * 但它的症状会是"预算怎么都不生效"；把它变成异常则会让 AI 端点直接 500，那是用一个更大的故障替换一个小故障。日志里带上类型名，让 排查有落点。
   */
  public long usage(String key) {
    Object raw;
    try {
      raw = redisTemplate.opsForValue().get(key);
    } catch (DataAccessException ex) {
      log.warn("配额已用量不可读，按 0 计（fail open）：key={}，原因={}", key, ex.getMessage());
      return 0;
    }
    if (raw == null) {
      return 0;
    }
    if (raw instanceof Number number) {
      return number.longValue();
    }
    try {
      return Long.parseLong(raw.toString().trim());
    } catch (NumberFormatException ex) {
      log.warn("配额键的值不是数字，按 0 计（fail open）：key={} type={}", key, raw.getClass().getName());
      return 0;
    }
  }

  /**
   * 记一次<b>配额</b>消费（{@code amount} 为累计量，如本次调用的 token 数）。
   *
   * <p>{@code amount <= 0} 直接返回、<b>不写键</b>：一个没花钱的调用不该在 Redis 里留下一个键（也让"预算键存在"这件事继续等价于"今天真的花过钱"）。
   *
   * <p>TTL 只在<b>首次写入</b>那次设置（同 {@link #record} 的正确范式，不无条件续期）。⚠️ 这里的 TTL 是<b>清理</b>而不是"窗口"：
   * 键名里已经带了日期， 即使 TTL 丢了也不会跨天累加，只是键会多躺一会儿。
   */
  public void charge(String key, long amount, long windowSeconds) {
    if (amount <= 0) {
      return;
    }
    Long count;
    try {
      count = redisTemplate.opsForValue().increment(key, amount);
      if (count == null) {
        // 管道/事务下 increment 可能返回 null：拿不到明确答复时不记（fail open）。
        log.warn("配额计数未返回结果，本次消费未记（fail open）：key={}", key);
        return;
      }
      if (count == amount) {
        // 首次写入：此时计数恰好等于本次增量。
        redisTemplate.expire(key, Duration.ofSeconds(windowSeconds));
      }
    } catch (DataAccessException ex) {
      log.warn("配额计数不可用，本次消费未记（fail open）：key={}，原因={}", key, ex.getMessage());
    }
  }

  /** 已超限：读取窗口剩余时间；窗口缺失则补回整窗并放行本次。 */
  private long retryAfterSeconds(String key, long windowSeconds) {
    Long ttlMillis;
    try {
      ttlMillis = redisTemplate.getExpire(key, TimeUnit.MILLISECONDS);
      if (ttlMillis == null || ttlMillis <= 0) {
        // 计数已满但窗口丢了 ⇒ 不补窗的话该主体永久 429（重启无效）。补回整窗并放行本次。
        redisTemplate.expire(key, Duration.ofSeconds(windowSeconds));
        log.warn("限流计数已超阈值但窗口缺失，已补回整窗并放行本次：key={}", key);
        return 0;
      }
    } catch (DataAccessException ex) {
      log.warn("限流窗口不可读，本次放行（fail open）：key={}，原因={}", key, ex.getMessage());
      return 0;
    }
    // 向上取整（照 MfaStateStore.lockRemainingSeconds 的算法）：直接除以 1000 会把「还剩 400ms」
    // 报成 0，而调用方判的是 > 0 ⇒ 该被拒的请求会被放行一次。
    return (ttlMillis + 999) / 1000;
  }
}
