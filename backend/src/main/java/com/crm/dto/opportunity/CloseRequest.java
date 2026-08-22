package com.crm.dto.opportunity;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** 关闭销售机会请求（FR-014）。 */
@Data
public class CloseRequest {

  @NotBlank(message = "必须提供赢单/输单结果")
  @Size(max = 20)
  private String closeResult;

  private Integer version;
}
