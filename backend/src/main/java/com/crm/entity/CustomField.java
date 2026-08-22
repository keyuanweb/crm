package com.crm.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

/** 自定义字段定义（016-system-enhancement）。 */
@Getter
@Setter
@TableName("custom_field")
public class CustomField extends BaseEntity {

  /** LEAD / CUSTOMER / OPPORTUNITY / TICKET。 */
  private String entityType;

  private String name;

  /** TEXT / TEXTAREA / NUMBER / DATE / SELECT。 */
  private String fieldType;

  private Integer required;

  /** SELECT 选项（逗号分隔）。 */
  private String options;

  private Integer enabled;
  private Integer sortOrder;
  private Long createdBy;
}
