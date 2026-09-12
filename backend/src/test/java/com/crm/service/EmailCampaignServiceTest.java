package com.crm.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.crm.config.MailStatus;
import com.crm.dto.email.CampaignRequest;
import com.crm.entity.Customer;
import com.crm.entity.EmailCampaign;
import com.crm.entity.EmailSendLog;
import com.crm.entity.EmailTemplate;
import com.crm.entity.EmailTrack;
import com.crm.entity.Segment;
import com.crm.repository.CustomerMapper;
import com.crm.repository.EmailCampaignMapper;
import com.crm.repository.EmailSendLogMapper;
import com.crm.repository.EmailTrackMapper;
import com.crm.security.JwtAuthFilter.CrmPrincipal;
import com.crm.security.SecurityUtil;
import java.util.List;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

/** EmailCampaignService 单元测试（030 T005）：群发收件人/统计/追踪。 */
class EmailCampaignServiceTest {

  @BeforeAll
  static void initTableInfo() {
    MybatisConfiguration configuration = new MybatisConfiguration();
    MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, "");
    TableInfoHelper.initTableInfo(assistant, EmailCampaign.class);
    TableInfoHelper.initTableInfo(assistant, EmailSendLog.class);
    TableInfoHelper.initTableInfo(assistant, EmailTrack.class);
  }

  private EmailCampaignMapper campaignMapper;
  private EmailSendLogMapper sendLogMapper;
  private EmailTrackMapper trackMapper;
  private EmailTemplateService templateService;
  private SegmentService segmentService;
  private CustomerMapper customerMapper;
  private AuditService auditService;
  private EmailUnsubscribeService unsubscribeService;
  private EmailCampaignService service;
  private MockedStatic<SecurityUtil> securityUtilMock;

  /** 重建被测服务：sender 用真实实现，使"未发送不得记 SENT"的判定真正被覆盖。 */
  private void buildService(MailStatus mailStatus, JavaMailSender mailSender) {
    EmailSenderService emailSender =
        new EmailSenderService(sendLogMapper, campaignMapper, mailStatus, mailSender);
    service =
        new EmailCampaignService(
            campaignMapper,
            sendLogMapper,
            trackMapper,
            templateService,
            segmentService,
            customerMapper,
            auditService,
            unsubscribeService,
            mailStatus,
            emailSender);
  }

  @BeforeEach
  void setUp() {
    campaignMapper = mock(EmailCampaignMapper.class);
    sendLogMapper = mock(EmailSendLogMapper.class);
    trackMapper = mock(EmailTrackMapper.class);
    templateService = mock(EmailTemplateService.class);
    segmentService = mock(SegmentService.class);
    customerMapper = mock(CustomerMapper.class);
    auditService = mock(AuditService.class);
    unsubscribeService = mock(EmailUnsubscribeService.class);
    // MyBatis-Plus 在真实 insert 后回填自增主键；mock 必须模拟这一契约，
    // 否则批次汇总回写会因 id 为空而跳过 —— 那正是本测试要覆盖的路径。
    when(campaignMapper.insert(any(EmailCampaign.class)))
        .thenAnswer(
            inv -> {
              inv.getArgument(0, EmailCampaign.class).setId(1L);
              return 1;
            });
    // 缺省：SMTP 未配置（crm.mail.host 为空）—— 正是此前被谎报成 SENT 的场景
    buildService(new MailStatus(""), null);
    securityUtilMock = Mockito.mockStatic(SecurityUtil.class);
    securityUtilMock.when(SecurityUtil::currentUserId).thenReturn(1L);
    securityUtilMock
        .when(SecurityUtil::currentPrincipal)
        .thenReturn(new CrmPrincipal(1L, "admin", "ADMIN"));
  }

  @AfterEach
  void tearDown() {
    securityUtilMock.close();
  }

  private EmailTemplate template() {
    EmailTemplate t = new EmailTemplate();
    t.setId(1L);
    t.setName("欢迎");
    t.setSubject("欢迎 {name}");
    t.setContent("<p>Hi {name}</p>");
    return t;
  }

  @Test
  @DisplayName("SEGMENT 收件人：解析细分成员（邮箱非空）建群发")
  void segmentRecipients() {
    when(templateService.require(1L)).thenReturn(template());
    Segment seg = new Segment();
    seg.setId(3L);
    when(segmentService.requirePublic(3L)).thenReturn(seg);
    when(segmentService.members(seg)).thenReturn(List.of(1L, 2L));
    Customer c1 = new Customer();
    c1.setId(1L);
    c1.setEmail("a@test.com");
    Customer c2 = new Customer();
    c2.setId(2L);
    c2.setEmail(null); // 邮箱空 → 跳过
    when(customerMapper.selectBatchIds(List.of(1L, 2L))).thenReturn(List.of(c1, c2));

    CampaignRequest req = new CampaignRequest();
    req.setName("召回");
    req.setTemplateId(1L);
    req.setSourceType("SEGMENT");
    req.setSegmentId(3L);

    EmailCampaign campaign = service.createAndSend(req);

    assertThat(campaign.getTotalCount()).isEqualTo(1); // 仅 1 封（邮箱非空）
    // 一期诚信修复（本次核心回归）：未配置 SMTP 时批次必须是 SKIPPED、成功数必须为 0，
    // 此前这里断言的是 DONE/1 —— 即"从未发出的邮件被统计成已发送"。
    assertThat(campaign.getStatus()).isEqualTo(EmailCampaign.STATUS_SKIPPED);
    assertThat(campaign.getSentCount()).isZero();
    verify(sendLogMapper).insert(any(EmailSendLog.class));
    // 每封的真实状态也必须落库为 SKIPPED，而不是留在 PENDING/SENT
    ArgumentCaptor<EmailSendLog> persisted = ArgumentCaptor.forClass(EmailSendLog.class);
    verify(sendLogMapper).updateById(persisted.capture());
    assertThat(persisted.getValue().getStatus()).isEqualTo(EmailSendLog.STATUS_SKIPPED);
    assertThat(persisted.getValue().getErrorMessage()).contains("SMTP 未配置");
  }

  @Test
  @DisplayName("退订过滤方向：排除的是已退订者，未退订者必须保留")
  void excludesOnlyUnsubscribed() {
    when(templateService.require(1L)).thenReturn(template());
    Customer keep = new Customer();
    keep.setId(1L);
    keep.setEmail("keep@test.com");
    Customer gone = new Customer();
    gone.setId(2L);
    gone.setEmail("gone@test.com");
    when(customerMapper.selectBatchIds(List.of(1L, 2L))).thenReturn(List.of(keep, gone));
    when(unsubscribeService.isUnsubscribed("gone@test.com")).thenReturn(true);
    when(unsubscribeService.isUnsubscribed("keep@test.com")).thenReturn(false);

    CampaignRequest req = new CampaignRequest();
    req.setName("召回");
    req.setTemplateId(1L);
    req.setSourceType("CUSTOMER_IDS");
    req.setCustomerIds(List.of(1L, 2L));

    EmailCampaign campaign = service.createAndSend(req);

    // 此前的实现按 filterUnsubscribed 的返回值（"未退订"的 id）做删除，方向正好相反：
    // 结果是只发给已退订的人、未退订的人全部收不到，且没人退订时整批发空。
    assertThat(campaign.getTotalCount()).isEqualTo(1);
    ArgumentCaptor<EmailSendLog> inserted = ArgumentCaptor.forClass(EmailSendLog.class);
    verify(sendLogMapper).insert(inserted.capture());
    assertThat(inserted.getValue().getEmail()).isEqualTo("keep@test.com");
  }

  @Test
  @DisplayName("SMTP 已配置：真正发出并记 SENT，批次汇总为 DONE")
  void configuredSmtpMarksSent() {
    JavaMailSender mailSender = mock(JavaMailSender.class);
    buildService(new MailStatus("smtp.example.com"), mailSender);
    when(templateService.require(1L)).thenReturn(template());
    Customer c1 = new Customer();
    c1.setId(1L);
    c1.setEmail("a@test.com");
    when(customerMapper.selectBatchIds(List.of(1L))).thenReturn(List.of(c1));

    CampaignRequest req = new CampaignRequest();
    req.setName("召回");
    req.setTemplateId(1L);
    req.setSourceType("CUSTOMER_IDS");
    req.setCustomerIds(List.of(1L));

    EmailCampaign campaign = service.createAndSend(req);

    // 真正调用了 SMTP，且只有真发出才记 SENT / DONE
    verify(mailSender).send(any(SimpleMailMessage.class));
    assertThat(campaign.getStatus()).isEqualTo(EmailCampaign.STATUS_DONE);
    assertThat(campaign.getSentCount()).isEqualTo(1);
    ArgumentCaptor<EmailSendLog> persisted = ArgumentCaptor.forClass(EmailSendLog.class);
    verify(sendLogMapper).updateById(persisted.capture());
    assertThat(persisted.getValue().getStatus()).isEqualTo(EmailSendLog.STATUS_SENT);
  }

  @Test
  @DisplayName("打开追踪：首次记录 OPEN，重复忽略")
  void trackOpenOnce() {
    EmailSendLog log = new EmailSendLog();
    log.setId(10L);
    log.setCampaignId(5L);
    when(sendLogMapper.selectById(10L)).thenReturn(log);
    when(trackMapper.selectCount(any())).thenReturn(0L);
    EmailCampaign c = new EmailCampaign();
    c.setId(5L);
    when(campaignMapper.selectById(5L)).thenReturn(c);

    byte[] gif = service.trackOpen(10L);

    assertThat(gif).isNotEmpty();
    verify(trackMapper).insert(any(EmailTrack.class));
    verify(campaignMapper).updateById(c);
    assertThat(c.getOpenCount()).isEqualTo(1);
  }

  @Test
  @DisplayName("点击追踪：记录 CLICK 并返回目标 URL")
  void trackClickRedirects() {
    EmailSendLog log = new EmailSendLog();
    log.setId(10L);
    log.setCampaignId(5L);
    when(sendLogMapper.selectById(10L)).thenReturn(log);
    EmailCampaign c = new EmailCampaign();
    c.setId(5L);
    when(campaignMapper.selectById(5L)).thenReturn(c);

    String target = service.trackClick(10L, "https://example.com/x");

    assertThat(target).isEqualTo("https://example.com/x");
    verify(trackMapper).insert(any(EmailTrack.class));
  }
}
