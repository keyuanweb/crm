package com.crm.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/** 跟进记录（data-model.md §5）。 */
@Getter
@Setter
@TableName("follow_up")
public class FollowUp extends BaseEntity {

  private Long customerId;
  private Long opportunityId;

  /** 关联线索（与 customer_id 二选一）。 */
  private Long leadId;

  /** PHONE / EMAIL / MEETING / OTHER。 */
  private String method;

  private String content;
  private LocalDateTime nextFollowUpAt;
  private Long followUpBy;
}
