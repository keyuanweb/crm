package com.crm.dto.personal;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** 更新显示名请求。 */
@Data
public class UpdateDisplayNameRequest {

  @NotBlank(message = "显示名不能为空")
  @Size(min = 3, max = 50, message = "显示名长度必须在 3~50 位之间")
  private String displayName;
}
