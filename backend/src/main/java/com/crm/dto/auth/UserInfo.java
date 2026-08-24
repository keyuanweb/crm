package com.crm.dto.auth;

import java.util.List;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 当前用户信息（auth/me，028 增加菜单与操作权限）。 */
@Data
@NoArgsConstructor
public class UserInfo {

  private Long id;
  private String username;
  private String displayName;
  private String role;

  /** 可见菜单 key（028）。 */
  private List<String> menus;

  /** 操作权限码（028）。 */
  private List<String> permissions;

  public UserInfo(Long id, String username, String displayName, String role) {
    this(id, username, displayName, role, List.of(), List.of());
  }

  public UserInfo(
      Long id,
      String username,
      String displayName,
      String role,
      List<String> menus,
      List<String> permissions) {
    this.id = id;
    this.username = username;
    this.displayName = displayName;
    this.role = role;
    this.menus = menus;
    this.permissions = permissions;
  }
}
