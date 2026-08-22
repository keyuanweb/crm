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
import com.crm.repository.SlaPolicyMapper;
import com.crm.repository.TicketMapper;
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
    auditService = mock(AuditService.class);
    service = new SlaPolicyService(slaPolicyMapper, ticketMapper, auditService);
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
}
