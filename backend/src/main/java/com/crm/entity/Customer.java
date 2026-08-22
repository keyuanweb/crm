package com.crm.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

/** 客户（核心主数据，data-model.md §2）。 */
@Getter
@Setter
@TableName("customer")
public class Customer extends BaseEntity {

  private String name;
  private String company;
  private String contactPerson;
  private String phone;
  private String email;
  private String address;
  private String remark;

  /** ACTIVE / INACTIVE。 */
  private String status;

  private Long createdBy;
}
