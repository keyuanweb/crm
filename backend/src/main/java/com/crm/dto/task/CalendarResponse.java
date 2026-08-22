package com.crm.dto.task;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 日历数据响应：按月 date → tasks。 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CalendarResponse {

  private String month;
  private List<CalendarDay> days;

  @Data
  @NoArgsConstructor
  @AllArgsConstructor
  public static class CalendarDay {
    private String date;
    private List<TaskResponse> tasks;
  }
}
