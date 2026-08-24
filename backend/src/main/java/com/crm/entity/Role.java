package com.crm.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

/** 角色（028-role-permissions）。 */
@Getter
@Setter
@TableName("role")
public class Role extends BaseEntity {

  /** 角色编码（唯一），如 ADMIN/SALES/SUPPORT。 */
  private String code;

  private String name;
  private String description;

  /** 默认数据范围：ALL / DEPT / SELF。 */
  private String dataScope;

  private Boolean enabled;

  /** 内建角色（1 不可删除）。 */
  private Boolean builtIn;
}
