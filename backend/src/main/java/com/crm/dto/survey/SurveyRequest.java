package com.crm.dto.survey;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** 工单满意度评分请求（FR-S01）。 */
@Data
public class SurveyRequest {

  @NotNull(message = "评分不能为空")
  @Min(value = 1, message = "评分须在 1-5 之间")
  @Max(value = 5, message = "评分须在 1-5 之间")
  private Integer rating;

  @Size(max = 500, message = "评语不能超过 500 字")
  private String comment;
}
