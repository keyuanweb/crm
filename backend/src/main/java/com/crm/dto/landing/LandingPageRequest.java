package com.crm.dto.landing;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** 落地页请求（FR-L01）。 */
@Data
public class LandingPageRequest {

  @NotBlank(message = "标题不能为空")
  @Size(max = 200, message = "标题不能超过 200 字")
  private String title;

  @Size(max = 500, message = "副标题不能超过 500 字")
  private String subtitle;

  private String description;

  @Size(max = 20, message = "品牌色不合法")
  private String themeColor;

  @NotNull(message = "必须关联在线表单")
  private Long formId;

  private Boolean enabled;

  private Integer version;
}
