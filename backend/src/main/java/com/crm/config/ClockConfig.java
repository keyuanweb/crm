package com.crm.config;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 时钟接缝（082-two-factor-auth）。
 *
 * <p><b>为什么需要一个 bean</b>：2FA 有三处行为由时间决定，而它们的周期都长到无法在测试里等：
 *
 * <ul>
 *   <li>TOTP 的码每 30 秒换一个（{@code time-step-seconds}）；
 *   <li>±1 步容错窗口 ⇒ "同一个码在两条不同票据上"这件事只有在换步之后才可分辨；
 *   <li>失败次数锁定 900 秒（{@code lock-duration-seconds}）。
 * </ul>
 *
 * <p>直接写 {@code Instant.now()} 的话，"锁定期满自动恢复"这条断言要等 15 分钟， "换一步之后同一个码仍被拒"要等 30
 * 秒以上——两条都会被实际写成"只测当前这一刻"， 于是"从不解锁"这类缺陷在全绿的测试里活着。注入一个 {@link Clock} 之后，两者都能 在毫秒内判定（见 {@code
 * FixedClockTestSupport}）。
 *
 * <p><b>为什么是 {@code systemDefaultZone()} 而不是 UTC</b>：这不是随手选的。MFA 写 {@code last_2fa_verified_at} 与
 * {@code last_login_at} 时用 {@code LocalDateTime.now(clock)}， 而本仓其余地方（{@code AuthService}、{@code
 * AuditService}）用的是 {@code LocalDateTime.now()}， 即<b>系统默认时区</b>。换成 UTC 会让这两列比 {@code
 * created_at}/{@code updated_at} 早或晚若干小时——<b>而且完全没有症状</b>：两个时间戳都"看起来正常"，只有对比时才 发现来自两个时区。裸 SQL
 * 排查时尤其看不出来（JDBC 写进 MySQL 的都是本地时间的字面量）。
 *
 * <p><b>本 bean 的存在不改变任何既有行为</b>：没有任何既有代码注入它，它们仍走 {@code LocalDateTime.now()}。时序相关的新代码（MFA）才注入。
 */
@Configuration
public class ClockConfig {

  @Bean
  public Clock clock() {
    return Clock.systemDefaultZone();
  }
}
