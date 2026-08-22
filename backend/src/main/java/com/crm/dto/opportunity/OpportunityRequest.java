package com.crm.dto.opportunity;

import com.crm.dto.customfield.CustomFieldValueDTO;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import lombok.Data;

/** 商机创建/编辑请求（data-model.md §3）。 */
@Data
public class OpportunityRequest {

  @NotNull(message = "必须关联客户")
  private Long customerId;

  @NotBlank(message = "商机名称不能为空")
  @Size(max = 100, message = "商机名称不能超过 100 字")
  private String name;

  @Min(value = 0, message = "预期金额下限不能为负")
  private Long expectedAmountMin;

  @Min(value = 0, message = "预期金额上限不能为负")
  private Long expectedAmountMax;

  @Size(max = 500)
  private String remark;

  private String status;

  /** 自定义字段值（016，可选）。 */
  private List<CustomFieldValueDTO> customFieldValues;

  private Integer version;
}
