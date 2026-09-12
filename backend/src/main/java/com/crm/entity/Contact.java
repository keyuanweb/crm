package com.crm.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

/** 联系人（005-contact-management，客户一对多）。 */
@Getter
@Setter
@TableName("contact")
public class Contact extends BaseEntity {

  private Long customerId;
  private String name;
  private String title;
  private String phone;
  private String email;

  /** DECISION_MAKER / INFLUENCER / EVALUATOR / CHAMPION / OTHER。 */
  private String role;

  private String remark;

  @TableField(fill = FieldFill.INSERT)
  private Long createdBy;
}
