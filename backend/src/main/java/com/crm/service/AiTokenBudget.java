package com.crm.service;

import com.crm.common.RateLimitExceededException;
import com.crm.config.AiStatus;
import com.crm.security.RateLimitStore;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 每用户每日 token 预算闸（104-ai-content-generation，FR-015 / FR-017）。
 *
 * <p><b>它是什么、不是什么</b>：{@code @RateLimit} 管的是<b>突发</b>（连点、脚本循环、前端重试风暴），本类管的是<b>总量</b>——一天里 一个人能烧掉多少
 * token。两者不可互替：突发闸拦不住"每 7 秒点一次、点一整天"，总量闸拦不住"一分钟点 600 次"。
 *
 * <p><b>键形</b>：{@code ai:gen:budget:user:{userId}:{yyyyMMdd}}（plan D8）。
 *
 * <ul>
 *   <li>前缀 {@code ai:gen:} 而非 {@code ai:}——后者<b>已被 022 占用</b>（{@code SuggestionService} 的 {@code
 *       IGNORE_PREFIX = "ai:ignore:"}，TTL 90 天）。两族互不影响：022 的忽略集不会误伤本项，反之亦然。 ⚠️ 这条是 {@code
 *       AiContentIT} 的 U7 在看着的，不是注释里的自我声明。
 *   <li>日期进键 ⇒ "每日"这件事<b>由键本身表达</b>，不依赖 TTL 到期的那一刻。后果是<b>跨天即重置</b>，无需任何定时任务；
 *       代价是换时区部署会让"今天"换一个键（一天内换时区 = 一次额外重置，同 {@code RateLimitKeys.PREFIX} 改动的告警）。
 *   <li>{@code user} 那一段是**身份种类**（照 {@code RateLimitKeys} 的 {@code rl:{scope}:user:{id}}
 *       形状）。将来若要加全局日闸， 正确做法是新增一个**同族**的键（如 {@code ai:gen:budget:global:all:{yyyyMMdd}}），而不是改这一段——
 *       改这一段会让今天已经记下的用量<b>读到新的桶</b>，症状是"预算突然又满了"。
 * </ul>
 *
 * <p><b>两段式：先查后记（check-then-charge），不是预扣</b>。查在出站之前（用"已用量"判），记在成功返回之后（用 SDK 给的 {@code usage}，FR-016
 * 明令<b>不得估算</b>）。这样做的代价是一条已知的窗口：并发 N 次调用可能各自通过检查、让当日用量超出预算至多 (N−1) 次调用的成本。
 * 接受它的理由：{@code @RateLimit}（10 次/60 秒/用户）就是 N 的上界，而预扣需要"按 max-tokens 预扣、按实际用量退回"， 那是两次写 +
 * 一条补偿路径，复杂度换来的只是把这一个小数抹掉。
 *
 * <p><b>失败立场是 fail open</b>（与 {@link RateLimitStore} 同一立场，理由见该类 javadoc）：Redis 不可用时<b>放行</b>，因为
 * "Redis 一抖 ⇒ 全站 AI 端点不可用"是更坏的失败。⚠️
 * 这条立场对<b>花钱</b>的东西比对限流更值得犹豫，故写明它的边界：真正的成本上界应当由<b>服务商侧的用量上限/账单告警</b>兜底， 本闸是<b>应用内的第一道</b>，不是最后一道。
 *
 * <p><b>为什么不把逻辑塞进 {@code AiContentService}</b>：出网点只负责"把两段发出去、把用量带回来"，它不该知道"谁在花钱"的记账规则； 而 {@link
 * RateLimitStore} 是唯一的计数原语落点，本类只在它之上定义<b>配额语义</b>（值 = token 累计量，不是请求次数）。
 */
@Component
public class AiTokenBudget {

  private static final Logger log = LoggerFactory.getLogger(AiTokenBudget.class);

  /** 键族前缀（plan D8）。⚠️ 改它等于把当天已记的用量全部作废（同 {@code RateLimitKeys.PREFIX} 的告警）。 */
  static final String PREFIX = "ai:gen:budget:";

  private static final DateTimeFormatter DAY = DateTimeFormatter.BASIC_ISO_DATE;

  private final RateLimitStore store;
  private final AiStatus aiStatus;

  public AiTokenBudget(RateLimitStore store, AiStatus aiStatus) {
    this.store = store;
    this.aiStatus = aiStatus;
  }

  /**
   * 出站之前判预算；耗尽即抛 {@link RateLimitExceededException}（→ 429 + {@code Retry-After}，FR-017 的"受控
   * 429、不静默失败"）。
   *
   * <p>{@code Retry-After} 给的是<b>到明天零点</b>的秒数，而不是某个窗口长度——这是本闸的语义决定的：耗尽的不是一个 60 秒窗口，是今天。
   *
   * @param userId 花钱的人；**恒非空**（见 {@link #requireUserId}）
   */
  public void check(Long userId) {
    long budget = aiStatus.dailyTokenBudget();
    if (budget <= 0) {
      // 显式关闭：<=0 表示"不设上限"。这不是"配置漏了"的默认值——默认值是正数（见 AiStatus）。
      return;
    }
    String key = key(requireUserId(userId), LocalDate.now());
    long used = store.usage(key);
    if (used >= budget) {
      log.warn("AI 日预算耗尽：userId={} used={} budget={}", userId, used, budget);
      throw new RateLimitExceededException(secondsUntilTomorrow(ZonedDateTime.now()));
    }
  }

  /**
   * 成功返回后记账（单位：token，取 SDK 的 {@code usage}）。
   *
   * <p>失败路径<b>不记账</b>——拿不到 {@code usage} 时唯一诚实的做法是不记，而 FR-016 同时禁止了估算。代价是"上游真烧了钱但我们不知道"的调用 不计入本闸；这与
   * FR-016"不得估算"是同一条纪律的两面。
   *
   * <p>窗口长度 = 到明天零点的秒数，只在该键<b>首次写入</b>时设置（见 {@link RateLimitStore#charge}）。它是<b>清理</b>用的： 即使 TTL
   * 丢了，键里的日期也保证它只在当天生效， 不会跨天累加。
   */
  public void charge(Long userId, long tokens) {
    long budget = aiStatus.dailyTokenBudget();
    if (budget <= 0) {
      return;
    }
    String key = key(requireUserId(userId), LocalDate.now());
    store.charge(key, tokens, secondsUntilTomorrow(ZonedDateTime.now()));
  }

  /**
   * 今天的预算键。
   *
   * <p>包级可见 + 具名（照 {@code RateLimitKeys}
   * 那组的先例）：调用形状可以被逐字核对。这不是形式主义——"键名写错"是一种<b>没有任何行为用例能发现</b>的缺陷：
   * 它不报错、不改变响应，只是让该共用一个桶的两次调用各数一份，或者让两个概念悄悄合并成一个。
   */
  static String key(long userId, LocalDate day) {
    return PREFIX + "user:" + userId + ":" + DAY.format(day);
  }

  /**
   * 到明天零点的秒数（{@code Retry-After} 用）。
   *
   * <p>纯函数、显式传入 {@code now}：这是本类唯一需要断言具体数值的地方，而"取当前时间"的写法只能断出一个宽区间（0 &lt; x ≤ 86400），
   * 断言强度差了一个量级。向上取整，理由同 {@code RateLimitStore.retryAfterSeconds}：向下取整会把"还剩 400ms"报成 0。
   */
  static long secondsUntilTomorrow(ZonedDateTime now) {
    ZonedDateTime tomorrow = now.toLocalDate().plusDays(1).atStartOfDay(now.getZone());
    long seconds = Duration.between(now, tomorrow).getSeconds();
    return Math.max(1, seconds);
  }

  /**
   * 花钱的人必须显式给出，**不允许 null**。
   *
   * <p>本项目前唯一的调用方是已认证的端点（{@code @RequirePermission}），故 {@code userId} 恒非空。这里不做"null 就跳过预算"的降级：
   * 那会让一个将来新增的非用户触发路径（定时任务、内部批处理）<b>静默地不受预算约束</b>，而症状是"账单变高但闸看起来在工作"。 那种调用方必须自己决定记在哪个桶上（传一个约定的
   * id，或改造本闸），而不是传 null。
   */
  private static long requireUserId(Long userId) {
    return Objects.requireNonNull(userId, "AI 预算闸需要一个明确的 userId：非用户触发的调用必须显式指定记账对象").longValue();
  }
}
