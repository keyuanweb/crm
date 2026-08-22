package com.crm.dto.contract;

import java.time.LocalDateTime;
import lombok.Data;

/** 合同模板响应。 */
@Data
public class ContractTemplateResponse {

  private Long id;
  private String name;
  private String content;
  private String status;
  private Integer version;
  private LocalDateTime createdAt;
}
