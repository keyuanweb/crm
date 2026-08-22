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
import com.crm.dto.user.ChangePasswordRequest;
import com.crm.dto.user.UserCreateRequest;
import com.crm.dto.user.UserUpdateRequest;
import com.crm.entity.User;
import com.crm.repository.UserMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

/** 用户管理单元测试（002-user-management：去重/密码强度/防护规则）。 */
@ExtendWith(MockitoExtension.class)
class UserServiceTest {

  private UserMapper userMapper;
  private RedisTemplate<String, Object> redisTemplate;
  private AuditService auditService;
  private UserService service;

  @BeforeEach
  void setUp() {
    userMapper = mock(UserMapper.class);
    redisTemplate = mock(RedisTemplate.class);
    auditService = mock(AuditService.class);
    service = new UserService(userMapper, new BCryptPasswordEncoder(), redisTemplate, auditService);
  }

  @AfterEach
  void clearContext() {
    SecurityContextHolder.clearContext();
  }

  private UserCreateRequest createRequest(String username, String role, String password) {
    UserCreateRequest req = new UserCreateRequest();
    req.setUsername(username);
    req.setDisplayName("测试用户");
    req.setRole(role);
    req.setPassword(password);
    return req;
  }

  @Test
  @DisplayName("创建用户：用户名重复抛出 USER_DUPLICATE")
  void createDuplicateThrows() {
    when(userMapper.selectCount(any())).thenReturn(1L);
    assertThatThrownBy(() -> service.create(createRequest("sales01", "SALES", "pass1234")))
        .isInstanceOf(BusinessException.class)
        .extracting(e -> ((BusinessException) e).getErrorCode())
        .isEqualTo(ErrorCode.USER_DUPLICATE);
  }

  @Test
  @DisplayName("创建用户：弱密码（纯字母）抛出 BAD_REQUEST")
  void createWeakPasswordThrows() {
    when(userMapper.selectCount(any())).thenReturn(0L);
    assertThatThrownBy(() -> service.create(createRequest("sales01", "SALES", "abcdefgh")))
        .isInstanceOf(BusinessException.class)
        .extracting(e -> ((BusinessException) e).getErrorCode())
        .isEqualTo(ErrorCode.BAD_REQUEST);
  }

  @Test
  @DisplayName("创建用户：成功且密码被加密存储")
  void createSuccessEncodesPassword() {
    when(userMapper.selectCount(any())).thenReturn(0L);
    when(userMapper.insert(any(User.class))).thenReturn(1);
    User saved = new User();
    saved.setId(1L);
    saved.setUsername("sales01");
    saved.setDisplayName("测试用户");
    saved.setRole("SALES");
    saved.setEnabled(true);
    org.mockito.ArgumentCaptor<User> captor = org.mockito.ArgumentCaptor.forClass(User.class);
    verify(userMapper, never()).insert(any());
    service.create(createRequest("sales01", "SALES", "pass1234"));
    verify(userMapper).insert(captor.capture());
    assertThat(captor.getValue().getPasswordHash()).startsWith("$2");
    assertThat(captor.getValue().getTokenVersion()).isZero();
  }

  @Test
  @DisplayName("不能停用当前登录账号（FR-007）")
  void cannotDisableSelf() {
    Authentication auth = mock(Authentication.class);
    when(auth.getPrincipal())
        .thenReturn(new com.crm.security.JwtAuthFilter.CrmPrincipal(5L, "admin", "ADMIN"));
    SecurityContextHolder.getContext().setAuthentication(auth);

    User user = new User();
    user.setId(5L);
    user.setUsername("admin");
    user.setRole("ADMIN");
    user.setEnabled(true);
    user.setTokenVersion(0);
    when(userMapper.selectById(5L)).thenReturn(user);

    UserUpdateRequest req = new UserUpdateRequest();
    req.setEnabled(false);
    req.setVersion(0);
    assertThatThrownBy(() -> service.update(5L, req))
        .isInstanceOf(BusinessException.class)
        .extracting(e -> ((BusinessException) e).getErrorCode())
        .isEqualTo(ErrorCode.FORBIDDEN);
  }

  @Test
  @DisplayName("停用最后一个启用 ADMIN 抛出 FORBIDDEN")
  void cannotDisableLastAdmin() {
    User admin = new User();
    admin.setId(1L);
    admin.setUsername("admin");
    admin.setRole("ADMIN");
    admin.setEnabled(true);
    admin.setTokenVersion(0);
    when(userMapper.selectById(1L)).thenReturn(admin);
    when(userMapper.selectCount(any())).thenReturn(0L); // 无其他启用 ADMIN

    UserUpdateRequest req = new UserUpdateRequest();
    req.setEnabled(false);
    req.setVersion(0);
    assertThatThrownBy(() -> service.update(1L, req))
        .isInstanceOf(BusinessException.class)
        .extracting(e -> ((BusinessException) e).getErrorCode())
        .isEqualTo(ErrorCode.FORBIDDEN);
  }

  @Test
  @DisplayName("修改自身密码：旧密码错误抛出 INVALID_CREDENTIALS")
  void changeOwnPasswordWrongOld() {
    Authentication auth = mock(Authentication.class);
    when(auth.getPrincipal())
        .thenReturn(new com.crm.security.JwtAuthFilter.CrmPrincipal(3L, "sales01", "SALES"));
    SecurityContextHolder.getContext().setAuthentication(auth);

    User user = new User();
    user.setId(3L);
    user.setUsername("sales01");
    user.setPasswordHash(new BCryptPasswordEncoder().encode("oldPass123"));
    when(userMapper.selectById(3L)).thenReturn(user);

    ChangePasswordRequest req = new ChangePasswordRequest();
    req.setOldPassword("wrongPass");
    req.setNewPassword("newPass456");
    assertThatThrownBy(() -> service.changeOwnPassword(req))
        .isInstanceOf(BusinessException.class)
        .extracting(e -> ((BusinessException) e).getErrorCode())
        .isEqualTo(ErrorCode.INVALID_CREDENTIALS);
  }
}
