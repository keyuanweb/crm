package com.crm.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

/** 角色-菜单关联（028-role-permissions）。 */
@Getter
@Setter
@TableName("role_menu")
public class RoleMenu {

  private Long id;
  private Long roleId;
  private String menuKey;
}
