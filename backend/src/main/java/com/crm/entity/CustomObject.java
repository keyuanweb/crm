package com.crm.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

/** 自定义对象定义（059-custom-object）。 */
@Getter
@Setter
@TableName("custom_object")
public class CustomObject extends BaseEntity {

  private String name;

  /** 对象编码（唯一，大写字母/数字/下划线）。 */
  private String code;

  /** 字段集 JSON。 */
  private String fields;

  private Integer enabled;

  @TableField(fill = FieldFill.INSERT)
  private Long createdBy;
}
