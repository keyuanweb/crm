package com.crm.security;

import com.crm.entity.ApiKey;
import com.crm.service.ApiKeyService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/** API Key 鉴权过滤器（055）：/api/v1/open/** 用 X-API-Key 校验，通过后注入 鉴权上下文（角色 OPEN_API + key id）。 */
public class ApiKeyAuthFilter extends OncePerRequestFilter {

  private final ApiKeyService apiKeyService;

  public ApiKeyAuthFilter(ApiKeyService apiKeyService) {
    this.apiKeyService = apiKeyService;
  }

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    return !request.getRequestURI().startsWith("/api/v1/open/");
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    String rawKey = request.getHeader("X-API-Key");
    try {
      ApiKey key = apiKeyService.authenticate(rawKey);
      // 主体为机器主体（FR-G11）：userId 取密钥所属主体（创建者）用于归属与审计，
      // 但角色不是 ADMIN —— 注入 ADMIN 会使 EntityAccessService.isUnrestricted() 与
      // PermissionAspect 同时短路，任何有效密钥都拿到全量数据（改造前的两处提权）。
      // details 保留 API Key 主体供开放端点 scope 校验
      var principal =
          new JwtAuthFilter.CrmPrincipal(key.getCreatedBy(), "open-api", "OPEN_API", true);
      var auth =
          new UsernamePasswordAuthenticationToken(
              principal, null, java.util.List.of(new SimpleGrantedAuthority("ROLE_OPEN_API")));
      auth.setDetails(new ApiKeyPrincipal(key.getId(), key.getName()));
      SecurityContextHolder.getContext().setAuthentication(auth);
      filterChain.doFilter(request, response);
    } catch (Exception ex) {
      // 鉴权失败：不注入上下文 → 后续授权拦截返回 401/403
      filterChain.doFilter(request, response);
    }
  }

  /** API Key 主体（供开放端点读取）。 */
  public record ApiKeyPrincipal(Long keyId, String name) {}
}
