package com.crm.dto.workflow;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.Map;
import lombok.Data;

/** 工作流规则创建/编辑请求（FR-W01）。 */
@Data
public class WorkflowRuleRequest {

  @NotBlank(message = "规则名称不能为空")
  @Size(max = 100, message = "名称不能超过 100 字")
  private String name;

  @NotBlank(message = "触发事件不能为空")
  @Pattern(
      regexp =
          "^(LEAD_CREATED|OPPORTUNITY_STAGE_CHANGED|FOLLOW_UP_CREATED|PAYMENT_RECORDED|"
              + "LEAD_SCORE_THRESHOLD|TAG_CHANGED)$",
      message = "触发事件不合法")
  private String eventType;

  /** 条件（单一等值）：field/value。 */
  private Map<String, String> condition;

  @NotBlank(message = "动作类型不能为空")
  @Pattern(regexp = "^(CREATE_TASK|ASSIGN|NOTIFY|SEND_EMAIL|ADD_TAG)$", message = "动作类型不合法")
  private String actionType;

  @NotNull(message = "动作配置不能为空")
  private Map<String, Object> action;

  private Boolean enabled;

  private Integer version;
}
