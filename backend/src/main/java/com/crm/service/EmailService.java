/** 邮件服务（079-scheduled-export）：发送导出文件邮件。 */
package com.crm.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
public class EmailService {

  private static final Logger log = LoggerFactory.getLogger(EmailService.class);

  private final JavaMailSender mailSender;
  private final String fromAddress;

  public EmailService(
      JavaMailSender mailSender, @Value("${spring.mail.from:crm@example.com}") String fromAddress) {
    this.mailSender = mailSender;
    this.fromAddress = fromAddress;
  }

  /** 发送简单邮件（无附件）。 */
  public void sendSimpleEmail(String to, String subject, String text) {
    sendEmailWithRetry(to, subject, text, null, 3);
  }

  /** 发送带附件的邮件。 */
  public void sendEmailWithAttachment(String to, String subject, String text, String filePath) {
    sendEmailWithRetry(to, subject, text, filePath, 3);
  }

  private void sendEmailWithRetry(
      String to, String subject, String text, String filePath, int maxRetries) {
    for (int i = 0; i < maxRetries; i++) {
      try {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(fromAddress);
        message.setTo(to);
        message.setSubject(subject);
        message.setText(text);

        if (mailSender != null) {
          mailSender.send(message);
        }

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
