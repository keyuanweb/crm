package com.crm.dto.sla;

import jakarta.validation.constraints.NotNull;
import java.util.List;
import lombok.Data;

/** SLA 日历配置请求（FR-L01/L02/L04）。 */
@Data
public class SlaCalendarConfigRequest {

  /** 工作时间段（可多段）。 */
  private List<WorkSlot> workSlots;

  /** 工作周（1-7，1=周一）。 */
  private List<Integer> workDays;

  /** 节假日（YYYY-MM-DD）。 */
  private List<String> holidays;

  @NotNull(message = "启用状态不能为空")
  private Boolean enabled;

  @Data
  public static class WorkSlot {
    private String start;
    private String end;
  }
}
