package com.crm.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

/** 角色-操作权限关联（028-role-permissions）。 */
@Getter
@Setter
@TableName("role_permission")
public class RolePermission {

  private Long id;
  private Long roleId;
  private String permissionCode;
}
