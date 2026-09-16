package com.crm.service;

import com.crm.dto.auth.AuthResponse;
import com.crm.dto.auth.UserInfo;
import com.crm.entity.User;
import com.crm.security.JwtUtil;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

/**
 * 令牌签发与 {@link UserInfo} 装配：从 {@code AuthService} 里原样搬出来的两个方法（082 第 3 步）。
 *
 * <p><b>为什么必须单独成类</b>：2FA 引入后 {@code AuthService.login} 要调用 {@code MfaChallengeService}
 * 判断是否走二次验证，而二次验证成功后又要把用户交回"签发令牌"这条既有路径。若签发仍留在 {@code AuthService}， 就成了 {@code AuthService →
 * MfaChallengeService} 与 {@code MfaVerificationService → AuthService} 的 <b>bean 环</b>——Spring
 * 会因为字段注入与否、以及是否被 AOP 代理而时好时坏地报错或静默降级， 且这个环本身没有表达任何业务含义。抽出来之后依赖是单向的：两个 MFA 服务都指向 {@code
 * TokenService}。
 *
 * <p><b>本次搬移是机械的</b>：{@link #issue} 与 {@link #toUserInfo} 的语句、注释、行为与搬移前逐字一致， 只把 {@code
 * jwtUtil}/{@code roleService}/{@code redisTemplate} 三个依赖从 {@code AuthService} 挪到这里。
 * 门禁是既有全量用例（{@code AuthServiceTest}、{@code UserIT.loginDoesNotBumpVersion} 与 74 个 IT）——
 * 这里没有任何新行为，所以它们<b>不新增断言也不放宽</b>。
 *
 * <p>⚠️ {@link #issue} 里的 Redis 写入是 {@code auth:refresh:} 键的<b>唯一</b>写入点（登出/改密是删除方）。
 * 它不设"已存在则不覆盖"——同一用户重复登录时后者覆盖前者是既有语义（一个 refresh token 对应一个会话槽）。
 */
@Service
public class TokenService {

  private static final String REFRESH_KEY_PREFIX = "auth:refresh:";

  private final JwtUtil jwtUtil;
  private final RedisTemplate<String, Object> redisTemplate;
  private final RoleService roleService;

  public TokenService(
      JwtUtil jwtUtil, RedisTemplate<String, Object> redisTemplate, RoleService roleService) {
    this.jwtUtil = jwtUtil;
    this.redisTemplate = redisTemplate;
    this.roleService = roleService;
  }

  /**
   * 签发一对新令牌，并把 refresh token 登记进 Redis。
   *
   * <p>搬移自 {@code AuthService.issueTokens}，逐字未改。
   */
  public AuthResponse issue(User user) {
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

  /**
   * 只签发 access token：刷新流程要<b>沿用</b>调用方手上的那个 refresh token，不重新签发。
   *
   * <p>搬移前这段 {@code jwtUtil.generateAccessToken(…)} 在 {@code issueTokens} 与 {@code
   * AuthService.refresh} 里各写了一遍、字面量完全相同；这里合成一处，避免将来只改了其中一处（例如加一个 claim）
   * 而刷新出来的令牌与登录出来的令牌不一致——那种缺陷在单测里看不出来，只在某个已登录用户刷新之后才显形。
   */
  public String accessToken(User user) {
    int tokenVersion = user.getTokenVersion() == null ? 0 : user.getTokenVersion();
    return jwtUtil.generateAccessToken(
        user.getId(), user.getUsername(), user.getRole(), tokenVersion);
  }

  /**
   * 装配 {@link UserInfo}：028 起含角色菜单/权限（ADMIN 兜底全量）。
   *
   * <p>搬移自 {@code AuthService.toUserInfo}，逐字未改。
   */
  public UserInfo toUserInfo(User user) {
    // 028：角色菜单/权限（ADMIN 兜底全量）
    if ("ADMIN".equals(user.getRole())) {
      return new UserInfo(
          user.getId(),
          user.getUsername(),
          user.getDisplayName(),
          user.getRole(),
          roleService.menuTree().stream()
              .flatMap(g -> ((List<?>) g.get("children")).stream())
              .map(m -> (String) ((Map<?, ?>) m).get("key"))
              .toList(),
          roleService.permissionDefs().stream()
              .flatMap(g -> ((List<?>) g.get("children")).stream())
              .map(p -> (String) ((Map<?, ?>) p).get("code"))
              .toList());
    }
    return new UserInfo(
        user.getId(),
        user.getUsername(),
        user.getDisplayName(),
        user.getRole(),
        roleService.menusOf(user.getRole()),
        roleService.permissionsOf(user.getRole()));
  }
}
