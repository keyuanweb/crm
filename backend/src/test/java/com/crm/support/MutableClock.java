package com.crm.support;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 一个**真的** {@link Clock}：停在某个时刻，可以被推着走（082-two-factor-auth）。
 *
 * <p><b>为什么是"真的 Clock 子类"而不是 {@code @MockBean Clock} + 几个 {@code doAnswer}</b>——后者能跑，
 * 但它有三个会咬人的地方，而这三处都能靠"是个真对象"直接消掉：
 *
 * <ul>
 *   <li>Mockito 在**每个用例之后**重置 {@code @MockBean}（默认 {@code MockReset.AFTER}）。桩写在字段初始化处的话， 第二个用例开始
 *       {@code instant()} 就返回 {@code null} —— 症状是"第一个用例好好的、第二个莫名其妙 NPE"。
 *   <li>没打桩的方法返回 {@code null}/{@code 0}：{@code getZone()} 返回 {@code null} 会在 {@code
 *       LocalDateTime.now(clock)} 里 NPE，而栈指向 JDK 的 {@code LocalDateTime}，看不出是替身缺了一句桩。
 *   <li>{@code withZone} 同样要单独给：真 {@code Clock} 从不返回 {@code null}，漏掉就是一个等着将来发作的 NPE。
 * </ul>
 *
 * <p>本类这三个方法都是普通实现，不可能返回 {@code null}，也不存在"桩被重置"这回事。
 *
 * <p><b>{@code withZone} 返回的新实例与原实例共享同一个"现在"</b>（同一个 {@link AtomicLong}）：真 {@code Clock} 的 {@code
 * withZone} 返回的是另一个对象，若这里让副本持有独立的时间，那么"推进原时钟"在副本上就看不见 —— 而调用方拿到的到底是哪一个， 在测试里是看不出来的。共享之后两边永远一致。
 */
public final class MutableClock extends Clock {

  private final AtomicLong millis;
  private final ZoneId zone;

  public MutableClock(Instant start) {
    this(start, ZoneId.systemDefault());
  }

  public MutableClock(Instant start, ZoneId zone) {
    this(new AtomicLong(start.toEpochMilli()), zone);
  }

  private MutableClock(AtomicLong millis, ZoneId zone) {
    this.millis = millis;
    this.zone = zone;
  }

  @Override
  public ZoneId getZone() {
    return zone;
  }

  /** 换时区，**共享同一个"现在"**（见类 javadoc）。 */
  @Override
  public Clock withZone(ZoneId newZone) {
    return new MutableClock(millis, newZone);
  }

  @Override
  public Instant instant() {
    return Instant.ofEpochMilli(millis.get());
  }

  @Override
  public long millis() {
    return millis.get();
  }

  /** 往前推 {@code duration}。 */
  public void advance(Duration duration) {
    millis.addAndGet(duration.toMillis());
  }

  /** 往前推 {@code seconds} 秒。 */
  public void advanceSeconds(long seconds) {
    advance(Duration.ofSeconds(seconds));
  }

  /** 直接落到某个时刻。 */
  public void setTo(Instant instant) {
    millis.set(instant.toEpochMilli());
  }
}
