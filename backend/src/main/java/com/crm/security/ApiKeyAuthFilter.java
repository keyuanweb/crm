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

/**
 * API Key 鉴权过滤器（055）：/api/v1/open/** 用 X-API-Key 校验，通过后注入
 * 鉴权上下文（角色 OPEN_API + key id）。
 */
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
      // 注入系统身份（ADMIN 语义，绕过行级权限）+ OPEN_API 角色；
      // details 保留 API Key 主体供开放端点 scope 校验
      var principal = new JwtAuthFilter.CrmPrincipal(0L, "open-api", "ADMIN");
      var auth =
          new UsernamePasswordAuthenticationToken(
              principal,
              null,
              java.util.List.of(new SimpleGrantedAuthority("ROLE_OPEN_API")));
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
