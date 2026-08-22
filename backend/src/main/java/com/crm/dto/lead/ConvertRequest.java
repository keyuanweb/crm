package com.crm.dto.lead;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ConvertRequest {

  @NotBlank(message = "商机名称不能为空")
  @Size(max = 100)
  private String opportunityName;

  @NotNull(message = "预期金额不能为空")
  @PositiveOrZero(message = "预期金额不能为负")
  private Long expectedAmount;

  @Size(max = 500)
  private String remark;
}
