package com.crm.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.crm.common.BusinessException;
import com.crm.common.ErrorCode;
import com.crm.dto.auth.AuthResponse;
import com.crm.dto.auth.LoginRequest;
import com.crm.dto.auth.RefreshRequest;
import com.crm.dto.auth.UserInfo;
import com.crm.entity.User;
import com.crm.repository.UserMapper;
import com.crm.security.JwtUtil;
import io.jsonwebtoken.Claims;
import java.time.Duration;
import java.time.LocalDateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

/** 认证服务：登录/刷新/登出（contracts/auth.md，research.md R1）。 */
@Service
public class AuthService {

  private static final Logger log = LoggerFactory.getLogger(AuthService.class);
  private static final String REFRESH_KEY_PREFIX = "auth:refresh:";
  private static final String FAIL_KEY_PREFIX = "auth:fail:";
  private static final int MAX_LOGIN_FAILURES = 5;
  private static final Duration FAIL_WINDOW = Duration.ofMinutes(15);

  private final UserMapper userMapper;
  private final PasswordEncoder passwordEncoder;
  private final JwtUtil jwtUtil;
  private final RedisTemplate<String, Object> redisTemplate;

  public AuthService(
      UserMapper userMapper,
      PasswordEncoder passwordEncoder,
      JwtUtil jwtUtil,
      RedisTemplate<String, Object> redisTemplate) {
    this.userMapper = userMapper;
    this.passwordEncoder = passwordEncoder;
    this.jwtUtil = jwtUtil;
    this.redisTemplate = redisTemplate;
  }

  public AuthResponse login(LoginRequest request) {
    if (isBlocked(request.getUsername())) {
      throw new BusinessException(ErrorCode.UNAUTHORIZED, "登录失败次数过多，请 15 分钟后再试");
    }
    User user =
        userMapper.selectOne(
            new LambdaQueryWrapper<User>().eq(User::getUsername, request.getUsername()));
    if (user == null || !passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
      recordFailure(request.getUsername());
      throw new BusinessException(ErrorCode.INVALID_CREDENTIALS);
    }
    clearFailures(request.getUsername());
    if (!Boolean.TRUE.equals(user.getEnabled())) {
      throw new BusinessException(ErrorCode.FORBIDDEN, "账号已停用");
    }
    // FR-002：更新最后登录时间
    user.setLastLoginAt(LocalDateTime.now());
    userMapper.updateById(user);
    return issueTokens(user);
  }

  /** 登录防暴力破解（T052）：失败 5 次后锁定 15 分钟。 */
  private boolean isBlocked(String username) {
    try {
      Object value = redisTemplate.opsForValue().get(FAIL_KEY_PREFIX + username);
      return value != null && Integer.parseInt(value.toString()) >= MAX_LOGIN_FAILURES;
    } catch (Exception ex) {
      log.warn("Failed to read login-failure counter: {}", ex.getMessage());
      return false;
    }
  }

  private void recordFailure(String username) {
    try {
      String key = FAIL_KEY_PREFIX + username;
      redisTemplate.opsForValue().increment(key);
      redisTemplate.expire(key, FAIL_WINDOW);
    } catch (Exception ex) {
      log.warn("Failed to record login failure: {}", ex.getMessage());
    }
  }

  private void clearFailures(String username) {
    try {
      redisTemplate.delete(FAIL_KEY_PREFIX + username);
    } catch (Exception ex) {
      log.warn("Failed to clear login-failure counter: {}", ex.getMessage());
    }
  }

  public AuthResponse refresh(RefreshRequest request) {
    Claims claims;
    try {
      claims = jwtUtil.parse(request.getRefreshToken());
    } catch (Exception ex) {
      throw new BusinessException(ErrorCode.REFRESH_TOKEN_INVALID);
    }
    Long userId = claims.get("userId") instanceof Number n ? n.longValue() : null;
    if (userId == null) {
      throw new BusinessException(ErrorCode.REFRESH_TOKEN_INVALID);
    }
    // 校验 Redis 中登记的 refresh token（登出后即失效）
    Object stored = redisTemplate.opsForValue().get(REFRESH_KEY_PREFIX + userId);
    if (stored == null || !request.getRefreshToken().equals(stored.toString())) {
      throw new BusinessException(ErrorCode.REFRESH_TOKEN_INVALID);
    }
    User user = userMapper.selectById(userId);
    if (user == null) {
      throw new BusinessException(ErrorCode.USER_NOT_FOUND);
    }
    int tokenVersion = user.getTokenVersion() == null ? 0 : user.getTokenVersion();
    String newAccess =
        jwtUtil.generateAccessToken(user.getId(), user.getUsername(), user.getRole(), tokenVersion);
    return new AuthResponse(newAccess, request.getRefreshToken(), toUserInfo(user));
  }

  public void logout(RefreshRequest request) {
    try {
      Claims claims = jwtUtil.parse(request.getRefreshToken());
      Long userId = claims.get("userId") instanceof Number n ? n.longValue() : null;
      if (userId != null) {
        redisTemplate.delete(REFRESH_KEY_PREFIX + userId);
      }
    } catch (Exception ex) {
      log.debug("Logout with invalid token: {}", ex.getMessage());
    }
  }

  public UserInfo me(Long userId) {
    User user = userMapper.selectById(userId);
    if (user == null) {
      throw new BusinessException(ErrorCode.USER_NOT_FOUND);
    }
    return toUserInfo(user);
  }

  private AuthResponse issueTokens(User user) {
    int tokenVersion = user.getTokenVersion() == null ? 0 : user.getTokenVersion();
    String access =
        jwtUtil.generateAccessToken(user.getId(), user.getUsername(), user.getRole(), tokenVersion);
    String refresh =
        jwtUtil.generateRefreshToken(
            user.getId(), user.getUsername(), user.getRole(), tokenVersion);
    redisTemplate
        .opsForValue()
        .set(
            REFRESH_KEY_PREFIX + user.getId(),
            refresh,
            Duration.ofSeconds(jwtUtil.refreshTtlSeconds()));
    return new AuthResponse(access, refresh, toUserInfo(user));
  }

  private UserInfo toUserInfo(User user) {
    return new UserInfo(user.getId(), user.getUsername(), user.getDisplayName(), user.getRole());
  }
}
