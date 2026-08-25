package com.crm.dto.call;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** 通话记录请求。 */
@Data
public class CallRecordRequest {

  private Long customerId;

  private Long contactId;

  @NotBlank(message = "通话方向不能为空")
  @Pattern(regexp = "^(INBOUND|OUTBOUND)$", message = "通话方向不合法")
  private String direction;

  @Min(value = 0, message = "时长不能为负")
  @Max(value = 86400, message = "时长超出范围")
  private Integer durationSeconds;

  @NotBlank(message = "通话结果不能为空")
  @Pattern(regexp = "^(CONNECTED|NO_ANSWER|BUSY|FAILED)$", message = "通话结果不合法")
  private String result;

  @Size(max = 500, message = "备注不能超过 500 字")
  private String remark;
}
