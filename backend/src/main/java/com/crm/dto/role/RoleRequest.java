package com.crm.dto.role;

import java.util.List;
import lombok.Data;

/** 角色创建/编辑请求（028-role-permissions）。 */
@Data
public class RoleRequest {

  private String code;
  private String name;
  private String description;
  private String dataScope;
  private Boolean enabled;
  private List<String> menus;
  private List<String> permissions;
}
