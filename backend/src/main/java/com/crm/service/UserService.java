package com.crm.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.crm.common.BusinessException;
import com.crm.common.ErrorCode;
import com.crm.common.PageResult;
import com.crm.dto.user.ChangePasswordRequest;
import com.crm.dto.user.ResetPasswordRequest;
import com.crm.dto.user.UserCreateRequest;
import com.crm.dto.user.UserResponse;
import com.crm.dto.user.UserUpdateRequest;
import com.crm.entity.User;
import com.crm.repository.UserMapper;
import com.crm.security.SecurityUtil;
import com.crm.security.UserStateCache;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/** 用户管理服务（002-user-management，FR-001~008）。 */
@Service
public class UserService {

  private static final Logger log = LoggerFactory.getLogger(UserService.class);
  private static final String REFRESH_KEY_PREFIX = "auth:refresh:";

  private final UserMapper userMapper;
  private final PasswordEncoder passwordEncoder;
  private final RedisTemplate<String, Object> redisTemplate;
  private final AuditService auditService;
  private final UserStateCache userStateCache;

  public UserService(
      UserMapper userMapper,
      PasswordEncoder passwordEncoder,
      RedisTemplate<String, Object> redisTemplate,
      AuditService auditService,
      UserStateCache userStateCache) {
    this.userMapper = userMapper;
    this.passwordEncoder = passwordEncoder;
    this.redisTemplate = redisTemplate;
    this.auditService = auditService;
    this.userStateCache = userStateCache;
  }

  public PageResult<UserResponse> page(String keyword, String role, long page, long pageSize) {
    LambdaQueryWrapper<User> qw = new LambdaQueryWrapper<>();
    if (StringUtils.hasText(keyword)) {
      String kw = keyword.trim();
      qw.and(w -> w.like(User::getUsername, kw).or().like(User::getDisplayName, kw));
    }
    if (StringUtils.hasText(role)) {
      qw.eq(User::getRole, role.trim());
    }
    qw.orderByAsc(User::getId);
    Page<User> p = userMapper.selectPage(new Page<>(page, pageSize), qw);
    return PageResult.of(
        p.getRecords().stream().map(this::toResponse).toList(), p.getTotal(), page, pageSize);
  }

  public UserResponse detail(Long id) {
    return toResponse(require(id));
  }

  @Transactional
  public UserResponse create(UserCreateRequest req) {
    Long exists =
        userMapper.selectCount(
            new LambdaQueryWrapper<User>().eq(User::getUsername, req.getUsername().trim()));
    if (exists != null && exists > 0) {
      throw new BusinessException(ErrorCode.USER_DUPLICATE);
    }
    validatePasswordStrength(req.getPassword());
    User user = new User();
    user.setUsername(req.getUsername().trim());
    user.setDisplayName(req.getDisplayName().trim());
    user.setRole(req.getRole());
    user.setPasswordHash(passwordEncoder.encode(req.getPassword()));
    user.setEnabled(true);
    user.setTokenVersion(0);
    userMapper.insert(user);
    auditService.record("CREATE", "USER", user.getId(), "创建用户：" + user.getUsername());
    return toResponse(user);
  }

  @Transactional
  public UserResponse update(Long id, UserUpdateRequest req) {
    User user = require(id);
    Long currentUserId = SecurityUtil.currentUserId();
    boolean disablingSelf = Boolean.FALSE.equals(req.getEnabled()) && id.equals(currentUserId);
    if (disablingSelf) {
      throw new BusinessException(ErrorCode.FORBIDDEN, "不能停用当前登录账号");
    }
    boolean changingAdmin = "ADMIN".equals(user.getRole());
    boolean removingAdminRole =
        StringUtils.hasText(req.getRole()) && !"ADMIN".equals(req.getRole());
    boolean disabling = Boolean.FALSE.equals(req.getEnabled());
    if (changingAdmin && (removingAdminRole || disabling)) {
      ensureOtherEnabledAdmin(id);
    }
    if (StringUtils.hasText(req.getDisplayName())) {
      user.setDisplayName(req.getDisplayName().trim());
    }
    if (StringUtils.hasText(req.getRole())) {
      user.setRole(req.getRole());
    }
    if (req.getEnabled() != null) {
      user.setEnabled(req.getEnabled());
    }
    user.setVersion(req.getVersion());
    int rows = userMapper.updateById(user);
    if (rows == 0) {
      throw new BusinessException(ErrorCode.VERSION_CONFLICT);
    }
    userStateCache.evict(id);
    auditService.record("UPDATE", "USER", id, "编辑用户：" + user.getUsername());
    return toResponse(userMapper.selectById(id));
  }

  /** FR-005：管理员重置密码，旧令牌全部失效。 */
  @Transactional
  public void resetPassword(Long id, ResetPasswordRequest req) {
    User user = require(id);
    validatePasswordStrength(req.getNewPassword());
    user.setPasswordHash(passwordEncoder.encode(req.getNewPassword()));
    user.setTokenVersion((user.getTokenVersion() == null ? 0 : user.getTokenVersion()) + 1);
    userMapper.updateById(user);
    invalidateUserTokens(user.getId());
    userStateCache.evict(user.getId());
    auditService.record("RESET_PASSWORD", "USER", id, "重置密码：" + user.getUsername());
  }

  /** FR-006：登录用户修改自己的密码，旧令牌失效。 */
  @Transactional
  public void changeOwnPassword(ChangePasswordRequest req) {
    Long currentUserId = SecurityUtil.currentUserId();
    User user = require(currentUserId);
    if (!passwordEncoder.matches(req.getOldPassword(), user.getPasswordHash())) {
      throw new BusinessException(ErrorCode.INVALID_CREDENTIALS, "旧密码不正确");
    }
    validatePasswordStrength(req.getNewPassword());
    user.setPasswordHash(passwordEncoder.encode(req.getNewPassword()));
    user.setTokenVersion((user.getTokenVersion() == null ? 0 : user.getTokenVersion()) + 1);
    userMapper.updateById(user);
    invalidateUserTokens(user.getId());
    userStateCache.evict(user.getId());
    auditService.record("CHANGE_PASSWORD", "USER", user.getId(), "修改自身密码");
  }

  public User require(Long id) {
    User user = userMapper.selectById(id);
    if (user == null) {
      throw new BusinessException(ErrorCode.USER_NOT_FOUND);
    }
    return user;
  }

  private void ensureOtherEnabledAdmin(Long excludeId) {
    Long count =
        userMapper.selectCount(
            new LambdaQueryWrapper<User>()
                .eq(User::getRole, "ADMIN")
                .eq(User::getEnabled, true)
                .ne(User::getId, excludeId));
    if (count == null || count == 0) {
      throw new BusinessException(ErrorCode.FORBIDDEN, "系统必须至少保留一个启用状态的 ADMIN");
    }
  }

  private void invalidateUserTokens(Long userId) {
    try {
      redisTemplate.delete(REFRESH_KEY_PREFIX + userId);
    } catch (Exception ex) {
      log.warn("Failed to invalidate refresh token: {}", ex.getMessage());
    }
  }

  private void validatePasswordStrength(String password) {
    if (password == null || !password.matches(".*[A-Za-z].*") || !password.matches(".*[0-9].*")) {
      throw new BusinessException(ErrorCode.BAD_REQUEST, "密码须同时包含字母与数字");
    }
  }

  private UserResponse toResponse(User user) {
    UserResponse resp = new UserResponse();
    resp.setId(user.getId());
    resp.setUsername(user.getUsername());
    resp.setDisplayName(user.getDisplayName());
    resp.setRole(user.getRole());
    resp.setEnabled(user.getEnabled());
    resp.setLastLoginAt(user.getLastLoginAt());
    resp.setVersion(user.getVersion());
    resp.setCreatedAt(user.getCreatedAt());
    return resp;
  }
}
