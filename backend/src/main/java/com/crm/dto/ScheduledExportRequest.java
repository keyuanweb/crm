/** 定时导出任务创建/更新请求 DTO（079-scheduled-export）。 */
package com.crm.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ScheduledExportRequest {

  @NotNull(message = "实体类型不能为空")
  private String entityType;

  private String filterConditions;

  @NotBlank(message = "导出格式不能为空")
  private String exportFormat;

  @NotBlank(message = "Cron 表达式不能为空")
  @Pattern(regexp = "^([0-9*,/-]+\\s){4}[0-9*,/-]+$", message = "Cron 表达式格式不正确")
  private String cronExpression;
}
