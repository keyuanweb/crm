package com.crm.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDate;
import lombok.Getter;
import lombok.Setter;

/** 营销活动（014-marketing）。 */
@Getter
@Setter
@TableName("marketing_campaign")
public class MarketingCampaign extends BaseEntity {

  private String name;

  /** WEBSITE/AD/EXHIBITION/REFERRAL/EMAIL/SOCIAL/OTHER。 */
  private String channel;

  /** 预算（分）。 */
  private Long budget;

  /** 成本（分）。 */
  private Long cost;

  private LocalDate startDate;
  private LocalDate endDate;

  /** PLANNING / RUNNING / ENDED。 */
  private String status;

  @TableField(fill = FieldFill.INSERT)
  private Long createdBy;
}
