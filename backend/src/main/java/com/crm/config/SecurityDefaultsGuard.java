package com.crm.config;

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

  @Override
  public void run(ApplicationArguments args) {
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
}
