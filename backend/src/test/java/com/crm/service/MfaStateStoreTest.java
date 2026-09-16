package com.crm.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.crm.common.BusinessException;
import com.crm.common.ErrorCode;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

/**
 * {@link MfaStateStore} 的<b>调用形状</b>与失败语义（082 第 6 步）。
 *
 * <p><b>为什么必须存在一个"断言 Redis 怎么被调用"的用例</b>——这是本批最容易被后人误解的一点： {@code consumeTicket} 的 {@code
 * getAndDelete} 与 {@code get} + {@code delete}， {@code markTimeStepUsed} 的 {@code setIfAbsent} 与
 * {@code get} + {@code set}， <b>在单线程下的可观测行为完全相同</b>。而 {@code MockMvc} 是单线程的 ⇒
 * <b>任何集成测试都区分不出正解与劣解</b>，无论它断言得多细。故：
 *
 * <ul>
 *   <li>本类钉<b>原子性</b>（用的是哪个原语），
 *   <li>{@code AuthMfaIT} 钉<b>可观测行为</b>（票据只能用一次、同一个码不能重放）。
 * </ul>
 *
 * 劣化后的版本能让 {@code AuthMfaIT} <b>全绿</b>——因为并发没有发生。反过来，只跑本类也不能证明 "用户看到的行为对"。两者缺一不可，且<b>不可互相替代</b>。
 *
 * <p>本类直接 {@code mock(RedisTemplate)} 而不装 {@code InMemoryRedisTestSupport}：后者的 {@code getAndDelete}
 * 与 {@code get} + {@code delete} 在功能上一样对，用它反而<b>看不出</b> 用了哪个原语——那正是本类唯一要问的问题。
 */
class MfaStateStoreTest {

  private static final long USER = 7L;

  private static final int MAX_ATTEMPTS = 5;

  private static final Duration LOCK = Duration.ofSeconds(900);

  private static final String FAIL_KEY = "auth:2fa-fail:7";

  private ValueOperations<String, Object> valueOps;

  private RedisTemplate<String, Object> template;

  private MfaStateStore store;

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
    store = new MfaStateStore(template, MAX_ATTEMPTS, LOCK.toSeconds());
  }

  // ===== 原子原语：本类存在的理由 =====

  /**
   * {@code consumeTicket} 必须用 {@code getAndDelete}，且<b>从不</b>出现 {@code get} 或 {@code delete}。
   *
   * <p>这条断言就是"一张票据 ⇒ 一个会话"在并发下的全部保证。换成 {@code get} + {@code delete} 之后 <b>本类之外没有任何测试会转红</b>（见类
   * javadoc）。
   *
   * <p><b>为什么两个原语都要喂桩</b>（这是被实测逼出来的写法，不是随手写的）：只桩 {@code getAndDelete} 时， 劣化版本在 {@code assertTrue}
   * 那一行就红了——{@code get} 没被桩过、返回 {@code null}， 报错是「expected: &lt;true&gt; but was:
   * &lt;false&gt;」。它<b>确实是红的</b>，但失败信息读起来像 "票据对不上这个用户"，指向 {@code userId} 比较那行，而真正的问题在<b>用了哪个原语</b>。
   * 两个都喂上之后，{@code assertTrue} 通过，红的是下面 {@code verify(..., never())}， 失败信息直接点名被多调用的那个方法 ——
   * <b>断言要红在它真正检查的那件事上</b>。
   */
  @Test
  @DisplayName("consumeTicket 用 getAndDelete，且整条路径不出现 get / delete")
  void consumeTicketUsesTheAtomicPrimitiveOnly() {
    when(valueOps.getAndDelete("auth:2fa-ticket:T")).thenReturn(USER);
    when(valueOps.get("auth:2fa-ticket:T")).thenReturn(USER); // 见 javadoc：让红落在调用形状上

    assertTrue(store.consumeTicket("T", USER));

    verify(valueOps).getAndDelete("auth:2fa-ticket:T");
    verify(valueOps, never()).get(anyString());
    verify(template, never()).delete(anyString());
  }

  @Test
  @DisplayName("consumeTicket：票据不存在 / 不属于该用户 ⇒ false（不抛）")
  void consumeTicketRejectsMissingAndForeignTickets() {
    when(valueOps.getAndDelete("auth:2fa-ticket:MISSING")).thenReturn(null);
    when(valueOps.getAndDelete("auth:2fa-ticket:FOREIGN")).thenReturn(USER + 1);

    assertFalse(store.consumeTicket("MISSING", USER), "票据过期是正常路径，不是故障");
    assertFalse(store.consumeTicket("FOREIGN", USER), "票据不属于这个用户");
    assertFalse(store.consumeTicket(null, USER));
    assertFalse(store.consumeTicket("  ", USER));
  }

  /** {@code setIfAbsent} 是防重放的全部保证；它必须带上调用方给的保留期。 */
  @Test
  @DisplayName("markTimeStepUsed 用 setIfAbsent 并带上保留期，不出现 get/set")
  void markTimeStepUsedUsesSetIfAbsentWithTheRetention() {
    when(valueOps.setIfAbsent("auth:2fa-used:7:59650800", "1", Duration.ofSeconds(90)))
        .thenReturn(true);

    assertTrue(store.markTimeStepUsed(USER, 59650800L, Duration.ofSeconds(90)));

    verify(valueOps, never()).get(anyString());
    verify(valueOps, never()).set(anyString(), any());
  }

  @Test
  @DisplayName("markTimeStepUsed：已被占用 ⇒ false；返回 null（管道下可能）⇒ 也按已占用处理")
  void markTimeStepUsedFailsClosedOnAmbiguity() {
    when(valueOps.setIfAbsent(anyString(), any(), any(Duration.class))).thenReturn(false);
    assertFalse(store.markTimeStepUsed(USER, 1L, Duration.ofSeconds(90)));

    when(valueOps.setIfAbsent(anyString(), any(), any(Duration.class))).thenReturn(null);
    assertFalse(
        store.markTimeStepUsed(USER, 1L, Duration.ofSeconds(90)), "拿不到明确答复时不能当作「我是第一个」——那会放行一次重放");
  }

  // ===== 锁定窗口的起点与补救 =====

  /**
   * 锁定窗口从<b>达到阈值的那次</b>失败起算，不是从第 1 次。
   *
   * <p>仓内既有的计数惯例是无条件 {@code expire}（每次失败都续一整窗）。照抄过来会让窗口 在用户"慢慢试"的过程中一直顺延，等于把 5 次尝试压进一个已经在倒计时的窗口里。
   */
  @Test
  @DisplayName("只有第 5 次失败才设 TTL（窗口从达阈值那次起算）")
  void lockWindowStartsAtTheFailureReachingTheThreshold() {
    AtomicLong counter = new AtomicLong();
    when(valueOps.increment(FAIL_KEY)).thenAnswer(inv -> counter.incrementAndGet());

    for (int i = 1; i < MAX_ATTEMPTS; i++) {
      assertEquals(i, store.recordFailure(USER));
      verify(template, never()).expire(anyString(), any(Duration.class));
    }

    assertEquals(MAX_ATTEMPTS, store.recordFailure(USER));
    verify(template, times(1)).expire(FAIL_KEY, LOCK);
  }

  /** 阈值之后（并发下可能多记一次）不再续窗：否则"再试一次"就等于把锁自己延长一窗。 */
  @Test
  @DisplayName("第 6 次失败不再续窗")
  void failuresBeyondTheThresholdDoNotExtendTheLock() {
    AtomicLong counter = new AtomicLong();
    when(valueOps.increment(FAIL_KEY)).thenAnswer(inv -> counter.incrementAndGet());

    for (int i = 0; i < MAX_ATTEMPTS + 1; i++) {
      store.recordFailure(USER);
    }

    verify(template, times(1)).expire(FAIL_KEY, LOCK);
  }

  @Test
  @DisplayName("未达阈值 ⇒ 报 0（不锁定）")
  void belowTheThresholdIsNotLocked() {
    when(valueOps.get(FAIL_KEY)).thenReturn(MAX_ATTEMPTS - 1);

    assertEquals(0, store.lockRemainingSeconds(USER));
    verify(template, never()).expire(anyString(), any(Duration.class));
  }

  @Test
  @DisplayName("达阈值且窗口在走 ⇒ 报剩余秒数，不补窗")
  void aLiveWindowIsReportedAsIs() {
    when(valueOps.get(FAIL_KEY)).thenReturn(MAX_ATTEMPTS);
    when(template.getExpire(FAIL_KEY, TimeUnit.MILLISECONDS)).thenReturn(120_000L);

    assertEquals(120, store.lockRemainingSeconds(USER));
    verify(template, never()).expire(anyString(), any(Duration.class));
  }

  /**
   * <b>窗口只剩不到 1 秒 ⇒ 仍报「已锁」，且不补窗</b>。
   *
   * <p>这条钉的是两个方向相反、却只差一个精度选择的错误（制造它的是 {@code getExpire(key)} 的秒版）：
   *
   * <ul>
   *   <li>秒精度下 Redis 把 400ms 报成 {@code 0}，与"窗口缺失"（{@code -1}/{@code -2}）撞在同一个值上 ⇒
   *       补窗逻辑被触发，<b>锁被自己续了一整窗</b>；
   *   <li>若改成"按毫秒读、再整除成秒"，400ms 会被报成 {@code 0} ⇒ 调用方判 {@code > 0} 失败， <b>还锁着的人被放行一次尝试</b>。
   * </ul>
   *
   * <p>正解是读毫秒、<b>向上取整</b>：既不会被误判成"窗口缺失"，也不会被报成"没锁"。
   */
  @Test
  @DisplayName("窗口只剩 400ms ⇒ 报 1 秒（仍锁定），且不补窗")
  void aWindowInItsLastSecondIsStillReportedAsLocked() {
    when(valueOps.get(FAIL_KEY)).thenReturn(MAX_ATTEMPTS);
    when(template.getExpire(FAIL_KEY, TimeUnit.MILLISECONDS)).thenReturn(400L);

    assertEquals(1, store.lockRemainingSeconds(USER), "还剩 400ms 也是「锁着」——报 0 会让调用方放行一次尝试");
    verify(template, never()).expire(anyString(), any(Duration.class));
  }

  /**
   * <b>计数已满但 TTL 不在了 ⇒ 补回整窗并报已锁</b>——这条是"永久锁定洞"的唯一防线。
   *
   * <p>把 {@code ttl <= 0} 读成"未锁定"，那个人的 2FA 就会永久不可用（键在 Redis 里，重启进程没用）， 而且症状是"这个人怎么试都不行"，排查方向会整个跑偏。
   *
   * <p>造这个键的现实路径是 {@code increment} 成功、紧跟的 {@code expire} 那次调用断了（两次调用，不是原子的一次），
   * 于是留下一个"计数满着、但没有过期时间"的键；而 {@code INCR} 会一直让它满着。故用 {@code -1}（有键无 TTL） 而不是 {@code -2}（键不存在）。
   */
  @Test
  @DisplayName("计数已满但 TTL ≤ 0 ⇒ 补回整窗并报已锁（堵掉永久锁定）")
  void aFullCounterWithoutATtlIsReArmedInsteadOfLockingForever() {
    when(valueOps.get(FAIL_KEY)).thenReturn(MAX_ATTEMPTS);
    when(template.getExpire(FAIL_KEY, TimeUnit.MILLISECONDS)).thenReturn(-1L);

    assertEquals(LOCK.toSeconds(), store.lockRemainingSeconds(USER), "应报「已锁」，而不是「没锁」");
    verify(template).expire(FAIL_KEY, LOCK);
  }

  // ===== 失败语义：全仓唯一的 fail-closed 边界 =====

  /**
   * <b>每一个</b>公开方法在 Redis 不可用时都必须以 {@code MFA_STORE_UNAVAILABLE} 失败。
   *
   * <p>这里逐个方法写，而不是只挑一个代表：本类的危险恰恰在于"将来某次改动只在其中一处 写了 {@code catch { return null;
   * }}"。漏掉的那一处不会让别的用例转红——它只会让 <b>Redis 抖动期间所有 2FA 账号静默降级为单因素</b>。
   */
  @Test
  @DisplayName("存储不可用 ⇒ 每个方法都以 MFA_STORE_UNAVAILABLE 失败（不降级为单因素）")
  void everyMethodFailsClosedWhenTheStoreIsDown() {
    RedisConnectionFailureException down = new RedisConnectionFailureException("redis down");
    doThrow(down).when(valueOps).set(anyString(), any(), any(Duration.class));
    doThrow(down).when(valueOps).get(anyString());
    doThrow(down).when(valueOps).getAndDelete(anyString());
    doThrow(down).when(valueOps).increment(anyString());
    doThrow(down).when(valueOps).setIfAbsent(anyString(), any(), any(Duration.class));
    doThrow(down).when(template).delete(anyString());
    doThrow(down).when(template).getExpire(anyString(), any(TimeUnit.class));

    assertUnavailable(() -> store.createTicket(USER, Duration.ofMinutes(5)));
    assertUnavailable(() -> store.findTicketUserId("T"));
    assertUnavailable(() -> store.consumeTicket("T", USER));
    assertUnavailable(() -> store.failureCount(USER));
    assertUnavailable(() -> store.recordFailure(USER));
    assertUnavailable(() -> store.clearFailures(USER));
    assertUnavailable(() -> store.markTimeStepUsed(USER, 1L, Duration.ofSeconds(90)));
    // 让计数达到阈值，好把 lockRemainingSeconds 推进到读 TTL 那一步。
    // 必须用 doReturn 而不是 when(...)：when() 会真的去调那个桩，
    // 而它上一步刚被 doThrow 设成抛异常 —— 异常会在测试方法里炸开，而不是被 SUT 的 catch 接住。
    doReturn(MAX_ATTEMPTS).when(valueOps).get(FAIL_KEY);
    assertUnavailable(() -> store.lockRemainingSeconds(USER));
  }

  private static void assertUnavailable(Executable action) {
    BusinessException ex = assertThrows(BusinessException.class, action);
    assertEquals(
        ErrorCode.MFA_STORE_UNAVAILABLE, ex.getErrorCode(), "存储故障必须与「票据无效」「码错误」区分开，否则排查会从用户查起");
    assertFalse(
        ex.getMessage() != null && ex.getMessage().contains("redis down"),
        "底层异常消息（可能含主机名/连接串）不得进响应：" + ex.getMessage());
  }

  // ===== 键族与票面 =====

  @Test
  @DisplayName("票面：32 字节 Base64URL（43 字符无填充）、两次不同、登记在 auth:2fa-ticket: 下")
  void ticketsAreRandomUrlSafeAndRegisteredUnderTheConventionalPrefix() {
    ArgumentCaptor<String> key = ArgumentCaptor.forClass(String.class);

    String first = store.createTicket(USER, Duration.ofMinutes(5));
    String second = store.createTicket(USER, Duration.ofMinutes(5));

    verify(valueOps, times(2)).set(key.capture(), eq(USER), eq(Duration.ofMinutes(5)));
    assertEquals("auth:2fa-ticket:" + first, key.getAllValues().get(0), "键名要与仓内 auth:* 惯例一致");
    assertTrue(first.matches("[A-Za-z0-9_-]{43}"), "应为无填充 Base64URL（32 字节 ⇒ 43 字符），实得：" + first);
    assertNotEquals(first, second, "两张票据相同意味着随机源没接上");
  }

  @Test
  @DisplayName("findTicketUserId：不消费（不出现 getAndDelete）；空票据 ⇒ 空，不查 Redis")
  void findTicketUserIdIsAReadAndSkipsBlankTickets() {
    when(valueOps.get("auth:2fa-ticket:T")).thenReturn(USER);

    assertEquals(Optional.of(USER), store.findTicketUserId("T"));

    verify(valueOps, never()).getAndDelete(anyString());
    assertTrue(store.findTicketUserId(null).isEmpty());
    assertTrue(store.findTicketUserId("").isEmpty());
    verify(valueOps, times(1)).get(anyString());
  }

  @Test
  @DisplayName("已用时间步的键带上用户与步值（不同用户/不同步互不干扰）")
  void usedTimeStepKeysAreScopedByUserAndStep() {
    assertNotEquals(MfaStateStore.usedKey(1L, 100L), MfaStateStore.usedKey(2L, 100L));
    assertNotEquals(MfaStateStore.usedKey(1L, 100L), MfaStateStore.usedKey(1L, 101L));
    assertEquals("auth:2fa-used:1:100", MfaStateStore.usedKey(1L, 100L));
  }

  @Test
  @DisplayName("构造器拒绝非法配置")
  void rejectsInvalidConfig() {
    assertThrows(IllegalArgumentException.class, () -> new MfaStateStore(template, 0, 900));
    assertThrows(IllegalArgumentException.class, () -> new MfaStateStore(template, 5, 0));
    assertThrows(IllegalArgumentException.class, () -> new MfaStateStore(template, -1, -1));
  }
}
