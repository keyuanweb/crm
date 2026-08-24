package com.crm.dto.approval;

import lombok.Data;

/** 审批流定义响应（033）。 */
@Data
public class FlowResponse {

  private Long id;
  private String name;
  private String businessType;
  private String nodes;
  private String conditionJson;
  private Boolean enabled;
}
