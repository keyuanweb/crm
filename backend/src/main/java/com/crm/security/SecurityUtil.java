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

  /**
   * 当前主体是否为**机器主体**（API Key，FR-G11）。
   *
   * <p>供行级数据权限判定使用：机器主体的 userId 是其所属主体，但数据边界不等于该主体的数据范围 ——否则管理员创建的密钥会按库中角色判为"无限制"并读到全量。未认证返回 {@code
   * false}（"无主体"是另一种主体，另有其分支）。
   */
  public static boolean isMachineSubject() {
    JwtAuthFilter.CrmPrincipal p = currentPrincipal();
    return p != null && p.machineSubject();
  }
}
