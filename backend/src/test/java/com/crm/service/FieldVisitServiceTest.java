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
import com.crm.dto.visit.CheckInRequest;
import com.crm.dto.visit.VisitRequest;
import com.crm.dto.visit.VisitResponse;
import com.crm.entity.Customer;
import com.crm.entity.FieldVisit;
import com.crm.entity.FollowUp;
import com.crm.repository.CustomerMapper;
import com.crm.repository.FieldVisitMapper;
import com.crm.repository.FollowUpMapper;
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

/** FieldVisitService 单元测试（035 T004）。 */
class FieldVisitServiceTest {

  @BeforeAll
  static void initTableInfo() {
    MybatisConfiguration configuration = new MybatisConfiguration();
    MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, "");
    TableInfoHelper.initTableInfo(assistant, FieldVisit.class);
    TableInfoHelper.initTableInfo(assistant, FollowUp.class);
  }

  private FieldVisitMapper visitMapper;
  private CustomerMapper customerMapper;
  private FollowUpMapper followUpMapper;
  private UserMapper userMapper;
  private AuditService auditService;
  private FieldVisitService service;
  private MockedStatic<SecurityUtil> securityUtilMock;

  @BeforeEach
  void setUp() {
    visitMapper = mock(FieldVisitMapper.class);
    customerMapper = mock(CustomerMapper.class);
    followUpMapper = mock(FollowUpMapper.class);
    userMapper = mock(UserMapper.class);
    auditService = mock(AuditService.class);
    service =
        new FieldVisitService(
            visitMapper, customerMapper, followUpMapper, userMapper, auditService);
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

  private FieldVisit visit(Long id, String status) {
    FieldVisit v = new FieldVisit();
    v.setId(id);
    v.setCustomerId(1L);
    v.setTheme("谈续约");
    v.setVisitTime(LocalDateTime.now().plusDays(1));
    v.setStatus(status);
    v.setCreatedBy(1L);
    return v;
  }

  @Test
  @DisplayName("创建拜访计划：客户不存在拒绝")
  void createRejectsMissingCustomer() {
    when(customerMapper.selectById(9L)).thenReturn(null);
    VisitRequest req = new VisitRequest();
    req.setCustomerId(9L);
    req.setTheme("拜访");
    req.setVisitTime(LocalDateTime.now());

    assertThatThrownBy(() -> service.create(req)).isInstanceOf(BusinessException.class);
  }

  @Test
  @DisplayName("创建拜访计划成功")
  void createSuccess() {
    when(customerMapper.selectById(1L)).thenReturn(new Customer());
    VisitRequest req = new VisitRequest();
    req.setCustomerId(1L);
    req.setTheme("谈续约");
    req.setVisitTime(LocalDateTime.now().plusDays(1));
    req.setDurationMinutes(60);

    VisitResponse resp = service.create(req);

    assertThat(resp.getStatus()).isEqualTo("PLANNED");
    verify(visitMapper).insert(any(FieldVisit.class));
  }

  @Test
  @DisplayName("签到：已签到拒绝重复")
  void checkInRejectsDone() {
    when(visitMapper.selectById(1L)).thenReturn(visit(1L, "DONE"));

    assertThatThrownBy(() -> service.checkIn(1L, new CheckInRequest()))
        .isInstanceOf(BusinessException.class);
  }

  @Test
  @DisplayName("签到：记录坐标 + 小结转跟进")
  void checkInWritesFollowUp() {
    when(visitMapper.selectById(1L)).thenReturn(visit(1L, "PLANNED"));

    CheckInRequest req = new CheckInRequest();
    req.setLatitude(31.2304);
    req.setLongitude(121.4737);
    req.setLocationText("上海市浦东新区");
    req.setSummary("客户确认续约");

    VisitResponse resp = service.checkIn(1L, req);

    assertThat(resp.getStatus()).isEqualTo("DONE");
    verify(followUpMapper).insert(any(FollowUp.class));
  }

  @Test
  @DisplayName("取消：仅计划中可取消")
  void cancelOnlyPlanned() {
    when(visitMapper.selectById(1L)).thenReturn(visit(1L, "DONE"));

    assertThatThrownBy(() -> service.cancel(1L)).isInstanceOf(BusinessException.class);
  }
}
