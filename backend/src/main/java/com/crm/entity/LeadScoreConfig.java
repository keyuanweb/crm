package com.crm.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

/** 线索评分配置（019-lead-scoring）：评分维度与分值可配置。 */
@Getter
@Setter
@TableName("lead_score_config")
public class LeadScoreConfig extends BaseEntity {

  /** SOURCE / INFO / FOLLOWUP / FRESHNESS / THRESHOLD。 */
  private String ruleKey;

  private String ruleLabel;

  /** 参数 JSON（分值映射/阈值等）。 */
  private String paramsJson;

  private Integer enabled;
  private Integer sortOrder;
}
