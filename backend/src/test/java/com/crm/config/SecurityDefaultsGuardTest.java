package com.crm.config;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.crm.service.MfaSecretEncryptionService;
import java.lang.reflect.Field;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 启动守卫里 MFA 密钥那一步（082，D12 的语义）。
 *
 * <p>覆盖两条互不替代的性质：① <b>判据与动作</b>——给定的配置值 ⇒ 告警放行还是抛； ② <b>这一步在 {@code run()} 里的位置</b>——它必须在 dev 分支那个
 * {@code return} 之前（见 {@link #checkRunsBeforeTheDevEarlyReturn()}）。两者分开写，是因为合成一条会让"判据错了"与
 * "位置错了"给出同一条失败信息，而这两种错法的修法完全不同。
 *
 * <p>直接 {@code new} 而不经 Spring：判据是纯函数，"位置"是同一个方法内两步的先后，都与容器无关。 真起一个 dev 上下文还要连
 * MySQL（本机没有），而那样测到的仍然是同一件事。
 */
class SecurityDefaultsGuardTest {

  private static final String VALID = "dGVzdC1tZmEta2V5LWZvci0wODItMDEyMzQ1Njc4OXg=";

  private static final String SHORT_16 = "MDEyMzQ1Njc4OWFiY2RlZg==";

  private static final String LONG_31 = "eHh4eHh4eHh4eHh4eHh4eHh4eHh4eHh4eHh4eHh4eA==";

  private static final String NOT_BASE64 = "!!!这不是 Base64!!!";

  private static final String DEV_DEFAULT_JWT_SECRET =
      "crm-dev-secret-key-please-override-in-prod-0123456789abcdef";

  private static SecurityDefaultsGuard guardWith(String key) {
    return new SecurityDefaultsGuard(new MfaSecretEncryptionService(key));
  }

  /**
   * 空白 = 未配置 = 合法状态 ⇒ 只告警。
   *
   * <p>这一条是 D12 的核心：若照 JWT 密钥的写法直接拒绝启动，本仓的整个测试套件（{@code @ActiveProfiles("test")} 不含 "dev"）与所有不用 2FA
   * 的部署都会起不来——用一个没人开启的功能炸掉整个服务。
   */
  @Test
  @DisplayName("未配置 ⇒ 放行（只告警）")
  void blankKeyIsWarnedNotFatal() {
    for (String blank : new String[] {null, "", "   "}) {
      assertDoesNotThrow(() -> guardWith(blank).checkMfaSecretKey(), "空值不该阻止启动：" + blank);
    }
  }

  @Test
  @DisplayName("合法密钥 ⇒ 放行")
  void validKeyPasses() {
    assertDoesNotThrow(() -> guardWith(VALID).checkMfaSecretKey());
  }

  /**
   * 畸形 = 误配 ⇒ 启动即抛。
   *
   * <p>与上一条的区别是"没配"与"配错"：配错在启动时毫无症状，要等某个人真的去绑定认证器时才炸， 而那时的现场是"某个人绑不上"——排查得从配置查起。把症状提前到启动。
   */
  @Test
  @DisplayName("非 Base64 / 长度不对 ⇒ 启动即抛")
  void malformedKeyFailsFast() {
    for (String malformed : new String[] {NOT_BASE64, SHORT_16, LONG_31}) {
      IllegalStateException ex =
          assertThrows(
              IllegalStateException.class,
              () -> guardWith(malformed).checkMfaSecretKey(),
              "畸形配置必须阻止启动：" + malformed);

      assertTrue(
          ex.getMessage() != null && ex.getMessage().startsWith("SECURITY: "),
          "启动守卫的消息应可被一眼认出是安全配置问题：" + ex.getMessage());
    }
  }

  /** 运维拿到这条消息时正在启动失败，消息里要带能直接照做的命令。 */
  @Test
  @DisplayName("失败消息带生成命令（照着做就能修）")
  void failureMessageTellsHowToFixIt() {
    IllegalStateException ex =
        assertThrows(IllegalStateException.class, () -> guardWith(SHORT_16).checkMfaSecretKey());

    assertTrue(ex.getMessage().contains("openssl rand -base64 32"), "应给出生成命令：" + ex.getMessage());
  }

  /**
   * <b>检查的位置</b>：必须在 {@code run()} 里 dev 分支那个 {@code return} <b>之前</b>。
   *
   * <p>这是本批唯一一条"靠注释保证不了"的性质（{@code run()} 上那段注释就是为此写的），所以必须有断言。 场景是真实的：{@code activeProfiles} 含
   * "dev" 且 JWT 密钥是开发默认值 ⇒ {@code run()} 在第 1 步打完 告警就 {@code return} 了，<b>其后的任何检查都不会执行</b>。而 dev
   * 恰恰是"本机没配 MFA 密钥"最常见 的地方——把守卫挪到 {@code run()} 末尾，这道检查就会在最需要它的环境里静默失效，且失效方向是 "看起来检查过了"。
   *
   * <p>用反射设两个 {@code @Value} 字段而不真起上下文：要测的只是"{@code run()} 里两步的先后"， 而这与 Spring 无关——真起一个 dev 上下文还要连
   * MySQL（本机没有）。
   */
  @Test
  @DisplayName("dev 早返回也不放过畸形密钥：检查确实在 return 之前")
  void checkRunsBeforeTheDevEarlyReturn() {
    SecurityDefaultsGuard guard = guardWith(NOT_BASE64);
    setField(guard, "activeProfiles", "dev");
    setField(guard, "jwtSecret", DEV_DEFAULT_JWT_SECRET);

    assertThrows(
        IllegalStateException.class, () -> guard.run(null), "在会提前 return 的 dev 路径上，畸形密钥仍必须让启动失败");
  }

  /** 对照：同一个 dev 路径上，合法 MFA 密钥不该抛——证明上一条转红的原因确实是 MFA 检查，而非别的。 */
  @Test
  @DisplayName("同一个 dev 路径上合法密钥不抛（上一条的对照组）")
  void devPathPassesWithAValidKey() {
    SecurityDefaultsGuard guard = guardWith(VALID);
    setField(guard, "activeProfiles", "dev");
    setField(guard, "jwtSecret", DEV_DEFAULT_JWT_SECRET);

    assertDoesNotThrow(() -> guard.run(null));
  }

  private static void setField(Object target, String name, Object value) {
    try {
      Field field = SecurityDefaultsGuard.class.getDeclaredField(name);
      field.setAccessible(true);
      field.set(target, value);
    } catch (ReflectiveOperationException ex) {
      throw new IllegalStateException("无法设置字段 " + name, ex);
    }
  }

  /** 配置值可能是运维误粘的别的东西（私钥、口令），不能进日志与异常。 */
  @Test
  @DisplayName("失败消息不回显配置内容")
  void failureMessageDoesNotEchoTheKey() {
    IllegalStateException ex =
        assertThrows(IllegalStateException.class, () -> guardWith(NOT_BASE64).checkMfaSecretKey());

    assertFalse(ex.getMessage().contains("!"), "消息里出现了配置内容：" + ex.getMessage());
    assertFalse(ex.getMessage().contains(NOT_BASE64), "消息里出现了完整的配置值");
  }
}
