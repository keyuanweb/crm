package com.crm.dto.role;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 角色下拉选项（028-role-permissions）。 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RoleOption {

  private Long id;
  private String code;
  private String name;
  private String dataScope;
}
