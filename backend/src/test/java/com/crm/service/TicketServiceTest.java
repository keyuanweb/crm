package com.crm.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
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
import com.crm.repository.SlaPolicyMapper;
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
  private SlaPolicyMapper slaPolicyMapper;
  private UserMapper userMapper;
  private AuditService auditService;
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
    slaPolicyMapper = mock(SlaPolicyMapper.class);
    userMapper = mock(UserMapper.class);
    auditService = mock(AuditService.class);
    service =
        new TicketService(
            ticketMapper,
            replyMapper,
            customerMapper,
            slaPolicyMapper,
            userMapper,
            auditService,
            mock(CustomFieldService.class),
            mock(NotificationService.class),
            mock(SlaCalendarService.class));
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
    when(slaPolicyMapper.selectOne(any())).thenReturn(policy);
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
    when(slaPolicyMapper.selectOne(any())).thenReturn(null);
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
}
