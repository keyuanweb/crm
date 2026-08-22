package com.crm.dto.export;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import java.util.Map;
import lombok.Data;

/** 导出任务请求（FR-S08）。 */
@Data
public class ExportRequest {

  @NotBlank(message = "导出类型不能为空")
  @Pattern(regexp = "^(LEAD|CUSTOMER|OPPORTUNITY|TICKET)$", message = "导出类型不合法")
  private String exportType;

  /** 筛选条件（与列表接口 query 对齐）。 */
  private Map<String, Object> filter;
}
