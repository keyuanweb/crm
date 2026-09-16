package com.crm.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.crm.common.BusinessException;
import com.crm.common.ErrorCode;
import com.crm.dto.auth.AuthResponse;
import com.crm.dto.auth.LoginRequest;
import com.crm.dto.auth.MfaChallenge;
import com.crm.dto.auth.RefreshRequest;
import com.crm.dto.auth.UserInfo;
import com.crm.entity.User;
import com.crm.repository.UserMapper;
import com.crm.security.ClientIpResolver;
import com.crm.security.JwtUtil;
import com.crm.security.UserStateCache;
import io.jsonwebtoken.Claims;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;
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
  private static final String IP_FAIL_KEY_PREFIX = "auth:ip-fail:";
  private static final int MAX_LOGIN_FAILURES = 5;
  private static final int MAX_IP_LOGIN_FAILURES = 10;
  private static final Duration FAIL_WINDOW = Duration.ofMinutes(15);

  private final UserMapper userMapper;
  private final PasswordEncoder passwordEncoder;
  private final JwtUtil jwtUtil;
  private final RedisTemplate<String, Object> redisTemplate;
  private final UserStateCache userStateCache;
  private final CaptchaService captchaService;
  private final TokenService tokenService;
  private final MfaChallengeService mfaChallengeService;
  private final boolean captchaEnabled;

  public AuthService(
      UserMapper userMapper,
      PasswordEncoder passwordEncoder,
      JwtUtil jwtUtil,
      RedisTemplate<String, Object> redisTemplate,
      UserStateCache userStateCache,
      CaptchaService captchaService,
      TokenService tokenService,
      MfaChallengeService mfaChallengeService,
      @org.springframework.beans.factory.annotation.Value("${crm.captcha.enabled:true}")
          boolean captchaEnabled) {
    this.userMapper = userMapper;
    this.passwordEncoder = passwordEncoder;
    this.jwtUtil = jwtUtil;
    this.redisTemplate = redisTemplate;
    this.userStateCache = userStateCache;
    this.captchaService = captchaService;
    this.tokenService = tokenService;
    this.mfaChallengeService = mfaChallengeService;
    this.captchaEnabled = captchaEnabled;
  }

  public AuthResponse login(LoginRequest request, String clientIp) {
    if (captchaEnabled) {
      captchaService.validate(request.getCaptchaId(), request.getCaptchaCode());
    }
    // IP 维度限流（T064）：同一 IP 短时间多次失败（分布式爆破防护）
    if (isIpBlocked(clientIp)) {
      throw new BusinessException(ErrorCode.UNAUTHORIZED, "登录尝试过于频繁，请 15 分钟后再试");
    }
    if (isBlocked(request.getUsername())) {
      throw new BusinessException(ErrorCode.UNAUTHORIZED, "登录失败次数过多，请 15 分钟后再试");
    }
    User user =
        userMapper.selectOne(
            new LambdaQueryWrapper<User>().eq(User::getUsername, request.getUsername()));
    if (user == null || !passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
      recordFailure(request.getUsername());
      recordIpFailure(clientIp);
      throw new BusinessException(ErrorCode.INVALID_CREDENTIALS);
    }
    clearFailures(request.getUsername());
    clearIpFailures(clientIp);
    if (!Boolean.TRUE.equals(user.getEnabled())) {
      throw new BusinessException(ErrorCode.FORBIDDEN, "账号已停用");
    }
    // 082（FR-M07）：口令对了、账号没停用，但该账号启用了 2FA ⇒ 密码阶段到此为止，改走一次性票据的二次验证。
    //
    // 位置刻意在 lastLoginAt 更新与签发**之前**（这两件事都挪到 MfaVerificationService 成功之后做）：
    // 此时"登录"尚未完成，而 last_login_at 常被用来判断某账号最近活没活跃 —— 若在这里就写，
    // 一次失败/未完成的二次验证会在库里看起来像一次成功登录。未启用 2FA 的账号不受影响：
    // challengeFor 返回空、本分支整体跳过，下面的语句与 082 之前逐字相同（FR-M14，由 LoginResponseShapeIT 守）。
    //
    // challengeFor 会签一张票据并写 Redis；Redis 不可用时它抛 503（fail closed），
    // 即"Redis 挂了时已启用 2FA 的账号登不进来"，而不是静默地少验一个因素。
    Optional<MfaChallenge> challenge = mfaChallengeService.challengeFor(user);
    if (challenge.isPresent()) {
      return AuthResponse.mfaChallenge(challenge.get().token(), challenge.get().expiresInSeconds());
    }
    // FR-002：更新最后登录时间
    // 085（FR-V01）：此处必须是**定向单列更新**，不得用实体级 updateById。
    // 原因：User 继承 BaseEntity，带 @Version；实体非空且携带 @Version 时，MyBatis-Plus 的乐观锁
    // 插件会生成 `SET version = version + 1 WHERE id = ? AND version = ?`。于是"登录"这个**读语义**
    // 操作消费掉了该用户对外的并发编辑令牌 —— 管理端"读过某用户 → 该用户自己登录了一次 → 管理端
    // 编辑该用户"必然收到 409 VERSION_CONFLICT，而错误信息指向一个根本不存在的原因（"他人修改"），
    // 且随该用户的登录频率随机出现。乐观锁保护的是**编辑**冲突，多人同时登录之间并无冲突可言。
    // 实体传 null 时插件不介入，故本条语句不会推进 version。
    // 附带收益：不再是整行回写。原写法把 passwordHash/role/enabled 等一并写回，会静默覆盖
    // "读后到写前"的他人改动（一类丢更新）。
    // updated_at 不会因此陈旧 —— 它在 V1__init.sql:15 定义为 ON UPDATE CURRENT_TIMESTAMP，由数据库维护。
    LocalDateTime loginAt = LocalDateTime.now();
    user.setLastLoginAt(loginAt);
    userMapper.update(
        null,
        new LambdaUpdateWrapper<User>()
            .eq(User::getId, user.getId())
            .set(User::getLastLoginAt, loginAt));
    // 预热用户状态缓存，减少登录后首次请求的 DB 查询
    int tv = user.getTokenVersion() == null ? 0 : user.getTokenVersion();
    userStateCache.put(
        user.getId(), new UserStateCache.UserState(Boolean.TRUE.equals(user.getEnabled()), tv));
    // 082：这里在"验证码/口令/停用检查之后、签发之前"插入二次验证分支（第 9 步）。
    // 未启用 2FA 的账号在此处的行为与 082 之前逐字相同——issue() 就是原来的 issueTokens()。
    return tokenService.issue(user);
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

  /** IP 维度登录限流（T064）：同一 IP 失败累计 MAX_IP_LOGIN_FAILURES 次后锁 15 分钟。 */
  private boolean isIpBlocked(String clientIp) {
    if (clientIp == null || clientIp.isBlank()) {
      return false;
    }
    try {
      Object value = redisTemplate.opsForValue().get(IP_FAIL_KEY_PREFIX + clientIp);
      return value != null && Integer.parseInt(value.toString()) >= MAX_IP_LOGIN_FAILURES;
    } catch (Exception ex) {
      log.warn("Failed to read ip-failure counter: {}", ex.getMessage());
      return false;
    }
  }

  private void recordIpFailure(String clientIp) {
    if (clientIp == null || clientIp.isBlank()) {
      return;
    }
    try {
      String key = IP_FAIL_KEY_PREFIX + clientIp;
      redisTemplate.opsForValue().increment(key);
      redisTemplate.expire(key, FAIL_WINDOW);
    } catch (Exception ex) {
      log.warn("Failed to record ip failure: {}", ex.getMessage());
    }
  }

  private void clearIpFailures(String clientIp) {
    if (clientIp == null || clientIp.isBlank()) {
      return;
    }
    try {
      redisTemplate.delete(IP_FAIL_KEY_PREFIX + clientIp);
    } catch (Exception ex) {
      log.warn("Failed to clear ip-failure counter: {}", ex.getMessage());
    }
  }

  /**
   * 提取客户端 IP：X-Forwarded-For 首个地址优先（代理场景），回退 remoteAddr。
   *
   * <p>⚠️ <b>2026-09-16（100-rate-limit-consolidation）</b>：方法体已换成对 {@link
   * ClientIpResolver#firstForwardedFor} 的 1 行委托 —— 改造前本方法与 {@code
   * EmailTrackController#clientIp}、{@code FormService#clientIp}
   * 是<b>三份逐字副本</b>，现收敛成一份。**签名与语义逐字不变**（本方法是 {@code public static}，被 {@code AuthController}
   * 与测试直接调用，保留签名才能做到那两处零改动）， 唯一的差异是畸形输入：旧体写 {@code forwarded.split(",")[0].trim()}，对 {@code
   * X-Forwarded-For: ","} 会抛 {@link ArrayIndexOutOfBoundsException}（{@code split} 丢弃末尾空段 ⇒ 长度 0
   * 的数组）； 新体对三种退化输入一律回退 {@code fallback}。这是**净收益**，不是行为破坏。
   *
   * <p>⚠️ <b>已知且刻意的口径不一致</b>：本方法走的是 {@code ClientIpResolver} 的<b>静态</b>入口，因此<b>不读</b> {@code
   * crm.rate-limit.trust-forwarded-for}（那个开关只有限流侧的 {@code RateLimitIdentity} 会读）。 后果：把该开关设成 {@code
   * false} 的部署里，<b>登录失败锁定的 IP 分桶仍看 XFF</b>，与限流侧口径不同。
   * 按用户裁决登录的两层锁定本批不动（语义是「锁到解锁为止」而非「窗口内容量」），故不把它一起收进来 —— 这是**如实登记的已知不一致**，不是漏改。
   */
  public static String resolveClientIp(
      jakarta.servlet.http.HttpServletRequest request, String fallback) {
    String first = ClientIpResolver.firstForwardedFor(request);
    return first == null ? fallback : first;
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
    // S5(安全审计)：refresh token 必须携带与当前 tokenVersion 一致的 tv，改密/重置后旧 refresh 立即失效
    int tokenVersion = user.getTokenVersion() == null ? 0 : user.getTokenVersion();
    Integer tvClaim = claims.get("tv") instanceof Number n ? n.intValue() : null;
    if (tvClaim == null || tvClaim != tokenVersion) {
      redisTemplate.delete(REFRESH_KEY_PREFIX + userId);
      throw new BusinessException(ErrorCode.REFRESH_TOKEN_INVALID);
    }
    String newAccess = tokenService.accessToken(user);
    return new AuthResponse(newAccess, request.getRefreshToken(), tokenService.toUserInfo(user));
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
    return tokenService.toUserInfo(user);
  }
}
