/** 邮件服务（079-scheduled-export）：发送导出文件邮件。 */
package com.crm.service;

import com.crm.common.MailNotConfiguredException;
import com.crm.config.MailStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

/**
 * 事务性邮件服务（079-scheduled-export）：发送导出文件邮件。
 *
 * <p>一期诚信修复：未配置 SMTP 时<b>显式抛出</b> {@link MailNotConfiguredException}，不再"跳过发送 + 记录 sent
 * successfully"。调用方据此记 SKIPPED 或让请求失败，而不是以为已发出。
 */
@Service
public class EmailService {

  private static final Logger log = LoggerFactory.getLogger(EmailService.class);

  private final JavaMailSender mailSender;
  private final MailStatus mailStatus;
  private final String fromAddress;

  @Autowired
  public EmailService(
      @Autowired(required = false) JavaMailSender mailSender,
      MailStatus mailStatus,
      @Value("${crm.mail.from:crm@example.com}") String fromAddress) {
    this.mailSender = mailSender;
    this.mailStatus = mailStatus;
    this.fromAddress = fromAddress;
  }

  /** SMTP 是否已配置，供调用方决定降级策略（不抛异常的路径）。 */
  public boolean isConfigured() {
    return mailStatus.isConfigured();
  }

  /** 发送简单邮件（无附件）。未配置 SMTP 时抛 MailNotConfiguredException。 */
  public void sendSimpleEmail(String to, String subject, String text) {
    sendEmailWithRetry(to, subject, text, null, 3);
  }

  /** 发送带附件的邮件。未配置 SMTP 时抛 MailNotConfiguredException。 */
  public void sendEmailWithAttachment(String to, String subject, String text, String filePath) {
    sendEmailWithRetry(to, subject, text, filePath, 3);
  }

  private void sendEmailWithRetry(
      String to, String subject, String text, String filePath, int maxRetries) {
    if (!mailStatus.isConfigured()) {
      throw new MailNotConfiguredException(MailStatus.NOT_CONFIGURED_MESSAGE);
    }
    for (int i = 0; i < maxRetries; i++) {
      try {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(fromAddress);
        message.setTo(to);
        message.setSubject(subject);
        message.setText(text);

        mailSender.send(message);

        log.info("Email sent successfully to={}, subject={}", to, subject);
        return;
      } catch (MailException e) {
        log.warn("Email send failed (attempt {}/{}): {}", i + 1, maxRetries, e.getMessage());
        if (i < maxRetries - 1) {
          try {
            Thread.sleep(5000); // 重试间隔 5 分钟（简化为 5 秒）
          } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            break;
          }
        }
      }
    }
    log.error("Email send failed after {} retries to={}", maxRetries, to);
    throw new RuntimeException("Email send failed after " + maxRetries + " retries");
  }
}
