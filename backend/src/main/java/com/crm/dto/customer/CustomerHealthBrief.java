package com.crm.dto.customer;

import java.time.LocalDateTime;
import lombok.Data;

/** 客户健康度预警列表项（018-customer-360，FR-005）。 */
@Data
public class CustomerHealthBrief {

  private Long id;
  private String name;
  private String company;

  /** 健康度 0-100。 */
  private int healthScore;

  private LocalDateTime lastFollowUpAt;
  private LocalDateTime lastOrderAt;

  /** 无业务活动天数。 */
  private int daysInactive;

  private String ownerName;
}
