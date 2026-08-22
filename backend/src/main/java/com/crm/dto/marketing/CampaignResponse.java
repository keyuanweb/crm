package com.crm.dto.marketing;

import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.Data;

/** 营销活动响应（含归因计数）。 */
@Data
public class CampaignResponse {

  private Long id;
  private String name;
  private String channel;
  private Long budget;
  private Long cost;
  private LocalDate startDate;
  private LocalDate endDate;
  private String status;

  /** 归因线索数（FR-M05）。 */
  private Long leadCount;

  /** 归因客户数（FR-M05）。 */
  private Long customerCount;

  private Integer version;
  private LocalDateTime createdAt;
}
