package com.crm.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/** 安全上下文工具。 */
public final class SecurityUtil {

  private SecurityUtil() {}

  /** 当前认证主体；未认证返回 null。 */
  public static JwtAuthFilter.CrmPrincipal currentPrincipal() {
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    if (auth == null || !(auth.getPrincipal() instanceof JwtAuthFilter.CrmPrincipal p)) {
      return null;
    }
    return p;
  }

  public static Long currentUserId() {
    JwtAuthFilter.CrmPrincipal p = currentPrincipal();
    return p == null ? null : p.userId();
  }
}
