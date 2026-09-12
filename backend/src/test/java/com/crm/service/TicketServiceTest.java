package com.crm.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.crm.common.BusinessException;
import com.crm.common.ErrorCode;
import com.crm.dto.ticket.TicketReplyRequest;
import com.crm.dto.ticket.TicketRequest;
import com.crm.entity.Customer;
import com.crm.entity.SlaPolicy;
import com.crm.entity.Ticket;
import com.crm.entity.User;
import com.crm.repository.CustomerMapper;
import com.crm.repository.TicketMapper;
import com.crm.repository.TicketReplyMapper;
import com.crm.repository.UserMapper;
import com.crm.security.JwtAuthFilter.CrmPrincipal;
import com.crm.security.SecurityUtil;
import java.time.LocalDateTime;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

/** TicketService 单元测试（015 T011/T028）：CRUD/状态流转/回复/分配/SLA/行级过滤。 */
class TicketServiceTest {

  private TicketMapper ticketMapper;
  private TicketReplyMapper replyMapper;
  private CustomerMapper customerMapper;
  private SlaPolicyService slaPolicyService;
  private UserMapper userMapper;
  private AuditService auditService;
  private SlaCalendarService slaCalendarService;
  private TicketService service;
  private MockedStatic<SecurityUtil> securityUtilMock;

  @BeforeAll
  static void initTableInfo() {
    MybatisConfiguration configuration = new MybatisConfiguration();
    MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, "");
    TableInfoHelper.initTableInfo(assistant, Ticket.class);
    TableInfoHelper.initTableInfo(assistant, com.crm.entity.TicketReply.class);
    TableInfoHelper.initTableInfo(assistant, Customer.class);
    TableInfoHelper.initTableInfo(assistant, SlaPolicy.class);
    TableInfoHelper.initTableInfo(assistant, User.class);
  }

  @BeforeEach
  void setUp() {
    ticketMapper = mock(TicketMapper.class);
    replyMapper = mock(TicketReplyMapper.class);
    customerMapper = mock(CustomerMapper.class);
    slaPolicyService = mock(SlaPolicyService.class);
    userMapper = mock(UserMapper.class);
    auditService = mock(AuditService.class);
    slaCalendarService = mock(SlaCalendarService.class);
    // 工作日历：按「直接加 N 小时」回退行为（未启用配置时 SlaCalendarService 的实际语义）
    when(slaCalendarService.advanceWorkingTime(any(), anyDouble()))
        .thenAnswer(inv -> ((LocalDateTime) inv.getArgument(0)).plusMinutes(60));
    service =
        new TicketService(
            ticketMapper,
            replyMapper,
            customerMapper,
            slaPolicyService,
            userMapper,
            auditService,
            mock(CustomFieldService.class),
            mock(NotificationService.class),
            slaCalendarService,
            mock(IntegrationChannelService.class));
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

  private TicketRequest request() {
    TicketRequest req = new TicketRequest();
    req.setCustomerId(10L);
    req.setTitle("登录失败");
    req.setPriority("HIGH");
    return req;
  }

  private Ticket ticket(Long id, String status) {
    Ticket t = new Ticket();
    t.setId(id);
    t.setCustomerId(10L);
    t.setTitle("登录失败");
    t.setPriority("HIGH");
    t.setStatus(status);
    t.setVersion(0);
    return t;
  }

  @Test
  @DisplayName("创建工单：默认 OPEN，SLA 按策略计算，审计记录")
  void createSucceeds() {
    Customer customer = new Customer();
    customer.setId(10L);
    customer.setName("客户A");
    when(customerMapper.selectById(10L)).thenReturn(customer);
    SlaPolicy policy = new SlaPolicy();
    policy.setPriority("HIGH");
    policy.setRespondHours(4);
    policy.setResolveHours(24);
    when(slaPolicyService.resolvePolicyFor("HIGH")).thenReturn(policy);
    when(ticketMapper.insert(any(Ticket.class)))
        .thenAnswer(
            invocation -> {
              Ticket t = invocation.getArgument(0);
              t.setId(1L);
              t.setCreatedAt(LocalDateTime.now());
              return 1;
            });
    when(ticketMapper.selectById(1L))
        .thenAnswer(
            invocation -> {
              Ticket t = ticket(1L, "OPEN");
              t.setSlaRespondDeadline(LocalDateTime.now().plusHours(4));
              t.setSlaResolveDeadline(LocalDateTime.now().plusHours(24));
              t.setSlaStatus("NORMAL");
              return t;
            });

    var resp = service.create(request());

    assertThat(resp.getId()).isEqualTo(1L);
    assertThat(resp.getStatus()).isEqualTo("OPEN");
    assertThat(resp.getSlaStatus()).isEqualTo("NORMAL");
    verify(ticketMapper).insert(any(Ticket.class));
    verify(auditService).record("CREATE", "TICKET", 1L, "创建工单：登录失败");
  }

  @Test
  @DisplayName("创建工单：无 SLA 策略则无到期时间")
  void createWithoutSlaPolicy() {
    Customer customer = new Customer();
    customer.setId(10L);
    when(customerMapper.selectById(10L)).thenReturn(customer);
    when(slaPolicyService.resolvePolicyFor("HIGH")).thenReturn(null);
    when(ticketMapper.insert(any(Ticket.class)))
        .thenAnswer(
            invocation -> {
              Ticket t = invocation.getArgument(0);
              t.setId(2L);
              return 1;
            });
    when(ticketMapper.selectById(2L)).thenReturn(ticket(2L, "OPEN"));

    var resp = service.create(request());

    assertThat(resp.getSlaRespondDeadline()).isNull();
    assertThat(resp.getSlaResolveDeadline()).isNull();
    assertThat(resp.getSlaStatus()).isNull();
  }

  @Test
  @DisplayName("状态流转：OPEN→IN_PROGRESS→RESOLVED→CLOSED 成功")
  void transitionSucceeds() {
    Ticket mutable = ticket(1L, "OPEN");
    when(ticketMapper.selectById(1L)).thenReturn(mutable);
    when(ticketMapper.updateById(any(Ticket.class))).thenReturn(1);

    var r1 = service.transition(1L, "IN_PROGRESS");
    assertThat(r1.getStatus()).isEqualTo("IN_PROGRESS");
    var r2 = service.transition(1L, "RESOLVED");
    assertThat(r2.getStatus()).isEqualTo("RESOLVED");
    var r3 = service.transition(1L, "CLOSED");
    assertThat(r3.getStatus()).isEqualTo("CLOSED");
  }

  @Test
  @DisplayName("状态流转：CLOSED 不可回退且 OPEN 不可跳 RESOLVED → 409")
  void transitionInvalidThrows() {
    when(ticketMapper.selectById(1L)).thenReturn(ticket(1L, "OPEN"));

    assertThatThrownBy(() -> service.transition(1L, "RESOLVED"))
        .isInstanceOf(BusinessException.class)
        .extracting(e -> ((BusinessException) e).getErrorCode())
        .isEqualTo(ErrorCode.TICKET_INVALID_STATE);
    verify(ticketMapper, never()).updateById(any(Ticket.class));
  }

  @Test
  @DisplayName("回复：成功记录并审计")
  void replySucceeds() {
    when(ticketMapper.selectById(1L)).thenReturn(ticket(1L, "IN_PROGRESS"));
    when(replyMapper.insert(any(com.crm.entity.TicketReply.class)))
        .thenAnswer(
            invocation -> {
              com.crm.entity.TicketReply r = invocation.getArgument(0);
              r.setId(100L);
              r.setCreatedAt(LocalDateTime.now());
              return 1;
            });

    TicketReplyRequest req = new TicketReplyRequest();
    req.setContent("已联系客户");
    var resp = service.reply(1L, req);

    assertThat(resp.getId()).isEqualTo(100L);
    assertThat(resp.getContent()).isEqualTo("已联系客户");
    verify(auditService).record("REPLY", "TICKET", 1L, "工单回复");
  }

  @Test
  @DisplayName("CLOSED 工单不可回复 → 409")
  void replyClosedThrows() {
    when(ticketMapper.selectById(1L)).thenReturn(ticket(1L, "CLOSED"));
    TicketReplyRequest req = new TicketReplyRequest();
    req.setContent("x");

    assertThatThrownBy(() -> service.reply(1L, req))
        .isInstanceOf(BusinessException.class)
        .extracting(e -> ((BusinessException) e).getErrorCode())
        .isEqualTo(ErrorCode.TICKET_INVALID_STATE);
  }

  @Test
  @DisplayName("分配处理人：成功且审计")
  void assignSucceeds() {
    when(ticketMapper.selectById(1L)).thenReturn(ticket(1L, "OPEN"));
    User user = new User();
    user.setId(5L);
    user.setDisplayName("客服小张");
    when(userMapper.selectById(5L)).thenReturn(user);
    when(ticketMapper.updateById(any(Ticket.class))).thenReturn(1);
    Ticket assigned = ticket(1L, "OPEN");
    assigned.setAssigneeId(5L);
    when(ticketMapper.selectById(1L)).thenReturn(assigned);

    var resp = service.assign(1L, 5L);

    assertThat(resp.getAssigneeId()).isEqualTo(5L);
    verify(ticketMapper).updateById(any(Ticket.class));
    verify(auditService).record("ASSIGN", "TICKET", 1L, "工单分配给用户 客服小张");
  }

  @Test
  @DisplayName("SALES 行级过滤：无自身客户时返回空列表")
  void salesScopeFilters() {
    securityUtilMock
        .when(SecurityUtil::currentPrincipal)
        .thenReturn(new CrmPrincipal(2L, "sales1", "SALES"));
    when(customerMapper.selectList(any())).thenReturn(java.util.List.of());

    var result = service.page(null, null, null, null, null, null, 1, 20);

    assertThat(result.getItems()).isEmpty();
    assertThat(result.getTotal()).isZero();
  }

  @Test
  @DisplayName("SLA 状态计算：已过解决时限 → OVERDUE")
  void slaOverdue() {
    Ticket t = ticket(1L, "OPEN");
    t.setSlaResolveDeadline(LocalDateTime.now().minusMinutes(5));
    t.setSlaRespondDeadline(LocalDateTime.now().plusHours(1));

    assertThat(TicketService.computeSlaStatus(t, LocalDateTime.now()))
        .isEqualTo(TicketService.SLA_OVERDUE);
  }

  @Test
  @DisplayName("SLA 状态计算：剩余 ≤2h → WARNING")
  void slaWarning() {
    Ticket t = ticket(1L, "OPEN");
    t.setSlaResolveDeadline(LocalDateTime.now().plusMinutes(90));
    t.setSlaRespondDeadline(LocalDateTime.now().plusMinutes(30));

    assertThat(TicketService.computeSlaStatus(t, LocalDateTime.now()))
        .isEqualTo(TicketService.SLA_WARNING);
  }

  // ===== 1.3-sla-escalation：响应侧超时判定的四种组合 =====

  @Test
  @DisplayName("SLA 状态：未响应 + respond 已过期 → OVERDUE（1.3 新增判定）")
  void slaRespondOverdueWhenNotResponded() {
    Ticket t = ticket(1L, "OPEN");
    t.setSlaRespondDeadline(LocalDateTime.now().minusMinutes(5));
    t.setSlaResolveDeadline(LocalDateTime.now().plusHours(10));

    assertThat(TicketService.computeSlaStatus(t, LocalDateTime.now()))
        .isEqualTo(TicketService.SLA_OVERDUE);
  }

  @Test
  @DisplayName("SLA 状态：已响应 + respond 已过期 → 既非 OVERDUE 也非 WARNING（已答复工单不得被误判）")
  void slaRespondExpiredButAlreadyRespondedIsNormal() {
    Ticket t = ticket(1L, "OPEN");
    t.setSlaRespondDeadline(LocalDateTime.now().minusDays(3));
    t.setSlaRespondedAt(LocalDateTime.now().minusDays(4)); // 在 deadline 之前响应
    t.setSlaResolveDeadline(LocalDateTime.now().plusHours(10));

    // 期望 NORMAL，不只是「非 OVERDUE」：respond deadline 过期后剩余分钟数为负，恒 ≤ 阈值，
    // 若不给 WARNING 分支加「尚未响应」的前置条件，这张解决时限还有 10 小时的工单会一直停在
    // WARNING，并让 SLA 升级作业发出一封假的「即将超时」通知。
    assertThat(TicketService.computeSlaStatus(t, LocalDateTime.now()))
        .isEqualTo(TicketService.SLA_NORMAL);
  }

  @Test
  @DisplayName("SLA 状态：resolve 已过期 → OVERDUE（原有判定不变）")
  void slaResolveOverdueStaysOverdue() {
    Ticket t = ticket(1L, "IN_PROGRESS");
    t.setSlaRespondDeadline(LocalDateTime.now().plusHours(10));
    t.setSlaRespondedAt(LocalDateTime.now().minusHours(1));
    t.setSlaResolveDeadline(LocalDateTime.now().minusMinutes(1));

    assertThat(TicketService.computeSlaStatus(t, LocalDateTime.now()))
        .isEqualTo(TicketService.SLA_OVERDUE);
  }

  @Test
  @DisplayName("SLA 状态：未过期 → WARNING（进入预警窗口）/ NORMAL（窗口外）")
  void slaWarningAndNormalCombinations() {
    Ticket warning = ticket(1L, "OPEN");
    warning.setSlaRespondDeadline(LocalDateTime.now().plusHours(10));
    warning.setSlaResolveDeadline(LocalDateTime.now().plusMinutes(30));
    assertThat(TicketService.computeSlaStatus(warning, LocalDateTime.now()))
        .isEqualTo(TicketService.SLA_WARNING);

    Ticket normal = ticket(2L, "OPEN");
    normal.setSlaRespondDeadline(LocalDateTime.now().plusHours(10));
    normal.setSlaResolveDeadline(LocalDateTime.now().plusHours(10));
    assertThat(TicketService.computeSlaStatus(normal, LocalDateTime.now()))
        .isEqualTo(TicketService.SLA_NORMAL);
  }

  // ===== 1.3：改优先级重算、首次响应、解决时刻 =====

  @Test
  @DisplayName("编辑工单：改优先级后按新策略重算到期时间（1.3）")
  void updateRecalculatesSlaOnPriorityChange() {
    Ticket existing = ticket(1L, "OPEN");
    existing.setPriority("LOW");
    when(ticketMapper.selectById(1L)).thenReturn(existing);
    when(ticketMapper.updateById(any(Ticket.class))).thenReturn(1);
    SlaPolicy urgent = new SlaPolicy();
    urgent.setPriority("URGENT");
    urgent.setRespondHours(1);
    urgent.setResolveHours(2);
    when(slaPolicyService.resolvePolicyFor("URGENT")).thenReturn(urgent);

    TicketRequest req = request();
    req.setPriority("URGENT");
    service.update(1L, req);

    // applySla 被调用：按 URGENT 策略写入了到期时间（否则两个 deadline 仍为 null）
    assertThat(existing.getSlaRespondDeadline()).isNotNull();
    assertThat(existing.getSlaResolveDeadline()).isNotNull();
    verify(slaPolicyService).resolvePolicyFor("URGENT");
  }

  @Test
  @DisplayName("编辑工单：优先级未变则不重算 SLA（1.3，避免无谓改写）")
  void updateKeepsSlaWhenPriorityUnchanged() {
    Ticket existing = ticket(1L, "OPEN");
    when(ticketMapper.selectById(1L)).thenReturn(existing);
    when(ticketMapper.updateById(any(Ticket.class))).thenReturn(1);

    service.update(1L, request()); // request() 的 priority 与 ticket() 一致，均为 HIGH

    verify(slaPolicyService, never()).resolvePolicyFor(any());
  }

  @Test
  @DisplayName("回复：首次回复写入响应时刻并刷新 SLA 状态（1.3）")
  void replyStampsFirstRespondedAt() {
    Ticket existing = ticket(1L, "IN_PROGRESS");
    existing.setSlaRespondDeadline(LocalDateTime.now().minusHours(1)); // 已过 respond 时限
    existing.setSlaResolveDeadline(LocalDateTime.now().plusHours(4));
    existing.setSlaStatus(TicketService.SLA_OVERDUE); // 回复前：未响应且 respond 已过期
    when(ticketMapper.selectById(1L)).thenReturn(existing);
    when(ticketMapper.updateById(any(Ticket.class))).thenReturn(1);
    when(replyMapper.insert(any(com.crm.entity.TicketReply.class))).thenReturn(1);

    TicketReplyRequest req = new TicketReplyRequest();
    req.setContent("已联系客户");
    service.reply(1L, req);

    assertThat(existing.getSlaRespondedAt()).isNotNull();
    // 响应后 respond 侧不再计入超时判定：状态不再是 OVERDUE
    assertThat(existing.getSlaStatus()).isNotEqualTo(TicketService.SLA_OVERDUE);
    verify(ticketMapper).updateById(existing);
  }

  @Test
  @DisplayName("回复：非首次回复不改写响应时刻（1.3）")
  void replyDoesNotOverwriteRespondedAt() {
    LocalDateTime first = LocalDateTime.now().minusHours(2);
    Ticket existing = ticket(1L, "IN_PROGRESS");
    existing.setSlaRespondedAt(first);
    when(ticketMapper.selectById(1L)).thenReturn(existing);
    when(replyMapper.insert(any(com.crm.entity.TicketReply.class))).thenReturn(1);

    TicketReplyRequest req = new TicketReplyRequest();
    req.setContent("再次回复");
    service.reply(1L, req);

    assertThat(existing.getSlaRespondedAt()).isEqualTo(first);
    verify(ticketMapper, never()).updateById(any(Ticket.class));
  }

  @Test
  @DisplayName("流转：到 RESOLVED/CLOSED 写入解决时刻，且只写一次（1.3）")
  void transitionStampsResolvedAt() {
    Ticket existing = ticket(1L, "OPEN");
    when(ticketMapper.selectById(1L)).thenReturn(existing);
    when(ticketMapper.updateById(any(Ticket.class))).thenReturn(1);

    service.transition(1L, "IN_PROGRESS");
    assertThat(existing.getResolvedAt()).isNull(); // 未解决不写
    service.transition(1L, "RESOLVED");
    LocalDateTime resolved = existing.getResolvedAt();
    assertThat(resolved).isNotNull();
    service.transition(1L, "CLOSED");
    assertThat(existing.getResolvedAt()).isEqualTo(resolved); // CLOSED 不覆盖 RESOLVED 的时刻
  }
}
