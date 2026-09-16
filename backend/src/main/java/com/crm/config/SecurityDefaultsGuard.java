package com.crm.config;

import com.crm.service.MfaSecretEncryptionService;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/** 启动安全检查（安全加固）：非 dev 环境使用默认 JWT 密钥时告警/阻止，防止默认配置泄漏生产。 */
@Component
public class SecurityDefaultsGuard implements ApplicationRunner {

  private static final Logger log = LoggerFactory.getLogger(SecurityDefaultsGuard.class);
  private static final String DEFAULT_DEV_SECRET =
      "crm-dev-secret-key-please-override-in-prod-0123456789abcdef";
  private static final int MIN_SECRET_LENGTH = 32;

  @Value("${jwt.secret}")
  private String jwtSecret;

  @Value("${spring.profiles.active:}")
  private String activeProfiles;

  /** 082：配置判据**只有一处**，在它里面（{@code configurationProblem()}）。本类只决定动作。 */
  private final MfaSecretEncryptionService mfaSecretEncryptionService;

  public SecurityDefaultsGuard(MfaSecretEncryptionService mfaSecretEncryptionService) {
    this.mfaSecretEncryptionService = mfaSecretEncryptionService;
  }

  @Override
  public void run(ApplicationArguments args) {
    // 082：MFA 密钥守卫。**必须留在本方法最前面**，这不是随手放的位置：
    // 下面第 1 步在 dev 环境走的是 `return`（而不是"检查完继续往下"），所以任何追加在它
    // 之后的检查在 dev 里**永远不会执行**。而 dev 恰恰是"本机没配 MFA 密钥"最常见的地方
    // ——放错位置会让这道检查在最需要它的环境里静默失效，且失败方向是"看起来检查过了"。
    checkMfaSecretKey();

    boolean dev = activeProfiles.toLowerCase().contains("dev");

    // 1. 默认密钥检查
    if (DEFAULT_DEV_SECRET.equals(jwtSecret)) {
      if (!dev) {
        // 非 dev 环境使用默认密钥 → fail-fast
        throw new IllegalStateException(
            "SECURITY: 当前环境为 ["
                + activeProfiles
                + "] 但仍在使用开发默认 JWT 密钥。必须通过环境变量 JWT_SECRET 设置强随机密钥后启动。\n"
                + "生成命令: openssl rand -hex 32");
      }
      log.warn("SECURITY: dev 环境使用默认 JWT 密钥，仅限本地开发。生产环境必须替换。");
      return;
    }

    // 2. 密钥强度检查（长度 ≥ 32 字符）
    if (jwtSecret.length() < MIN_SECRET_LENGTH) {
      if (!dev) {
        throw new IllegalStateException(
            "SECURITY: JWT 密钥长度仅 "
                + jwtSecret.length()
                + " 字符，低于最低要求 "
                + MIN_SECRET_LENGTH
                + " 字符。生产环境请使用至少 32 字符的强随机密钥。\n"
                + "生成命令: openssl rand -hex 32");
      }
      log.warn(
          "SECURITY: JWT 密钥长度仅 {} 字符（最低要求 {}），建议生产环境使用至少 {} 字符的强随机密钥。",
          jwtSecret.length(),
          MIN_SECRET_LENGTH,
          MIN_SECRET_LENGTH);
    }

    // 3. 生产环境提示
    if (!dev) {
      log.info("SECURITY: 生产环境 JWT 密钥已配置（长度 {} 字符），安全基线检查通过。", jwtSecret.length());
    }
  }

  /**
   * MFA 密钥守卫（082-two-factor-auth）。
   *
   * <p>判据本身在 {@link MfaSecretEncryptionService#configurationProblem()} 里——本方法只决定
   * <b>动作</b>，两种错法分开处置：
   *
   * <ul>
   *   <li><b>空白（未配置）</b>：告警后放行。2FA 是可选的，很多部署不开；直接拒绝启动等于用一个 没人开启的功能炸掉整个服务。真正用到密钥的路径在调用时以 {@code
   *       MFA_SECRET_MISSING} 失败——<b>fail closed，不会静默降级为单因素</b>。
   *   <li><b>配了但不合法</b>：<b>启动即抛</b>。这是误配而不是"没配"：它在启动时没有任何症状，
   *       要等某个人真的去绑定认证器时才炸，而那一刻的错误现场是"某个人绑不上"——排查得从 配置查起。把症状提前到启动时，代价是一次拒绝启动，收益是错误现场指向正确的地方。
   * </ul>
   *
   * <p>刻意<b>不</b>检查"是不是用了默认值"：与 JWT 密钥不同，本项目不提供 MFA 的默认密钥， 留空是"未配置"的合法表达，不存在"以为在生产、其实用了开发默认值"这条路径。
   *
   * <p>告警与异常里都<b>不回显配置内容</b>（只报长度与形态）：那个值可能是运维误粘的别的东西。
   *
   * <p><b>包内可见而不是 {@code private}</b>：这是一步可以独立验证的检查（{@code SecurityDefaultsGuardTest} 直接调它），而"它在
   * {@code run()} 里被调到、且在 dev 的 {@code return} 之前"是另一条性质（同文件 {@code
   * checkRunsBeforeTheDevEarlyReturn}：反射设好 dev 与默认 JWT 密钥这两个 {@code @Value} 字段后直接调 {@code
   * run()}）。两者分开测，是因为合成一个会 让"判据错了"与"位置错了"给出同一条失败信息。
   */
  void checkMfaSecretKey() {
    if (!mfaSecretEncryptionService.isConfigured()) {
      log.warn(
          "SECURITY: 未配置双因素认证密钥（crm.security.mfa.secret-key / 环境变量 MFA_SECRET_KEY）。"
              + "2FA 的绑定与启用会在调用时以 MFA_SECRET_MISSING 失败（不会降级为单因素）。"
              + "需要该功能时用 `openssl rand -base64 32` 生成后注入。");
      return;
    }
    Optional<String> problem = mfaSecretEncryptionService.configurationProblem();
    if (problem.isPresent()) {
      throw new IllegalStateException(
          "SECURITY: " + problem.get() + "\n生成命令: openssl rand -base64 32");
    }
  }
}
