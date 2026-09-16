package com.crm.service;

import com.crm.common.TotpGenerator;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.util.OptionalLong;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * TOTP 的时间侧（082-two-factor-auth）：把"现在几点"与"接受多宽的漂移"接进 {@link TotpGenerator}。
 *
 * <p>纯算法在 {@link TotpGenerator} 里（无时钟、无配置、可对 RFC 官方向量逐条核对）；本类只负责 ① 从注入的 {@link Clock} 取时间；②
 * 按配置的容错步数在窗口内逐个时间步试算；③ 把命中的**那一步** 报出去——第 ③ 点是本类最重要的接口决定，理由见 {@link #matchTimeStep}。
 */
@Service
public class TotpService {

  /** 本项目使用的码位数。RFC 允许 6~8，认证器 App 与规格都用 6，不做成可配置项。 */
  private static final int DIGITS = 6;

  /** 码的形态：恰好 6 位十进制。不满足即在窗口内不可能匹配，见 {@link #matchTimeStep}。 */
  private static final String CODE_PATTERN = "[0-9]{" + DIGITS + "}";

  private final Clock clock;
  private final int timeStepSeconds;
  private final int tolerance;

  public TotpService(
      Clock clock,
      @Value("${crm.security.mfa.time-step-seconds:30}") int timeStepSeconds,
      @Value("${crm.security.mfa.time-step-tolerance:1}") int tolerance) {
    if (timeStepSeconds <= 0) {
      // 早失败：这个值不合法时 TotpGenerator.timeStepOf 会抛，但那条路径要等到有人登录才走到，
      // 而错误现场会是"某个人验证码总是不对"。配置错就该在装配期响。
      throw new IllegalArgumentException(
          "crm.security.mfa.time-step-seconds 必须为正数：" + timeStepSeconds);
    }
    if (tolerance < 0) {
      throw new IllegalArgumentException("crm.security.mfa.time-step-tolerance 不能为负数：" + tolerance);
    }
    this.clock = clock;
    this.timeStepSeconds = timeStepSeconds;
    this.tolerance = tolerance;
  }

  /** 生成一把新的随机密钥（Base32 无填充）。 */
  public String generateSecret() {
    return TotpGenerator.randomSecret();
  }

  /** 当前时间步。 */
  public long currentTimeStep() {
    return TotpGenerator.timeStepOf(clock.instant().getEpochSecond(), timeStepSeconds);
  }

  public int timeStepSeconds() {
    return timeStepSeconds;
  }

  /**
   * 校验一个动态码，返回<b>命中的那一个时间步</b>；不匹配返回空。
   *
   * <p><b>为什么返回 {@code OptionalLong} 而不是 {@code boolean}</b>：命中之后必须把"这一步已被用过"
   * 记下来才能防重放（FR-M08），而记的就是这个步值。若只报真假，调用方就只能假设"命中的是当前步"—— 在 ±1
   * 的窗口里那个假设是错的：用户提交的可能是上一步的码，把它按当前步去标记，等于给真正被用掉的
   * 那一步留下了第二次机会。这个错误<b>不会让任何测试转红</b>（单次验证完全正常），只在重放时显形。
   *
   * <p><b>扫窗口的顺序是刻意的</b>：当前步 → 未来步 → 过去步。窗口内多步同时命中在正常密钥下不会 发生（不同时间步的 HMAC
   * 输出不同），但一旦发生，选取"更晚的那一步"是安全侧的选择：标记一个 <b>过去</b>的步并不阻止同一张票据上继续用当前步的码重放，而标记当前/未来的步会。可用性上的代价
   * 为零——被标记的那些步本来就是"刚被用掉"的。
   *
   * <p><b>码的形态不对 ⇒ 返回空，不抛异常</b>：{@code null}、{@code "12"}、{@code "abcdef"} 都是 "码不对"，调用方会把它映射成 401
   * {@code MFA_CODE_INVALID}。若在这里抛，用户输错一个字母就会 得到 500。而<b>密钥</b>形态不对则相反，抛——那说明库里的数据坏了（密钥由我们自己生成），
   * 属于服务端故障，伪装成"你的码不对"会让排查从用户查起。
   *
   * <p><b>比较用 {@code MessageDigest.isEqual}</b>：六位码的时序侧信道在"5 次尝试即锁定"的约束下
   * 实操不可行，所以这不是必须的——但它的成本是零，而没有理由留一个可避免的侧信道。
   *
   * @param base32Secret 库存的密钥（Base32，可能带大小写/填充/空格的宽容写法）
   * @param code 用户提交的码，可为 {@code null}
   * @throws IllegalStateException 库存密钥不是合法 Base32（数据损坏）
   */
  public OptionalLong matchTimeStep(String base32Secret, String code) {
    if (code == null || !code.matches(CODE_PATTERN)) {
      return OptionalLong.empty();
    }
    byte[] key;
    try {
      key = TotpGenerator.decodeSecret(base32Secret);
    } catch (IllegalArgumentException ex) {
      throw new IllegalStateException("库存的 TOTP 密钥不是合法 Base32 —— 数据损坏或写入路径有误（本异常刻意不含密钥内容）", ex);
    }

    long now = currentTimeStep();
    for (long step = now; step <= now + tolerance; step++) {
      if (matches(key, step, code)) {
        return OptionalLong.of(step);
      }
    }
    for (long step = now - 1; step >= now - tolerance; step--) {
      if (matches(key, step, code)) {
        return OptionalLong.of(step);
      }
    }
    return OptionalLong.empty();
  }

  /**
   * 已被使用的时间步需要记住多久。
   *
   * <p>取值 = 完整窗口宽度 = {@code (2 * tolerance + 1) * timeStepSeconds}，默认 90 秒。 这个数<b>由参数推出</b>而不是写死
   * 90：容错窗口若被改宽而这里没跟上，就会出现"被标记的步 刚好又开始可接受"的缺口——那时同一个码能在两条票据上各用一次，正是 FR-M08 要防的重放。
   *
   * <p>为什么取整窗宽度而不是更紧的下界：真正的下界是"被标记的步在它自己还会被接受的期限内 一直有效"。标记发生在该步开始之后（{@code t ≥ S·step}），而该步最晚被接受是在
   * {@code (S + tolerance + 1)·step}，故下界为 {@code (tolerance + 1)·step} = 60 秒。 取整窗宽（90
   * 秒）多留了一个步长：多余的 TTL 只占一个 Redis 键，而少留会直接变成重放窗口。
   */
  public Duration timeStepRetention() {
    return Duration.ofSeconds((2L * tolerance + 1) * timeStepSeconds);
  }

  private static boolean matches(byte[] key, long timeStep, String code) {
    String expected = TotpGenerator.codeAt(key, timeStep, DIGITS);
    return MessageDigest.isEqual(
        expected.getBytes(StandardCharsets.US_ASCII), code.getBytes(StandardCharsets.US_ASCII));
  }
}
