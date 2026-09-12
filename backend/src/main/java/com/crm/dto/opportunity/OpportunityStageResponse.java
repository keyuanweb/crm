package com.crm.dto.opportunity;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Data;

/** 商机阶段响应。 */
@Data
public class OpportunityStageResponse {

  private Long id;
  private String code;
  private String name;
  private Integer sortOrder;
  private BigDecimal probability;

  /** ACTIVE / WON / LOST。 */
  private String stageType;

  private Integer enabled;

  /**
   * 是否为内建阶段（不可删除）。
   *
   * <p>由后端算好下发，而不是让前端自己比对编码清单：前端再抄一份清单就是又一个「两份清单会分叉」的源头。
   */
  private Boolean builtIn;

  private Integer version;
  private LocalDateTime createdAt;
}
