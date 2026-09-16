package com.crm.support;

import com.crm.AbstractIntegrationTest;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.ContextConfiguration;

/**
 * 把应用的 {@code Clock} bean 换成 {@link MutableClock}：**停在某一刻、可被推着走**（082-two-factor-auth）。
 *
 * <p><b>为什么非要有它</b>：2FA 有三条行为由时间决定，而它们的周期都长到无法在测试里等 ——
 *
 * <ul>
 *   <li>TOTP 每 30 秒换码 ⇒ "换一步之后同一个码必须被拒"（FR-M08/SC-M05）不冻结就<b>无法判定</b>： 要么等到真的换步（30
 *       秒起，还不一定），要么把断言写成"只测当前这一步"，而后者抓不到任何东西；
 *   <li>失败 5 次锁定 900 秒 ⇒ "锁定期满自动恢复"（SC-M04）要么等 15 分钟，要么写成"只测还锁着" —— 一个"永不解锁"的实现能让那种断言全绿；
 *   <li>票据 TTL 300 秒 ⇒ 同理。
 * </ul>
 *
 * <p>有了它，上面三条都在毫秒内可判：{@code advanceSeconds(901)} 就是"过了 15 分钟零 1 秒"。
 *
 * <p><b>必须与 {@link InMemoryRedisTestSupport} 共用同一个 Clock</b>（把它传进 {@code install(template,
 * clock)}）： 否则"业务认为过了 901 秒"与"Redis 替身认为还不到 900 秒"各说各话，症状是"锁定期满自动恢复"这条断言怎么调都不对， 而单看两个类都是对的。
 *
 * <h2>用法</h2>
 *
 * <pre>
 * class AuthMfaIT extends FixedClockTestSupport {
 *   private final InMemoryRedisTestSupport redis = new InMemoryRedisTestSupport();
 *
 *   {@literal @}BeforeEach
 *   void installRedis() { redis.clear(); redis.install(redisTemplate, clock); }
 * }
 * </pre>
 *
 * <h2>四个必须写明的坑</h2>
 *
 * <ol>
 *   <li><b>bean 名不能叫 {@code clock}</b>：{@code ClockConfig} 已经有一个同名的 bean，Spring Boot 2.1 起默认禁止 bean
 *       定义覆盖，重名会让上下文<b>根本起不来</b>（报 {@code BeanDefinitionOverrideException}，读起来像配置写错了，
 *       而不是"有两个时钟"）。故本类的 bean 叫 {@code mutableClock}，靠 {@link Primary} 在按类型注入时胜出。
 *   <li><b>每个用例前必须把时钟拨回去</b>（见 {@link #rewindToFrozenInstant()}）：本 bean 是被**上下文缓存**的单例 ——
 *       同一个类里的用例共用它。若某个用例把时钟推了 15 分钟而下一个用例不归位，第二个用例就在"未来"里跑： 症状是"单独跑绿、一起跑红"。{@code @MockBean}
 *       版本没这个问题（Mockito 每个用例后重置），本类必须自己还这笔债。
 *   <li><b>不要在本类之外拿 {@code Clock.systemDefaultZone()} 算期望值</b>：应用里所有 MFA 时间都走这个 bean，
 *       测试里若有一处用了系统时钟，那处断言就会在"冻结的现在"与"真实的现在"之间随机红。
 *   <li><b>本类的 {@code @TestConfiguration} 必须靠 {@link ContextConfiguration} 显式点名</b>：Spring 的默认配置类探测
 *       （{@code AnnotationConfigContextLoaderUtils}）只扫<b>测试类自己</b>的 {@code getDeclaredClasses()}，
 *       <b>不递归父类</b>。所以"把 {@code @TestConfiguration} 嵌在抽象基类里、让子类白拿"是<b>不成立的</b>： 子类报的是 {@code
 *       NoSuchBeanDefinitionException: No qualified bean of type 'MutableClock'}， 而它指向基类那个
 *       {@code @Autowired} 字段 —— 读起来像"bean 没定义"，实际是"没人扫到这个配置类"。 （本类首次被继承时就是这么红的：9 条用例 9 个
 *       error，被测代码一行没错。）<br>
 *       {@link ContextConfiguration} 的查找是 {@code TYPE_HIERARCHY} 语义，写在基类上子类能继承到；
 *       而那个嵌套类<b>必须留在本类内部</b>——挪成顶层类不会让自动探测开始工作，只是把同一个坑换了个位置。
 * </ol>
 *
 * <p><b>代价如实记</b>：{@code @Primary} 一套额外的 bean 定义会改变上下文缓存键，故凡是用到本类的用例<b>自成一个 Spring 上下文</b>， IT
 * 相位会变慢（{@code AuthCaptchaIT} 已有同款先例）。因此 MFA 的 IT 要<b>收敛</b>：需要冻结时钟的用例尽量放进同一个类， 别让每个用例各起一个上下文。
 */
@ContextConfiguration(classes = FixedClockTestSupport.FrozenClockConfig.class)
public abstract class FixedClockTestSupport extends AbstractIntegrationTest {

  /**
   * 冻结的基准时刻。选一个**固定值**（而不是 {@code Instant.now()}）是刻意的：时间步由此完全确定， 失败信息里的动态码、时间步、TTL 在重跑之间可比 ——
   * 排查"这条用例为什么红"时，一个每次都在变的数字会把可复现性吃掉。
   */
  private static final Instant FROZEN_AT = Instant.parse("2026-09-16T10:00:00Z");

  /** 提供冻结时钟，按类型注入时胜过 {@code ClockConfig} 的那个（见类 javadoc 第 1 条：名字不能撞）。 */
  @TestConfiguration
  static class FrozenClockConfig {
    @Bean
    @Primary
    MutableClock mutableClock() {
      return new MutableClock(FROZEN_AT);
    }
  }

  /** 应用注入的那个时钟。传给 {@link InMemoryRedisTestSupport#install} 的必须就是它。 */
  @Autowired protected MutableClock clock;

  /** 每个用例开始都把时钟拨回基准（见类 javadoc 第 2 条：单例 bean 跨用例共用）。 */
  @BeforeEach
  void rewindToFrozenInstant() {
    clock.setTo(FROZEN_AT);
  }

  /** 当前冻结的毫秒时间戳。 */
  protected long nowMillis() {
    return clock.millis();
  }

  /** 把时钟往前推 {@code seconds} 秒。 */
  protected void advanceSeconds(long seconds) {
    clock.advanceSeconds(seconds);
  }

  /** 把时钟往前推 {@code duration}。 */
  protected void advance(Duration duration) {
    clock.advance(duration);
  }
}
