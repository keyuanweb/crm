package com.crm.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

/** 合同模板（008-contract-management，正文含占位符）。 */
@Getter
@Setter
@TableName("contract_template")
public class ContractTemplate extends BaseEntity {

  private String name;
  private String content;

  /** ACTIVE / INACTIVE。 */
  private String status;

  @TableField(fill = FieldFill.INSERT)
  private Long createdBy;
}
