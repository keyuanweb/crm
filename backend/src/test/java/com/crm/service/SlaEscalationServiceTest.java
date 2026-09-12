package com.crm.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.crm.entity.SlaPolicy;
import com.crm.entity.Ticket;
import com.crm.entity.User;
import com.crm.repository.SlaPolicyMapper;
import com.crm.repository.TicketMapper;
import com.crm.repository.UserMapper;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** SlaEscalationService 单元测试（1.3-sla-escalation）：分级升级/幂等/收件人回退。 */
class SlaEscalationServiceTest {

  private static final long TICKET_ID = 10L;
  private static final long ASSIGNEE_ID = 5L;
  private static final long ADMIN_ID = 1L;

  private TicketMapper ticketMapper;
  private SlaPolicyMapper slaPolicyMapper;
  private UserMapper userMapper;
  private NotificationService notificationService;
  private IntegrationChannelService integrationChannelService;
  private AuditService auditService;
  private SlaEscalationService service;

  @BeforeAll
  static void initTableInfo() {
    MybatisConfiguration configuration = new MybatisConfiguration();
    MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, "");
    TableInfoHelper.initTableInfo(assistant, Ticket.class);
    TableInfoHelper.initTableInfo(assistant, SlaPolicy.class);
    TableInfoHelper.initTableInfo(assistant, User.class);
  }

  @BeforeEach
  void setUp() {
    ticketMapper = mock(TicketMapper.class);
    slaPolicyMapper = mock(SlaPolicyMapper.class);
    userMapper = mock(UserMapper.class);
    notificationService = mock(NotificationService.class);
    integrationChannelService = mock(IntegrationChannelService.class);
    auditService = mock(AuditService.class);
    service =
        new SlaEscalationService(
            ticketMapper,
            slaPolicyMapper,
            userMapper,
            notificationService,
            integrationChannelService,
            auditService);
    when(slaPolicyMapper.selectList(any())).thenReturn(List.of(policy()));
    User admin = new User();
    admin.setId(ADMIN_ID);
    admin.setRole("ADMIN");
    admin.setEnabled(true);
    when(userMapper.selectList(any())).thenReturn(List.of(admin));
  }

  private SlaPolicy policy() {
    SlaPolicy p = new SlaPolicy();
    p.setId(100L);
    p.setPriority("HIGH");
    p.setRespondHours(1);
    p.setResolveHours(2);
    p.setEnabled(1);
    return p;
  }

  private Ticket ticket(Long id, Long assigneeId) {
    Ticket t = new Ticket();
    t.setId(id);
    t.setTitle("登录失败");
    t.setPriority("HIGH");
    t.setStatus(TicketService.STATUS_OPEN);
    t.setAssigneeId(assigneeId);
    t.setEscalateLevel(0);
    return t;
  }

  @Test
  @DisplayName("WARNING → 只通知处理人；再次扫描不重复通知（幂等）")
  void warningNotifiesAssigneeOnlyAndIsIdempotent() {
    Ticket t = ticket(TICKET_ID, ASSIGNEE_ID);
    t.setSlaResolveDeadline(LocalDateTime.now().plusHours(1)); // 落在预警窗口内
    when(ticketMapper.selectList(any())).thenReturn(List.of(t));

    assertThat(service.scanAndEscalate()).isEqualTo(1);
    assertThat(t.getEscalateLevel()).isEqualTo(SlaEscalationService.LEVEL_WARNING);
    assertThat(t.getSlaStatus()).isEqualTo(TicketService.SLA_WARNING);
    verify(notificationService)
        .notify(
            eq(ASSIGNEE_ID),
            eq(NotificationService.TYPE_SLA_WARNING),
            anyString(),
            eq("TICKET"),
            eq(TICKET_ID));
    verify(notificationService, never())
        .notify(eq(ADMIN_ID), anyString(), anyString(), anyString(), anyLong());
    verify(ticketMapper).updateById(t);
    verify(auditService)
        .recordAsSystem(eq("SLA_ESCALATE"), eq("TICKET"), eq(TICKET_ID), anyString());

    // 第二轮：仍在预警窗口内，级别已是 L1 → 不再通知任何人
    assertThat(service.scanAndEscalate()).isZero();
    assertThat(t.getEscalateLevel()).isEqualTo(SlaEscalationService.LEVEL_WARNING);
    verify(notificationService, times(1))
        .notify(anyLong(), anyString(), anyString(), anyString(), anyLong());
  }

  @Test
  @DisplayName("OVERDUE → 通知处理人 + 全部 ADMIN，级别升到 L2")
  void overdueNotifiesAssigneeAndAdmins() {
    Ticket t = ticket(TICKET_ID, ASSIGNEE_ID);
    t.setSlaResolveDeadline(LocalDateTime.now().minusMinutes(1));
    when(ticketMapper.selectList(any())).thenReturn(List.of(t));

    assertThat(service.scanAndEscalate()).isEqualTo(1);
    assertThat(t.getEscalateLevel()).isEqualTo(SlaEscalationService.LEVEL_OVERDUE);
    assertThat(t.getSlaStatus()).isEqualTo(TicketService.SLA_OVERDUE);
    verify(notificationService)
        .notify(
            eq(ASSIGNEE_ID),
            eq(NotificationService.TYPE_SLA_OVERDUE),
            anyString(),
            eq("TICKET"),
            eq(TICKET_ID));
    verify(notificationService)
        .notify(
            eq(ADMIN_ID),
            eq(NotificationService.TYPE_SLA_OVERDUE),
            anyString(),
            eq("TICKET"),
            eq(TICKET_ID));
    // L2 还不广播
    verify(integrationChannelService, never()).publish(anyString(), anyString());
  }

  @Test
  @DisplayName("持续超时（距上次升级满一个间隔）→ L3 只再通知 ADMIN 并广播")
  void sustainedOverdueEscalatesToBroadcast() {
    Ticket t = ticket(TICKET_ID, ASSIGNEE_ID);
    t.setSlaResolveDeadline(LocalDateTime.now().minusHours(3));
    t.setEscalateLevel(SlaEscalationService.LEVEL_OVERDUE);
    t.setLastEscalatedAt(LocalDateTime.now().minusHours(3)); // 策略 resolve 时限 2h → 已满一个间隔
    when(ticketMapper.selectList(any())).thenReturn(List.of(t));

    assertThat(service.scanAndEscalate()).isEqualTo(1);
    assertThat(t.getEscalateLevel()).isEqualTo(SlaEscalationService.LEVEL_BROADCAST);
    verify(notificationService)
        .notify(
            eq(ADMIN_ID),
            eq(NotificationService.TYPE_SLA_OVERDUE),
            anyString(),
            eq("TICKET"),
            eq(TICKET_ID));
    // L3 不再打扰处理人（迁移前的 L2 已通知过）
    verify(notificationService, never())
        .notify(eq(ASSIGNEE_ID), anyString(), anyString(), anyString(), anyLong());
    verify(integrationChannelService).publish(eq("TICKET_SLA_ESCALATED"), anyString());
  }

  @Test
  @DisplayName("已超时但未满一个间隔 → 停在 L2，不重复通知")
  void overdueWithinIntervalDoesNotRepeat() {
    Ticket t = ticket(TICKET_ID, ASSIGNEE_ID);
    t.setSlaResolveDeadline(LocalDateTime.now().minusHours(3));
    t.setEscalateLevel(SlaEscalationService.LEVEL_OVERDUE);
    t.setLastEscalatedAt(LocalDateTime.now().minusMinutes(10));
    when(ticketMapper.selectList(any())).thenReturn(List.of(t));

    assertThat(service.scanAndEscalate()).isZero();
    assertThat(t.getEscalateLevel()).isEqualTo(SlaEscalationService.LEVEL_OVERDUE);
    verifyNoInteractions(notificationService);
  }

  @Test
  @DisplayName("无处理人 → L1 落到 ADMIN，通知不静默丢失")
  void warningWithoutAssigneeFallsBackToAdmins() {
    Ticket t = ticket(TICKET_ID, null);
    t.setSlaResolveDeadline(LocalDateTime.now().plusMinutes(30));
    when(ticketMapper.selectList(any())).thenReturn(List.of(t));

    assertThat(service.scanAndEscalate()).isEqualTo(1);
    verify(notificationService)
        .notify(
            eq(ADMIN_ID),
            eq(NotificationService.TYPE_SLA_WARNING),
            anyString(),
            eq("TICKET"),
            eq(TICKET_ID));
  }

  @Test
  @DisplayName("无策略 → 不升级（SLA 承诺不存在就不催办）")
  void noPolicyMeansNoEscalation() {
    when(slaPolicyMapper.selectList(any())).thenReturn(List.of());
    Ticket t = ticket(TICKET_ID, ASSIGNEE_ID);
    t.setSlaResolveDeadline(LocalDateTime.now().minusHours(5));
    when(ticketMapper.selectList(any())).thenReturn(List.of(t));

    assertThat(service.scanAndEscalate()).isZero();
    assertThat(t.getEscalateLevel()).isEqualTo(SlaEscalationService.LEVEL_NONE);
    verifyNoInteractions(notificationService);
  }

  @Test
  @DisplayName("NORMAL 工单不升级、不写库")
  void normalTicketIsUntouched() {
    Ticket t = ticket(TICKET_ID, ASSIGNEE_ID);
    t.setSlaResolveDeadline(LocalDateTime.now().plusHours(10));
    when(ticketMapper.selectList(any())).thenReturn(List.of(t));

    assertThat(service.scanAndEscalate()).isZero();
    assertThat(t.getEscalateLevel()).isEqualTo(SlaEscalationService.LEVEL_NONE);
    verifyNoInteractions(notificationService);
    verify(ticketMapper, never()).updateById(any(Ticket.class));
  }

  @Test
  @DisplayName("候选集为空 → 短路返回 0，不查策略与用户")
  void emptyScanShortCircuits() {
    when(ticketMapper.selectList(any())).thenReturn(List.of());

    assertThat(service.scanAndEscalate()).isZero();
    verifyNoInteractions(slaPolicyMapper);
    verifyNoInteractions(userMapper);
    verifyNoInteractions(notificationService);
  }
}
