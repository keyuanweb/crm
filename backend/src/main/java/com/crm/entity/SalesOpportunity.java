package com.crm.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/** 销售机会（子实体，data-model.md §4）。 */
@Getter
@Setter
@TableName("sales_opportunity")
public class SalesOpportunity extends BaseEntity {

  private Long opportunityId;
  private Long amount;

  /** INITIAL_CONTACT / NEGOTIATING / CLOSED_WON / CLOSED_LOST。 */
  private String stage;

  private LocalDate expectedCloseDate;

  /** WON / LOST。 */
  private String closeResult;

  private LocalDateTime closedAt;
  private Long createdBy;
}
