package com.crm.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.crm.common.BusinessException;
import com.crm.common.ErrorCode;
import com.crm.dto.sla.SlaPolicyRequest;
import com.crm.entity.SlaPolicy;
import com.crm.entity.Ticket;
import com.crm.entity.User;
import com.crm.repository.SlaPolicyMapper;
import com.crm.repository.TicketMapper;
import com.crm.repository.UserMapper;
import com.crm.security.JwtAuthFilter.CrmPrincipal;
import com.crm.security.SecurityUtil;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

/** SlaPolicyService 单元测试（015 T027）：CRUD/优先级唯一/校验。 */
class SlaPolicyServiceTest {

  private SlaPolicyMapper slaPolicyMapper;
  private TicketMapper ticketMapper;
  private UserMapper userMapper;
  private AuditService auditService;
  private SlaPolicyService service;
  private MockedStatic<SecurityUtil> securityUtilMock;

  @BeforeAll
  static void initTableInfo() {
    MybatisConfiguration configuration = new MybatisConfiguration();
    MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, "");
    TableInfoHelper.initTableInfo(assistant, SlaPolicy.class);
    TableInfoHelper.initTableInfo(assistant, Ticket.class);
  }

  @BeforeEach
  void setUp() {
    slaPolicyMapper = mock(SlaPolicyMapper.class);
    ticketMapper = mock(TicketMapper.class);
    userMapper = mock(UserMapper.class);
    auditService = mock(AuditService.class);
    service = new SlaPolicyService(slaPolicyMapper, ticketMapper, userMapper, auditService);
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

  private SlaPolicyRequest request(String priority) {
    SlaPolicyRequest req = new SlaPolicyRequest();
    req.setPriority(priority);
    req.setRespondHours(4);
    req.setResolveHours(24);
    return req;
  }

  private SlaPolicy policy(Long id, String priority) {
    SlaPolicy p = new SlaPolicy();
    p.setId(id);
    p.setPriority(priority);
    p.setRespondHours(4);
    p.setResolveHours(24);
    p.setEnabled(1);
    p.setVersion(0);
    return p;
  }

  @Test
  @DisplayName("创建策略成功：审计记录")
  void createSucceeds() {
    when(slaPolicyMapper.selectCount(any())).thenReturn(0L);
    when(slaPolicyMapper.insert(any(SlaPolicy.class)))
        .thenAnswer(
            invocation -> {
              SlaPolicy p = invocation.getArgument(0);
              p.setId(1L);
              return 1;
            });

    var resp = service.create(request("HIGH"));

    assertThat(resp.getId()).isEqualTo(1L);
    assertThat(resp.getPriority()).isEqualTo("HIGH");
    verify(auditService).record("CREATE", "SLA_POLICY", 1L, "创建 SLA 策略：HIGH");
  }

  @Test
  @DisplayName("创建策略：优先级重复 → 409")
  void createDuplicateThrows() {
    when(slaPolicyMapper.selectCount(any())).thenReturn(1L);

    assertThatThrownBy(() -> service.create(request("HIGH")))
        .isInstanceOf(BusinessException.class)
        .extracting(e -> ((BusinessException) e).getErrorCode())
        .isEqualTo(ErrorCode.SLA_POLICY_DUPLICATE);
  }

  @Test
  @DisplayName("创建策略：两项时限均为空 → 422")
  void createNoLimitsThrows() {
    SlaPolicyRequest req = request("LOW");
    req.setRespondHours(null);
    req.setResolveHours(null);

    assertThatThrownBy(() -> service.create(req))
        .isInstanceOf(BusinessException.class)
        .extracting(e -> ((BusinessException) e).getErrorCode())
        .isEqualTo(ErrorCode.SLA_POLICY_INVALID);
  }

  @Test
  @DisplayName("更新策略：成功后回查返回")
  void updateSucceeds() {
    when(slaPolicyMapper.selectById(1L)).thenReturn(policy(1L, "HIGH"));
    when(slaPolicyMapper.selectCount(any())).thenReturn(0L);
    when(slaPolicyMapper.updateById(any(SlaPolicy.class))).thenReturn(1);
    when(slaPolicyMapper.selectById(1L)).thenReturn(policy(1L, "HIGH"));

    SlaPolicyRequest req = request("HIGH");
    req.setResolveHours(48);
    req.setVersion(0);
    var resp = service.update(1L, req);

    assertThat(resp.getResolveHours()).isEqualTo(48); // apply 生效
    verify(auditService).record("UPDATE", "SLA_POLICY", 1L, "编辑 SLA 策略：HIGH");
  }

  @Test
  @DisplayName("超时统计：未关闭工单中 OVERDUE 计数正确")
  void overviewCountsOverdue() {
    Ticket open1 = new Ticket();
    open1.setPriority("HIGH");
    open1.setStatus("OPEN");
    open1.setSlaResolveDeadline(LocalDateTime.now().minusHours(1)); // 已超时
    Ticket open2 = new Ticket();
    open2.setPriority("HIGH");
    open2.setStatus("IN_PROGRESS");
    open2.setSlaResolveDeadline(LocalDateTime.now().plusHours(5)); // 正常
    Ticket open3 = new Ticket();
    open3.setPriority("LOW");
    open3.setStatus("OPEN");
    open3.setSlaResolveDeadline(LocalDateTime.now().minusMinutes(30)); // 已超时
    when(ticketMapper.selectList(any())).thenReturn(List.of(open1, open2, open3));

    var resp = service.overview();

    assertThat(resp.getTotalOpen()).isEqualTo(3);
    assertThat(resp.getOverdue()).isEqualTo(2);
    assertThat(resp.getOverdueRate()).isEqualTo(2.0 / 3.0);
    assertThat(resp.getByPriority()).hasSize(2);
  }

  @Test
  @DisplayName("超时统计：响应/解决达成率（1.3）")
  void overviewComplianceRates() {
    Ticket onTime = new Ticket();
    onTime.setPriority("HIGH");
    onTime.setStatus("OPEN");
    onTime.setSlaRespondDeadline(LocalDateTime.now().minusHours(2));
    onTime.setSlaRespondedAt(LocalDateTime.now().minusHours(3)); // 提前响应 → 响应达标
    onTime.setSlaResolveDeadline(LocalDateTime.now().plusHours(5));
    Ticket lateRespond = new Ticket();
    lateRespond.setPriority("HIGH");
    lateRespond.setStatus("OPEN");
    lateRespond.setSlaRespondDeadline(LocalDateTime.now().minusHours(3));
    lateRespond.setSlaRespondedAt(LocalDateTime.now().minusHours(1)); // 迟于 deadline → 响应违约
    lateRespond.setSlaResolveDeadline(LocalDateTime.now().minusHours(1)); // 且解决已过期 → 解决违约
    when(ticketMapper.selectList(any())).thenReturn(List.of(onTime, lateRespond));

    var resp = service.overview();

    assertThat(resp.getRespondComplianceRate()).isEqualTo(0.5);
    assertThat(resp.getResolveComplianceRate()).isEqualTo(0.5);
  }

  @Test
  @DisplayName("超时统计：无未关闭工单时达成率为 null（不臆造 0%）")
  void overviewComplianceRatesNullWhenNoTickets() {
    when(ticketMapper.selectList(any())).thenReturn(List.of());

    var resp = service.overview();

    assertThat(resp.getTotalOpen()).isZero();
    assertThat(resp.getRespondComplianceRate()).isNull();
    assertThat(resp.getResolveComplianceRate()).isNull();
    assertThat(resp.getByAssignee()).isEmpty();
  }

  @Test
  @DisplayName("超时统计：按处理人维度含未分配分组与姓名（1.3）")
  void overviewByAssignee() {
    Ticket assigned = new Ticket();
    assigned.setPriority("HIGH");
    assigned.setStatus("OPEN");
    assigned.setAssigneeId(7L);
    assigned.setSlaResolveDeadline(LocalDateTime.now().minusHours(1)); // 超时
    Ticket unassigned = new Ticket();
    unassigned.setPriority("LOW");
    unassigned.setStatus("OPEN");
    unassigned.setSlaResolveDeadline(LocalDateTime.now().plusHours(10)); // 正常
    when(ticketMapper.selectList(any())).thenReturn(List.of(assigned, unassigned));
    User user = new User();
    user.setId(7L);
    user.setDisplayName("客服小张");
    when(userMapper.selectBatchIds(any())).thenReturn(List.of(user));

    var resp = service.overview();

    assertThat(resp.getByAssignee()).hasSize(2);
    // 超时多的排前面
    assertThat(resp.getByAssignee().get(0).getAssigneeId()).isEqualTo(7L);
    assertThat(resp.getByAssignee().get(0).getAssigneeName()).isEqualTo("客服小张");
    assertThat(resp.getByAssignee().get(0).getOverdue()).isEqualTo(1);
    assertThat(resp.getByAssignee().get(1).getAssigneeId()).isNull();
    assertThat(resp.getByAssignee().get(1).getAssigneeName()).isEqualTo("未分配");
  }

  @Test
  @DisplayName("取策略：按优先级返回启用策略，无策略返回 null（1.3 抽出）")
  void resolvePolicyForPriority() {
    when(slaPolicyMapper.selectOne(any())).thenReturn(policy(1L, "HIGH"));

    assertThat(service.resolvePolicyFor("HIGH")).isNotNull();
    assertThat(service.resolvePolicyFor("HIGH").getPriority()).isEqualTo("HIGH");
    assertThat(service.resolvePolicyFor(null)).isNull();
  }
}
