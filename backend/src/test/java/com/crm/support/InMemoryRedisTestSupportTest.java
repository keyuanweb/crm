package com.crm.support;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.RedisTemplate;

/**
 * {@link InMemoryRedisTestSupport} 自己的语义（082 第 6 步）。
 *
 * <p><b>为什么替身也要有自己的用例</b>：它的定位是"让 IT 能真跑通 Redis 路径"，于是它一旦有偏差， 结果是<b>别处的 IT 因为错误的原因变绿</b>——
 * 那比一个失败的用例糟得多，因为它不会 报错，只会让人相信一条不成立的结论。本类把替身那几个"看起来显然、写错却完全静默"的性质钉住： TTL 真的到期、{@code getExpire} 区分
 * {@code -1} 与 {@code -2}、{@code INCR} 不碰已有 TTL、 故障注入只作用于指定前缀。
 *
 * <p>本类是纯 surefire（无 Spring 上下文、无数据库）：替身本身不值得为它付一次上下文启动。
 */
class InMemoryRedisTestSupportTest {

  private static final Instant START = Instant.parse("2026-09-16T10:00:00Z");

  private InMemoryRedisTestSupport redis;
  private MutableClock clock;
  private RedisTemplate<String, Object> template;

  @SuppressWarnings("unchecked")
  @BeforeEach
  void install() {
    redis = new InMemoryRedisTestSupport();
    clock = new MutableClock(START);
    template = mock(RedisTemplate.class);
    redis.install(template, clock);
  }

  @Test
  @DisplayName("写进去能读回来（父类的裸 mock 做不到这件事，本类存在的理由）")
  void valuesSurviveARoundTrip() {
    template.opsForValue().set("k", "v");

    assertEquals("v", template.opsForValue().get("k"));
    assertTrue(redis.containsKey("k"));
  }

  @Test
  @DisplayName("带 TTL 的键：过期前读得到，到期那一刻就读不到了")
  void aKeyDisappearsExactlyWhenItsTtlElapses() {
    template.opsForValue().set("k", "v", Duration.ofSeconds(300));

    clock.advanceSeconds(299);
    assertEquals("v", template.opsForValue().get("k"), "差 1 秒到期时仍必须读得到");

    clock.advanceSeconds(1);
    assertNull(template.opsForValue().get("k"), "到期即不可读——否则『票据 TTL』『锁定 900 秒』这类断言全是假的");
    assertFalse(redis.containsKey("k"));
  }

  @Test
  @DisplayName("getExpire 区分 -2（键不存在）与 -1（有键无 TTL），并报剩余毫秒")
  void getExpireDistinguishesMissingFromNoTtl() {
    assertEquals(-2L, template.getExpire("nope", TimeUnit.MILLISECONDS).longValue(), "键不存在");

    template.opsForValue().set("forever", "v");
    assertEquals(-1L, template.getExpire("forever", TimeUnit.MILLISECONDS).longValue(), "有键、无 TTL");

    template.opsForValue().set("k", "v", Duration.ofSeconds(300));
    assertEquals(300_000L, template.getExpire("k", TimeUnit.MILLISECONDS).longValue());
    clock.advanceSeconds(100);
    assertEquals(200_000L, template.getExpire("k", TimeUnit.MILLISECONDS).longValue());
    assertEquals(200L, template.getExpire("k", TimeUnit.SECONDS).longValue(), "单位要真的被换算，不是永远报毫秒");
  }

  @Test
  @DisplayName("increment 保留已有 TTL：既不续期，也不把 TTL 清掉")
  void incrementKeepsAnExistingTtl() {
    assertEquals(1L, template.opsForValue().increment("c").longValue(), "首次即 delta");
    assertEquals(
        -1L, template.getExpire("c", TimeUnit.MILLISECONDS).longValue(), "INCR 新建的键没有 TTL");

    template.expire("c", Duration.ofSeconds(900));
    assertEquals(2L, template.opsForValue().increment("c").longValue());
    assertEquals(
        900L,
        template.getExpire("c", TimeUnit.SECONDS).longValue(),
        "真 Redis 的 INCR 不修改 TTL；替身若在这里续期或清 TTL，『increment 后紧跟 expire』那种写法就不会被发现有问题");

    clock.advanceSeconds(900);
    assertNull(template.opsForValue().get("c"), "窗口过了计数就该消失");
  }

  @Test
  @DisplayName("expire 对不存在的键返回 false（真 Redis 返回 0）")
  void expireReportsFalseForAMissingKey() {
    assertFalse(template.expire("nope", Duration.ofSeconds(900)));

    template.opsForValue().set("k", "v");
    assertTrue(template.expire("k", Duration.ofSeconds(900)));
  }

  @Test
  @DisplayName("delete 报的是『键是否真的在』")
  void deleteReportsWhetherTheKeyWasThere() {
    template.opsForValue().set("k", "v");
    assertTrue(template.delete("k"));
    assertFalse(template.delete("k"), "已经不在了");

    template.opsForValue().set("expired", "v", Duration.ofSeconds(10));
    clock.advanceSeconds(10);
    assertFalse(template.delete("expired"), "已过期的键按不存在算");
  }

  @Test
  @DisplayName("getAndDelete 只读得到一次")
  void getAndDeleteReadsOnce() {
    template.opsForValue().set("t", 7L);

    assertEquals(7L, ((Number) template.opsForValue().getAndDelete("t")).longValue());
    assertNull(template.opsForValue().getAndDelete("t"));
  }

  @Test
  @DisplayName("setIfAbsent：占用时返回 false 且不覆盖；带保留期的那个到期后真的会释放")
  void setIfAbsentKeepsTheFirstValueAndHonoursRetention() {
    assertTrue(template.opsForValue().setIfAbsent("t", 1L));
    assertFalse(template.opsForValue().setIfAbsent("t", 2L));
    assertEquals(1L, ((Number) template.opsForValue().get("t")).longValue(), "不得被后一次写入覆盖");

    assertTrue(template.opsForValue().setIfAbsent("u", 1L, Duration.ofSeconds(90)));
    assertFalse(template.opsForValue().setIfAbsent("u", 2L, Duration.ofSeconds(90)));

    clock.advanceSeconds(90);
    assertNull(template.opsForValue().get("u"), "保留期过了要能重新占——防重放的保留期取短了就是靠这条发现的");
  }

  @Test
  @DisplayName("故障注入只作用于指定前缀：2FA 键全抛，同一份替身下非 2FA 键照常工作")
  void failureInjectionIsScopedToThePrefix() {
    redis.failOnKeyPrefix("auth:2fa-");

    assertThrows(
        RedisConnectionFailureException.class,
        () -> template.opsForValue().get("auth:2fa-ticket:T"));
    assertThrows(
        RedisConnectionFailureException.class,
        () -> template.opsForValue().set("auth:2fa-fail:1", 1));
    assertThrows(
        RedisConnectionFailureException.class,
        () -> template.opsForValue().increment("auth:2fa-fail:1"));
    assertThrows(
        RedisConnectionFailureException.class,
        () -> template.opsForValue().setIfAbsent("auth:2fa-used:1:2", "1", Duration.ofSeconds(90)));
    assertThrows(
        RedisConnectionFailureException.class,
        () -> template.opsForValue().getAndDelete("auth:2fa-ticket:T"));
    assertThrows(RedisConnectionFailureException.class, () -> template.delete("auth:2fa-used:1:2"));
    assertThrows(
        RedisConnectionFailureException.class,
        () -> template.getExpire("auth:2fa-fail:1", TimeUnit.MILLISECONDS));
    assertThrows(
        RedisConnectionFailureException.class,
        () -> template.expire("auth:2fa-fail:1", Duration.ofSeconds(900)));

    // 这一半才是重点：同一次故障注入、同一份替身里，非 2FA 的路径必须**完全不受影响**。
    // 拆成两个用例各自注入一次的话，这条其实是在一个没有故障的替身上跑的 —— 等于没验证隔离性。
    template.opsForValue().set("auth:fail:1", 3);
    assertEquals(3, template.opsForValue().get("auth:fail:1"));
    assertTrue(template.opsForValue().setIfAbsent("auth:refresh:x", "tok", Duration.ofSeconds(60)));
    assertTrue(redis.containsKey("auth:refresh:x"));
  }

  @Test
  @DisplayName("已过期的键不会出现在 snapshot 里")
  void expiredKeysAreNotInTheSnapshot() {
    template.opsForValue().set("gone", "v", Duration.ofSeconds(10));
    template.opsForValue().set("kept", "v");

    clock.advanceSeconds(10);

    assertEquals(Set.of("kept"), redis.snapshot().keySet());
  }

  @Test
  @DisplayName("clear 连故障注入一起撤掉（故障不跨用例泄漏）")
  void clearAlsoHealsInjectedFailures() {
    redis.failOnKeyPrefix("auth:2fa-");
    redis.clear();

    assertDoesNotThrow(() -> template.opsForValue().get("auth:2fa-ticket:T"));
    assertTrue(redis.snapshot().isEmpty());
  }
}
