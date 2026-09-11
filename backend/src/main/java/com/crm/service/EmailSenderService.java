package com.crm.service;

import com.crm.entity.EmailSendLog;
import com.crm.entity.EmailTemplate;
import com.crm.repository.EmailSendLogMapper;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * 邮件异步发送器（独立 bean 使 @Async 代理生效——B1 安全/性能审计修复： 原 EmailCampaignService 内自调用导致 @Async 失效、群发同步阻塞业务事务）。
 */
@Service
public class EmailSenderService {

  private static final Logger log = LoggerFactory.getLogger(EmailSenderService.class);

  private final EmailSendLogMapper sendLogMapper;
  private final JavaMailSender mailSender;

  public EmailSenderService(EmailSendLogMapper sendLogMapper, @org.springframework.beans.factory.annotation.Autowired(required = false) JavaMailSender mailSender) {
    this.sendLogMapper = sendLogMapper;
    this.mailSender = mailSender;
  }

  /** 群发异步发送：有 SMTP 逐封发，无 SMTP 日志模拟；失败标记 FAILED。 */
  @Async
  public void sendAsync(Long campaignId, List<EmailSendLog> logs, EmailTemplate template) {
    for (EmailSendLog sendLog : logs) {
      try {
        if (mailSender != null) {
          SimpleMailMessage msg = new SimpleMailMessage();
          msg.setTo(sendLog.getEmail());
          msg.setSubject(sendLog.getSubject());
          msg.setText(stripHtml(sendLog.getContent()));
          mailSender.send(msg);
        } else {
          log.debug(
              "Mail simulated (no SMTP): to={} subject={}",
              sendLog.getEmail(),
              sendLog.getSubject());
        }
      } catch (Exception ex) {
        log.warn("Mail send failed to {}: {}", sendLog.getEmail(), ex.getMessage());
        sendLog.setStatus("FAILED");
        sendLog.setErrorMessage(ex.getMessage());
        sendLogMapper.updateById(sendLog);
      }
    }
  }

  private String stripHtml(String html) {
    if (html == null) {
      return "";
    }
    return html.replaceAll("<[^>]*>", "").replace("&nbsp;", " ").trim();
  }
}
