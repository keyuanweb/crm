package com.crm.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/** 启动安全检查（安全加固）：非 dev 环境使用默认 JWT 密钥时告警，防止默认配置泄漏生产。 */
@Component
public class SecurityDefaultsGuard implements ApplicationRunner {

  private static final Logger log = LoggerFactory.getLogger(SecurityDefaultsGuard.class);
  private static final String DEFAULT_DEV_SECRET =
      "crm-dev-secret-key-please-override-in-prod-0123456789abcdef";

  @Value("${jwt.secret}")
  private String jwtSecret;

  @Value("${spring.profiles.active:}")
  private String activeProfiles;

  @Override
  public void run(ApplicationArguments args) {
    boolean dev = activeProfiles.toLowerCase().contains("dev");
    if (!dev && DEFAULT_DEV_SECRET.equals(jwtSecret)) {
      // S1(安全审计)：非 dev 环境使用默认 JWT 密钥 → fail-fast，防止令牌伪造接管
      throw new IllegalStateException(
          "SECURITY: 当前环境为 ["
              + activeProfiles
              + "] 但仍在使用开发默认 JWT 密钥。必须通过环境变量 JWT_SECRET 设置强随机密钥后启动。");
    }
    if (dev && DEFAULT_DEV_SECRET.equals(jwtSecret)) {
      log.warn("SECURITY: dev 环境使用默认 JWT 密钥，仅限本地开发。");
    }
  }
}
