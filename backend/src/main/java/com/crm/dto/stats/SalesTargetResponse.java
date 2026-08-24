package com.crm.dto.stats;

import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 销售目标响应（contracts/stats.md）。 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SalesTargetResponse {

  private String month;
  private Long targetAmount;

  /** 归属用户（NULL=全局目标，020）。 */
  private Long userId;

  private Long createdBy;
  private LocalDateTime updatedAt;
}
