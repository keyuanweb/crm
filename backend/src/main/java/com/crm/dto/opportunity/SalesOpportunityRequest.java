package com.crm.dto.opportunity;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import lombok.Data;

/** 销售机会创建/编辑请求（data-model.md §4）。 */
@Data
public class SalesOpportunityRequest {

  @NotNull(message = "必须归属于一个商机")
  private Long opportunityId;

  @Min(value = 0, message = "金额不能为负")
  private Long amount;

  @NotBlank(message = "阶段不能为空")
  @Size(max = 30)
  private String stage;

  private LocalDate expectedCloseDate;

  private Integer version;
}
