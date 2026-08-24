package com.crm.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.crm.common.BusinessException;
import com.crm.dto.auth.LoginRequest;
import com.crm.repository.UserMapper;
import com.crm.security.JwtUtil;
import com.crm.security.UserStateCache;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/** AuthService 单元测试（T064）：IP 维度登录限流 + 用户名锁定 + IP 解析。 */
class AuthServiceTest {

  private UserMapper userMapper;
  private RedisTemplate<String, Object> redis;
  private ValueOperations<String, Object> ops;
  private CaptchaService captchaService;
  private AuthService service;

  @SuppressWarnings("unchecked")
  @BeforeEach
  void setUp() {
    userMapper = mock(UserMapper.class);
    redis = mock(RedisTemplate.class);
    ops = mock(ValueOperations.class);
    when(redis.opsForValue()).thenReturn(ops);
    PasswordEncoder encoder = new BCryptPasswordEncoder();
    JwtUtil jwtUtil = mock(JwtUtil.class);
    UserStateCache userStateCache = mock(UserStateCache.class);
    captchaService = mock(CaptchaService.class);
    service =
        new AuthService(
            userMapper,
            encoder,
            jwtUtil,
            redis,
            userStateCache,
            captchaService,
            mock(RoleService.class),
            false);
  }

  private LoginRequest req(String username, String password) {
    LoginRequest r = new LoginRequest();
    r.setUsername(username);
    r.setPassword(password);
    return r;
  }

  @Test
  @DisplayName("IP 失败累计 10 次后锁定，第 11 次登录被拒")
  void ipBlockedAfterFailures() {
    when(ops.get("auth:ip-fail:1.2.3.4")).thenReturn(10);
    when(userMapper.selectOne(any())).thenReturn(null);

    assertThatThrownBy(() -> service.login(req("admin", "wrong"), "1.2.3.4"))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("过于频繁");
    verify(userMapper, never()).selectOne(any());
  }

  @Test
  @DisplayName("IP 失败计数未达阈值时正常校验凭据并记录失败")
  void ipFailureRecordedOnBadCredential() {
    when(ops.get("auth:ip-fail:1.2.3.4")).thenReturn(3);
    when(userMapper.selectOne(any())).thenReturn(null);

    assertThatThrownBy(() -> service.login(req("admin", "wrong"), "1.2.3.4"))
        .isInstanceOf(BusinessException.class);

    verify(ops).increment("auth:ip-fail:1.2.3.4");
    verify(redis).expire(eq("auth:ip-fail:1.2.3.4"), any());
  }

  @Test
  @DisplayName("用户名 5 次锁定仍然生效（既有逻辑）")
  void usernameBlockStillWorks() {
    when(ops.get("auth:fail:admin")).thenReturn(5);

    assertThatThrownBy(() -> service.login(req("admin", "anything"), "9.9.9.9"))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("失败次数过多");
  }

  @Test
  @DisplayName("resolveClientIp 优先取 X-Forwarded-For 首个地址")
  void resolveClientIpPrefersForwarded() {
    HttpServletRequest req = mock(HttpServletRequest.class);
    when(req.getHeader("X-Forwarded-For")).thenReturn("203.0.113.5, 10.0.0.1");

    String ip = AuthService.resolveClientIp(req, "127.0.0.1");

    assertThat(ip).isEqualTo("203.0.113.5");
  }

  @Test
  @DisplayName("resolveClientIp 无转发头时回退 remoteAddr")
  void resolveClientIpFallsBack() {
    HttpServletRequest req = mock(HttpServletRequest.class);
    when(req.getHeader("X-Forwarded-For")).thenReturn(null);

    String ip = AuthService.resolveClientIp(req, "127.0.0.1");

    assertThat(ip).isEqualTo("127.0.0.1");
  }

  @Test
  @DisplayName("登录成功清 IP 计数")
  void successClearsIpFailures() {
    // 用户存在且密码正确
    when(ops.get("auth:fail:admin")).thenReturn(null);
    com.crm.entity.User u = new com.crm.entity.User();
    u.setId(1L);
    u.setUsername("admin");
    u.setPasswordHash(new BCryptPasswordEncoder().encode("pass1234"));
    u.setEnabled(true);
    when(userMapper.selectOne(any())).thenReturn(u);

    service.login(req("admin", "pass1234"), "1.2.3.4");

    verify(redis).delete("auth:ip-fail:1.2.3.4");
    verify(redis).delete("auth:fail:admin");
  }
}
