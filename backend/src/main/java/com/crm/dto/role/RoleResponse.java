package com.crm.dto.role;

import java.util.List;
import lombok.Data;

/** 角色响应（028-role-permissions）。 */
@Data
public class RoleResponse {

  private Long id;
  private String code;
  private String name;
  private String description;
  private String dataScope;
  private Boolean enabled;
  private Boolean builtIn;
  private List<String> menus;
  private List<String> permissions;
}
