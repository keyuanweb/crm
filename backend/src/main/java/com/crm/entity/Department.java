package com.crm.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

/** 部门（012-data-permission，树形单上级）。 */
@Getter
@Setter
@TableName("department")
public class Department extends BaseEntity {

  private String name;

  /** 上级部门（空 = 顶级）。 */
  private Long parentId;

  private Long createdBy;
}
