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
import com.crm.dto.pool.BatchTransferRequest;
import com.crm.entity.Customer;
import com.crm.entity.FollowUp;
import com.crm.entity.User;
import com.crm.repository.CustomerMapper;
import com.crm.repository.FollowUpMapper;
import com.crm.repository.UserMapper;
import com.crm.security.SecurityUtil;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

/** CustomerPoolService 单元测试（011 T008/T014/T019）：公海/领取/扫描/批量转移。 */
@ExtendWith(MockitoExtension.class)
class CustomerPoolServiceTest {

  private CustomerMapper customerMapper;
  private FollowUpMapper followUpMapper;
  private UserMapper userMapper;
  private AuditService auditService;
  private CustomerPoolService service;
  private MockedStatic<SecurityUtil> securityUtilMock;

  /** 纯 Mockito 测试无 Spring 上下文：注册实体 TableInfo，供 LambdaQueryWrapper 解析列名。 */
  @BeforeAll
  static void initTableInfo() {
    MybatisConfiguration configuration = new MybatisConfiguration();
    MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, "");
    TableInfoHelper.initTableInfo(assistant, Customer.class);
    TableInfoHelper.initTableInfo(assistant, FollowUp.class);
    TableInfoHelper.initTableInfo(assistant, User.class);
  }

  @BeforeEach
  void setUp() {
    customerMapper = mock(CustomerMapper.class);
    followUpMapper = mock(FollowUpMapper.class);
    userMapper = mock(UserMapper.class);
    auditService = mock(AuditService.class);
    service = new CustomerPoolService(customerMapper, followUpMapper, userMapper, auditService);
    ReflectionTestUtils.setField(service, "staleDays", 30);
    securityUtilMock = Mockito.mockStatic(SecurityUtil.class);
    securityUtilMock.when(SecurityUtil::currentUserId).thenReturn(1L);
  }

  @AfterEach
  void tearDown() {
    securityUtilMock.close();
  }

  private Customer customer(Long id, Long ownerId, LocalDateTime createdAt) {
    Customer c = new Customer();
    c.setId(id);
    c.setName("测试客户" + id);
    c.setOwnerId(ownerId);
    c.setCreatedAt(createdAt);
    return c;
  }

  @Test
  @DisplayName("公海查询：owner 为空，批量装配 ownerName")
  void poolQuery() {
    when(customerMapper.selectPage(any(), any()))
        .thenAnswer(
            invocation -> {
              @SuppressWarnings("unchecked")
              com.baomidou.mybatisplus.extension.plugins.pagination.Page<Customer> page =
                  invocation.getArgument(0);
              page.setRecords(List.of(customer(1L, null, LocalDateTime.now())));
              page.setTotal(1);
              return page;
            });

    var result = service.pool(null, null, 1, 20);

    assertThat(result.getTotal()).isEqualTo(1);
    assertThat(result.getItems().get(0).getOwnerId()).isNull();
  }

  @Test
  @DisplayName("领取成功：owner 空 → 本人")
  void claimSucceeds() {
    when(customerMapper.selectById(1L)).thenReturn(customer(1L, null, LocalDateTime.now()));
    when(customerMapper.update(org.mockito.ArgumentMatchers.isNull(), any())).thenReturn(1);
    Customer claimed = customer(1L, 1L, LocalDateTime.now());
    when(customerMapper.selectById(1L)).thenReturn(claimed);

    var resp = service.claim(1L);

    assertThat(resp.getOwnerId()).isEqualTo(1L);
    verify(auditService).record("CLAIM", "CUSTOMER", 1L, "领取客户：测试客户1");
  }

  @Test
  @DisplayName("重复领取（条件更新 0 行）抛出 CUSTOMER_ALREADY_OWNED")
  void claimAlreadyOwnedThrows() {
    when(customerMapper.selectById(1L)).thenReturn(customer(1L, 999L, LocalDateTime.now()));

    assertThatThrownBy(() -> service.claim(1L))
        .isInstanceOf(BusinessException.class)
        .extracting(e -> ((BusinessException) e).getErrorCode())
        .isEqualTo(ErrorCode.CUSTOMER_ALREADY_OWNED);
  }

  @Test
  @DisplayName("客户不存在领取抛出 CUSTOMER_NOT_FOUND")
  void claimMissingThrows() {
    when(customerMapper.selectById(99L)).thenReturn(null);

    assertThatThrownBy(() -> service.claim(99L))
        .isInstanceOf(BusinessException.class)
        .extracting(e -> ((BusinessException) e).getErrorCode())
        .isEqualTo(ErrorCode.CUSTOMER_NOT_FOUND);
  }

  @Test
  @DisplayName("扫描：超 30 天未跟进退回，近期跟进保留")
  void scanReturnsStale() {
    LocalDateTime now = LocalDateTime.now();
    Customer stale = customer(1L, 1L, now.minusDays(40));
    Customer recent = customer(2L, 1L, now.minusDays(10));
    when(customerMapper.selectList(any())).thenReturn(List.of(stale, recent));
    FollowUp fu = new FollowUp();
    fu.setCustomerId(2L);
    fu.setCreatedAt(now.minusDays(5));
    when(followUpMapper.selectList(any())).thenReturn(List.of(fu));

    var result = service.scan();

    assertThat(result.getReturnedCount()).isEqualTo(1L);
    verify(customerMapper).update(org.mockito.ArgumentMatchers.isNull(), any());
    verify(auditService).record("POOL_RETURN", "CUSTOMER", 1L, "超期未跟进退回公海");
  }

  @Test
  @DisplayName("扫描：无跟进按创建时间判定")
  void scanUsesCreatedAtWhenNoFollowUp() {
    LocalDateTime now = LocalDateTime.now();
    Customer stale = customer(1L, 1L, now.minusDays(40));
    when(customerMapper.selectList(any())).thenReturn(List.of(stale));
    when(followUpMapper.selectList(any())).thenReturn(List.of());

    var result = service.scan();

    assertThat(result.getReturnedCount()).isEqualTo(1L);
  }

  @Test
  @DisplayName("批量转移：目标用户不存在抛出 USER_NOT_FOUND")
  void batchTransferMissingTargetThrows() {
    when(userMapper.selectById(99L)).thenReturn(null);
    BatchTransferRequest req = new BatchTransferRequest();
    req.setCustomerIds(List.of(1L, 2L));
    req.setTargetOwnerId(99L);

    assertThatThrownBy(() -> service.batchTransfer(req))
        .isInstanceOf(BusinessException.class)
        .extracting(e -> ((BusinessException) e).getErrorCode())
        .isEqualTo(ErrorCode.USER_NOT_FOUND);
    verify(customerMapper, never()).update(any(), any());
  }

  @Test
  @DisplayName("批量转移成功：归属更新并审计")
  void batchTransferSucceeds() {
    User target = new User();
    target.setId(5L);
    when(userMapper.selectById(5L)).thenReturn(target);
    when(customerMapper.update(org.mockito.ArgumentMatchers.isNull(), any())).thenReturn(1);
    BatchTransferRequest req = new BatchTransferRequest();
    req.setCustomerIds(List.of(1L, 2L));
    req.setTargetOwnerId(5L);

    long updated = service.batchTransfer(req);

    assertThat(updated).isEqualTo(2L);
    verify(auditService).record("TRANSFER", "CUSTOMER", 1L, "转移客户至用户 5");
    verify(auditService).record("TRANSFER", "CUSTOMER", 2L, "转移客户至用户 5");
  }
}
