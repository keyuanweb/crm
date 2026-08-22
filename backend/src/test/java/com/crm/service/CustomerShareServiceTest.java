package com.crm.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.crm.common.BusinessException;
import com.crm.common.ErrorCode;
import com.crm.dto.share.CustomerShareRequest;
import com.crm.entity.Customer;
import com.crm.entity.CustomerShare;
import com.crm.entity.User;
import com.crm.repository.CustomerMapper;
import com.crm.repository.CustomerShareMapper;
import com.crm.repository.UserMapper;
import com.crm.security.SecurityUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

/** CustomerShareService 单元测试（012 T027）：共享/去重/非归属者 403。 */
@ExtendWith(MockitoExtension.class)
class CustomerShareServiceTest {

  private CustomerShareMapper shareMapper;
  private CustomerMapper customerMapper;
  private UserMapper userMapper;
  private AuditService auditService;
  private CustomerShareService service;
  private MockedStatic<SecurityUtil> securityUtilMock;

  @BeforeEach
  void setUp() {
    shareMapper = mock(CustomerShareMapper.class);
    customerMapper = mock(CustomerMapper.class);
    userMapper = mock(UserMapper.class);
    auditService = mock(AuditService.class);
    service = new CustomerShareService(shareMapper, customerMapper, userMapper, auditService);
    securityUtilMock = Mockito.mockStatic(SecurityUtil.class);
    securityUtilMock.when(SecurityUtil::currentUserId).thenReturn(1L);
  }

  @AfterEach
  void tearDown() {
    securityUtilMock.close();
  }

  private Customer customer(Long id, Long ownerId) {
    Customer c = new Customer();
    c.setId(id);
    c.setName("共享客户");
    c.setOwnerId(ownerId);
    return c;
  }

  private User user(Long id, String role) {
    User u = new User();
    u.setId(id);
    u.setRole(role);
    return u;
  }

  @Test
  @DisplayName("归属者共享成功：记录审计")
  void ownerSharesSucceeds() {
    when(customerMapper.selectById(10L)).thenReturn(customer(10L, 1L));
    when(userMapper.selectById(1L)).thenReturn(user(1L, "SALES"));
    when(shareMapper.selectCount(any())).thenReturn(0L);
    when(shareMapper.insert(any(CustomerShare.class))).thenReturn(1);

    CustomerShareRequest req = new CustomerShareRequest();
    req.setCustomerId(10L);
    req.setSharedToUserId(5L);
    var resp = service.share(req);

    assertThat(resp.getCustomerId()).isEqualTo(10L);
    verify(shareMapper).insert(any(CustomerShare.class));
    verify(auditService).record("SHARE", "CUSTOMER", 10L, "共享客户给用户 5");
  }

  @Test
  @DisplayName("非归属者非管理员共享抛出 FORBIDDEN")
  void nonOwnerShareThrows() {
    when(customerMapper.selectById(10L)).thenReturn(customer(10L, 999L));
    when(userMapper.selectById(1L)).thenReturn(user(1L, "SALES"));

    CustomerShareRequest req = new CustomerShareRequest();
    req.setCustomerId(10L);
    req.setSharedToUserId(5L);

    assertThatThrownBy(() -> service.share(req))
        .isInstanceOf(BusinessException.class)
        .extracting(e -> ((BusinessException) e).getErrorCode())
        .isEqualTo(ErrorCode.FORBIDDEN);
    verify(shareMapper, never()).insert(any());
  }

  @Test
  @DisplayName("重复共享抛出 SHARE_EXISTS")
  void duplicateShareThrows() {
    when(customerMapper.selectById(10L)).thenReturn(customer(10L, 1L));
    when(userMapper.selectById(1L)).thenReturn(user(1L, "SALES"));
    when(shareMapper.selectCount(any())).thenReturn(1L);

    CustomerShareRequest req = new CustomerShareRequest();
    req.setCustomerId(10L);
    req.setSharedToUserId(5L);

    assertThatThrownBy(() -> service.share(req))
        .isInstanceOf(BusinessException.class)
        .extracting(e -> ((BusinessException) e).getErrorCode())
        .isEqualTo(ErrorCode.SHARE_EXISTS);
    verify(shareMapper, never()).insert(any());
  }

  @Test
  @DisplayName("取消共享：非归属者非管理员抛出 FORBIDDEN")
  void unshareNonOwnerThrows() {
    CustomerShare share = new CustomerShare();
    share.setId(1L);
    share.setCustomerId(10L);
    when(shareMapper.selectById(1L)).thenReturn(share);
    when(customerMapper.selectById(10L)).thenReturn(customer(10L, 999L));
    when(userMapper.selectById(1L)).thenReturn(user(1L, "SALES"));

    assertThatThrownBy(() -> service.unshare(1L))
        .isInstanceOf(BusinessException.class)
        .extracting(e -> ((BusinessException) e).getErrorCode())
        .isEqualTo(ErrorCode.FORBIDDEN);
    verify(shareMapper, never()).deleteById(org.mockito.ArgumentMatchers.<Long>any());
  }

  @Test
  @DisplayName("共享记录不存在抛出 SHARE_NOT_FOUND")
  void unshareMissingThrows() {
    when(shareMapper.selectById(99L)).thenReturn(null);

    assertThatThrownBy(() -> service.unshare(99L))
        .isInstanceOf(BusinessException.class)
        .extracting(e -> ((BusinessException) e).getErrorCode())
        .isEqualTo(ErrorCode.SHARE_NOT_FOUND);
  }
}
