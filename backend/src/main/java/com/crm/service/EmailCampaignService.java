package com.crm.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.crm.common.BusinessException;
import com.crm.common.ErrorCode;
import com.crm.common.PageResult;
import com.crm.dto.email.CampaignRequest;
import com.crm.entity.Customer;
import com.crm.entity.EmailCampaign;
import com.crm.entity.EmailSendLog;
import com.crm.entity.EmailTemplate;
import com.crm.entity.EmailTrack;
import com.crm.repository.CustomerMapper;
import com.crm.repository.EmailCampaignMapper;
import com.crm.repository.EmailSendLogMapper;
import com.crm.repository.EmailTrackMapper;
import com.crm.security.SecurityUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 邮件群发服务（030-email-marketing，FR-002/003/004）：创建批次 → 解析收件人 → @Async 异步逐封发送（无 SMTP dev 模拟 SENT）→
 * 打开/点击追踪记录。
 */
@Service
public class EmailCampaignService {

  private static final Logger log = LoggerFactory.getLogger(EmailCampaignService.class);
  private static final ObjectMapper MAPPER = new ObjectMapper();
  private static final int BATCH_CAP = 500;

  private final EmailCampaignMapper campaignMapper;
  private final EmailSendLogMapper sendLogMapper;
  private final EmailTrackMapper trackMapper;
  private final EmailTemplateService templateService;
  private final SegmentService segmentService;
  private final CustomerMapper customerMapper;
  private final AuditService auditService;
  private final JavaMailSender mailSender;

  public EmailCampaignService(
      EmailCampaignMapper campaignMapper,
      EmailSendLogMapper sendLogMapper,
      EmailTrackMapper trackMapper,
      EmailTemplateService templateService,
      SegmentService segmentService,
      CustomerMapper customerMapper,
      AuditService auditService,
      @org.springframework.beans.factory.annotation.Autowired(required = false)
          JavaMailSender mailSender) {
    this.campaignMapper = campaignMapper;
    this.sendLogMapper = sendLogMapper;
    this.trackMapper = trackMapper;
    this.templateService = templateService;
    this.segmentService = segmentService;
    this.customerMapper = customerMapper;
    this.auditService = auditService;
    this.mailSender = mailSender;
  }

  /** 创建并异步发送群发批次。 */
  @Transactional
  public EmailCampaign createAndSend(CampaignRequest req) {
    if (!java.util.Set.of("SEGMENT", "CUSTOMER_IDS").contains(req.getSourceType())) {
      throw new BusinessException(ErrorCode.BAD_REQUEST, "收件人来源不合法");
    }
    EmailTemplate template = templateService.require(req.getTemplateId());
    // 解析收件人（客户 id → 邮箱）
    List<Long> customerIds = resolveRecipients(req);
    if (customerIds.size() > BATCH_CAP) {
      customerIds = customerIds.subList(0, BATCH_CAP);
    }
    Map<Long, String> emails = emailsOf(customerIds);

    EmailCampaign campaign = new EmailCampaign();
    campaign.setTemplateId(template.getId());
    campaign.setName(req.getName() == null ? template.getName() : req.getName());
    campaign.setSourceType(req.getSourceType());
    campaign.setSourceRef(
        req.getSourceType().equals("SEGMENT")
            ? String.valueOf(req.getSegmentId())
            : idsToJson(customerIds));
    campaign.setTotalCount(emails.size());
    campaign.setStatus("RUNNING");
    campaign.setCreatedBy(SecurityUtil.currentUserId());
    campaignMapper.insert(campaign);

    // 逐封建发送记录（异步发送在 sendAsync 中执行）
    List<EmailSendLog> logs = new ArrayList<>();
    for (Map.Entry<Long, String> e : emails.entrySet()) {
      EmailSendLog l = new EmailSendLog();
      l.setCampaignId(campaign.getId());
      l.setCustomerId(e.getKey());
      l.setEmail(e.getValue());
      l.setSubject(template.getSubject());
      l.setContent(template.getContent());
      l.setStatus("SENT");
      l.setCreatedAt(java.time.LocalDateTime.now());
      sendLogMapper.insert(l);
      logs.add(l);
    }
    // 异步真正发送（无 SMTP 时仅日志，状态已 SENT）
    sendAsync(campaign.getId(), logs, template);
    campaign.setSentCount(logs.size());
    campaign.setStatus("DONE");
    campaignMapper.updateById(campaign);
    auditService.record(
        "SEND",
        "EMAIL_CAMPAIGN",
        campaign.getId(),
        "邮件群发：" + campaign.getName() + " 共 " + logs.size() + " 封");
    return campaign;
  }

  /** 异步发送：有 SMTP 逐封发，无 SMTP 日志模拟。 */
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

  /** 打开追踪：返回 1x1 透明 GIF，记录 OPEN（防重复）。 */
  @Transactional
  public byte[] trackOpen(Long sendLogId) {
    EmailSendLog l = sendLogMapper.selectById(sendLogId);
    if (l != null) {
      Long exists =
          trackMapper.selectCount(
              new LambdaQueryWrapper<EmailTrack>()
                  .eq(EmailTrack::getSendLogId, sendLogId)
                  .eq(EmailTrack::getTrackType, "OPEN"));
      if (exists == null || exists == 0) {
        EmailTrack t = new EmailTrack();
        t.setSendLogId(sendLogId);
        t.setTrackType("OPEN");
        t.setCreatedAt(java.time.LocalDateTime.now());
        trackMapper.insert(t);
        incrementOpen(l.getCampaignId());
      }
    }
    // 1x1 透明 GIF
    byte[] gif = {
      (byte) 0x47,
      (byte) 0x49,
      (byte) 0x46,
      (byte) 0x38,
      (byte) 0x39,
      (byte) 0x61,
      (byte) 0x01,
      (byte) 0x00,
      (byte) 0x01,
      (byte) 0x00,
      (byte) 0x80,
      (byte) 0x00,
      (byte) 0x00,
      (byte) 0x00,
      (byte) 0x00,
      (byte) 0x00,
      (byte) 0xFF,
      (byte) 0xFF,
      (byte) 0xFF,
      (byte) 0x21,
      (byte) 0xF9,
      (byte) 0x04,
      (byte) 0x01,
      (byte) 0x00,
      (byte) 0x00,
      (byte) 0x00,
      (byte) 0x00,
      (byte) 0x2C,
      (byte) 0x00,
      (byte) 0x00,
      (byte) 0x00,
      (byte) 0x00,
      (byte) 0x01,
      (byte) 0x00,
      (byte) 0x01,
      (byte) 0x00,
      (byte) 0x00,
      (byte) 0x02,
      (byte) 0x02,
      (byte) 0x44,
      (byte) 0x01,
      (byte) 0x00,
      (byte) 0x3B
    };
    return gif;
  }

  /** 点击追踪：记录 CLICK，返回目标 URL。 */
  @Transactional
  public String trackClick(Long sendLogId, String url) {
    EmailSendLog l = sendLogMapper.selectById(sendLogId);
    if (l != null) {
      EmailTrack t = new EmailTrack();
      t.setSendLogId(sendLogId);
      t.setTrackType("CLICK");
      t.setClickUrl(url);
      t.setCreatedAt(java.time.LocalDateTime.now());
      trackMapper.insert(t);
      incrementClick(l.getCampaignId());
    }
    return url == null ? "/" : url;
  }

  /** 活动列表。 */
  public List<EmailCampaign> list() {
    return campaignMapper.selectList(
        new LambdaQueryWrapper<EmailCampaign>().orderByDesc(EmailCampaign::getId));
  }

  /** 活动详情（含发送记录分页）。 */
  public PageResult<EmailSendLog> detail(Long id, long page, long pageSize) {
    EmailCampaign campaign = campaignMapper.selectById(id);
    if (campaign == null) {
      throw new BusinessException(ErrorCode.BAD_REQUEST, "活动不存在");
    }
    com.baomidou.mybatisplus.extension.plugins.pagination.Page<EmailSendLog> p =
        sendLogMapper.selectPage(
            new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(page, pageSize),
            new LambdaQueryWrapper<EmailSendLog>()
                .eq(EmailSendLog::getCampaignId, id)
                .orderByDesc(EmailSendLog::getId));
    return PageResult.of(p.getRecords(), p.getTotal(), page, pageSize);
  }

  /** 测试发送：渲染模板发给自己。 */
  @Transactional
  public void testSend(Long campaignId, String email) {
    EmailCampaign c = campaignMapper.selectById(campaignId);
    EmailTemplate t = templateService.require(c.getTemplateId());
    EmailSendLog sendLog = new EmailSendLog();
    sendLog.setCampaignId(campaignId);
    sendLog.setEmail(email);
    sendLog.setSubject(t.getSubject());
    sendLog.setContent(t.getContent());
    sendLog.setStatus("SENT");
    sendLog.setCreatedAt(java.time.LocalDateTime.now());
    sendLogMapper.insert(sendLog);
    try {
      if (mailSender != null) {
        SimpleMailMessage msg = new SimpleMailMessage();
        msg.setTo(email);
        msg.setSubject("[测试] " + t.getSubject());
        msg.setText(stripHtml(t.getContent()));
        mailSender.send(msg);
      }
    } catch (Exception ex) {
      log.warn("Test mail failed: {}", ex.getMessage());
    }
  }

  private List<Long> resolveRecipients(CampaignRequest req) {
    if ("SEGMENT".equals(req.getSourceType())) {
      if (req.getSegmentId() == null) {
        throw new BusinessException(ErrorCode.BAD_REQUEST, "请选择细分");
      }
      com.crm.entity.Segment seg = segmentService.requirePublic(req.getSegmentId());
      return segmentService.members(seg);
    }
    return req.getCustomerIds() == null ? List.of() : req.getCustomerIds();
  }

  private Map<Long, String> emailsOf(List<Long> customerIds) {
    if (customerIds.isEmpty()) {
      return Map.of();
    }
    Map<Long, String> result = new HashMap<>();
    for (Customer c : customerMapper.selectBatchIds(customerIds)) {
      if (c.getEmail() != null && !c.getEmail().isBlank()) {
        result.put(c.getId(), c.getEmail().trim());
      }
    }
    return result;
  }

  private void incrementOpen(Long campaignId) {
    EmailCampaign c = campaignMapper.selectById(campaignId);
    if (c != null) {
      c.setOpenCount(c.getOpenCount() + 1);
      campaignMapper.updateById(c);
    }
  }

  private void incrementClick(Long campaignId) {
    EmailCampaign c = campaignMapper.selectById(campaignId);
    if (c != null) {
      c.setClickCount(c.getClickCount() + 1);
      campaignMapper.updateById(c);
    }
  }

  private String idsToJson(List<Long> ids) {
    try {
      return MAPPER.writeValueAsString(ids);
    } catch (Exception ex) {
      return String.join(",", ids.stream().map(String::valueOf).toList());
    }
  }

  private String stripHtml(String html) {
    return html == null ? "" : html.replaceAll("<[^>]*>", "").replace("&nbsp;", " ");
  }
}
