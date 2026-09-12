package com.crm.common;

/**
 * SMTP 未配置（一期诚信修复）：事务性邮件无法发送时抛出。
 *
 * <p>此前 {@code EmailService} 在 mailSender 为 null 时跳过发送，却仍然记录 "Email sent successfully"，
 * 调用方据此认为发送成功。改为显式抛出，由调用方决定是降级（记 SKIPPED）还是让请求失败。
 */
public class MailNotConfiguredException extends RuntimeException {

  public MailNotConfiguredException(String message) {
    super(message);
  }
}
