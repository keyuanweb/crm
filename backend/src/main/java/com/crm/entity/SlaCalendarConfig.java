package com.crm.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/** SLA 工作日历配置（054-sla-calendar，全局单条）。 */
@Getter
@Setter
@TableName("sla_calendar_config")
public class SlaCalendarConfig {

  private Long id;

  /** 工作时间段 JSON。 */
  private String workSlots;

  /** 工作周 JSON（1-7，1=周一）。 */
  private String workDays;

  /** 节假日 JSON。 */
  private String holidays;

  private Integer enabled;
  private LocalDateTime createdAt;
  private LocalDateTime updatedAt;
}
