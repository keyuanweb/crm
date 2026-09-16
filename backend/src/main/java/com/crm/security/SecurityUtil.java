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
   * 当前请求所用的 API Key 的 id；非机器主体返回 {@code null}（100-rate-limit-consolidation）。
   *
   * <p><b>为什么限流需要它</b>：{@link #currentUserId()} 对机器主体返回的是**密钥创建者**的 id （{@code ApiKeyAuthFilter}
   * 注入的是 {@code key.getCreatedBy()}）⇒ 按 userId 给开放 API 分桶，会让
   * 同一个管理员创建的**多个密钥共用一个桶**：一个密钥打满、其余全部被拒，而每个密钥单独看都「没超限」。 密钥 id 由 {@code ApiKeyAuthFilter} 放在 {@code
   * authentication.getDetails()} 里。
   *
   * <p>与 {@link #isMachineSubject()} 分开取、不合并成一个 getter：两者的失败姿态不同——限定符写错时 {@code isMachineSubject()}
   * 返回 {@code false}（当成普通用户处理），而本方法返回 {@code null} （调用方各自决定退化到哪个桶）。
   */
  public static Long currentApiKeyId() {
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    if (auth == null || !(auth.getDetails() instanceof ApiKeyAuthFilter.ApiKeyPrincipal key)) {
      return null;
    }
    return key.keyId();
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
