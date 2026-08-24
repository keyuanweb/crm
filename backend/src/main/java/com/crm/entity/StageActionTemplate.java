package com.crm.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

/** 阶段动作模板（045-sales-playbook，管理端配置）。 */
@Getter
@Setter
@TableName("stage_action_template")
public class StageActionTemplate extends BaseEntity {

  /** INITIAL_CONTACT / NEGOTIATING（仅活动阶段）。 */
  private String stage;

  private String actionName;
  private String description;
  private Integer sortOrder;
  private Integer required;
  private Integer enabled;
  private Long createdBy;
}
