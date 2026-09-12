package com.crm.dto.user;

import jakarta.validation.constraints.Size;
import lombok.Data;

/** 编辑用户请求（FR-004）。 */
@Data
public class UserUpdateRequest {

  @Size(max = 50, message = "显示名不能超过 50 字")
  private String displayName;

  /**
   * 角色编码。
   *
   * <p>这里原先写的是 {@code @Pattern("^(ADMIN|SALES|SUPPORT)$")}，而 081 已经在 {@code role} 表里新增了 10 个
   * 预置角色（SALES_MANAGER、SUPPORT_AGENT、FINANCE_MANAGER、ANALYST…）。后果是：新建用户时能选这些角色 （{@code
   * UserCreateRequest} 只校验命名格式），建完却**永远改不掉**——把角色从 SALES_REP 调成 SALES_MANAGER 会被这条正则拒掉，提示还写着「角色须为
   * ADMIN/SALES/SUPPORT」，即把过时的规则当成规则本身。
   *
   * <p>改为由 {@code UserService} 校验角色在 {@code role} 表中真实存在且启用（与创建路径同一个 {@code
   * validateRole}）：这样「哪些角色可选」只有一处来源（角色表），新增角色不必再改这里。
   */
  private String role;

  private Boolean enabled;

  private Integer version;
}
