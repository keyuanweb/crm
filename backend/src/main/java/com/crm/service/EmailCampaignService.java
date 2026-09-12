package com.crm.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.crm.common.BusinessException;
import com.crm.common.ErrorCode;
import com.crm.common.PageResult;
import com.crm.config.MailStatus;
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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * 邮件群发服务（030-email-marketing，FR-002/003/004）：创建批次 → 解析收件人 → 逐封发送（SMTP 已配置走 @Async， 未配置则同步标记
 * SKIPPED）→ 打开/点击追踪记录。
 *
 * <p>一期诚信修复：本类不再自行判定"发送成功"——真实状态一律由 {@link EmailSenderService} 回写。
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
  private final EmailUnsubscribeService unsubscribeService;
  private final MailStatus mailStatus;
  private final EmailSenderService emailSender;

  public EmailCampaignService(
      EmailCampaignMapper campaignMapper,
      EmailSendLogMapper sendLogMapper,
      EmailTrackMapper trackMapper,
      EmailTemplateService templateService,
      SegmentService segmentService,
      CustomerMapper customerMapper,
      AuditService auditService,
      EmailUnsubscribeService unsubscribeService,
      MailStatus mailStatus,
      EmailSenderService emailSender) {
    this.campaignMapper = campaignMapper;
    this.sendLogMapper = sendLogMapper;
    this.trackMapper = trackMapper;
    this.templateService = templateService;
    this.segmentService = segmentService;
    this.customerMapper = customerMapper;
    this.auditService = auditService;
    this.unsubscribeService = unsubscribeService;
    this.mailStatus = mailStatus;
    this.emailSender = emailSender;
  }

  /** 创建并异步发送群发批次。 */
  @Transactional
  public EmailCampaign createAndSend(CampaignRequest req) {
    if (!java.util.Set.of("SEGMENT", "CUSTOMER_IDS").contains(req.getSourceType())) {
      throw new BusinessException(ErrorCode.BAD_REQUEST, "收件人来源不合法");
    }
    EmailTemplate template = templateService.require(req.getTemplateId());
    // 052：A/B 测试校验（variant=AB 需 B 主题）
    boolean abTest = "AB".equalsIgnoreCase(req.getVariant());
    if (abTest && !StringUtils.hasText(req.getSubjectB())) {
      throw new BusinessException(ErrorCode.EMAIL_AB_SUBJECT_REQUIRED);
    }
    // 解析收件人（客户 id → 邮箱）
    List<Long> customerIds = resolveRecipients(req);
    if (customerIds.size() > BATCH_CAP) {
      customerIds = customerIds.subList(0, BATCH_CAP);
    }
    Map<Long, String> emails = emailsOf(customerIds);
    // 052：排除已退订邮箱。
    // filterUnsubscribed 返回的是"未退订"的 id（其单测 assert 的正是这个语义），此前的循环却把它们全部删除 ——
    // 方向正好相反：群发只发给已退订的人，未退订的人一个都收不到；没人退订时收件人为 0（整批发空）。
    // 改为在此显式逐个判定，意图不依赖对方法名或返回值的记忆。
    for (Long cid : List.copyOf(emails.keySet())) {
      if (unsubscribeService.isUnsubscribed(emails.get(cid))) {
        emails.remove(cid);
      }
    }

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
    campaign.setVariant(abTest ? "AB" : "NONE");
    campaign.setSubjectB(abTest ? req.getSubjectB() : null);
    campaign.setCreatedBy(SecurityUtil.currentUserId());
    campaignMapper.insert(campaign);

    // 逐封建发送记录（异步发送在 sendAsync 中执行）；A/B 按客户 id 奇偶分组
    List<EmailSendLog> logs = new ArrayList<>();
    for (Map.Entry<Long, String> e : emails.entrySet()) {
      EmailSendLog l = new EmailSendLog();
      l.setCampaignId(campaign.getId());
      l.setCustomerId(e.getKey());
      l.setEmail(e.getValue());
      if (abTest) {
        String variant = e.getKey() % 2 == 0 ? "A" : "B";
        l.setVariant(variant);
        l.setSubject("A".equals(variant) ? template.getSubject() : req.getSubjectB());
      } else {
        l.setSubject(template.getSubject());
      }
      l.setContent(template.getContent());
      l.setStatus(EmailSendLog.STATUS_PENDING);
      l.setCreatedAt(java.time.LocalDateTime.now());
      sendLogMapper.insert(l);
      logs.add(l);
    }
    // 一期诚信修复：发送前不再预置 SENT，也由发送器（而非此处）回写汇总。
    //   SMTP 已配置 → 异步发送，完成后发送器回写真实汇总；
    //   未配置     → 同步标记 SKIPPED（配置检查是纯内存判断，无需异步，也避免异步线程与未提交事务竞争）。
    if (mailStatus.isConfigured()) {
      emailSender.sendAsync(campaign, logs);
    } else {
      emailSender.send(campaign, logs);
    }
    auditService.record(
        "SEND",
        "EMAIL_CAMPAIGN",
        campaign.getId(),
        "邮件群发：" + campaign.getName() + " 共 " + logs.size() + " 封" + (abTest ? "（A/B 测试）" : ""));
    return campaign;
  }

  /** 营销自动化单发（049）：按模板发单封邮件，记录 EmailSendLog（campaignId=null 标记自动化）。 */
  @Transactional
  public void sendAutomationEmail(Long templateId, String toEmail) {
    if (toEmail == null || toEmail.isBlank()) {
      throw new BusinessException(ErrorCode.BAD_REQUEST, "收件人邮箱为空");
    }
    // 052：退订邮箱跳过自动化发送
    if (unsubscribeService.isUnsubscribed(toEmail)) {
      return;
    }
    EmailTemplate template = templateService.require(templateId);
    EmailSendLog l = new EmailSendLog();
    l.setCampaignId(null);
    l.setCustomerId(null);
    l.setEmail(toEmail);
    l.setSubject(template.getSubject());
    l.setContent(template.getContent());
    l.setStatus(EmailSendLog.STATUS_PENDING);
    l.setCreatedAt(java.time.LocalDateTime.now());
    sendLogMapper.insert(l);
    // 一期诚信修复：由发送器统一裁决并回写 SENT / FAILED / SKIPPED，未配置 SMTP 不再假装已发
    emailSender.sendOne(l);
    auditService.record(
        "SEND", "EMAIL_CAMPAIGN", l.getId(), "自动化邮件（模板 " + templateId + " → " + toEmail + "）");
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

  /** 052：群发统计（送达/打开/点击 + 率 + A/B 变体）。 */
  public Map<String, Object> stats(Long campaignId) {
    EmailCampaign campaign = campaignMapper.selectById(campaignId);
    if (campaign == null) {
      throw new BusinessException(ErrorCode.BAD_REQUEST, "活动不存在");
    }
    List<EmailSendLog> logs =
        sendLogMapper.selectList(
            new LambdaQueryWrapper<EmailSendLog>().eq(EmailSendLog::getCampaignId, campaignId));
    long sent = logs.stream().filter(l -> EmailSendLog.STATUS_SENT.equals(l.getStatus())).count();
    long failed =
        logs.stream().filter(l -> EmailSendLog.STATUS_FAILED.equals(l.getStatus())).count();
    long skipped =
        logs.stream().filter(l -> EmailSendLog.STATUS_SKIPPED.equals(l.getStatus())).count();
    Long openCount = 0L;
    Long clickCount = 0L;
    if (!logs.isEmpty()) {
      openCount =
          trackMapper.selectCount(
              new LambdaQueryWrapper<EmailTrack>()
                  .eq(EmailTrack::getTrackType, "OPEN")
                  .in(EmailTrack::getSendLogId, logs.stream().map(EmailSendLog::getId).toList()));
      clickCount =
          trackMapper.selectCount(
              new LambdaQueryWrapper<EmailTrack>()
                  .eq(EmailTrack::getTrackType, "CLICK")
                  .in(EmailTrack::getSendLogId, logs.stream().map(EmailSendLog::getId).toList()));
    }
    long open = openCount == null ? 0 : openCount;
    long click = clickCount == null ? 0 : clickCount;

    Map<String, Object> resp = new java.util.HashMap<>();
    resp.put("campaignId", campaignId);
    resp.put("total", logs.size());
    resp.put("sent", sent);
    resp.put("failed", failed);
    // 一期诚信修复：未配置 SMTP 时整批跳过，界面据此提示"邮件未真实发送"
    resp.put("skipped", skipped);
    resp.put("openCount", open);
    resp.put("clickCount", click);
    // 一封都没发出时打开/点击率按 0 报，不做 sent==0 → 除以 1 的口径掩盖
    resp.put("openRate", sent == 0 ? 0.0 : Math.round(open * 1000.0 / sent) / 10.0);
    resp.put("clickRate", sent == 0 ? 0.0 : Math.round(click * 1000.0 / sent) / 10.0);
    resp.put("variant", campaign.getVariant());

    // A/B 变体统计
    if ("AB".equals(campaign.getVariant())) {
      List<Map<String, Object>> variantStats = new ArrayList<>();
      for (String variant : List.of("A", "B")) {
        List<EmailSendLog> vLogs =
            logs.stream().filter(l -> variant.equals(l.getVariant())).toList();
        long vSent =
            vLogs.stream().filter(l -> EmailSendLog.STATUS_SENT.equals(l.getStatus())).count();
        Long vOpen = 0L;
        if (!vLogs.isEmpty()) {
          vOpen =
              trackMapper.selectCount(
                  new LambdaQueryWrapper<EmailTrack>()
                      .eq(EmailTrack::getTrackType, "OPEN")
                      .in(
                          EmailTrack::getSendLogId,
                          vLogs.stream().map(EmailSendLog::getId).toList()));
        }
        Map<String, Object> vs = new java.util.HashMap<>();
        vs.put("variant", variant);
        vs.put("sent", vSent);
        vs.put("openCount", vOpen == null ? 0 : vOpen);
        vs.put(
            "openRate",
            Math.round((vOpen == null ? 0 : vOpen) * 1000.0 / (vSent == 0 ? 1 : vSent)) / 10.0);
        variantStats.add(vs);
      }
      resp.put("variantStats", variantStats);
      // 标记更优者（打开率）
      double rateA = ((Number) variantStats.get(0).get("openRate")).doubleValue();
      double rateB = ((Number) variantStats.get(1).get("openRate")).doubleValue();
      String winner = rateA == rateB ? "NONE" : (rateA > rateB ? "A" : "B");
      resp.put("winner", winner);
    }
    return resp;
  }

  /**
   * 测试发送：渲染模板发给自己。发送结果由发送器回写（未配置 SMTP 记 SKIPPED）。
   *
   * <p>返回是否真正发出：调用方（控制器→前端）此前无条件提示"测试邮件已发送"，与未配置 SMTP 时的实际行为相反， 故把真实结果上抛。
   */
  @Transactional
  public boolean testSend(Long campaignId, String email) {
    EmailCampaign c = campaignMapper.selectById(campaignId);
    EmailTemplate t = templateService.require(c.getTemplateId());
    EmailSendLog sendLog = new EmailSendLog();
    sendLog.setCampaignId(campaignId);
    sendLog.setEmail(email);
    // 记录与实发主题保持一致（原先记录模板主题、实发带 [测试] 前缀，两边对不上）
    sendLog.setSubject("[测试] " + t.getSubject());
    sendLog.setContent(t.getContent());
    sendLog.setStatus(EmailSendLog.STATUS_PENDING);
    sendLog.setCreatedAt(java.time.LocalDateTime.now());
    sendLogMapper.insert(sendLog);
    return emailSender.sendOne(sendLog);
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
}
