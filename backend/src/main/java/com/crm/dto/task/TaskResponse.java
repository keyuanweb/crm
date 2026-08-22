package com.crm.dto.task;

import java.time.LocalDateTime;
import lombok.Data;

/** 任务响应（含提醒标识，contracts/tasks.md）。 */
@Data
public class TaskResponse {

  private Long id;
  private String title;
  private LocalDateTime dueAt;
  private String priority;
  private String status;
  private String linkedType;
  private Long linkedId;
  private String remark;

  /** OVERDUE / TODAY / NORMAL / DONE。 */
  private String reminderStatus;

  /** 逾期天数（仅逾期时）。 */
  private Long overdueDays;

  private Integer version;
  private LocalDateTime createdAt;
}
