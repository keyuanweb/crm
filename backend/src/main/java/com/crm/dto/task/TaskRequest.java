package com.crm.dto.task;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;
import lombok.Data;

/** 任务创建/编辑请求（FR-T02/T03）。 */
@Data
public class TaskRequest {

  @NotBlank(message = "任务标题不能为空")
  @Size(max = 200, message = "标题不能超过 200 字")
  private String title;

  private LocalDateTime dueAt;

  @Pattern(regexp = "^(HIGH|MEDIUM|LOW)$", message = "优先级不合法")
  private String priority;

  @Pattern(regexp = "^(CUSTOMER|LEAD|CONTRACT|ORDER)$", message = "关联类型不合法")
  private String linkedType;

  private Long linkedId;

  @Size(max = 500, message = "备注不能超过 500 字")
  private String remark;

  private Integer version;
}
