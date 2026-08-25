package com.crm.dto.sla;

import java.time.LocalDateTime;
import java.util.List;
import lombok.Data;

/** SLA 日历配置响应。 */
@Data
public class SlaCalendarConfigResponse {

  private Long id;
  private List<SlaCalendarConfigRequest.WorkSlot> workSlots;
  private List<Integer> workDays;
  private List<String> holidays;
  private Boolean enabled;
  private LocalDateTime updatedAt;
}
