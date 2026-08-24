package com.crm.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.crm.common.BusinessException;
import com.crm.common.ErrorCode;
import com.crm.dto.lead.ConvertRequest;
import com.crm.dto.lead.LeadRequest;
import com.crm.entity.Customer;
import com.crm.entity.Lead;
import com.crm.entity.Opportunity;
import com.crm.entity.SalesOpportunity;
import com.crm.entity.User;
import com.crm.repository.CustomerMapper;
import com.crm.repository.FollowUpMapper;
import com.crm.repository.LeadMapper;
import com.crm.repository.OpportunityMapper;
import com.crm.repository.SalesOpportunityMapper;
import com.crm.repository.UserMapper;
import com.crm.security.SecurityUtil;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

/** LeadService 单元测试（T016）：CRUD/状态校验/转化查重/线索池。 */
@ExtendWith(MockitoExtension.class)
class LeadServiceTest {

  private LeadMapper leadMapper;
  private CustomerMapper customerMapper;
  private OpportunityMapper opportunityMapper;
  private SalesOpportunityMapper salesOpportunityMapper;
  private FollowUpMapper followUpMapper;
  private UserMapper userMapper;
  private AuditService auditService;
  private LeadService service;
  private MockedStatic<SecurityUtil> securityUtilMock;

  @BeforeEach
  void setUp() {
    leadMapper = mock(LeadMapper.class);
    customerMapper = mock(CustomerMapper.class);
    opportunityMapper = mock(OpportunityMapper.class);
    salesOpportunityMapper = mock(SalesOpportunityMapper.class);
    followUpMapper = mock(FollowUpMapper.class);
    userMapper = mock(UserMapper.class);
    auditService = mock(AuditService.class);
    service =
        new LeadService(
            leadMapper,
            customerMapper,
            opportunityMapper,
            salesOpportunityMapper,
            followUpMapper,
            userMapper,
            auditService,
            mock(WorkflowEventPublisher.class),
            mock(CustomFieldService.class),
            mock(LeadScoreService.class));
    securityUtilMock = Mockito.mockStatic(SecurityUtil.class);
    securityUtilMock.when(SecurityUtil::currentUserId).thenReturn(1L);
  }

  @AfterEach
  void tearDown() {
    securityUtilMock.close();
  }

  private LeadRequest request(String name, String company) {
    LeadRequest req = new LeadRequest();
    req.setName(name);
    req.setCompany(company);
    req.setSource("WEBSITE");
    req.setScore(80);
    return req;
  }

  private Lead lead(Long id, String status) {
    Lead l = new Lead();
    l.setId(id);
    l.setName("张三");
    l.setCompany("测试科技");
    l.setStatus(status);
    l.setSource("WEBSITE");
    l.setScore(80);
    l.setVersion(0);
    return l;
  }

  @Test
  @DisplayName("创建线索成功：默认状态 NEW、来源 OTHER、评分 0")
  void createWithDefaults() {
    LeadRequest req = new LeadRequest();
    req.setName("张三");
    req.setCompany("测试科技");
    when(leadMapper.insert(any(Lead.class)))
        .thenAnswer(
            invocation -> {
              Lead l = invocation.getArgument(0);
              l.setId(1L);
              return 1;
            });
    Lead saved = lead(1L, "NEW");
    saved.setName("张三");
    saved.setSource("OTHER");
    saved.setScore(0);
    when(leadMapper.selectById(1L)).thenReturn(saved);

    var resp = service.create(req);

    assertThat(resp.getName()).isEqualTo("张三");
    assertThat(resp.getStatus()).isEqualTo("NEW");
    assertThat(resp.getSource()).isEqualTo("OTHER");
    assertThat(resp.getScore()).isZero();
    verify(leadMapper).insert(any(Lead.class));
  }

  @Test
  @DisplayName("编辑已转化线索抛出 LEAD_INVALID_STATE")
  void updateConvertedThrows() {
    when(leadMapper.selectById(1L)).thenReturn(lead(1L, "QUALIFIED"));

    assertThatThrownBy(() -> service.update(1L, request("张三", "测试科技")))
        .isInstanceOf(BusinessException.class)
        .extracting(e -> ((BusinessException) e).getErrorCode())
        .isEqualTo(ErrorCode.LEAD_INVALID_STATE);
    verify(leadMapper, never()).updateById(any());
  }

  @Test
  @DisplayName("编辑无效线索抛出 LEAD_INVALID_STATE")
  void updateDisqualifiedThrows() {
    when(leadMapper.selectById(1L)).thenReturn(lead(1L, "DISQUALIFIED"));

    assertThatThrownBy(() -> service.update(1L, request("张三", "测试科技")))
        .isInstanceOf(BusinessException.class)
        .extracting(e -> ((BusinessException) e).getErrorCode())
        .isEqualTo(ErrorCode.LEAD_INVALID_STATE);
  }

  @Test
  @DisplayName("删除已转化线索抛出 LEAD_ALREADY_CONVERTED")
  void deleteConvertedThrows() {
    when(leadMapper.selectById(1L)).thenReturn(lead(1L, "QUALIFIED"));

    assertThatThrownBy(() -> service.delete(1L))
        .isInstanceOf(BusinessException.class)
        .extracting(e -> ((BusinessException) e).getErrorCode())
        .isEqualTo(ErrorCode.LEAD_ALREADY_CONVERTED);
    verify(leadMapper, never()).deleteById(org.mockito.ArgumentMatchers.<Long>any());
  }

  @Test
  @DisplayName("删除普通线索成功")
  void deleteNormalSucceeds() {
    when(leadMapper.selectById(1L)).thenReturn(lead(1L, "NEW"));
    doReturn(1).when(leadMapper).deleteById(1L);

    service.delete(1L);

    verify(leadMapper).deleteById(org.mockito.ArgumentMatchers.eq(1L));
  }

  @Test
  @DisplayName("领取线索：NEW→WORKING，设置 ownerId")
  void claimSetsOwnerAndStatus() {
    Lead l = lead(1L, "NEW");
    l.setOwnerId(null);
    when(leadMapper.selectById(1L)).thenReturn(l);
    when(leadMapper.updateById(any(Lead.class))).thenReturn(1);
    when(leadMapper.selectById(1L)).thenReturn(l);

    var resp = service.claim(1L);

    assertThat(resp.getOwnerId()).isEqualTo(1L);
    assertThat(resp.getStatus()).isEqualTo("WORKING");
  }

  @Test
  @DisplayName("领取已被领取的线索抛出 LEAD_INVALID_STATE")
  void claimAlreadyOwnedThrows() {
    Lead l = lead(1L, "WORKING");
    l.setOwnerId(2L);
    when(leadMapper.selectById(1L)).thenReturn(l);

    assertThatThrownBy(() -> service.claim(1L))
        .isInstanceOf(BusinessException.class)
        .extracting(e -> ((BusinessException) e).getErrorCode())
        .isEqualTo(ErrorCode.LEAD_INVALID_STATE);
  }

  @Test
  @DisplayName("分配线索：NEW→WORKING，设置指定 ownerId")
  void assignSetsOwner() {
    Lead l = lead(1L, "NEW");
    when(leadMapper.selectById(1L)).thenReturn(l);
    User owner = new User();
    owner.setId(3L);
    owner.setUsername("sales01");
    when(userMapper.selectById(3L)).thenReturn(owner);
    when(leadMapper.updateById(any(Lead.class))).thenReturn(1);

    var resp = service.assign(1L, 3L);

    assertThat(resp.getOwnerId()).isEqualTo(3L);
    assertThat(resp.getStatus()).isEqualTo("WORKING");
  }

  @Test
  @DisplayName("转化已转化线索抛出 LEAD_INVALID_STATE")
  void convertConvertedThrows() {
    when(leadMapper.selectById(1L)).thenReturn(lead(1L, "QUALIFIED"));

    ConvertRequest req = new ConvertRequest();
    req.setOpportunityName("测试商机");
    req.setExpectedAmount(100000L);

    assertThatThrownBy(() -> service.convert(1L, req))
        .isInstanceOf(BusinessException.class)
        .extracting(e -> ((BusinessException) e).getErrorCode())
        .isEqualTo(ErrorCode.LEAD_INVALID_STATE);
  }

  @Test
  @DisplayName("转化成功：创建新客户+商机+销售机会，线索状态→QUALIFIED")
  void convertCreatesCustomerAndOpportunity() {
    Lead l = lead(1L, "WORKING");
    l.setPhone("13800138000");
    l.setEmail("zhangsan@test.com");
    when(leadMapper.selectById(1L)).thenReturn(l);
    when(customerMapper.selectOne(any())).thenReturn(null);
    when(customerMapper.insert(any(Customer.class)))
        .thenAnswer(
            invocation -> {
              Customer c = invocation.getArgument(0);
              c.setId(100L);
              return 1;
            });
    when(opportunityMapper.insert(any(Opportunity.class))).thenReturn(1);
    when(salesOpportunityMapper.insert(any(SalesOpportunity.class))).thenReturn(1);
    when(leadMapper.updateById(any(Lead.class))).thenReturn(1);
    lenient().when(followUpMapper.selectList(any())).thenReturn(List.of());

    ConvertRequest req = new ConvertRequest();
    req.setOpportunityName("测试商机");
    req.setExpectedAmount(500000L);

    var resp = service.convert(1L, req);

    assertThat(resp.getStatus()).isEqualTo("QUALIFIED");
    assertThat(resp.getConvertedCustomerId()).isEqualTo(100L);
    assertThat(resp.getConvertedAt()).isNotNull();
    verify(customerMapper).insert(any(Customer.class));
    verify(opportunityMapper).insert(any(Opportunity.class));
    verify(salesOpportunityMapper).insert(any(SalesOpportunity.class));
  }

  @Test
  @DisplayName("转化时公司名已存在：关联已有客户，不重复创建")
  void convertReusesExistingCustomer() {
    Lead l = lead(1L, "WORKING");
    when(leadMapper.selectById(1L)).thenReturn(l);
    Customer existing = new Customer();
    existing.setId(99L);
    existing.setCompany("测试科技");
    when(customerMapper.selectOne(any())).thenReturn(existing);
    when(opportunityMapper.insert(any(Opportunity.class))).thenReturn(1);
    when(salesOpportunityMapper.insert(any(SalesOpportunity.class))).thenReturn(1);
    when(leadMapper.updateById(any(Lead.class))).thenReturn(1);
    lenient().when(followUpMapper.selectList(any())).thenReturn(List.of());

    ConvertRequest req = new ConvertRequest();
    req.setOpportunityName("测试商机");
    req.setExpectedAmount(500000L);

    var resp = service.convert(1L, req);

    assertThat(resp.getConvertedCustomerId()).isEqualTo(99L);
    verify(customerMapper, never()).insert(any());
    verify(opportunityMapper).insert(any(Opportunity.class));
  }

  @Test
  @DisplayName("详情包含跟进记录时间线")
  void detailIncludesFollowUps() {
    Lead l = lead(1L, "WORKING");
    when(leadMapper.selectById(1L)).thenReturn(l);
    com.crm.entity.FollowUp fu = new com.crm.entity.FollowUp();
    fu.setId(10L);
    fu.setMethod("PHONE");
    fu.setContent("电话沟通");
    when(followUpMapper.selectList(any())).thenReturn(List.of(fu));

    var detail = service.detail(1L);

    assertThat(detail.getFollowUps()).hasSize(1);
    assertThat(detail.getFollowUps().get(0).getMethod()).isEqualTo("PHONE");
  }

  @Test
  @DisplayName("线索池筛选：ownerId IS NULL AND status IN (NEW, WORKING)")
  void pagePoolOnlyFiltersUnowned() {
    com.baomidou.mybatisplus.extension.plugins.pagination.Page<Lead> p =
        new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(1, 20);
    p.setRecords(List.of());
    p.setTotal(0);
    when(leadMapper.selectPage(any(), any())).thenReturn(p);

    var result = service.page(null, null, null, null, true, null, 1, 20);

    assertThat(result.getItems()).isEmpty();
    verify(leadMapper).selectPage(any(), any());
  }
}
