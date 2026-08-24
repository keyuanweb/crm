package com.crm.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.crm.common.BusinessException;
import com.crm.common.ErrorCode;
import com.crm.dto.customer.CustomerRequest;
import com.crm.dto.customer.CustomerResponse;
import com.crm.entity.Customer;
import com.crm.entity.FollowUp;
import com.crm.entity.Opportunity;
import com.crm.entity.SalesOpportunity;
import com.crm.repository.ContactMapper;
import com.crm.repository.CustomerMapper;
import com.crm.repository.FollowUpMapper;
import com.crm.repository.OpportunityMapper;
import com.crm.repository.SalesOpportunityMapper;
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

/** CustomerService 单元测试（T019）：校验/去重/乐观锁/逻辑删除/数据权限。 */
@ExtendWith(MockitoExtension.class)
class CustomerServiceTest {

  private CustomerMapper customerMapper;
  private OpportunityMapper opportunityMapper;
  private FollowUpMapper followUpMapper;
  private SalesOpportunityMapper salesOpportunityMapper;
  private ContactMapper contactMapper;
  private AuditService auditService;
  private DashboardStatsService dashboardStatsService;
  private DataPermissionService dataPermissionService;
  private com.crm.repository.CustomerShareMapper customerShareMapper;
  private com.crm.repository.UserMapper userMapper;
  private CustomerService service;
  private MockedStatic<SecurityUtil> securityUtilMock;

  @BeforeEach
  void setUp() {
    customerMapper = mock(CustomerMapper.class);
    opportunityMapper = mock(OpportunityMapper.class);
    followUpMapper = mock(FollowUpMapper.class);
    salesOpportunityMapper = mock(SalesOpportunityMapper.class);
    contactMapper = mock(ContactMapper.class);
    auditService = mock(AuditService.class);
    dashboardStatsService = mock(DashboardStatsService.class);
    dataPermissionService = mock(DataPermissionService.class);
    customerShareMapper = mock(com.crm.repository.CustomerShareMapper.class);
    userMapper = mock(com.crm.repository.UserMapper.class);
    service =
        new CustomerService(
            customerMapper,
            opportunityMapper,
            followUpMapper,
            salesOpportunityMapper,
            contactMapper,
            auditService,
            dashboardStatsService,
            dataPermissionService,
            customerShareMapper,
            userMapper,
            mock(com.crm.repository.SalesOrderMapper.class),
            mock(CustomFieldService.class),
            mock(Customer360Service.class));
    securityUtilMock = Mockito.mockStatic(SecurityUtil.class);
    securityUtilMock.when(SecurityUtil::currentUserId).thenReturn(1L);
    // 当前用户为管理员（数据权限 ALL），detail/update/delete 权限校验通过
    com.crm.entity.User current = new com.crm.entity.User();
    current.setId(1L);
    current.setRole("ADMIN");
    lenient().when(userMapper.selectById(1L)).thenReturn(current);
    lenient().when(dataPermissionService.resolveVisibleOwnerIds(1L)).thenReturn(List.of());
  }

  @AfterEach
  void tearDown() {
    securityUtilMock.close();
  }

  private CustomerRequest request(String name, String company) {
    CustomerRequest req = new CustomerRequest();
    req.setName(name);
    req.setCompany(company);
    return req;
  }

  @Test
  @DisplayName("创建客户成功：插入并返回响应")
  void createInsertsCustomer() {
    when(customerMapper.selectCount(any())).thenReturn(0L);
    when(customerMapper.insert(any(Customer.class))).thenReturn(1);

    CustomerResponse resp = service.create(request("张三", "XX 科技"));

    assertThat(resp.getName()).isEqualTo("张三");
    assertThat(resp.getStatus()).isEqualTo("ACTIVE");
    verify(customerMapper).insert(any(Customer.class));
  }

  @Test
  @DisplayName("重复客户抛出 CUSTOMER_DUPLICATE")
  void createDuplicateThrows() {
    when(customerMapper.selectCount(any())).thenReturn(1L);

    assertThatThrownBy(() -> service.create(request("张三", "XX 科技")))
        .isInstanceOf(BusinessException.class)
        .extracting(e -> ((BusinessException) e).getErrorCode())
        .isEqualTo(ErrorCode.CUSTOMER_DUPLICATE);
    verify(customerMapper, never()).insert(any());
  }

  @Test
  @DisplayName("乐观锁版本冲突抛出 VERSION_CONFLICT")
  void updateVersionConflictThrows() {
    Customer existing = new Customer();
    existing.setId(1L);
    existing.setName("张三");
    existing.setCompany("XX 科技");
    existing.setStatus("ACTIVE");
    when(customerMapper.selectById(1L)).thenReturn(existing);
    when(customerMapper.selectCount(any())).thenReturn(0L);
    when(customerMapper.updateById(any(Customer.class))).thenReturn(0);

    CustomerRequest req = request("张三", "XX 科技");
    req.setVersion(0);

    assertThatThrownBy(() -> service.update(1L, req))
        .isInstanceOf(BusinessException.class)
        .extracting(e -> ((BusinessException) e).getErrorCode())
        .isEqualTo(ErrorCode.VERSION_CONFLICT);
  }

  @Test
  @DisplayName("删除不存在客户抛出 CUSTOMER_NOT_FOUND")
  void deleteMissingThrows() {
    when(customerMapper.selectById(99L)).thenReturn(null);
    assertThatThrownBy(() -> service.delete(99L))
        .isInstanceOf(BusinessException.class)
        .extracting(e -> ((BusinessException) e).getErrorCode())
        .isEqualTo(ErrorCode.CUSTOMER_NOT_FOUND);
  }

  @Test
  @DisplayName("详情聚合商机与跟进记录")
  void detailAggregatesRelatedData() {
    Customer customer = new Customer();
    customer.setId(1L);
    customer.setName("张三");
    customer.setCompany("XX 科技");
    customer.setStatus("ACTIVE");
    when(customerMapper.selectById(1L)).thenReturn(customer);

    Opportunity opp = new Opportunity();
    opp.setId(10L);
    opp.setName("年度合作");
    opp.setStatus("ACTIVE");
    when(opportunityMapper.selectList(any())).thenReturn(List.of(opp));
    SalesOpportunity so = new SalesOpportunity();
    so.setOpportunityId(10L);
    lenient().when(salesOpportunityMapper.selectList(any())).thenReturn(List.of(so));

    FollowUp followUp = new FollowUp();
    followUp.setId(100L);
    followUp.setMethod("PHONE");
    followUp.setContent("沟通");
    when(followUpMapper.selectList(any())).thenReturn(List.of(followUp));

    var detail = service.detail(1L);
    assertThat(detail.getOpportunities()).hasSize(1);
    assertThat(detail.getFollowUps()).hasSize(1);
  }

  @Test
  @DisplayName("列表响应电话/邮箱脱敏（FR-016）")
  void pageMasksSensitiveFields() {
    Customer c = new Customer();
    c.setId(1L);
    c.setName("张三");
    c.setCompany("XX 科技");
    c.setPhone("13812345678");
    c.setEmail("zhangsan@example.com");
    c.setStatus("ACTIVE");
    com.baomidou.mybatisplus.extension.plugins.pagination.Page<Customer> p =
        new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(1, 20);
    p.setRecords(List.of(c));
    p.setTotal(1);
    when(customerMapper.selectPage(any(), any())).thenReturn(p);

    var result = service.page(null, null, null, 1, 20);
    assertThat(result.getItems()).hasSize(1);
    assertThat(result.getItems().get(0).getPhone()).isEqualTo("138****5678");
    assertThat(result.getItems().get(0).getEmail()).isEqualTo("z***n@example.com");
  }

  @Test
  @DisplayName("创建客户写入审计日志（FR-017）")
  void createWritesAudit() {
    when(customerMapper.selectCount(any())).thenReturn(0L);
    when(customerMapper.insert(any(Customer.class))).thenReturn(1);
    service.create(request("张三", "XX 科技"));
    verify(auditService)
        .record(
            org.mockito.ArgumentMatchers.eq("CREATE"),
            org.mockito.ArgumentMatchers.eq("CUSTOMER"),
            any(),
            any());
  }
}
