package com.crm.dto.contract;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import lombok.Data;

/** 合同创建/编辑请求（FR-CT02/CT03）。 */
@Data
public class ContractRequest {

  @NotBlank(message = "合同标题不能为空")
  @Size(max = 200, message = "标题不能超过 200 字")
  private String title;

  @NotNull(message = "必须关联客户")
  private Long customerId;

  /** 关联报价单（可选，须已通过审批）。 */
  private Long quoteId;

  @Min(value = 0, message = "合同金额不能为负")
  private Long amount;

  private LocalDate startDate;

  private LocalDate endDate;

  private String content;

  /** 合同模板（可选，提供时用模板生成正文）。 */
  private Long templateId;

  @Size(max = 500, message = "备注不能超过 500 字")
  private String remark;

  /** 续约来源合同 id（046，可选）。 */
  private Long renewedFromId;

  private Integer version;
}
