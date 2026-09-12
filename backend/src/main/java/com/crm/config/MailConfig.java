package com.crm.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.util.StringUtils;

/**
 * 邮件配置（030-email-marketing）：SMTP 环境变量；未配置 host 时返回 null bean，应用照常启动（不阻塞其他功能）。
 *
 * <p>一期诚信修复：null bean 不再代表"模拟发送成功"。是否可用由 {@link MailStatus} 统一判定， 各发送路径在未配置时标记 SKIPPED 而非 SENT。
 */
@Configuration
public class MailConfig {

  @Bean
  public JavaMailSender javaMailSender(
      @Value("${crm.mail.host:}") String host,
      @Value("${crm.mail.port:587}") int port,
      @Value("${crm.mail.username:}") String username,
      @Value("${crm.mail.password:}") String password) {
    if (!StringUtils.hasText(host)) {
      return null;
    }
    JavaMailSenderImpl sender = new JavaMailSenderImpl();
    sender.setHost(host);
    sender.setPort(port);
    if (StringUtils.hasText(username)) {
      sender.setUsername(username);
      sender.setPassword(password);
    }
    return sender;
  }
}
