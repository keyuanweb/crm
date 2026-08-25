package com.crm.dto.landing;

import java.time.LocalDateTime;
import lombok.Data;

/** 落地页响应。 */
@Data
public class LandingPageResponse {

  private Long id;
  private String title;
  private String subtitle;
  private String description;
  private String themeColor;
  private Long formId;
  private String formName;
  private Boolean enabled;
  private Integer version;
  private LocalDateTime createdAt;
}
