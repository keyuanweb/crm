package com.crm.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

/**
 * {@link RateLimitStore} 的存储协议（100-rate-limit-consolidation，T5 + T6 的原语形状）。
 *
 * <p><b>本类问的是"计数怎么算"，不问"这算谁的"</b>：后者在 {@link RateLimitIdentityTest}（纯函数）与 {@link
 * RateLimiterShapeTest}（组合层调用形状）里。分开的理由见 {@code RateLimiter} 类 javadoc —— 混在一起时
 * 「键名拼错」会在装了替身的用例里表现成「计数没落到预期键上」，很容易被读成「没接线」。
 *
 * <p><b>本类用裸 {@code mock(RedisTemplate)} 而不是功能型替身</b>（照 {@code MfaStateStoreTest} 的论述）： 替身会让 {@code
 * get}+{@code set} 的劣解与 {@code increment} 在功能上一样对，于是「用了哪个原语」这个问题 <b>问不出来</b>——而那正是 T6 唯一要问的。⚠️
 * 反过来，裸 mock 下未打桩的 {@code increment} 返回 {@code null} ⇒ 走 fail-open ⇒ 限流是 no-op。**「在裸 mock
 * 上看到放行」不能当作行为证据**， 那是本批最大的假绿通道（见 tasks.md 验证节与 falsification-evidence.md）。
 */
class RateLimitStoreTest {

  private static final String KEY = "rl:test-scope:user:42";

  private static final int LIMIT = 3;

  private static final long WINDOW = 60L;

  private ValueOperations<String, Object> valueOps;

  private RedisTemplate<String, Object> template;

  private RateLimitStore store;

  @SuppressWarnings("unchecked")
  private static RedisTemplate<String, Object> mockTemplate() {
    return mock(RedisTemplate.class);
  }

  @SuppressWarnings("unchecked")
  private static ValueOperations<String, Object> mockValueOps() {
    return mock(ValueOperations.class);
  }

  @BeforeEach
  void setUp() {
    template = mockTemplate();
    valueOps = mockValueOps();
    when(template.opsForValue()).thenReturn(valueOps);
    store = new RateLimitStore(template);
  }

  // ===== 原语与窗口的起算点（T6 + T5 的一半） =====

  /**
   * 计数必须走 {@code increment}，且整条路径<b>不出现</b> {@code get} / {@code set}。
   *
   * <p>换成 {@code get} + {@code set} 之后，单线程下可观测行为完全相同 ⇒ <b>任何集成测试都区分不出正解与劣解</b> （MockMvc
   * 是单线程的，并发没有发生）。故这条断言是「并发不漏计」在没有并发压测时的唯一代表。
   *
   * <p><b>为什么两个原语都要喂桩</b>（照 {@code MfaStateStoreTest} 被实测逼出来的写法）：只桩 {@code increment}
   * 时劣解会先在某条断言上以「计数没到」的形式红掉，失败信息指向计数比较那行，而真正的问题在<b>用了哪个原语</b>。 两个都喂上之后，红的是 {@code verify(...,
   * never())}，直接点名被多调用的方法。
   */
  @Test
  @DisplayName("用 increment 计数，不出现 get / set")
  void countsWithIncrementNeverWithGetAndSet() {
    when(valueOps.increment(KEY)).thenReturn(1L);
    when(valueOps.get(KEY)).thenReturn(0); // 见 javadoc：让红落在调用形状上
    // set 返回 void，桩不了；未打桩的 void mock 调用本身就是空操作，无需喂桩。

    assertEquals(0, store.record(KEY, LIMIT, WINDOW));

    verify(valueOps).increment(KEY);
    verify(valueOps, never()).get(anyString());
    verify(valueOps, never()).set(anyString(), any());
  }

  /**
   * <b>窗口只在第一次计数时设置</b>（照 {@code MfaStateStore.recordFailure} 的范式，<b>不照</b> {@code
   * AuthService.recordFailure} 的无条件续窗）。
   *
   * <p>无条件续窗会把「3 次 / 60 秒」事实上变成「每 60 秒只准过 3 次，之后每来一次就把窗口推后一次」—— 一个稳定 4
   * 次/分钟的客户端<b>永远过不去</b>，而每一行代码单独看都对。
   */
  @Test
  @DisplayName("只有首次计数才设 TTL；后续请求不续窗")
  void theWindowIsSetOnlyOnTheFirstRequest() {
    AtomicLong counter = new AtomicLong();
    when(valueOps.increment(KEY)).thenAnswer(inv -> counter.incrementAndGet());

    for (int i = 1; i <= LIMIT; i++) {
      assertEquals(0, store.record(KEY, LIMIT, WINDOW), "第 " + i + " 次在阈值内，应放行");
    }

    verify(template, times(1)).expire(KEY, Duration.ofSeconds(WINDOW));
  }

  @Test
  @DisplayName("阈值内放行，且不去读 TTL")
  void belowTheLimitIsAllowedWithoutReadingTheTtl() {
    when(valueOps.increment(KEY)).thenReturn((long) LIMIT);

    assertEquals(0, store.record(KEY, LIMIT, WINDOW), "第 limit 次仍在配额内");
    verify(template, never()).getExpire(anyString(), any(TimeUnit.class));
  }

  @Test
  @DisplayName("刚好超限一次即拒，报剩余秒数（不四舍五入到 0）")
  void overTheLimitReportsTheRemainingSeconds() {
    when(valueOps.increment(KEY)).thenReturn((long) LIMIT + 1);
    when(template.getExpire(KEY, TimeUnit.MILLISECONDS)).thenReturn(120_000L);

    assertEquals(120, store.record(KEY, LIMIT, WINDOW));
    verify(template, never()).expire(anyString(), any(Duration.class));
  }

  /**
   * 窗口只剩不到 1 秒 ⇒ <b>仍报「已超限」</b>（向上取整成 1）。
   *
   * <p>直接整除会把 400ms 报成 {@code 0}，而调用方判的是 {@code > 0} ⇒ <b>该被拒的请求被放行一次</b> （照 {@code
   * MfaStateStore.lockRemainingSeconds} 的算法：读毫秒、向上取整）。
   */
  @Test
  @DisplayName("窗口只剩 400ms ⇒ 报 1 秒（仍拒绝）")
  void aWindowInItsLastSecondStillRejects() {
    when(valueOps.increment(KEY)).thenReturn((long) LIMIT + 1);
    when(template.getExpire(KEY, TimeUnit.MILLISECONDS)).thenReturn(400L);

    assertEquals(1, store.record(KEY, LIMIT, WINDOW), "报 0 会被调用方读成「放行」");
  }

  // ===== 读时补窗：与 MfaStateStore 刻意相反的那一处 =====

  /**
   * <b>计数已满但窗口丢了 ⇒ 补回整窗并放行本次</b>（T5 的核心判据）。
   *
   * <p>造这个键的现实路径是 {@code INCR} 成功、紧跟的 {@code EXPIRE} 那次调用断了（两次调用，不是原子的一次）， 于是留下「计数满着、但没有过期时间」的键，而
   * {@code INCR} 会一直让它满着 ⇒ 该主体<b>永久 429</b> （键在 Redis 里，重启进程无效），症状是「这个人怎么都不行」，排查方向会整个跑偏。
   *
   * <p>处置与 {@code MfaStateStore.lockRemainingSeconds}（补窗后<b>继续锁</b>）<b>刻意相反</b>：那里锁的是
   * 「一个人的一个流程」，多放一次等于绕过一次二次验证；这里锁的是「一个主体一个窗口的请求配额」，多放一次的代价是
   * <b>一个请求</b>，而误判的代价是一个客户端在窗口长度内谁都救不了。故此处返回 {@code 0} = <b>放行</b>。
   *
   * <p>用 {@code -1}（有键无 TTL）与 {@code -2}（键不存在）两种都验：前者是上面那条真实路径，后者是键被别的 路径删掉——两种都不该让人永久不可用。
   */
  @Test
  @DisplayName("计数已满但 TTL ≤ 0 ⇒ 补回整窗并放行本次（堵掉永久 429）")
  void aFullCounterWithoutATtlIsReArmedAndAllowed() {
    when(valueOps.increment(KEY)).thenReturn((long) LIMIT + 1);
    when(template.getExpire(KEY, TimeUnit.MILLISECONDS)).thenReturn(-1L);

    assertEquals(0, store.record(KEY, LIMIT, WINDOW), "补窗后本次必须放行，否则补窗等于没补");
    verify(template).expire(KEY, Duration.ofSeconds(WINDOW));

    when(template.getExpire(KEY, TimeUnit.MILLISECONDS)).thenReturn(-2L);
    assertEquals(0, store.record(KEY, LIMIT, WINDOW));
    verify(template, times(2)).expire(KEY, Duration.ofSeconds(WINDOW));
  }

  // ===== fail open：每一条失效路径单独验 =====

  /**
   * 计数的<b>每一次</b>失败都必须放行，不能拒。
   *
   * <p>依据 {@code MfaStateStore} 类 javadoc 对两种立场的论述：那里说 fail open「对限流是合理的——代价只是限流 暂时失效，而不放行会让整个系统在
   * Redis 抖动时不可用」。⚠️ 只 catch {@code DataAccessException} （{@code RedisConnectionFailureException}
   * 是它的子类，写成多 catch 是编译错误）， <b>不 catch 裸 {@code Exception}</b>——那会把「键构造写错」这类真 bug 也静默降级成放行。
   *
   * <p>{@code increment} 返回 {@code null}（裸 mock 的默认答案、也是管道/事务下的可能值）同属「拿不到明确答复」，
   * 一律放行：这条同时是<b>假绿陷阱的书面记录</b>——裸 mock 下不装功能型替身时，限流就是这样静默失效的。
   */
  @Test
  @DisplayName("存储故障 / 无明确答复 ⇒ 放行（fail open），且不再去读 TTL")
  void everyStoreFailureIsFailOpen() {
    doThrow(new RedisConnectionFailureException("redis down")).when(valueOps).increment(KEY);
    assertEquals(0, store.record(KEY, LIMIT, WINDOW), "Redis 挂掉必须放行，否则全站导出在抖动期间不可用");

    // 必须用 doReturn 而不是 when(...)：when() 会**真的去调**那个桩，而它上一步刚被 doThrow 设成抛异常，
    // 异常会在测试方法里炸开而不是被 SUT 的 catch 接住（照 MfaStateStoreTest 的同款论述）。
    doReturn(null).when(valueOps).increment(KEY);
    assertEquals(0, store.record(KEY, LIMIT, WINDOW), "拿不到计数不能当作「已超限」");

    verify(template, never()).getExpire(anyString(), any(TimeUnit.class));
  }

  /** 读窗口失败同属 fail open：拒绝路径上 Redis 可能正好不可用，此时不能把请求变成 5xx。 */
  @Test
  @DisplayName("读窗口失败 ⇒ 放行（fail open）")
  void anUnreadableWindowIsFailOpen() {
    when(valueOps.increment(KEY)).thenReturn((long) LIMIT + 1);
    doThrow(new RedisConnectionFailureException("redis down"))
        .when(template)
        .getExpire(KEY, TimeUnit.MILLISECONDS);

    assertEquals(0, store.record(KEY, LIMIT, WINDOW));
  }

  // ===== 键族（调用形状：键名写错没有任何行为用例能发现） =====

  @Test
  @DisplayName("键族为 rl:<scope>:<身份>，三种身份互不同键")
  void keysAreScopedByScopeAndIdentity() {
    assertEquals("rl:export-generate:user:42", RateLimitKeys.user("export-generate", 42L));
    assertEquals("rl:open-api-read:key:7", RateLimitKeys.apiKey("open-api-read", 7L));
    assertEquals("rl:public-read:ip:203.0.113.7", RateLimitKeys.ip("public-read", "203.0.113.7"));
    assertEquals("rl:", RateLimitKeys.PREFIX, "前缀与 auth:* 同级，运维按前缀注入故障与监控");
  }

  // ===== 配额语义：同一个类型上的第二种计数（104-ai-content-generation，2026-09-27） =====

  /**
   * 配额<b>读</b>：走 {@code get}，且整条路径<b>不写</b>。
   *
   * <p>与 {@link #countsWithIncrementNeverWithGetAndSet} 是同一条纪律的镜像：两种语义必须各用各的原语。
   * 读路径一旦带上写，就会出现"查一次预算就凭空多记一次消费"这种症状——而它在任何只看响应码的用例里 都看不出来（响应照样是 200）。
   */
  @Test
  @DisplayName("配额读：走 get（不是 increment），且不写键、不设窗")
  void usageReadsWithGetAndNeverWrites() {
    when(valueOps.get(KEY)).thenReturn(1234L);

    assertEquals(1234L, store.usage(KEY));

    verify(valueOps).get(KEY);
    verify(valueOps, never()).increment(anyString());
    verify(valueOps, never()).increment(anyString(), anyLong());
    verify(template, never()).expire(anyString(), any(Duration.class));
  }

  /**
   * 配额读对<b>值的形态</b>宽容：键不存在 ⇒ 0；字符串数字 ⇒ 按数字读；真不是数字 ⇒ 0 而不是抛。
   *
   * <p>后两种是"序列化器与写入方不匹配"这一真 bug 的两种表现。把它变成异常，会让 AI 端点直接 500—— <b>用一个更大的故障替换一个小故障</b>（同 fail open
   * 的立场论述）；而日志里带上类型名，让排查有落点。
   */
  @Test
  @DisplayName("配额读：键不存在 ⇒ 0；字符串数字 ⇒ 按数字读；非数字 ⇒ 0（都不抛）")
  void usageIsLenientAboutTheStoredShape() {
    when(valueOps.get(KEY)).thenReturn(null);
    assertEquals(0L, store.usage(KEY), "键不存在 = 今天还没花过（也让「键存在」等价于「真的花过钱」）");

    when(valueOps.get(KEY)).thenReturn("42");
    assertEquals(42L, store.usage(KEY), "写进去的是字符串数字时仍要读得出来，否则预算静默失效");

    when(valueOps.get(KEY)).thenReturn("不是数字");
    assertEquals(0L, store.usage(KEY), "按 0 计 = 暂时失效；抛异常 = 全站 AI 端点不可用");
  }

  @Test
  @DisplayName("配额读：Redis 故障 ⇒ 0（fail open，与 record 同一立场）")
  void usageIsFailOpen() {
    doThrow(new RedisConnectionFailureException("redis down")).when(valueOps).get(KEY);

    assertEquals(0L, store.usage(KEY), "拿不到已用量时「当作花光了」会让全站在抖动期间不可用");
  }

  /**
   * 配额<b>记</b>：走 {@code increment(key, amount)}，窗口只在首次写入时设。
   *
   * <p>"首次"的判据是 {@code count == amount}（恰好等于本次增量），不是 {@code count == 0}—— 后者在累计量语义下永远为假，于是 TTL
   * 一次都不会被设上。故这条用会真累加的答案 （照 {@link #theWindowIsSetOnlyOnTheFirstRequest} 的 AtomicLong 写法），而不是常量答案。
   */
  @Test
  @DisplayName("配额记：走 increment(key, amount)；首次写入设窗，后续消费不续窗")
  void chargeIncrementsByAmountAndArmsTheWindowOnlyOnce() {
    AtomicLong accumulated = new AtomicLong();
    when(valueOps.increment(KEY, 500L)).thenAnswer(inv -> accumulated.addAndGet(500L));

    store.charge(KEY, 500L, WINDOW);
    store.charge(KEY, 500L, WINDOW);

    verify(valueOps, times(2)).increment(KEY, 500L);
    verify(valueOps, never()).increment(anyString());
    verify(template, times(1)).expire(KEY, Duration.ofSeconds(WINDOW));
  }

  /**
   * {@code amount <= 0} ⇒ <b>一个键都不写</b>。
   *
   * <p>这不是省一次往返的优化：「预算键存在」这件事被当作"今天真的花过钱"的证据（{@code AiTokenBudget} 的 记账判据、以及运维按前缀观察成本），一个没花钱的调用在
   * Redis 里留下键会让这条推断失真。
   */
  @Test
  @DisplayName("配额记：amount ≤ 0 一个键都不写（「键存在」继续等价于「今天真的花过钱」）")
  void chargeWritesNothingForNonPositiveAmounts() {
    store.charge(KEY, 0, WINDOW);
    store.charge(KEY, -5, WINDOW);

    verifyNoInteractions(valueOps);
    verify(template, never()).expire(anyString(), any(Duration.class));
  }

  /** 配额记的失败立场同 {@link #everyStoreFailureIsFailOpen}：不记、不抛，且没拿到答复就不设窗。 */
  @Test
  @DisplayName("配额记：存储故障 / 无明确答复 ⇒ 不记也不抛（fail open）")
  void chargeIsFailOpen() {
    doThrow(new RedisConnectionFailureException("redis down")).when(valueOps).increment(KEY, 500L);
    store.charge(KEY, 500L, WINDOW);

    // doReturn 而不是 when(...)：when() 会真的去调那个刚被设成抛异常的桩（同上一条用例的论述）。
    doReturn(null).when(valueOps).increment(KEY, 500L);
    store.charge(KEY, 500L, WINDOW);

    verify(template, never()).expire(anyString(), any(Duration.class));
  }
}
