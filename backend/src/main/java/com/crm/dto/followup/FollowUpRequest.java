package com.crm.dto.followup;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;
import lombok.Data;

/** 跟进记录请求（data-model.md §5）。 */
@Data
public class FollowUpRequest {

  @NotNull(message = "必须关联客户")
  private Long customerId;

  private Long opportunityId;

  @NotBlank(message = "跟进方式不能为空")
  @Size(max = 20)
  private String method;

  @NotBlank(message = "跟进内容不能为空")
  @Size(max = 2000, message = "跟进内容不能超过 2000 字")
  private String content;

  private LocalDateTime nextFollowUpAt;

  private Integer version;
}
