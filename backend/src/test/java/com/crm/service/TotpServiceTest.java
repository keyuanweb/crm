package com.crm.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.crm.common.TotpGenerator;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.OptionalLong;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * TOTP 的时间侧（082）：容错窗口、以及<b>命中在哪一步</b>。
 *
 * <p><b>期望值用 {@link TotpGenerator} 自己算</b>，而不是再抄一份 RFC 向量：本类要测的不是"HMAC 算得对不对" （那是 {@code
 * TotpGeneratorTest} 对着 RFC 6238 附录 B / RFC 4226 附录 D 官方向量的职责），而是"窗口
 * 有多宽、返回哪一步、什么输入不匹配"。用下游的正确实现算期望值是恰当的——它已被官方向量钉住。
 *
 * <p>时钟是 {@link Clock#fixed} 造出来的，所以"前进 4 个步长"这种断言在毫秒内可判，不必等两分钟。
 */
class TotpServiceTest {

  private static final String SECRET = "JBSWY3DPEHPK3PXPJBSWY3DPEHPK3PXP";

  private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");

  /** 一个固定的时刻，不含任何"当前时间"的语义。 */
  private static final Instant AT = Instant.parse("2026-09-16T02:00:00Z");

  private static TotpService serviceAt(Instant instant, int tolerance) {
    return new TotpService(Clock.fixed(instant, ZONE), 30, tolerance);
  }

  private static TotpService serviceAt(Instant instant) {
    return serviceAt(instant, 1);
  }

  /** 算 {@code AT + offsetSteps} 那一步的码。offset 为负即"过去的步"。 */
  private static String codeAt(Instant instant, int offsetSteps) {
    long step = TotpGenerator.timeStepOf(instant.getEpochSecond(), 30) + offsetSteps;
    return TotpGenerator.codeAt(TotpGenerator.decodeSecret(SECRET), step, 6);
  }

  @Test
  @DisplayName("当前步的码 ⇒ 命中当前时间步")
  void matchesCurrentStep() {
    TotpService service = serviceAt(AT);

    assertEquals(
        OptionalLong.of(service.currentTimeStep()), service.matchTimeStep(SECRET, codeAt(AT, 0)));
  }

  /**
   * <b>本类最重要的一条断言</b>：命中时返回的是**那一步**，不是"当前步"。
   *
   * <p>返回值必须是真实命中的步，因为调用方要用它做防重放的键。若实现图省事返回 {@code currentTimeStep()}，
   * 单次验证的表现完全正常（用户提交哪个窗口内的码都通过），只有重放时才出问题： 用户提交上一步的码、被按当前步记下来 ⇒ 上一步没被标记 ⇒ 同一个码还能再用一次。
   * 这条破坏不会让任何"能登录"的测试转红，所以必须由这里钉住。
   */
  @Test
  @DisplayName("±1 容错：返回命中那一步本身（上一步的码 ⇒ 上一步；下一步的码 ⇒ 下一步）")
  void returnsTheMatchedStepNotTheCurrentOne() {
    TotpService service = serviceAt(AT, 1);
    long now = service.currentTimeStep();

    assertEquals(
        OptionalLong.of(now - 1), service.matchTimeStep(SECRET, codeAt(AT, -1)), "上一步的码应命中上一步");
    assertEquals(
        OptionalLong.of(now + 1), service.matchTimeStep(SECRET, codeAt(AT, 1)), "下一步的码应命中下一步");
  }

  @Test
  @DisplayName("窗口之外：±2 步的码一律不匹配")
  void rejectsOutsideTheWindow() {
    TotpService service = serviceAt(AT, 1);

    assertTrue(service.matchTimeStep(SECRET, codeAt(AT, -2)).isEmpty(), "两步之前的码不该被接受");
    assertTrue(service.matchTimeStep(SECRET, codeAt(AT, 2)).isEmpty(), "两步之后的码不该被接受");
  }

  @Test
  @DisplayName("容错为 0 时只剩当前步：±1 的码被拒")
  void zeroToleranceAcceptsOnlyCurrentStep() {
    TotpService service = serviceAt(AT, 0);

    assertTrue(service.matchTimeStep(SECRET, codeAt(AT, 0)).isPresent());
    assertTrue(service.matchTimeStep(SECRET, codeAt(AT, -1)).isEmpty());
    assertTrue(service.matchTimeStep(SECRET, codeAt(AT, 1)).isEmpty());
  }

  /**
   * 用户输错一个字母不该得到 500。
   *
   * <p>形态不对的输入与"码不匹配"在调用方眼里是同一件事（401 码错误），所以这里返回空而不是抛。 注意 {@code " 123456"} 也在拒绝之列：{@code matches}
   * 要求整个串匹配，前导空格不会被忽略—— 与 {@code "1234567"}（多一位）同理，两者都必须是"不匹配"而不是"匹配了前六位"。
   */
  @Test
  @DisplayName("码形态不对 ⇒ 空，不抛（含 null、空串、位数不对、非数字、带空格）")
  void malformedCodeIsNotAnError() {
    TotpService service = serviceAt(AT);

    for (String bad :
        new String[] {null, "", "12345", "1234567", "abcdef", "12345a", " 123456", "123456 "}) {
      assertTrue(service.matchTimeStep(SECRET, bad).isEmpty(), "应视为不匹配而非报错：" + bad);
    }
  }

  /**
   * 与上一条相反：<b>密钥</b>坏了要抛。
   *
   * <p>密钥由我们自己生成并加密存库，它解不开说明数据坏了或写入路径有问题——那是服务端故障。 把它伪装成"你的码不对"会让排查从用户查起，方向完全错。
   */
  @Test
  @DisplayName("库存密钥损坏 ⇒ 抛，且不回显密钥内容")
  void corruptStoredSecretThrows() {
    TotpService service = serviceAt(AT);

    IllegalStateException ex =
        assertThrows(IllegalStateException.class, () -> service.matchTimeStep("0189", "123456"));

    assertFalse(
        ex.getMessage() != null && ex.getMessage().contains("0189"), "异常消息里不得回显密钥内容（它会进日志与错误报告）");
  }

  @Test
  @DisplayName("保留期由窗口参数推出：tolerance=1 ⇒ 90s，tolerance=2 ⇒ 150s")
  void retentionIsDerivedFromTheWindow() {
    assertEquals(Duration.ofSeconds(90), serviceAt(AT, 1).timeStepRetention());
    assertEquals(Duration.ofSeconds(150), serviceAt(AT, 2).timeStepRetention(), "容错窗口变宽时保留期必须跟着变");
  }

  @Test
  @DisplayName("时钟推进 ⇒ 当前步推进，且旧码随窗口滑出")
  void clockAdvancesDriveTheWindow() {
    TotpService early = serviceAt(AT);
    String oldCode = codeAt(AT, 0);
    assertTrue(early.matchTimeStep(SECRET, oldCode).isPresent(), "前提：这个码在当初是可用的");

    // 前进 4 个步长（120 秒）⇒ 原来的步已落在 ±1 窗口之外
    TotpService later = serviceAt(AT.plusSeconds(120));

    assertTrue(later.matchTimeStep(SECRET, oldCode).isEmpty(), "120 秒后同一个码必须已失效");
    assertEquals(early.currentTimeStep() + 4, later.currentTimeStep());
  }

  @Test
  @DisplayName("generateSecret：32 个 Base32 字符、两次不同，且产出的密钥可直接参与校验")
  void generatedSecretIsUsableAndRandom() {
    TotpService service = serviceAt(AT);

    String first = service.generateSecret();
    String second = service.generateSecret();

    assertEquals(32, first.length());
    assertTrue(first.matches("[A-Z2-7]{32}"), "应为无填充的大写 Base32：" + first);
    assertNotEquals(first, second, "两次取到同一把密钥意味着随机源没接上");

    // 自洽性：生成的密钥拿去算码，必须能被同一个服务认出来。这条同时排除了
    // "generateSecret 与 decodeSecret 对同一串的理解不一致"（编码带填充/大小写之类）。
    String code =
        TotpGenerator.codeAt(TotpGenerator.decodeSecret(first), service.currentTimeStep(), 6);
    assertEquals(OptionalLong.of(service.currentTimeStep()), service.matchTimeStep(first, code));
  }

  /**
   * 非法配置在装配期就响，而不是等到有人登录。
   *
   * <p>{@code time-step-seconds} 为 0 时 {@code TotpGenerator.timeStepOf} 会抛，但那条路径要等到
   * 某个人提交验证码才走到——那时的错误现场是"某个人验证码总是不对"，而真正的原因在配置文件里。
   */
  @Test
  @DisplayName("构造器拒绝非法配置")
  void rejectsInvalidConfig() {
    assertThrows(
        IllegalArgumentException.class, () -> new TotpService(Clock.fixed(AT, ZONE), 0, 1));
    assertThrows(
        IllegalArgumentException.class, () -> new TotpService(Clock.fixed(AT, ZONE), -30, 1));
    assertThrows(
        IllegalArgumentException.class, () -> new TotpService(Clock.fixed(AT, ZONE), 30, -1));
  }
}
