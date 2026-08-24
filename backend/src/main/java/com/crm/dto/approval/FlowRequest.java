package com.crm.dto.approval;

import java.util.List;
import lombok.Data;

/** 审批流定义请求（033）。 */
@Data
public class FlowRequest {

  private String name;
  private String businessType;
  private List<FlowNode> nodes;
  private ConditionJson conditionJson;
  private Boolean enabled;

  @Data
  public static class FlowNode {
    private String name;

    /** ROLE / USER / MANAGER。 */
    private String approverType;

    private String approverValue;
  }

  @Data
  public static class ConditionJson {
    private String field;
    private String op;
    private Double value;
    private List<FlowNode> extraNodes;
  }
}
