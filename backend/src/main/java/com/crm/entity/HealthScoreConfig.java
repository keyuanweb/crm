package com.crm.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

/** 客户健康度评分配置（018-customer-360）：维度权重与参数可配置。 */
@Getter
@Setter
@TableName("health_score_config")
public class HealthScoreConfig extends BaseEntity {

  /** FOLLOWUP / PAYMENT / TICKET / DEPTH / ACTIVITY。 */
  private String dimensionKey;

  private String dimensionLabel;

  /** 权重（合计 100）。 */
  private Integer weight;

  /** 参数 JSON（天数阈值等）。 */
  private String paramsJson;

  private Integer enabled;
  private Integer sortOrder;
}
