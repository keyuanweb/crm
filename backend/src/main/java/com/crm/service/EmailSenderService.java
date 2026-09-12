package com.crm.service;

import com.crm.config.MailStatus;
import com.crm.entity.EmailCampaign;
import com.crm.entity.EmailSendLog;
import com.crm.repository.EmailCampaignMapper;
import com.crm.repository.EmailSendLogMapper;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * 邮件异步发送器（独立 bean 使 @Async 代理生效——B1 安全/性能审计修复： 原 EmailCampaignService 内自调用导致 @Async 失效、群发同步阻塞业务事务）。
 *
 * <p>一期诚信修复：本类是"邮件是否真发出"的唯一裁决处。未配置 SMTP 时一律标记 {@link EmailSendLog#STATUS_SKIPPED} 并写入 {@link
 * MailStatus#NOT_CONFIGURED_MESSAGE}，<b>绝不标记 SENT</b>；调用方也不得在发送前预置 SENT。 每封邮件无论成功、失败还是跳过，状态都必定回写。
 */
@Service
public class EmailSenderService {

  private static final Logger log = LoggerFactory.getLogger(EmailSenderService.class);

  private final EmailSendLogMapper sendLogMapper;
  private final EmailCampaignMapper campaignMapper;
  private final MailStatus mailStatus;
  private final JavaMailSender mailSender;

  public EmailSenderService(
      EmailSendLogMapper sendLogMapper,
      EmailCampaignMapper campaignMapper,
      MailStatus mailStatus,
      @Autowired(required = false) JavaMailSender mailSender) {
    this.sendLogMapper = sendLogMapper;
    this.campaignMapper = campaignMapper;
    this.mailStatus = mailStatus;
    this.mailSender = mailSender;
  }

  /**
   * 群发异步发送（仅在 SMTP 已配置时由调用方使用）。
   *
   * <p>@Async 使其在独立线程执行；发送完成后回写批次汇总。未配置 SMTP 时调用方应改用同步的 {@link #send} ——
   * 配置检查是纯内存判断，无需异步，异步反而会与调用方未提交的事务竞争。
   */
  @Async
  public void sendAsync(EmailCampaign campaign, List<EmailSendLog> logs) {
    send(campaign, logs);
  }

  /**
   * 群发发送（同步）：逐封尝试并回写真实状态，最后回写批次汇总。
   *
   * <p>未配置 SMTP 时全部标记 SKIPPED；配置了则逐封发，成功 SENT、异常 FAILED。
   */
  public void send(EmailCampaign campaign, List<EmailSendLog> logs) {
    if (!mailStatus.isConfigured()) {
      log.warn(
          "SMTP 未配置，批次 {} 的 {} 封邮件未发送（标记 {}）",
          campaign.getId(),
          logs.size(),
          EmailSendLog.STATUS_SKIPPED);
    }
    for (EmailSendLog sendLog : logs) {
      sendOne(sendLog);
    }
    finalizeCampaign(campaign, logs);
  }

  /**
   * 发送单封邮件并回写状态：未配置 SMTP → SKIPPED，成功 → SENT，异常 → FAILED。
   *
   * <p>无论结果如何都会 {@code updateById}，不存在"静默跳过"的分支。
   *
   * @return 是否真正发出
   */
  public boolean sendOne(EmailSendLog sendLog) {
    if (!mailStatus.isConfigured()) {
      sendLog.setStatus(EmailSendLog.STATUS_SKIPPED);
      sendLog.setErrorMessage(MailStatus.NOT_CONFIGURED_MESSAGE);
      sendLogMapper.updateById(sendLog);
      return false;
    }
    try {
      SimpleMailMessage msg = new SimpleMailMessage();
      msg.setTo(sendLog.getEmail());
      msg.setSubject(sendLog.getSubject());
      msg.setText(stripHtml(sendLog.getContent()));
      mailSender.send(msg);
      sendLog.setStatus(EmailSendLog.STATUS_SENT);
      sendLog.setErrorMessage(null);
      sendLogMapper.updateById(sendLog);
      return true;
    } catch (Exception ex) {
      log.warn("Mail send failed to {}: {}", sendLog.getEmail(), ex.getMessage());
      sendLog.setStatus(EmailSendLog.STATUS_FAILED);
      sendLog.setErrorMessage(ex.getMessage());
      sendLogMapper.updateById(sendLog);
      return false;
    }
  }

  /**
   * 按每封的真实状态回写批次汇总：sentCount 只数真正发出的，批次状态由实际结果决定。
   *
   * <p>直接改写传入的 campaign 对象（调用方在 fire-and-forget 之后不再写该行），避免重新查库 —— 异步路径下重查会撞上调用方尚未提交的事务。
   */
  private void finalizeCampaign(EmailCampaign campaign, List<EmailSendLog> logs) {
    if (campaign == null || campaign.getId() == null) {
      return;
    }
    long sent = logs.stream().filter(l -> EmailSendLog.STATUS_SENT.equals(l.getStatus())).count();
    long failed =
        logs.stream().filter(l -> EmailSendLog.STATUS_FAILED.equals(l.getStatus())).count();
    long skipped =
        logs.stream().filter(l -> EmailSendLog.STATUS_SKIPPED.equals(l.getStatus())).count();
    campaign.setSentCount((int) sent);
    campaign.setFailedCount((int) failed);
    // 有跳过则整批标记 SKIPPED，让界面能一眼看出"这批根本没发出去"
    campaign.setStatus(skipped > 0 ? EmailCampaign.STATUS_SKIPPED : EmailCampaign.STATUS_DONE);
    campaignMapper.updateById(campaign);
    log.info(
        "批次 {} 汇总回写：成功 {}，失败 {}，跳过 {}，状态 {}",
        campaign.getId(),
        sent,
        failed,
        skipped,
        campaign.getStatus());
  }

  private String stripHtml(String html) {
    if (html == null) {
      return "";
    }
    return html.replaceAll("<[^>]*>", "").replace("&nbsp;", " ").trim();
  }
}
