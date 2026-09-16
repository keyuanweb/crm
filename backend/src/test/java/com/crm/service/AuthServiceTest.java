package com.crm.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.crm.common.BusinessException;
import com.crm.dto.auth.LoginRequest;
import com.crm.entity.User;
import com.crm.repository.UserMapper;
import com.crm.security.JwtUtil;
import com.crm.security.UserStateCache;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Optional;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/** AuthService 单元测试（T064）：IP 维度登录限流 + 用户名锁定 + IP 解析。 */
class AuthServiceTest {

  /**
   * 把 {@code User} 的 MyBatis-Plus 元数据（lambda 缓存）装好 —— **本仓每一个直接构造 lambda wrapper 的单测都做这件事**（见
   * {@code CommentServiceTest} 等 40 余处）。
   *
   * <p>不加会怎样（082 第 9 步实测）：{@code AuthService.login} 成功路径上会构造 {@code new
   * LambdaUpdateWrapper<User>()...set(User::getLastLoginAt, ...)}，而该缓存是**进程级静态**的、 由某个测试类首次 {@code
   * initTableInfo} 时写入。于是本类的结果取决于**同 JVM 里有没有别的类先跑过**： 全量 {@code mvn test} 绿，而 {@code mvn test
   * -Dtest=AuthServiceTest} 会在 {@code successClearsIpFailures} 上报 {@code MybatisPlusException: can
   * not find lambda cache for this entity [com.crm.entity.User]} —— 一个与被测逻辑毫无关系的红，且只在单跑时出现。
   */
  @BeforeAll
  static void initTableInfo() {
    MybatisConfiguration configuration = new MybatisConfiguration();
    MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, "");
    TableInfoHelper.initTableInfo(assistant, User.class);
  }

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
    // 082 第 9 步：AuthService.login 多了一个"要不要走二次验证"的分支。本类测的是限流、锁定与凭据校验，
    // 全部发生在那个分支之前或与它无关，故让挑战判定恒返回 empty —— 那是**逐字等价于本次改动之前**的登录路径
    // （也正是 FR-M14 要求非 2FA 账号走的同一条路径）。
    MfaChallengeService mfaChallengeService = mock(MfaChallengeService.class);
    when(mfaChallengeService.challengeFor(any())).thenReturn(Optional.empty());
    service =
        new AuthService(
            userMapper,
            encoder,
            jwtUtil,
            redis,
            userStateCache,
            captchaService,
            // 082 第 3 步：签发令牌与装配 UserInfo 已搬到 TokenService。本类只测限流/锁定/凭据校验，
            // 对令牌内容零断言，故这里传替身——登录成功那条路径上它返回 null，用例不看返回值。
            mock(TokenService.class),
            mfaChallengeService,
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
