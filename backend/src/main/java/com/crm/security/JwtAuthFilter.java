package com.crm.security;

import com.crm.entity.User;
import com.crm.repository.UserMapper;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * JWT 认证过滤器：解析 Bearer 令牌、校验账号状态与令牌版本（research.md R1，FR-005/006）。
 *
 * <p>用户状态（enabled/tokenVersion）通过 UserStateCache 缓存（TTL 30s），缓存命中时零 DB 查询；未命中时查 DB 并回写缓存。用户状态变更时主动
 * evict。
 */
@Component
public class JwtAuthFilter extends OncePerRequestFilter {

  private static final Logger log = LoggerFactory.getLogger(JwtAuthFilter.class);

  private final JwtUtil jwtUtil;
  private final UserMapper userMapper;
  private final UserStateCache userStateCache;

  public JwtAuthFilter(JwtUtil jwtUtil, UserMapper userMapper, UserStateCache userStateCache) {
    this.jwtUtil = jwtUtil;
    this.userMapper = userMapper;
    this.userStateCache = userStateCache;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    String header = request.getHeader("Authorization");
    if (header != null
        && header.startsWith("Bearer ")
        && SecurityContextHolder.getContext().getAuthentication() == null) {
      String token = header.substring(7);
      try {
        Claims claims = jwtUtil.parse(token);
        String username = claims.getSubject();
        Object userIdObj = claims.get("userId");
        Object roleObj = claims.get("role");
        Object tvObj = claims.get("tv");
        Long userId = userIdObj instanceof Number n ? n.longValue() : null;
        String role = roleObj instanceof String s ? s : "";
        if (username != null && userId != null) {
          // FR-005/FR-006/SC-004：密码变更/重置后 tokenVersion 递增，旧令牌失效；停用账号即时失效
          int claimTv = tvObj instanceof Number n ? n.intValue() : -1;
          boolean tokenValid = validateUserState(userId, claimTv);
          if (tokenValid) {
            var authentication =
                new UsernamePasswordAuthenticationToken(
                    new CrmPrincipal(userId, username, role),
                    null,
                    List.of(new SimpleGrantedAuthority("ROLE_" + role)));
            authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
            SecurityContextHolder.getContext().setAuthentication(authentication);
          }
        }
      } catch (Exception ex) {
        log.debug("Invalid JWT token: {}", ex.getMessage());
      }
    }
    filterChain.doFilter(request, response);
  }

  /**
   * 校验用户状态：优先读缓存，未命中查 DB 并回写。
   *
   * @return true 如果用户存在、启用且令牌版本匹配
   */
  private boolean validateUserState(Long userId, int claimTv) {
    UserStateCache.UserState cached = userStateCache.get(userId);
    if (cached != null) {
      return cached.enabled() && cached.tokenVersion() == claimTv;
    }
    // 缓存未命中：查 DB 并回写
    User user = userMapper.selectById(userId);
    if (user == null) {
      return false;
    }
    int dbTv = user.getTokenVersion() == null ? 0 : user.getTokenVersion();
    boolean enabled = Boolean.TRUE.equals(user.getEnabled());
    userStateCache.put(userId, new UserStateCache.UserState(enabled, dbTv));
    return enabled && dbTv == claimTv;
  }

  /** 认证主体：暴露 userId/role 供业务层使用。 */
  public record CrmPrincipal(Long userId, String username, String role) {}
}
