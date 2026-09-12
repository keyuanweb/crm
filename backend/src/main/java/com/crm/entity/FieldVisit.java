package com.crm.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/** 外勤拜访（035-field-visit）。 */
@Getter
@Setter
@TableName("field_visit")
public class FieldVisit extends BaseEntity {

  private Long customerId;
  private String theme;
  private LocalDateTime visitTime;
  private Integer durationMinutes;

  /** PLANNED / DONE / CANCELED。 */
  private String status;

  private BigDecimal latitude;
  private BigDecimal longitude;
  private String locationText;
  private LocalDateTime checkInTime;
  private String summary;
  private Boolean lateFlag;

  @TableField(fill = FieldFill.INSERT)
  private Long createdBy;
}
