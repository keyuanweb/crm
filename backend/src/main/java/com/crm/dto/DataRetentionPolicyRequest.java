/** 数据保留策略创建/更新请求 DTO（080-data-retention）。 */
package com.crm.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class DataRetentionPolicyRequest {

  @NotBlank(message = "实体类型不能为空")
  private String entityType;

  @NotNull(message = "保留期限不能为空")
  @Min(value = 1, message = "保留期限必须大于 0")
  private Integer retentionDays;

  @NotBlank(message = "归档方式不能为空")
  private String actionType;
}
