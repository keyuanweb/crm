package com.crm.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/** 销售机会动作完成（045-sales-playbook，销售端勾选）。 */
@Getter
@Setter
@TableName("sales_opportunity_action")
public class SalesOpportunityAction {

  private Long id;
  private Long opportunityId;
  private Long templateId;
  private Long completedBy;
  private LocalDateTime completedAt;
}
