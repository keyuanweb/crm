package com.crm.dto.followup;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;
import lombok.Data;

/** 跟进记录请求（data-model.md §5）。 */
@Data
public class FollowUpRequest {

  /** 关联客户（与 leadId 二选一）。 */
  private Long customerId;

  /** 关联线索（与 customerId 二选一）。 */
  private Long leadId;

  private Long opportunityId;

  @NotBlank(message = "跟进方式不能为空")
  @Size(max = 20)
  private String method;

  @NotBlank(message = "跟进内容不能为空")
  @Size(max = 2000, message = "跟进内容不能超过 2000 字")
  private String content;

  private LocalDateTime nextFollowUpAt;

  /** 是否自动创建跟进任务（010，需 nextFollowUpAt 非空）。 */
  private Boolean createTask;

  private Integer version;
}
