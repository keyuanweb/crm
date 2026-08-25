package com.crm.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

/** 托管落地页（053-landing-page）。 */
@Getter
@Setter
@TableName("landing_page")
public class LandingPage extends BaseEntity {

  private String title;
  private String subtitle;
  private String description;
  private String themeColor;

  /** 关联在线表单。 */
  private Long formId;

  private Integer enabled;
  private Long createdBy;
}
